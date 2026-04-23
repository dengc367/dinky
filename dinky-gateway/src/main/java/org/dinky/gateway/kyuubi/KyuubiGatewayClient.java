/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.dinky.gateway.kyuubi;

import org.dinky.assertion.Asserts;
import org.dinky.gateway.model.KyuubiGatewayConfig;
import org.dinky.gateway.result.TestResult;
import org.dinky.metadata.result.JdbcSelectResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kyuubi.client.BatchRestApi;
import org.apache.kyuubi.client.KyuubiRestClient;
import org.apache.kyuubi.client.OperationRestApi;
import org.apache.kyuubi.client.SessionRestApi;
import org.apache.kyuubi.client.api.v1.dto.Batch;
import org.apache.kyuubi.client.api.v1.dto.BatchRequest;
import org.apache.kyuubi.client.api.v1.dto.ColumnDesc;
import org.apache.kyuubi.client.api.v1.dto.Field;
import org.apache.kyuubi.client.api.v1.dto.OperationHandle;
import org.apache.kyuubi.client.api.v1.dto.ResultRowSet;
import org.apache.kyuubi.client.api.v1.dto.ResultSetMetaData;
import org.apache.kyuubi.client.api.v1.dto.Row;
import org.apache.kyuubi.client.api.v1.dto.SessionHandle;
import org.apache.kyuubi.client.api.v1.dto.SessionOpenRequest;
import org.apache.kyuubi.client.api.v1.dto.StatementRequest;

/**
 * Kyuubi gateway client used by cluster configuration & task submission.
 *
 * <p>Implementation based on official kyuubi-rest-client (v1.11.1).
 */
public class KyuubiGatewayClient {

    public TestResult test(KyuubiGatewayConfig cfg) {
        try {
            String ping = ping(cfg);
            if ("pong".equalsIgnoreCase(ping == null ? null : ping.trim())) {
                return TestResult.success();
            }
            return TestResult.fail("Kyuubi ping failed: " + ping);
        } catch (Exception e) {
            return TestResult.fail(e.getMessage());
        }
    }

    private static String ping(KyuubiGatewayConfig cfg) throws Exception {
        Asserts.checkNotNull(cfg, "kyuubi config is null");
        Asserts.checkNullString(cfg.getHost(), "kyuubi host is blank");

        String base = cfg.getHost().endsWith("/") ? cfg.getHost().substring(0, cfg.getHost().length() - 1) : cfg.getHost();
        URI uri = URI.create(base + "/api/v1/ping");

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri)
                .GET();

        if (cfg.getAuthType() == KyuubiGatewayConfig.AuthType.BASIC) {
            String user = Asserts.isNullString(cfg.getUsername()) ? "" : cfg.getUsername();
            String pass = Asserts.isNullString(cfg.getPassword()) ? "" : cfg.getPassword();
            String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
            request.header("Authorization", "Basic " + token);
        }

