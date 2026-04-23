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

package org.dinky.service.task;

import org.dinky.assertion.Asserts;
import org.dinky.config.Dialect;
import org.dinky.data.annotations.SupportDialect;
import org.dinky.data.constant.CommonConstant;
import org.dinky.data.dto.TaskDTO;
import org.dinky.data.model.ClusterConfiguration;
import org.dinky.gateway.kyuubi.KyuubiGatewayClient;
import org.dinky.gateway.model.KyuubiGatewayConfig;
import org.dinky.gateway.model.KyuubiGatewayConfig.RunMode;
import org.dinky.job.Job;
import org.dinky.job.JobResult;
import org.dinky.metadata.result.JdbcSelectResult;
import org.dinky.service.ClusterConfigurationService;
import org.dinky.service.TaskService;
import org.dinky.service.impl.TaskServiceImpl;
import org.dinky.utils.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;

import org.apache.kyuubi.client.api.v1.dto.Batch;
import org.apache.kyuubi.client.api.v1.dto.BatchRequest;

@Slf4j
@SupportDialect({Dialect.FLINK_SQL, Dialect.FLINK_SQL_ENV, Dialect.FLINK_JAR})
public class KyuubiTask extends BaseTask {

    public KyuubiTask(TaskDTO task) {
        super(task);
    }

    @Override
    public JobResult execute() throws Exception {
        if (Asserts.isNull(task.getClusterConfigurationId())) {
            throw new IllegalArgumentException("KyuubiTask requires clusterConfigurationId:" + task);
        }

        ClusterConfigurationService clusterCfgService = SpringUtil.getBean(ClusterConfigurationService.class);
        ClusterConfiguration clusterCfg = clusterCfgService.getById(task.getClusterConfigurationId());
        if (clusterCfg == null || !CommonConstant.CLUSTER_TYPE_KYUUBI.equalsIgnoreCase(clusterCfg.getType())) {
            throw new IllegalStateException("KyuubiTask requires clusterConfiguration type KYUUBI");
        }

        TaskService taskService = SpringUtil.getBean(TaskServiceImpl.class);
        String statement = taskService.buildEnvSql(task) + task.getStatement();

        KyuubiGatewayConfig kyuubiCfg = JsonUtils.convertValue(clusterCfg.getConfigJson(), KyuubiGatewayConfig.class);

        JobResult jr = new JobResult();
        jr.setStatement(statement);
        jr.setStartTimeNow();
        jr.setEndTimeNow();

        RunMode runMode = kyuubiCfg.getRunMode() == null ? RunMode.SESSION : kyuubiCfg.getRunMode();
        if (runMode == RunMode.SESSION) {
            JdbcSelectResult selectResult =
                    new KyuubiGatewayClient().sessionExecute(kyuubiCfg, statement, task.getMaxRowNum());
            jr.setResults(Collections.singletonList(selectResult));
            if (selectResult != null && selectResult.isSuccess()) {
                jr.setStatus(Job.JobStatus.SUCCESS);
                jr.setSuccess(true);
            } else {
                jr.setStatus(Job.JobStatus.FAILED);
                jr.setSuccess(false);
                jr.setError(selectResult == null ? "Kyuubi execute failed" : selectResult.getError());
            }
            return jr;
        }

        BatchRequest req = buildBatchRequest(kyuubiCfg, task.getName(), statement);
        Batch batch = new KyuubiGatewayClient().submitBatchAndWait(kyuubiCfg, req);
        String appState = batch == null ? null : batch.getAppState();
        jr.setJobId(batch == null ? null : batch.getAppId());
        if ("FINISHED".equalsIgnoreCase(appState)) {
            jr.setStatus(Job.JobStatus.SUCCESS);
            jr.setSuccess(true);
        } else {
            jr.setStatus(Job.JobStatus.FAILED);
            jr.setSuccess(false);
            String diagnostic = batch == null ? null : batch.getAppDiagnostic();
            if (Asserts.isNotNullString(diagnostic)) {
                jr.setError("Kyuubi batch failed, appState=" + appState + ", diagnostic=" + diagnostic);
            } else {
                jr.setError("Kyuubi batch failed, appState=" + appState);
            }
        }
        return jr;
    }

    private static BatchRequest buildBatchRequest(KyuubiGatewayConfig cfg, String taskName, String statement) {
        BatchRequest req = new BatchRequest();
        // For SQL tasks, use SQL batch type by default; engine type should be controlled via kyuubi.engine.type in conf.
        req.setBatchType("SQL");
        if (Asserts.isNotNullString(taskName)) {
            req.setName(taskName);
        }

        Map<String, String> conf = new HashMap<>();
        if (cfg.getConf() != null) {
            conf.putAll(cfg.getConf());
        }
        if (Asserts.isNotNullString(cfg.getProfile())) {
            conf.put("kyuubi.session.conf.profile", cfg.getProfile());
        }
        req.setConf(conf);

        List<String> args = new ArrayList<>();
        args.add(statement);
        req.setArgs(args);
        return req;
    }

    @Override
    public boolean stop() {
        return false;
    }
}

