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

package org.dinky.metadata.config;

import org.dinky.metadata.constant.KyuubiConstant;

import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(value = "KyuubiConnectConfig", description = "Configuration for Kyuubi connection")
public class KyuubiConnectConfig extends AbstractJdbcConfig {

    @ApiModelProperty(value = "Kyuubi mode: SESSION_JDBC or BATCH_REST", dataType = "String", example = "SESSION_JDBC")
    private KyuubiConstant.Mode mode;

    @ApiModelProperty(value = "Kyuubi REST endpoint, e.g. http://host:10099", dataType = "String")
    private String endpoint;

    @ApiModelProperty(value = "Kyuubi batch type, e.g. SPARK/FLINK", dataType = "String", example = "SPARK")
    private String batchType;

    @ApiModelProperty(value = "Batch resource path (required for REST batch)", dataType = "String")
    private String resource;

    @ApiModelProperty(value = "Batch main class name (required for REST batch)", dataType = "String")
    private String className;

    @ApiModelProperty(value = "Batch name", dataType = "String")
    private String name;

    @ApiModelProperty(value = "Batch conf properties", dataType = "Map")
    private Map<String, String> conf;

    @ApiModelProperty(value = "Batch args", dataType = "List")
    private List<String> args;

    @ApiModelProperty(value = "REST session configs used to create session", dataType = "Map")
    private Map<String, String> sessionConfigs;
}

