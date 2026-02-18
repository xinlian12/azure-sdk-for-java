// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * POJO representing a single tenant's Cosmos account configuration.
 * Deserialized from the tenants.json file described in §4.3 of the test plan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantAccountInfo {

    private static final Logger logger = LoggerFactory.getLogger(TenantAccountInfo.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonProperty("id")
    private String id;

    @JsonProperty("serviceEndpoint")
    private String serviceEndpoint;

    @JsonProperty("masterKey")
    private String masterKey;

    @JsonProperty("databaseId")
    private String databaseId;

    @JsonProperty("containerId")
    private String containerId;

    @JsonProperty("overrides")
    private Map<String, String> overrides;

    public TenantAccountInfo() {
        this.overrides = new HashMap<>();
    }

    public TenantAccountInfo(String id, String serviceEndpoint, String masterKey,
                             String databaseId, String containerId, Map<String, String> overrides) {
        this.id = id;
        this.serviceEndpoint = serviceEndpoint;
        this.masterKey = masterKey;
        this.databaseId = databaseId;
        this.containerId = containerId;
        this.overrides = overrides != null ? overrides : new HashMap<>();
    }

    // ── Getters ──

    public String getId() {
        return id;
    }

    public String getServiceEndpoint() {
        return serviceEndpoint;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public String getContainerId() {
        return containerId;
    }

    public Map<String, String> getOverrides() {
        return overrides != null ? overrides : Collections.emptyMap();
    }

    // ── Setters ──

    public void setId(String id) {
        this.id = id;
    }

    public void setServiceEndpoint(String serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public void setOverrides(Map<String, String> overrides) {
        this.overrides = overrides;
    }

    @Override
    public String toString() {
        return "TenantAccountInfo{" +
            "id='" + id + '\'' +
            ", serviceEndpoint='" + serviceEndpoint + '\'' +
            ", databaseId='" + databaseId + '\'' +
            ", containerId='" + containerId + '\'' +
            ", overrides=" + (overrides != null ? overrides.keySet() : "null") +
            '}';
    }

    // ── Static parsing ──

    /**
     * Parsed result from a tenants.json file.
     */
    public static class TenantsConfig {
        private final Map<String, String> globalDefaults;
        private final List<TenantAccountInfo> tenants;

        public TenantsConfig(Map<String, String> globalDefaults, List<TenantAccountInfo> tenants) {
            this.globalDefaults = globalDefaults;
            this.tenants = tenants;
        }

        public Map<String, String> getGlobalDefaults() {
            return globalDefaults;
        }

        public List<TenantAccountInfo> getTenants() {
            return tenants;
        }
    }

    /**
     * Parse a tenants.json file into global defaults + a list of tenant accounts.
     * Supports both explicit {@code tenants[]} and {@code tenantTemplate} expansion.
     *
     * @param tenantsFile path to tenants.json
     * @return parsed config
     * @throws IOException if the file cannot be read or parsed
     */
    public static TenantsConfig parseTenantsFile(File tenantsFile) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(tenantsFile);

        // Parse globalDefaults as flat key-value map
        Map<String, String> globalDefaults = new HashMap<>();
        JsonNode defaultsNode = root.get("globalDefaults");
        if (defaultsNode != null && defaultsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = defaultsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                globalDefaults.put(entry.getKey(), entry.getValue().asText());
            }
        }

        List<TenantAccountInfo> tenants = new ArrayList<>();

        // Parse explicit tenants[]
        JsonNode tenantsNode = root.get("tenants");
        if (tenantsNode != null && tenantsNode.isArray()) {
            for (JsonNode tenantNode : tenantsNode) {
                TenantAccountInfo tenant = OBJECT_MAPPER.treeToValue(tenantNode, TenantAccountInfo.class);
                tenants.add(tenant);
            }
        }

        // Parse tenantTemplate (generates N tenants from pattern)
        JsonNode templateNode = root.get("tenantTemplate");
        if (templateNode != null && templateNode.isObject()) {
            boolean enabled = templateNode.has("enabled") && templateNode.get("enabled").asBoolean(false);
            if (enabled) {
                int count = templateNode.has("count") ? templateNode.get("count").asInt(0) : 0;
                String endpointPattern = templateNode.has("endpointPattern")
                    ? templateNode.get("endpointPattern").asText() : "";
                String keyEnvVarPattern = templateNode.has("keyEnvVarPattern")
                    ? templateNode.get("keyEnvVarPattern").asText() : "";
                String databaseId = templateNode.has("databaseId")
                    ? templateNode.get("databaseId").asText() : "";
                String containerIdPattern = templateNode.has("containerIdPattern")
                    ? templateNode.get("containerIdPattern").asText() : "";

                logger.info("Expanding tenant template: {} tenants from pattern", count);

                for (int i = 0; i < count; i++) {
                    String tenantId = "tenant-" + i;
                    String endpoint = endpointPattern.replace("{i}", String.valueOf(i));
                    String containerId = containerIdPattern.replace("{i}", String.valueOf(i));

                    // Resolve master key from environment variable
                    String keyEnvVar = keyEnvVarPattern.replace("{i}", String.valueOf(i));
                    String masterKey = System.getenv(keyEnvVar);
                    if (masterKey == null || masterKey.isEmpty()) {
                        logger.warn("Environment variable {} not set for tenant {}. " +
                            "This tenant will need managed identity or the key must be set later.",
                            keyEnvVar, tenantId);
                    }

                    TenantAccountInfo tenant = new TenantAccountInfo(
                        tenantId, endpoint, masterKey, databaseId, containerId, null);
                    tenants.add(tenant);
                }
            }
        }

        logger.info("Parsed {} tenants from {}", tenants.size(), tenantsFile.getName());
        return new TenantsConfig(globalDefaults, tenants);
    }
}
