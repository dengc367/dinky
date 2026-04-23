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

import org.dinky.data.model.rbac.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Keycloak Admin REST + token endpoint via plain HTTP (no keycloak-admin-client). */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakOauthProvider implements OAuthProvider {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    /** Fixed client_credentials scope for the admin client (replaces configurable oauth profile). */
    private static final String CLIENT_CREDENTIALS_SCOPE = "openid profile";

    private final ObjectMapper objectMapper;

    @Override
    public String getAccessToken(OAuthConfiguration config) {
        String base = normalizeBaseUrl(config.getBaseUrl());
        String tokenUrl = base + "/realms/" + config.getRealm() + "/protocol/openid-connect/token";
        RestTemplate rt = buildRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.getAdminClientId());
        form.add("client_secret", config.getAdminClientSecret());
        form.add("scope", CLIENT_CREDENTIALS_SCOPE);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<String> res = rt.postForEntity(tokenUrl, request, String.class);
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new IllegalStateException("HTTP " + res.getStatusCodeValue());
            }
            JsonNode node = objectMapper.readTree(res.getBody());
            if (node.has("error")) {
                String desc = node.path("error_description").asText(node.path("error").asText("token_error"));
                throw new IllegalStateException(desc);
            }
            JsonNode tokenNode = node.get("access_token");
            if (tokenNode == null || tokenNode.isNull()) {
                throw new IllegalStateException("No access_token in token response");
            }
            return tokenNode.asText();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String body = e.getResponseBodyAsString();
            log.warn("Keycloak token HTTP error: {} {}", e.getStatusCode(), body);
            throw new IllegalStateException(e.getStatusCode() + " " + body, e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Keycloak token network timeout: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void createOrUpdateClient(OAuthConfiguration config, String token, User user) {
        String username = user.getUsername().trim();
        String base = normalizeBaseUrl(config.getBaseUrl());
        String adminBase = base + "/admin/realms/" + config.getRealm();
        RestTemplate rt = buildRestTemplate();

        String internalId = findClientInternalId(rt, adminBase, token, username);
        if (internalId != null) {
            ObjectNode merged = loadClientJson(rt, adminBase, token, internalId);
            applyDinkyUserClientFields(merged, user, username, internalId);
            putJson(rt, adminBase + "/clients/" + internalId, token, merged);
            return;
        }

        ObjectNode createBody = applyDinkyUserClientFields(objectMapper.createObjectNode(), user, username, null);
        postCreateClient(rt, adminBase + "/clients", token, createBody, adminBase, username);
    }

    private void postCreateClient(
            RestTemplate rt,
            String url,
            String token,
            ObjectNode body,
            String adminBase,
            String username) {
        try {
            ResponseEntity<String> res =
                    rt.exchange(url, HttpMethod.POST, jsonEntity(token, body), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                return;
            }
            throw new IllegalStateException("Create client HTTP " + res.getStatusCodeValue());
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() == 409) {
                String id = findClientInternalId(rt, adminBase, token, username);
                if (id == null) {
                    throw new IllegalStateException("409 on create but client not found: " + username);
                }
                ObjectNode merged = loadClientJson(rt, adminBase, token, id);
                applyDinkyUserClientFields(merged, user, username, id);
                putJson(rt, adminBase + "/clients/" + id, token, merged);
                return;
            }
            throw adminHttpError("Create client", e);
        } catch (HttpServerErrorException e) {
            throw adminHttpError("Create client", e);
        }
    }

    private static ObjectNode applyDinkyUserClientFields(
            ObjectNode n, User user, String clientId, String internalId) {
        if (StrUtil.isNotBlank(internalId)) {
            n.put("id", internalId);
        }
        n.put("clientId", clientId);
        n.put("secret", user.getPassword());
        n.put("protocol", "openid-connect");
        n.put("publicClient", false);
        n.put("serviceAccountsEnabled", true);
        n.put("authorizationServicesEnabled", false);
        n.put("standardFlowEnabled", true);
        n.put("directAccessGrantsEnabled", true);
        n.put("name", "dinky-user-" + clientId);
        n.put("enabled", Boolean.TRUE.equals(user.getEnabled()));
        n.put("clientAuthenticatorType", "client-secret");
        ArrayNode redirects = n.putArray("redirectUris");
        redirects.add("*");
        return n;
    }

    private String findClientInternalId(RestTemplate rt, String adminBase, String token, String clientId) {
        String q = adminBase + "/clients?clientId=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        try {
            ResponseEntity<String> res = rt.exchange(q, HttpMethod.GET, bearerEntity(token), String.class);
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new IllegalStateException("List clients HTTP " + res.getStatusCodeValue());
            }
            JsonNode arr = objectMapper.readTree(res.getBody());
            if (!arr.isArray() || arr.isEmpty()) {
                return null;
            }
            return arr.get(0).path("id").asText(null);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw adminHttpError("List clients", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private ObjectNode loadClientJson(RestTemplate rt, String adminBase, String token, String internalId) {
        String url = adminBase + "/clients/" + internalId;
        try {
            ResponseEntity<String> res = rt.exchange(url, HttpMethod.GET, bearerEntity(token), String.class);
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new IllegalStateException("GET client HTTP " + res.getStatusCodeValue());
            }
            JsonNode node = objectMapper.readTree(res.getBody());
            if (!(node instanceof ObjectNode)) {
                throw new IllegalStateException("Unexpected client JSON");
            }
            return (ObjectNode) node;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw adminHttpError("Get client", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void putJson(RestTemplate rt, String url, String token, ObjectNode body) {
        try {
            ResponseEntity<String> res =
                    rt.exchange(url, HttpMethod.PUT, jsonEntity(token, body), String.class);
            if (!res.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("PUT client HTTP " + res.getStatusCodeValue());
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw adminHttpError("Update client", e);
        }
    }

    private static IllegalStateException adminHttpError(String what, RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        log.warn("{}: {} {}", what, e.getRawStatusCode(), body);
        return new IllegalStateException(what + ": HTTP " + e.getRawStatusCode() + " " + body, e);
    }

    private HttpEntity<String> jsonEntity(String token, ObjectNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static HttpEntity<Void> bearerEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    static String normalizeBaseUrl(String raw) {
        String s = StrUtil.trim(raw);
        if (StrUtil.isBlank(s)) {
            throw new IllegalArgumentException("OAuth endpoint (Keycloak base URL) is blank");
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
