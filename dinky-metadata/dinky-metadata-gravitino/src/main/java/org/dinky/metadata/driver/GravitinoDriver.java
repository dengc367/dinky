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
import org.dinky.data.model.QueryData;
import org.dinky.data.model.Schema;
import org.dinky.data.model.Table;
import org.dinky.data.result.SqlExplainResult;
import org.dinky.metadata.config.DriverConfig;
import org.dinky.metadata.config.GravitinoConfig;
import org.dinky.metadata.convert.ITypeConvert;
import org.dinky.metadata.query.IDBQuery;
import org.dinky.metadata.result.JdbcSelectResult;
import org.dinky.utils.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Gravitino metadata driver (metalake scoped).
 *
 * <p>connectConfig expected shape (stored in dinky_database.connect_config):</p>
 * <pre>
 * {
 *   "endpoint": "https://gravitino:8090",
 *   "metalake": "prod",
 *   "authType": "basic|oauth2_client_credentials",
 *   "username": "...",
 *   "password": "...",
 *   "tokenUrl": "https://oauth/token",
 *   "clientId": "...",
 *   "clientSecret": "...",
 *   "scope": "..."
 * }
 * </pre>
 */
@Slf4j
public class GravitinoDriver extends AbstractDriver<GravitinoConfig> {

    private static final String TYPE = "Gravitino";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private transient GravitinoRestClient client;
    private transient CatalogFilter catalogFilter;

    @Override
    public IDBQuery getDBQuery() {
        return null;
    }

    @Override
    public ITypeConvert<GravitinoConfig> getTypeConvert() {
        return null;
    }

    @Override
    public <T> Driver buildDriverConfig(String name, String type, T config) {
        GravitinoConfig gravitinoConfig = JsonUtils.convertValue(config, GravitinoConfig.class);
        this.config = new DriverConfig<>(name, type, gravitinoConfig);
        this.catalogFilter = CatalogFilter.from(gravitinoConfig);
        return this;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return "Gravitino";
    }

    @Override
    public String test() {
        connect();
        List<CatalogRef> catalogs = client.listCatalogs();
        log.info("connect gravitino success, catalogs: {}", catalogs.stream().map(CatalogRef::toString).collect(
                Collectors.joining(",")));
        return CommonConstant.HEALTHY;
    }

    @Override
    public boolean isHealth() {
        return client != null;
    }

    @Override
    public Driver connect() {
        if (client != null) {
            return this;
        }
        Asserts.checkNotNull(config, "无效的数据源配置");
        GravitinoConfig cc = config.getConnectConfig();
        Asserts.checkNullString(cc.getEndpoint(), "Gravitino endpoint is required");
        Asserts.checkNullString(cc.getMetalake(), "Gravitino metalake is required");

        this.client = GravitinoRestClient.build(cc, DEFAULT_TIMEOUT);
        return this;
    }

    @Override
    public void close() {
        this.client = null;
        this.catalogFilter = null;
    }

    @Override
    public List<Schema> listSchemas() {
        connect();
        List<CatalogRef> catalogs = listCatalogsFiltered();
        return catalogs.stream()
                .map(c -> new Schema(formatCatalogSchemaName(c.name, c.type)))
                .collect(Collectors.toList());
    }

    @Override
    public boolean existSchema(String schemaName) {
        connect();
        ParsedSchema parsed = ParsedSchema.parse(schemaName);
        return listCatalogsFiltered().stream().anyMatch(c -> c.name.equals(parsed.catalogName));
    }

    @Override
    public boolean createSchema(String schemaName) {
        throw new UnsupportedOperationException("Gravitino driver does not support create schema via Dinky");
    }

    @Override
    public String generateCreateSchemaSql(String schemaName) {
        return null;
    }

