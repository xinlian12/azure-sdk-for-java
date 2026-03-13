// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.azure.cosmos.implementation.HttpConstants;
import com.azure.cosmos.implementation.directconnectivity.WFConstants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * A collection of headers on an HTTP request or response.
 */
public class HttpHeaders implements Iterable<HttpHeader>, JsonSerializable {
    // Pre-computed lowercase mappings for known header names.
    // Avoids String.toLowerCase() allocation for the ~80% of headers
    // whose names are known SDK constants.
    private static final Map<String, String> LOWERCASE_CACHE = buildLowerCaseCache();

    private Map<String, HttpHeader> headers;

    /**
     * Create an empty HttpHeaders instance.
     */
    public HttpHeaders() {
        this.headers = new HashMap<>();
    }

    /**
     * Create an HttpHeaders instance with the given size.
     */
    public HttpHeaders(int size) {
        this.headers = new HashMap<>(size);
    }

    /**
     * Create a HttpHeaders instance with the provided initial headers.
     *
     * @param headers the map of initial headers
     */
    public HttpHeaders(Map<String, String> headers) {
        this.headers = new HashMap<>(headers.size());
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            this.set(header.getKey(), header.getValue());
        }
    }

    /**
     * Gets the number of headers in the collection.
     *
     * @return the number of headers in this collection.
     */
    public int size() {
        return headers.size();
    }

    /**
     * Set a header.
     *
     * if header with same name already exists then the value will be overwritten.
     * if value is null and header with provided name already exists then it will be removed.
     *
     * @param name the name
     * @param value the value
     * @return this HttpHeaders
     */
    public HttpHeaders set(String name, String value) {
        final String headerKey = internLowerCase(name);
        if (value == null) {
            headers.remove(headerKey);
        } else {
            headers.put(headerKey, new HttpHeader(name, value));
        }
        return this;
    }

    /**
     * Get the header value for the provided header name. Null will be returned if the header
     * name isn't found.
     *
     * @param name the name of the header to look for
     * @return The String value of the header, or null if the header isn't found
     */
    public String value(String name) {
        final HttpHeader header = getHeader(name);
        return header == null ? null : header.value();
    }

    /**
     * Get the header values for the provided header name. Null will be returned if
     * the header name isn't found.
     *
     * @param name the name of the header to look for
     * @return the values of the header, or null if the header isn't found
     */
    public String[] values(String name) {
        final HttpHeader header = getHeader(name);
        return header == null ? null : header.values();
    }

    private HttpHeader getHeader(String headerName) {
        final String headerKey = internLowerCase(headerName);
        return headers.get(headerKey);
    }

    /**
     * Returns the lowercase form of a header name, using a pre-computed cache
     * for known SDK header constants to avoid String allocation.
     */
    private static String internLowerCase(String name) {
        String cached = LOWERCASE_CACHE.get(name);
        return cached != null ? cached : name.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> buildLowerCaseCache() {
        Map<String, String> cache = new HashMap<>(256);
        // Populate from HttpConstants.HttpHeaders
        collectStringConstants(HttpConstants.HttpHeaders.class, cache);
        // Populate from WFConstants.BackendHeaders
        collectStringConstants(WFConstants.BackendHeaders.class, cache);
        return cache;
    }

    private static void collectStringConstants(Class<?> clazz, Map<String, String> cache) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == String.class
                && java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                try {
                    String value = (String) field.get(null);
                    if (value != null) {
                        String lower = value.toLowerCase(Locale.ROOT);
                        cache.put(value, lower);
                        // Also cache the lowercase form mapping to itself
                        cache.put(lower, lower);
                    }
                } catch (IllegalAccessException e) {
                    // skip inaccessible fields
                }
            }
        }
    }

    /**
     * Get {@link Map} representation of the HttpHeaders collection.
     *
     * @return the headers as map
     */
    public Map<String, String> toMap() {
        final Map<String, String> result = new HashMap<>(headers.size());
        for (final HttpHeader header : headers.values()) {
            result.put(header.name(), header.value());
        }
        return result;
    }

    /**
     * Get {@link Map} representation of the HttpHeaders collection with lower casing header name.
     *
     * @return the headers as map
     */
    public Map<String, String> toLowerCaseMap() {
        final Map<String, String> result = new HashMap<>(headers.size());
        for (Map.Entry<String, HttpHeader> entry : headers.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value());
        }
        return result;
    }

    @Override
    public Iterator<HttpHeader> iterator() {
        return headers.values().iterator();
    }

    @Override
    public void serialize(JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeObject(toMap());
    }

    @Override
    public void serializeWithType(JsonGenerator jsonGenerator, SerializerProvider serializerProvider, TypeSerializer typeSerializer) throws IOException {
        serialize(jsonGenerator, serializerProvider);
    }
}
