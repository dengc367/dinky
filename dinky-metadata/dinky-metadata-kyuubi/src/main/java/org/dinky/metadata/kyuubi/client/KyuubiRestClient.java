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

package org.dinky.metadata.kyuubi.client;

import org.dinky.assertion.Asserts;
import org.dinky.metadata.config.KyuubiConnectConfig;
import org.dinky.metadata.result.JdbcSelectResult;
import org.dinky.utils.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kyuubi.client.BatchRestApi;
import org.apache.kyuubi.client.KyuubiRestClient.AuthHeaderMethod;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class KyuubiRestClient {

    private static final Logger logger = LoggerFactory.getLogger(KyuubiRestClient.class);

    private final KyuubiConnectConfig connectConfig;
    private final org.apache.kyuubi.client.KyuubiRestClient client;
    private final SessionRestApi sessionApi;
    private final OperationRestApi operationApi;
    private final BatchRestApi batchApi;

    private final AtomicReference<String> sessionHandle = new AtomicReference<>();

    public KyuubiRestClient(KyuubiConnectConfig connectConfig) {
        Asserts.checkNotNull(connectConfig, "Kyuubi connect config is null");
        this.connectConfig = connectConfig;
        String endpoint = connectConfig.getEndpoint();
        Asserts.checkNullString(endpoint, "Kyuubi REST endpoint is empty");
        this.client = org.apache.kyuubi.client.KyuubiRestClient.builder(endpoint)
                .apiVersion(org.apache.kyuubi.client.KyuubiRestClient.ApiVersion.V1)
                .authHeaderMethod(AuthHeaderMethod.BASIC)
                .username(connectConfig.getUsername())
                .password(connectConfig.getPassword())
                .build();
        this.sessionApi = new SessionRestApi(client);
        this.operationApi = new OperationRestApi(client);
        this.batchApi = new BatchRestApi(client);
    }

    public JdbcSelectResult executeStatement(String statement, Integer maxRows) {
        Asserts.checkNullString(statement, "Sql 语句为空");
        ensureSession();

        try {
            OperationHandle opHandle = sessionApi.executeStatement(
                    sessionHandle.get(), new StatementRequest(statement, false, null));
            String opId = opHandle == null || opHandle.getIdentifier() == null
                    ? null
                    : opHandle.getIdentifier().toString();
            Asserts.checkNullString(opId, "Create statement operation failed: missing identifier");

            ResultSetMetaData meta = operationApi.getResultSetMetadata(opId);
            List<String> columns = new ArrayList<>();
            if (meta != null && meta.getColumns() != null) {
                for (ColumnDesc col : meta.getColumns()) {
                    if (col != null && Asserts.isNotNullString(col.getColumnName())) {
                        columns.add(col.getColumnName());
                    }
                }
            }

            List<LinkedHashMap<String, Object>> rows = fetchRowData(opId, columns, maxRows);
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

    public JdbcSelectResult submitBatch(ObjectNode batchRequest) {
        Asserts.checkNotNull(batchRequest, "batchRequest is null");
        try {
            BatchRequest req = JsonUtils.objectMapper.treeToValue(batchRequest, BatchRequest.class);
            Batch batch = batchApi.createBatch(req);
            Batch finalBatch = waitBatchDone(batch);
            return buildBatchResult(finalBatch);
        } catch (Exception e) {
            JdbcSelectResult result = JdbcSelectResult.buildResult();
            result.error(e.getMessage());
            return result;
        }
    }

    private void ensureSession() {
        if (Asserts.isNotNullString(sessionHandle.get())) {
            return;
        }
        try {
            Map<String, String> configs = connectConfig.getSessionConfigs();
            SessionHandle session = sessionApi.openSession(new SessionOpenRequest(configs));
            Asserts.checkNotNull(session, "Create kyuubi session failed: empty response");
            Asserts.checkNotNull(session.getIdentifier(), "Create kyuubi session failed: missing identifier");
            sessionHandle.set(session.getIdentifier().toString());
            if (Asserts.isNotNullString(session.getKyuubiInstance())) {
                client.setHostUrls(session.getKyuubiInstance());
            }
        } catch (Exception e) {
            throw new RuntimeException("Create kyuubi session failed: " + e.getMessage(), e);
        }
    }

    private List<LinkedHashMap<String, Object>> fetchRowData(String opHandle, List<String> columns, Integer maxRows) {
        int limit = Asserts.isNull(maxRows) ? 100 : maxRows;
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        ResultRowSet rowSet = operationApi.getNextRowSet(opHandle, "FETCH_NEXT", limit);
        if (rowSet == null || rowSet.getRows() == null) {
            return rows;
        }
        for (Row r : rowSet.getRows()) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            List<Field> fields = r == null ? null : r.getFields();
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i);
                Object value = null;
                if (fields != null && i < fields.size()) {
                    Field f = fields.get(i);
                    value = f == null ? null : f.getValue();
                }
                row.put(colName, value);
            }
            rows.add(row);
        }
        return rows;
    }

    private Batch waitBatchDone(Batch batch) throws InterruptedException {
        if (batch == null) {
            return null;
        }
        String batchId = batch.getId();
        String appState = batch.getAppState();
        while (!isAppTerminated(appState)) {
            Thread.sleep(5000L);
            Batch latest = batchApi.getBatchById(batchId);
            if (latest != null) {
                batch = latest;
                appState = batch.getAppState();
            } else {
                break;
            }
        }
        return batch;
    }

    private boolean isAppTerminated(String appState) {
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

    private JdbcSelectResult buildBatchResult(Batch batch) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        if (batch != null) {
            row.put("id", batch.getId());
            row.put("batchType", batch.getBatchType());
            row.put("name", batch.getName());
            row.put("state", batch.getState());
            row.put("appId", batch.getAppId());
            row.put("appUrl", batch.getAppUrl());
            row.put("appState", batch.getAppState());
            row.put("appDiagnostic", batch.getAppDiagnostic());
            row.put("kyuubiInstance", batch.getKyuubiInstance());
            row.put("createTime", batch.getCreateTime());
            row.put("endTime", batch.getEndTime());
        }
        JdbcSelectResult result = JdbcSelectResult.buildResult();
        result.setColumns(new ArrayList<>(row.keySet()));
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        result.setRowData(rows);
        result.success();
        return result;
    }
}