        HttpResponse<String> resp = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (resp == null) {
            return null;
        }
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return resp.body();
        }
        return "http " + resp.statusCode() + ": " + resp.body();
    }

    public JdbcSelectResult sessionExecute(KyuubiGatewayConfig cfg, String statement, Integer maxRows) {
        Asserts.checkNullString(statement, "Sql 语句为空");
        int limit = Asserts.isNull(maxRows) ? 100 : maxRows;
        try (ClientBundle bundle = ClientBundle.open(cfg)) {
            bundle.ensureSession();
            OperationHandle op = bundle.sessionApi.executeStatement(
                    bundle.sessionId, new StatementRequest(statement, false, null));
            String opId = opId(op);
            ResultSetMetaData meta = bundle.operationApi.getResultSetMetadata(opId);
            List<String> columns = toColumns(meta);
            List<LinkedHashMap<String, Object>> rows = fetchRowData(bundle.operationApi, opId, columns, limit);
            JdbcSelectResult result = JdbcSelectResult.buildResult();
            result.setColumns(columns);
            result.setRowData(rows);
            result.success();
            return result;
        } catch (Exception e) {
            JdbcSelectResult result = JdbcSelectResult.buildResult();
            result.error(e.getMessage());
            return result;
        }
    }

    public Batch submitBatchAndWait(KyuubiGatewayConfig cfg, BatchRequest req) throws Exception {
        Asserts.checkNotNull(req, "batchRequest is null");
        try (ClientBundle bundle = ClientBundle.open(cfg)) {
            Batch batch = bundle.batchApi.createBatch(req);
            return waitBatchDone(bundle.batchApi, batch);
        }
    }

    private static String opId(OperationHandle op) {
        Asserts.checkNotNull(op, "OperationHandle is null");
        UUID id = op.getIdentifier();
        Asserts.checkNotNull(id, "OperationHandle.identifier is null");
        return id.toString();
    }

    private static List<String> toColumns(ResultSetMetaData meta) {
        List<String> columns = new ArrayList<>();
        if (meta == null || meta.getColumns() == null) {
            return columns;
        }
        for (ColumnDesc c : meta.getColumns()) {
            if (c != null && Asserts.isNotNullString(c.getColumnName())) {
                columns.add(c.getColumnName());
            }
        }
        return columns;
    }

    private static List<LinkedHashMap<String, Object>> fetchRowData(
            OperationRestApi opApi, String opId, List<String> columns, int limit) {
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        ResultRowSet rowSet = opApi.getNextRowSet(opId, "FETCH_NEXT", limit);
        if (rowSet == null || rowSet.getRows() == null) {
            return rows;
        }
        for (Row r : rowSet.getRows()) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            List<Field> fields = r == null ? null : r.getFields();
            for (int i = 0; i < columns.size(); i++) {
                Object value = null;
                if (fields != null && i < fields.size()) {
                    Field f = fields.get(i);
                    value = f == null ? null : f.getValue();
                }
                row.put(columns.get(i), value);
            }
            rows.add(row);
        }
        return rows;
    }

    private static Batch waitBatchDone(BatchRestApi api, Batch batch) throws InterruptedException {
        if (batch == null) {
            return null;
        }
        String batchId = batch.getId();
        String appState = batch.getAppState();
        while (!isAppTerminated(appState)) {
            Thread.sleep(5000L);
            Batch latest = api.getBatchById(batchId);
            if (latest != null) {
                batch = latest;
                appState = batch.getAppState();
            } else {
                break;
            }
        }
        return batch;
    }

    private static boolean isAppTerminated(String appState) {
        if (appState == null) {
            return false;
        }
        switch (appState) {
            case "FAILED":
            case "KILLED":
            case "FINISHED":
            case "NOT_FOUND":
                return true;
            default:
                return false;
        }
    }

    private static class ClientBundle implements AutoCloseable {
        private final KyuubiGatewayConfig cfg;
        private final KyuubiRestClient client;
        private final SessionRestApi sessionApi;
        private final OperationRestApi operationApi;
        private final BatchRestApi batchApi;

        private String sessionId;

        private ClientBundle(KyuubiGatewayConfig cfg, KyuubiRestClient client) {
            this.cfg = cfg;
            this.client = client;
            this.sessionApi = new SessionRestApi(client);
            this.operationApi = new OperationRestApi(client);
            this.batchApi = new BatchRestApi(client);
        }

        static ClientBundle open(KyuubiGatewayConfig cfg) {
            KyuubiRestClient.Builder builder = KyuubiRestClient.builder(cfg.getHost())
                    .apiVersion(KyuubiRestClient.ApiVersion.V1);

            if (cfg.getAuthType() == KyuubiGatewayConfig.AuthType.BASIC) {
                builder.authHeaderMethod(KyuubiRestClient.AuthHeaderMethod.BASIC)
                        .username(cfg.getUsername())
                        .password(cfg.getPassword());
            } else {
                builder.authHeaderMethod(KyuubiRestClient.AuthHeaderMethod.CUSTOM)
                        .authHeaderGenerator(() -> null);
            }

            return new ClientBundle(cfg, builder.build());
        }

        void ensureSession() {
            if (Asserts.isNotNullString(sessionId)) {
                return;
            }
            Map<String, String> configs = cfgSessionConfigs();
            SessionHandle handle = sessionApi.openSession(new SessionOpenRequest(configs));
            Asserts.checkNotNull(handle, "Create kyuubi session failed: empty response");
            Asserts.checkNotNull(handle.getIdentifier(), "Create kyuubi session failed: missing identifier");
            this.sessionId = handle.getIdentifier().toString();
            // kyuubiInstance 是没有带 http://前缀的，不可用。
            // if (Asserts.isNotNullString(handle.getKyuubiInstance())) {
            //     client.setHostUrls(handle.getKyuubiInstance());
            // }
        }

        private Map<String, String> cfgSessionConfigs() {
            Map<String, String> configs = new HashMap<>();
            if (cfg.getConf() != null) {
                configs.putAll(cfg.getConf());
            }
            if (Asserts.isNotNullString(cfg.getProfile())) {
                configs.put("kyuubi.session.conf.profile", cfg.getProfile());
            }
            // best-effort: inject engine type if provided via conf is recommended by kyuubi
            return configs;
        }

        @Override
        public void close() throws Exception {
            try {
                if (Asserts.isNotNullString(sessionId)) {
                    sessionApi.closeSession(sessionId);
                }
            } finally {
                client.close();
            }
        }
    }
}

