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
import org.dinky.data.model.Column;
import org.dinky.data.model.Schema;
import org.dinky.data.model.Table;
import org.dinky.metadata.config.DriverConfig;
import org.dinky.metadata.config.KyuubiConnectConfig;
import org.dinky.metadata.constant.KyuubiConstant;
import org.dinky.metadata.convert.AbstractJdbcTypeConvert;
import org.dinky.metadata.convert.KyuubiTypeConvert;
import org.dinky.metadata.enums.DriverType;
import org.dinky.metadata.query.IDBQuery;
import org.dinky.metadata.query.KyuubiQuery;
import org.dinky.metadata.result.JdbcSelectResult;
import org.dinky.utils.JsonUtils;
import org.dinky.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KyuubiDriver extends AbstractJdbcDriver implements Driver {

    private static final String KYUUBI_JDBC_URL_PREFIX = "jdbc:kyuubi://";

    private transient KyuubiConnectConfig kyuubiConnectConfig;

    @Override
    public <T> Driver buildDriverConfig(String name, String type, T config) {
        KyuubiConnectConfig connectConfig = JsonUtils.convertValue(config, KyuubiConnectConfig.class);
        if (connectConfig == null
                || connectConfig.getUrl() == null
                || !connectConfig.getUrl().startsWith(KYUUBI_JDBC_URL_PREFIX)) {
            throw new IllegalArgumentException("Kyuubi datasource url must start with " + KYUUBI_JDBC_URL_PREFIX);
        }
        this.kyuubiConnectConfig = connectConfig;
        this.config = new DriverConfig<>(name, type, connectConfig);
        return this;
    }

    @Override
    public String test() {
        Asserts.checkNotNull(config, "无效的数据源配置");
        return super.test();
    }

    @Override
    public Driver connect() {
        return super.connect();
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public JdbcSelectResult query(String sql, Integer limit) {
        return super.query(sql, limit);
    }

    @Override
    public boolean execute(String sql) throws Exception {
        return super.execute(sql);
    }

    @Override
    public int executeUpdate(String sql) throws Exception {
        return super.executeUpdate(sql);
    }

    @Override
    public JdbcSelectResult executeSql(String sql, Integer limit) {
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
        return super.listSchemas();
    }

    @Override
    public List<Table> listTables(String schemaName) {
        return super.listTables(schemaName);
    }

    @Override
    public List<Column> listColumns(String schemaName, String tableName) {
        return super.listColumns(schemaName, tableName);
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

