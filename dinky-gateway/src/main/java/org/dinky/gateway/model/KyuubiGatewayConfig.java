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

package org.dinky.gateway.model;

import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "KyuubiGatewayConfig", description = "Configuration for Kyuubi gateway execution engine")
public class KyuubiGatewayConfig {

    public enum AuthType {
        NONE,
        BASIC
    }

    public enum EngineType {
        SPARK,
        FLINK
    }

    public enum RunMode {
        BATCH,
        SESSION
    }

    @ApiModelProperty(value = "Kyuubi REST endpoint, e.g. http://localhost:10099", required = true)
    private String host;

    @ApiModelProperty(value = "Auth type (NONE/BASIC)", required = true)
    private AuthType authType = AuthType.NONE;

    @ApiModelProperty(value = "Username for BASIC auth")
    private String username;

    @ApiModelProperty(value = "Password for BASIC auth")
    private String password;

    @ApiModelProperty(
            value = "Configuration profile, mapped to kyuubi.session.conf.profile",
            notes = "Used to load pre-defined server-side config group")
    private String profile;

    @ApiModelProperty(value = "Engine type (SPARK/FLINK)", required = true)
    private EngineType engineType = EngineType.FLINK;

    @ApiModelProperty(value = "Run mode (BATCH/SESSION)", required = true)
    private RunMode runMode = RunMode.SESSION;

    @ApiModelProperty(
            value = "Extra Kyuubi/Flink configs (key-value)",
            notes = "Will be sent as Kyuubi conf/session configs or batch conf depending on mode")
    private Map<String, String> conf;
}

