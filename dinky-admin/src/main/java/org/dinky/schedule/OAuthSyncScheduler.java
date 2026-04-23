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

package org.dinky.schedule;

import org.dinky.data.model.SystemConfiguration;
import org.dinky.data.result.Result;
import org.dinky.service.OAuthService;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hourly OAuth / Keycloak sync when enabled. Uses Redis SETNX when {@link StringRedisTemplate} is available to
 * reduce duplicate runs in a cluster; otherwise every node may run the job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthSyncScheduler {

    private static final String LOCK_KEY = "dinky:oauth:sync:lock";

    private final OAuthService oauthService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** One hour between runs; initial delay two minutes after startup. */
    @Scheduled(initialDelay = 120_000, fixedDelay = 3_600_000)
    public void scheduledSync() {
        SystemConfiguration cfg = SystemConfiguration.getInstances();
        if (!Boolean.TRUE.equals(cfg.getOauthEnable().getValue())
                || !Boolean.TRUE.equals(cfg.getOauthIsCron().getValue())) {
            return;
        }
        if (!tryAcquireLock()) {
            log.debug("OAuth scheduled sync skipped: lock not acquired (another instance or stale lock)");
            return;
        }
        Result<Void> result = oauthService.syncUsers();
        if (!result.isSuccess()) {
            log.warn("OAuth scheduled sync finished with error: {}", result.getMsg());
        }
    }

    private boolean tryAcquireLock() {
        if (stringRedisTemplate == null) {
            return true;
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(70));
        return Boolean.TRUE.equals(ok);
    }
}
