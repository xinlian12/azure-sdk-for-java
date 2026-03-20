// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * A collection of headers on an HTTP request or response.
 */
public class HttpHeaders implements Iterable<HttpHeader>, JsonSerializable {
    private Map<String, HttpHeader> headers;
    private final boolean keysAlreadyLowerCased;

    /**
     * Create an empty HttpHeaders instance.
     */
    public HttpHeaders() {
        this.headers = new HashMap<>();
        this.keysAlreadyLowerCased = false;
    }

    /**
     * Create an HttpHeaders instance with the given size.
     */
    public HttpHeaders(int size) {
        this.headers = new HashMap<>(size);
        this.keysAlreadyLowerCased = false;
    }

    /**
     * Create an HttpHeaders instance with the given size.
     * When {@code keysAlreadyLowerCased} is true, {@link #set(String, String)} skips
     * the {@code toLowerCase()} call on header names. This is an optimization for HTTP/2
     * responses where header names are guaranteed to be lowercase per RFC 7540 §8.1.2.
     *
     * @param size the initial capacity
     * @param keysAlreadyLowerCased true if header names are guaranteed to be lowercase (e.g. HTTP/2)
     */
    public HttpHeaders(int size, boolean keysAlreadyLowerCased) {
        this.headers = new HashMap<>(size);
        this.keysAlreadyLowerCased = keysAlreadyLowerCased;
    }

    /**
     * Create a HttpHeaders instance with the provided initial headers.
     *
     * @param headers the map of initial headers
     */
    public HttpHeaders(Map<String, String> headers) {
        this.headers = new HashMap<>(headers.size());
        this.keysAlreadyLowerCased = false;
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
        final String headerKey = keysAlreadyLowerCased ? name : name.toLowerCase(Locale.ROOT);
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
        final String headerKey = keysAlreadyLowerCased ? headerName : headerName.toLowerCase(Locale.ROOT);
        return headers.get(headerKey);
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

    /**
     * Returns true if header keys are guaranteed to be lowercase,
     * meaning {@link #toLowerCaseMap()} and {@link #toMap()} produce equivalent keys.
     */
    public boolean areKeysLowerCased() {
        return keysAlreadyLowerCased;
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
