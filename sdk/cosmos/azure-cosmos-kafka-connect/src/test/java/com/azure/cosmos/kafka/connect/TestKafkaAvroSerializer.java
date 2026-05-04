// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.kafka.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A test-only Kafka Avro serializer that produces messages in the Confluent wire format
 * (magic byte + 4-byte schema ID + Avro binary data) without depending on Confluent's
 * kafka-avro-serializer library.
 *
 * This allows removing the dependency on the Confluent Maven repository
 * (packages.confluent.io) while still being able to produce Avro messages compatible
 * with the Confluent AvroConverter running inside the Kafka Connect Docker container.
 */
public class TestKafkaAvroSerializer implements Serializer<GenericRecord> {
    private static final byte MAGIC_BYTE = 0x0;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String schemaRegistryUrl;
    private String basicAuthHeader;
    private boolean isKey;
    private final ConcurrentHashMap<String, Integer> schemaIdCache = new ConcurrentHashMap<>();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.isKey = isKey;
        this.schemaRegistryUrl = (String) configs.get("schema.registry.url");
        if (this.schemaRegistryUrl != null && this.schemaRegistryUrl.endsWith("/")) {
            this.schemaRegistryUrl = this.schemaRegistryUrl.substring(0, this.schemaRegistryUrl.length() - 1);
        }

        String authSource = (String) configs.get("basic.auth.credentials.source");
        if ("USER_INFO".equals(authSource)) {
            String userInfo = (String) configs.get("basic.auth.user.info");
            if (userInfo != null && !userInfo.isEmpty()) {
                this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(
                    userInfo.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Override
    public byte[] serialize(String topic, GenericRecord data) {
        if (data == null) {
            return null;
        }

        try {
            Schema schema = data.getSchema();
            String subject = topic + (isKey ? "-key" : "-value");

            int schemaId = getOrRegisterSchemaId(subject, schema);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC_BYTE);
            out.write(ByteBuffer.allocate(4).putInt(schemaId).array());

            GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(data, encoder);
            encoder.flush();

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Avro record", e);
        }
    }

    private int getOrRegisterSchemaId(String subject, Schema schema) {
        String cacheKey = subject + ":" + schema.toString();
        Integer cachedId = schemaIdCache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }
        int id = registerSchema(subject, schema);
        schemaIdCache.put(cacheKey, id);
        return id;
    }

    private int registerSchema(String subject, Schema schema) {
        try {
            String url = schemaRegistryUrl + "/subjects/" + subject + "/versions";
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            connection.setRequestProperty("Accept", "application/vnd.schemaregistry.v1+json");

            if (basicAuthHeader != null) {
                connection.setRequestProperty("Authorization", basicAuthHeader);
            }

            connection.setDoOutput(true);

            // The Schema Registry API expects {"schema": "<escaped schema json string>"}
            String requestBody = "{\"schema\": " + OBJECT_MAPPER.writeValueAsString(schema.toString()) + "}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                InputStream errorStream = connection.getErrorStream();
                String errorBody = errorStream != null
                    ? new String(readAllBytes(errorStream), StandardCharsets.UTF_8)
                    : "unknown";
                throw new RuntimeException(
                    "Failed to register schema for subject '" + subject
                        + "'. Status: " + responseCode + ", Body: " + errorBody);
            }

            String responseBody = new String(
                readAllBytes(connection.getInputStream()), StandardCharsets.UTF_8);
            JsonNode response = OBJECT_MAPPER.readTree(responseBody);
            return response.get("id").asInt();
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to register schema with Schema Registry at " + schemaRegistryUrl, e);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    @Override
    public void close() {
        // No resources to close
    }
}
