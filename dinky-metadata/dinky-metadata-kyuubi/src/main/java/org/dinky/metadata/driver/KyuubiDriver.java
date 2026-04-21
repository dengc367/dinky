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

package org.dinky.metadata.driver;

import org.dinky.assertion.Asserts;
import org.dinky.data.constant.CommonConstant;
import org.dinky.data.model.Column;
import org.dinky.data.model.Schema;
import org.dinky.data.model.Table;
import org.dinky.metadata.config.DriverConfig;
import org.dinky.metadata.config.KyuubiConnectConfig;
import org.dinky.metadata.constant.KyuubiConstant;
import org.dinky.metadata.convert.AbstractJdbcTypeConvert;
import org.dinky.metadata.convert.KyuubiTypeConvert;
import org.dinky.metadata.enums.DriverType;
import org.dinky.metadata.kyuubi.client.KyuubiRestClient;
import org.dinky.metadata.query.IDBQuery;
import org.dinky.metadata.query.KyuubiQuery;
import org.dinky.metadata.result.JdbcSelectResult;
import org.dinky.utils.JsonUtils;
import org.dinky.utils.LogUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KyuubiDriver extends AbstractJdbcDriver implements Driver {

    private transient KyuubiRestClient restClient;
    private transient KyuubiConnectConfig kyuubiConnectConfig;

    @Override
    public <T> Driver buildDriverConfig(String name, String type, T config) {
        KyuubiConnectConfig connectConfig = JsonUtils.convertValue(config, KyuubiConnectConfig.class);
        if (connectConfig.getMode() == null) {
            connectConfig.setMode(KyuubiConstant.Mode.SESSION_JDBC);
        }
        this.kyuubiConnectConfig = connectConfig;
        this.config = new DriverConfig<>(name, type, connectConfig);
        return this;
    }

    @Override
    public String test() {
        Asserts.checkNotNull(config, "无效的数据源配置");
        if (!isBatchRest()) {
            return super.test();
        }
        try {
            // Lightweight REST probe: create session + run a simple statement.
            if (restClient == null) {
                restClient = new KyuubiRestClient(getKyuubiConfig());
            }
            JdbcSelectResult res = restClient.executeStatement(getDBQuery().schemaAllSql(), 1);
            if (res != null && res.isSuccess()) {
                return CommonConstant.HEALTHY;
            }
            return res == null ? "Kyuubi REST test failed: empty response" : res.getError();
        } catch (Exception e) {
            log.error("Kyuubi REST test failed! error: {}", e.getMessage(), e);
            return e.getMessage();
        }
    }

    @Override
    public Driver connect() {
        if (isBatchRest()) {
            if (restClient == null) {
                restClient = new KyuubiRestClient(getKyuubiConfig());
            }
            return this;
        }
        return super.connect();
    }

    @Override
    public void close() {
        if (!isBatchRest()) {
            super.close();
        }
    }

    @Override
    public JdbcSelectResult query(String sql, Integer limit) {
        if (isBatchRest()) {
            return restClient.executeStatement(sql, limit);
        }
        return super.query(sql, limit);
    }

    @Override
    public boolean execute(String sql) throws Exception {
        if (isBatchRest()) {
            JdbcSelectResult result = restClient.executeStatement(sql, 1);
            if (!result.isSuccess()) {
                throw new RuntimeException(result.getError());
            }
            return true;
        }
        return super.execute(sql);
    }

    @Override
    public int executeUpdate(String sql) throws Exception {
        if (isBatchRest()) {
            JdbcSelectResult result = restClient.executeStatement(sql, 1);
            if (!result.isSuccess()) {
                throw new RuntimeException(result.getError());
            }
            return 1;
        }
        return super.executeUpdate(sql);
    }

    @Override
    public JdbcSelectResult executeSql(String sql, Integer limit) {
        if (isBatchRest()) {
            return restClient.submitBatch(buildBatchRequest(sql));
        }
        return executeSqlAsHiveParser(sql, limit);
    }

    private JdbcSelectResult executeSqlAsHiveParser(String sql, Integer limit) {
        log.info("Start parse sql...");
        List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, "hive");
        log.info(CharSequenceUtil.format("A total of {} statement have been Parsed.", stmtList.size()));
        List<Object> resList = new ArrayList<>();
        JdbcSelectResult result = JdbcSelectResult.buildResult();
        log.info("Start execute sql...");
        for (SQLStatement item : stmtList) {
            String type = item.getClass().getSimpleName();
            if (type.toUpperCase().contains("SELECT")
                    || type.toUpperCase().contains("SHOW")
                    || type.toUpperCase().contains("DESC")
                    || type.toUpperCase().contains("SQLEXPLAINSTATEMENT")) {
                log.info("Execute query.");
                return query(item.toString(), limit);
            } else if (type.toUpperCase().contains("INSERT")
                    || type.toUpperCase().contains("UPDATE")
                    || type.toUpperCase().contains("DELETE")) {
                try {
                    log.info("Execute update.");
                    resList.add(executeUpdate(item.toString()));
                    result.setStatusList(resList);
                } catch (Exception e) {
                    resList.add(0);
                    result.setStatusList(resList);
                    result.error(LogUtil.getError(e));
                    log.error(e.getMessage());
                    return result;
                }
            } else {
                try {
                    log.info("Execute DDL.");
                    execute(item.toString());
                    resList.add(1);
                    result.setStatusList(resList);
                } catch (Exception e) {
                    resList.add(0);
                    result.setStatusList(resList);
                    result.error(LogUtil.getError(e));
                    log.error(e.getMessage());
                    return result;
                }
            }
        }
        result.success();
        return result;
    }

    @Override
    public List<Schema> listSchemas() {
        if (isBatchRest()) {
            return listSchemasByRest();
        }
        return super.listSchemas();
    }

    @Override
    public List<Table> listTables(String schemaName) {
        if (isBatchRest()) {
            return listTablesByRest(schemaName);
        }
        return super.listTables(schemaName);
    }

    @Override
    public List<Column> listColumns(String schemaName, String tableName) {
        if (isBatchRest()) {
            return listColumnsByRest(schemaName, tableName);
        }
        return super.listColumns(schemaName, tableName);
    }

    private List<Schema> listSchemasByRest() {
        JdbcSelectResult res = restClient.executeStatement(getDBQuery().schemaAllSql(), 10000);
        if (!res.isSuccess() || Asserts.isNull(res.getRowData())) {
            return new ArrayList<>();
        }
        List<Schema> schemas = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : res.getRowData()) {
            String schemaName = getFirstStringValue(row);
            if (Asserts.isNotNullString(schemaName)) {
                schemas.add(new Schema(schemaName));
            }
        }
        return schemas;
    }

    private List<Table> listTablesByRest(String schemaName) {
        List<Table> tables = new ArrayList<>();
        JdbcSelectResult res = restClient.executeStatement(getDBQuery().tablesSql(schemaName), 10000);
        if (!res.isSuccess() || Asserts.isNull(res.getRowData())) {
            return tables;
        }
        for (LinkedHashMap<String, Object> row : res.getRowData()) {
            String tableName = getFirstStringValue(row);
            if (Asserts.isNotNullString(tableName)) {
                Table t = new Table();
                t.setSchema(schemaName);
                t.setName(tableName);
                tables.add(t);
            }
        }
        return tables;
    }

    private List<Column> listColumnsByRest(String schemaName, String tableName) {
        List<Column> columns = new ArrayList<>();
        JdbcSelectResult res = restClient.executeStatement(getDBQuery().columnsSql(schemaName, tableName), 10000);
        if (!res.isSuccess() || Asserts.isNull(res.getRowData())) {
            return columns;
        }
        AbstractJdbcTypeConvert typeConvert = getTypeConvert();
        int position = 1;
        for (LinkedHashMap<String, Object> row : res.getRowData()) {
            String colName = getString(row, getDBQuery().columnName(), 0);
            if (Asserts.isNullString(colName) || colName.startsWith("#")) {
                continue;
            }
            String colType = getString(row, getDBQuery().columnType(), 1);
            String colComment = getString(row, getDBQuery().columnComment(), 2);
            if (Asserts.isNullString(colName) || Asserts.isNullString(colType)) {
                continue;
            }
            Column c = new Column();
            c.setName(colName);
            c.setType(colType);
            c.setComment(colComment);
            c.setPosition(position++);
            c.setDataType(typeConvert.convert(c));
            columns.add(c);
        }
        return columns;
    }

    private String getFirstStringValue(LinkedHashMap<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object v = row.values().iterator().next();
        return v == null ? null : String.valueOf(v);
    }

    private String getString(LinkedHashMap<String, Object> row, String key, int fallbackIndex) {
        if (row == null) {
            return null;
        }
        Object v = row.get(key);
        if (v != null) {
            return String.valueOf(v);
        }
        int idx = 0;
        for (Object item : row.values()) {
            if (idx == fallbackIndex) {
                return item == null ? null : String.valueOf(item);
            }
            idx++;
        }
        return null;
    }

    private boolean isBatchRest() {
        return getKyuubiConfig().getMode() == KyuubiConstant.Mode.BATCH_REST;
    }

    private KyuubiConnectConfig getKyuubiConfig() {
        if (kyuubiConnectConfig != null) {
            return kyuubiConnectConfig;
        }
        if (config != null && config.getConnectConfig() instanceof KyuubiConnectConfig) {
            return (KyuubiConnectConfig) config.getConnectConfig();
        }
        return new KyuubiConnectConfig();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode buildBatchRequest(String sql) {
        KyuubiConnectConfig c = getKyuubiConfig();
        try {
            ObjectMapper mapper = new ObjectMapper();
            return (ObjectNode) mapper.readTree(sql);
        } catch(Exception e) {
            // TODO
        }
        com.fasterxml.jackson.databind.node.ObjectNode node = JsonUtils.createObjectNode();
        if (Asserts.isNotNullString(c.getBatchType())) {
            node.put("batchType", c.getBatchType());
        }
        if (Asserts.isNotNullString(c.getResource())) {
            node.put("resource", c.getResource());
        }
        if (Asserts.isNotNullString(c.getClassName())) {
            node.put("className", c.getClassName());
        }
        if (Asserts.isNotNullString(c.getName())) {
            node.put("name", c.getName());
        }
        if (c.getConf() != null && !c.getConf().isEmpty()) {
            com.fasterxml.jackson.databind.node.ObjectNode confNode = node.putObject("conf");
            c.getConf().forEach(confNode::put);
        }
        List<String> args = new ArrayList<>();
        if (c.getArgs() != null) {
            args.addAll(c.getArgs());
        }
        args.add(sql);
        node.set("args", JsonUtils.toJsonNode(args));
        return node;
    }

    @Override
    public IDBQuery getDBQuery() {
        return new KyuubiQuery();
    }

    @Override
    public AbstractJdbcTypeConvert getTypeConvert() {
        return new KyuubiTypeConvert();
    }

    @Override
    String getDriverClass() {
        return "org.apache.kyuubi.jdbc.KyuubiHiveDriver";
    }

    @Override
    public String getType() {
        return DriverType.KYUUBI.getValue();
    }

    @Override
    public String getName() {
        return "Kyuubi";
    }

    @Override
    public StringBuilder genQueryOption(org.dinky.data.model.QueryData queryData) {
        StringBuilder optionBuilder = new StringBuilder()
                .append("select * from ")
                .append(queryData.getSchemaName())
                .append(".")
                .append(queryData.getTableName());
        if (Asserts.isNotNull(queryData.getOption())) {
            String where = queryData.getOption().getWhere();
            if (Asserts.isNotNullString(where)) {
                optionBuilder.append(" where ").append(where);
            }
            String order = queryData.getOption().getOrder();
            if (Asserts.isNotNullString(order)) {
                optionBuilder.append(" order by ").append(order);
            }
            int limitStart = queryData.getOption().getLimitStart();
            int limitEnd = queryData.getOption().getLimitEnd();
            optionBuilder.append(" limit ").append(limitStart).append(",").append(limitEnd);
        }
        return optionBuilder;
    }
}

