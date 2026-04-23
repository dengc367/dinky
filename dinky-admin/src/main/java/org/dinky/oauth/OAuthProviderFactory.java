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

package org.dinky.oauth;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;

/** Selects an {@link OAuthProvider} from global config {@code oauth_center} (e.g. keycloak). */
@Component
@RequiredArgsConstructor
public class OAuthProviderFactory {

    private static final String DEFAULT_CENTER = "keycloak";

    private final KeycloakOauthProvider keycloakOauthProvider;

    /**
     * @param oauthCenter value from {@link org.dinky.data.model.SystemConfiguration#getOauthCenter()} (may be blank)
     * @return provider implementation for the given center
     * @throws IllegalArgumentException if the center is unknown or unsupported
     */
    public OAuthProvider getProvider(String oauthCenter) {
        String c = StrUtil.trim(StrUtil.blankToDefault(oauthCenter, DEFAULT_CENTER));
        if ("keycloak".equalsIgnoreCase(c)) {
            return keycloakOauthProvider;
        }
        throw new IllegalArgumentException("Unsupported oauth center: " + c + " (supported: keycloak)");
    }
}