    @Override
    public List<Table> listTables(String schemaName) {
        connect();
        ParsedSchema parsed = ParsedSchema.parse(schemaName);
        // If the schema is filtered out, return empty list.
        if (!listCatalogsFiltered().stream().anyMatch(c -> c.name.equals(parsed.catalogName))) {
            return Collections.emptyList();
        }
        List<GravitinoObjectRef> objects = client.listObjectsUnderCatalog(parsed.catalogName, parsed.catalogType);
        return objects.stream()
                .map(o -> {
                    Table t = new Table();
                    t.setName(o.displayName());
                    t.setSchema(schemaName);
                    t.setType(o.objectType);
                    t.setCatalog(parsed.catalogName);
                    t.setDriverType(getType());
                    return t;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Table> listTables(String schemaName, String tableName) {
        return listTables(schemaName).stream()
                .filter(t -> Asserts.isEqualsIgnoreCase(t.getName(), tableName))
                .collect(Collectors.toList());
    }

    @Override
    public List<Column> listColumns(String schemaName, String tableName) {
        connect();
        // Gravitino catalogs may contain non-tabular objects (topics/filesets). For now we return empty columns.
        return Collections.emptyList();
    }

    @Override
    public List<Column> listColumnsSortByPK(String schemaName, String tableName) {
        return listColumns(schemaName, tableName);
    }

    @Override
    public boolean createTable(Table table) {
        throw new UnsupportedOperationException("Gravitino driver does not support create table via Dinky");
    }

    @Override
    public boolean dropTable(Table table) {
        throw new UnsupportedOperationException("Gravitino driver does not support drop table via Dinky");
    }

    @Override
    public boolean truncateTable(Table table) {
        throw new UnsupportedOperationException("Gravitino driver does not support truncate table via Dinky");
    }

    @Override
    public String getCreateTableSql(Table table) {
        return null;
    }

    @Override
    public boolean execute(String sql) {
        throw new UnsupportedOperationException("Gravitino driver does not support execute SQL");
    }

    @Override
    public int executeUpdate(String sql) {
        throw new UnsupportedOperationException("Gravitino driver does not support executeUpdate");
    }

    @Override
    public JdbcSelectResult query(String sql, Integer limit) {
        return JdbcSelectResult.buildResult().error("Gravitino driver does not support query");
    }

    @Override
    public JdbcSelectResult query(QueryData queryData) {
        return query(queryData.getSql(), queryData.getOption() != null ? queryData.getOption().getLimitEnd() : null);
    }

    @Override
    public StringBuilder genQueryOption(QueryData queryData) {
        throw new UnsupportedOperationException("Gravitino driver does not support genQueryOption");
    }

    @Override
    public JdbcSelectResult executeSql(String sql, Integer limit) {
        return query(sql, limit);
    }

    @Override
    public List<SqlExplainResult> explain(String sql) {
        return Collections.singletonList(SqlExplainResult.fail(sql, "Gravitino driver does not support explain"));
    }

    @Override
    public Map<String, String> getFlinkColumnTypeConversion() {
        return Collections.emptyMap();
    }

    private static String formatCatalogSchemaName(String catalogName, String catalogType) {
        if (Asserts.isNullString(catalogType)) {
            return catalogName;
        }
        return catalogName + " (" + catalogType + ")";
    }

    private List<CatalogRef> listCatalogsFiltered() {
        List<CatalogRef> catalogs = client.listCatalogs();
        CatalogFilter filter = this.catalogFilter;
        if (filter == null || filter.isNoop()) {
            return catalogs;
        }
        return catalogs.stream().filter(c -> filter.matches(c.name)).collect(Collectors.toList());
    }

    private static final class CatalogFilter {
        private final Set<String> whitelist;
        private final java.util.regex.Pattern namePattern;

        private CatalogFilter(Set<String> whitelist, java.util.regex.Pattern namePattern) {
            this.whitelist = whitelist;
            this.namePattern = namePattern;
        }

        static CatalogFilter from(GravitinoConfig config) {
            if (config == null) {
                return new CatalogFilter(Collections.emptySet(), null);
            }
            Set<String> wl = splitToSet(config.getCatalogWhitelist());
            java.util.regex.Pattern p = null;
            if (Asserts.isNotNullString(config.getCatalogNameRegex())) {
                p = java.util.regex.Pattern.compile(config.getCatalogNameRegex());
            }
            return new CatalogFilter(wl, p);
        }

        boolean isNoop() {
            return (whitelist == null || whitelist.isEmpty()) && namePattern == null;
        }

        boolean matches(String catalogName) {
            if (Asserts.isNullString(catalogName)) {
                return false;
            }
            if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(catalogName)) {
                return false;
            }
            if (namePattern != null && !namePattern.matcher(catalogName).find()) {
                return false;
            }
            return true;
        }

        private static Set<String> splitToSet(String text) {
            if (Asserts.isNullString(text)) {
                return Collections.emptySet();
            }
            return Stream.of(text.split("[,\\s\\n\\r\\t]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    private static final class ParsedSchema {
        final String raw;
        final String catalogName;
        final String catalogType;

        private ParsedSchema(String raw, String catalogName, String catalogType) {
            this.raw = raw;
            this.catalogName = catalogName;
            this.catalogType = catalogType;
        }

        static ParsedSchema parse(String schemaName) {
            if (schemaName == null) {
                return new ParsedSchema(null, null, null);
            }
            int idx = schemaName.lastIndexOf(" (");
            if (idx > 0 && schemaName.endsWith(")")) {
                String name = schemaName.substring(0, idx);
                String type = schemaName.substring(idx + 2, schemaName.length() - 1);
                return new ParsedSchema(schemaName, name, type);
            }
            return new ParsedSchema(schemaName, schemaName, null);
        }
    }

    static final class CatalogRef {
        final String name;
        final String type;

        CatalogRef(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return type == null ? name : name + "(" + type + ")";
        }
    }

    static final class GravitinoObjectRef {
        final String namespace;
        final String name;
        final String objectType;

        GravitinoObjectRef(String namespace, String name, String objectType) {
            this.namespace = namespace;
            this.name = name;
            this.objectType = objectType;
        }

        String displayName() {
            if (Asserts.isNullString(namespace)) {
                return name;
            }
            return namespace + "." + name;
        }
    }

    /**
     * Minimal REST client for Gravitino.
     *
     * <p>We intentionally parse responses via JsonNode to minimize coupling to specific API response DTOs.</p>
     */
    static final class GravitinoRestClient {
        private final SimpleHttp http;
        private final String endpoint;
        private final String metalake;

        private GravitinoRestClient(SimpleHttp http, String endpoint, String metalake) {
            this.http = http;
            this.endpoint = stripTrailingSlash(endpoint);
            this.metalake = metalake;
        }

        static GravitinoRestClient build(GravitinoConfig config, Duration timeout) {
            SimpleHttp http = SimpleHttp.build(config, timeout);
            return new GravitinoRestClient(http, config.getEndpoint(), config.getMetalake());
        }

        List<CatalogRef> listCatalogs() {
            JsonNode node = http.getJson(api("/metalakes/" + urlEncode(metalake) + "/catalogs"));
            // best-effort parsing: try common fields
            List<JsonNode> catalogs = extractArray(node, "catalogs", "data", "items");
            List<CatalogRef> refs = new ArrayList<>();
            for (JsonNode c : catalogs) {
                String name = textAt(c, "name");
                String type = textAt(c, "type");
                if (name != null) {
                    refs.add(new CatalogRef(name, type));
                }
            }
            // If list endpoint doesn't include type, fetch each catalog details to fill type.
            boolean needsType = refs.stream().anyMatch(r -> r.type == null);
            if (needsType) {
                return refs.stream()
                        .map(r -> new CatalogRef(r.name, Optional.ofNullable(getCatalogType(r.name)).orElse(r.type)))
                        .collect(Collectors.toList());
            }
            return refs;
        }

        String getCatalogType(String catalogName) {
            JsonNode node = http.getJson(api("/metalakes/" + urlEncode(metalake) + "/catalogs/" + urlEncode(catalogName)));
            JsonNode catalog = node.path("catalog");
            String type = textAt(catalog, "type");
            if (type != null) {
                return type;
            }
            // fallback: sometimes wrapped under data
            return textAt(node.path("data").path("catalog"), "type");
        }

        List<GravitinoObjectRef> listObjectsUnderCatalog(String catalogName, String catalogType) {
            // Gravitino has catalog -> schema -> objects. We flatten as "<schema>.<object>".
            List<String> schemas = listSchemas(catalogName);
            List<GravitinoObjectRef> res = new ArrayList<>();
            for (String schema : schemas) {
                res.addAll(listObjectsUnderSchema(catalogName, catalogType, schema));
            }
            return res;
        }

        List<String> listSchemas(String catalogName) {
            JsonNode node = http.getJson(api("/metalakes/" + urlEncode(metalake)
                    + "/catalogs/" + urlEncode(catalogName)
                    + "/schemas"));
            List<JsonNode> schemas = extractArray(node, "schemas", "data", "items");
            List<String> names = new ArrayList<>();
            for (JsonNode s : schemas) {
                String name = textAt(s, "name");
                if (name != null) {
                    names.add(name);
                }
            }
            return names;
        }

        List<GravitinoObjectRef> listObjectsUnderSchema(String catalogName, String catalogType, String schemaName) {
            String typeUpper = catalogType == null ? "" : catalogType.toUpperCase();
            if (Objects.equals(typeUpper, "KAFKA") || Objects.equals(typeUpper, "MESSAGE") || Objects.equals(typeUpper, "MESSAGING")) {
                return listTopics(catalogName, schemaName);
            }
            if (Objects.equals(typeUpper, "FILESET") || Objects.equals(typeUpper, "FILESYSTEM")) {
                return listFilesets(catalogName, schemaName);
            }
            // default: relational/tabular
            return listTables(catalogName, schemaName);
        }

        List<GravitinoObjectRef> listTables(String catalogName, String schemaName) {
            JsonNode node = http.getJson(api("/metalakes/" + urlEncode(metalake)
                    + "/catalogs/" + urlEncode(catalogName)
                    + "/schemas/" + urlEncode(schemaName)
                    + "/tables"));
            List<JsonNode> tables = extractArray(node, "tables", "data", "items");
            List<GravitinoObjectRef> res = new ArrayList<>();
            for (JsonNode t : tables) {
                String name = textAt(t, "name");
                if (name != null) {
                    res.add(new GravitinoObjectRef(schemaName, name, "TABLE"));
                }
            }
            return res;
        }

        List<GravitinoObjectRef> listTopics(String catalogName, String schemaName) {
            JsonNode node = http.getJson(api("/metalakes/" + urlEncode(metalake)
                    + "/catalogs/" + urlEncode(catalogName)
                    + "/schemas/" + urlEncode(schemaName)
                    + "/topics"));
            List<JsonNode> topics = extractArray(node, "topics", "data", "items");
            List<GravitinoObjectRef> res = new ArrayList<>();
            for (JsonNode t : topics) {
                String name = textAt(t, "name");
                if (name != null) {
                    res.add(new GravitinoObjectRef(schemaName, name, "TOPIC"));
                }
            }
            return res;
        }

        List<GravitinoObjectRef> listFilesets(String catalogName, String schemaName) {
            JsonNode node = http.getJson(api("/metalakes/" + urlEncode(metalake)
                    + "/catalogs/" + urlEncode(catalogName)
                    + "/schemas/" + urlEncode(schemaName)
                    + "/filesets"));
            List<JsonNode> filesets = extractArray(node, "filesets", "data", "items");
            List<GravitinoObjectRef> res = new ArrayList<>();
            for (JsonNode t : filesets) {
                String name = textAt(t, "name");
                if (name != null) {
                    res.add(new GravitinoObjectRef(schemaName, name, "FILESET"));
                }
            }
            return res;
        }

        private String api(String path) {
            return endpoint + "/api" + path;
        }

        private static List<JsonNode> extractArray(JsonNode root, String... candidates) {
            if (root == null) {
                return Collections.emptyList();
            }
            for (String key : candidates) {
                JsonNode n = root.path(key);
                if (n.isArray()) {
                    List<JsonNode> out = new ArrayList<>();
                    n.forEach(out::add);
                    return out;
                }
                // common wrap: data -> <arrayKey>
                if (n.isObject()) {
                    // try common inside keys
                    for (String inner : new String[] {"catalogs", "schemas", "tables", "topics", "filesets", "items"}) {
                        JsonNode innerNode = n.path(inner);
                        if (innerNode.isArray()) {
                            List<JsonNode> out = new ArrayList<>();
                            innerNode.forEach(out::add);
                            return out;
                        }
                    }
                }
            }
            // last resort: find first array child
            if (root.isObject()) {
                for (JsonNode child : (Iterable<JsonNode>) root::elements) {
                    if (child.isArray()) {
                        List<JsonNode> out = new ArrayList<>();
                        child.forEach(out::add);
                        return out;
                    }
                }
            }
            return Collections.emptyList();
        }

        private static String textAt(JsonNode node, String field) {
            if (node == null || field == null) {
                return null;
            }
            JsonNode n = node.get(field);
            if (n == null || n.isNull()) {
                return null;
            }
            String s = n.asText();
            return Asserts.isNullString(s) ? null : s;
        }

        private static String stripTrailingSlash(String s) {
            if (s == null) {
                return null;
            }
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }

        private static String urlEncode(String s) {
            return SimpleHttp.urlEncode(s);
        }
    }

    static final class SimpleHttp {
        private final Duration timeout;
        private final GravitinoConfig config;
        private volatile OAuthToken token;

        private SimpleHttp(Duration timeout, GravitinoConfig config) {
            this.timeout = timeout;
            this.config = config;
        }

        static SimpleHttp build(GravitinoConfig config, Duration timeout) {
            return new SimpleHttp(timeout, config);
        }

        JsonNode getJson(String url) {
            try {
                cn.hutool.http.HttpRequest req = cn.hutool.http.HttpRequest.get(url)
                        .timeout((int) timeout.toMillis());
                applyAuth(req);
                cn.hutool.http.HttpResponse resp = req.execute();
                if (resp.getStatus() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + resp.getStatus() + " from " + url + ": " + resp.body());
                }
                return JsonUtils.parseObject(resp.body());
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void applyAuth(cn.hutool.http.HttpRequest req) {
            String authType = Optional.ofNullable(config.getAuthType()).orElse("basic").trim().toLowerCase();
            switch (authType) {
                case "oauth2_client_credentials":
                case "oauth2":
                    String bearer = getBearerToken();
                    req.header("Authorization", "Bearer " + bearer);
                    break;
                case "basic":
                default:
                    if (Asserts.isNotNullString(config.getUsername())) {
                        String raw = config.getUsername() + ":" + Optional.ofNullable(config.getPassword()).orElse("");
                        String enc = java.util.Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        req.header("Authorization", "Basic " + enc);
                    }
            }
            req.header("Accept", "application/json");
        }

        private String getBearerToken() {
            OAuthToken current = token;
            long now = System.currentTimeMillis();
            if (current != null && current.expiresAtMs - 30_000 > now) {
                return current.accessToken;
            }
            synchronized (this) {
                current = token;
                now = System.currentTimeMillis();
                if (current != null && current.expiresAtMs - 30_000 > now) {
                    return current.accessToken;
                }
                token = fetchToken();
                return token.accessToken;
            }
        }

        private OAuthToken fetchToken() {
            Asserts.checkNullString(config.getTokenUrl(), "tokenUrl is required for oauth2_client_credentials");
            Asserts.checkNullString(config.getClientId(), "clientId is required for oauth2_client_credentials");
            Asserts.checkNullString(config.getClientSecret(), "clientSecret is required for oauth2_client_credentials");

            String form = "grant_type=client_credentials"
                    + "&client_id=" + urlEncode(config.getClientId())
                    + "&client_secret=" + urlEncode(config.getClientSecret());
            if (Asserts.isNotNullString(config.getScope())) {
                form += "&scope=" + urlEncode(config.getScope());
            }

            try {
                cn.hutool.http.HttpRequest req = cn.hutool.http.HttpRequest.post(config.getTokenUrl())
                        .timeout((int) timeout.toMillis())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .body(form);
                cn.hutool.http.HttpResponse resp = req.execute();
                if (resp.getStatus() / 100 != 2) {
                    throw new IllegalStateException("OAuth token HTTP " + resp.getStatus() + ": " + resp.body());
                }
                JsonNode json = JsonUtils.parseObject(resp.body());
                String accessToken = json.path("access_token").asText(null);
                long expiresIn = json.path("expires_in").asLong(3600);
                if (Asserts.isNullString(accessToken)) {
                    throw new IllegalStateException("OAuth token response missing access_token");
                }
                return new OAuthToken(accessToken, System.currentTimeMillis() + expiresIn * 1000);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        static String urlEncode(String s) {
            if (s == null) {
                return "";
            }
            try {
                return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return s;
            }
        }
    }

    static final class OAuthToken {
        final String accessToken;
        final long expiresAtMs;

        OAuthToken(String accessToken, long expiresAtMs) {
            this.accessToken = accessToken;
            this.expiresAtMs = expiresAtMs;
        }
    }
}

