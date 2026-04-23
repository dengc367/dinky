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

package org.dinky.service.impl;

import org.dinky.data.model.SystemConfiguration;
import org.dinky.data.model.rbac.User;
import org.dinky.data.result.Result;
import org.dinky.oauth.KeycloakOauthProvider;
import org.dinky.oauth.OAuthConfiguration;
import org.dinky.oauth.OAuthProvider;
import org.dinky.oauth.OAuthProviderFactory;
import org.dinky.service.OAuthService;
import org.dinky.service.UserService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Syncs Dinky users to the configured OAuth center via {@link OAuthProvider} (Keycloak HTTP implementation). */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {

    private final UserService userService;
    private final OAuthProviderFactory oauthProviderFactory;

    @Override
    public Result<Void> syncUsers() {
        SystemConfiguration cfg = SystemConfiguration.getInstances();
        if (!Boolean.TRUE.equals(cfg.getOauthEnable().getValue())) {
            return Result.failed("OAuth sync is disabled");
        }
        final OAuthProvider provider;
        try {
            provider = oauthProviderFactory.getProvider(cfg.getOauthCenter().getValue());
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        }

        String baseUrl = cfg.getOauthEndpoint().getValue();
        String realm = cfg.getOauthRealm().getValue();
        String adminClientId = cfg.getOauthAdminClientId().getValue();
        String adminSecret = cfg.getOauthAdminClientSecret().getValue();
        if (StrUtil.isBlank(baseUrl) || StrUtil.isBlank(realm) || StrUtil.isBlank(adminClientId) || StrUtil.isBlank(adminSecret)) {
            return Result.failed("OAuth base URL, realm, admin client id or admin client secret is not configured");
        }

        OAuthConfiguration oc;
        try {
            oc = OAuthConfiguration.builder()
                    .baseUrl(KeycloakOauthProvider.normalizeBaseUrl(baseUrl))
                    .realm(realm.trim())
                    .adminClientId(adminClientId.trim())
                    .adminClientSecret(adminSecret)
                    .build();
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        }

        String accessToken;
        try {
            accessToken = provider.getAccessToken(oc);
        } catch (Exception e) {
            log.error("Failed to obtain OAuth admin token", e);
            return Result.failed("OAuth token: " + e.getMessage());
        }

        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        w.eq(User::getIsDelete, false);
        List<User> users = userService.list(w);
        int ok = 0;
        int skipped = 0;
        for (User user : users) {
            if (StrUtil.isBlank(user.getUsername())) {
                skipped++;
                continue;
            }
            if (StrUtil.isBlank(user.getPassword())) {
                log.warn("Skip OAuth sync for user {}: empty password", user.getUsername());
                skipped++;
                continue;
            }
            try {
                provider.createOrUpdateClient(oc, accessToken, user);
                ok++;
            } catch (Exception ex) {
                log.error("OAuth sync failed for user {}", user.getUsername(), ex);
                return Result.failed("Sync failed at user " + user.getUsername() + ": " + ex.getMessage());
            }
        }
        log.info("OAuth sync finished: {} updated/created, {} skipped", ok, skipped);
        return Result.succeed(null, "Synced " + ok + " client(s), skipped " + skipped);
    }
}
