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

import lombok.Data;

/**
 * Gravitino connect config (stored in dinky_database.connect_config).
 */
@Data
public class GravitinoConfig implements IConnectConfig {

    /** Gravitino REST endpoint, e.g. https://host:port */
    private String endpoint;

    /** Metalake name */
    private String metalake;

    /** basic | oauth2_client_credentials */
    private String authType;

    /** Basic auth username */
    private String username;

    /** Basic auth password */
    private String password;

    /** OAuth2 client credentials token URL */
    private String tokenUrl;

    /** OAuth2 clientId */
    private String clientId;

    /** OAuth2 clientSecret */
    private String clientSecret;

    /**
     * OAuth2 scope. (If your server uses audience instead, put it here for now; the driver will map it to scope
     * parameter.)
     */
    private String scope;

    /**
     * Optional catalog whitelist. One name per line (or separated by comma/space).
     * If set, only catalogs in this list will be shown in Dinky.
     */
    private String catalogWhitelist;

    /**
     * Optional catalog name regex. If set, only catalogs whose name matches will be shown in Dinky.
     * Applied after {@link #catalogWhitelist} (if present).
     */
    private String catalogNameRegex;
}

