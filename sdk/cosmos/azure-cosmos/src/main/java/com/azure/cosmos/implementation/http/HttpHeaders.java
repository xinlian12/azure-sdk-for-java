// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A collection of headers on an HTTP request or response.
 *
 * Internally stores headers as lowercased-key to value mappings,
 * avoiding per-header HttpHeader object allocation.
 */
public class HttpHeaders implements Iterable<HttpHeader>, JsonSerializable {
    private Map<String, String> headers;

    /**
     * Create an empty HttpHeaders instance.
     */
    public HttpHeaders() {
        this.headers = new HashMap<>(16);
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
        final String headerKey = name.toLowerCase(Locale.ROOT);
        if (value == null) {
            headers.remove(headerKey);
        } else {
            headers.put(headerKey, value);
        }
        return this;
    }

    /**
     * Set a header using a pre-lowered key, skipping toLowerCase.
     * The caller MUST guarantee that loweredKey is already lowercase.
     *
     * @param loweredKey the already-lowercase header name
     * @param value the value
     * @return this HttpHeaders
     */
    public HttpHeaders setLowered(String loweredKey, String value) {
        if (value == null) {
            headers.remove(loweredKey);
        } else {
            headers.put(loweredKey, value);
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
        final String headerKey = name.toLowerCase(Locale.ROOT);
        return headers.get(headerKey);
    }

    /**
     * Get the header value for a pre-lowered header name, skipping toLowerCase.
     * The caller MUST guarantee that loweredName is already lowercase.
     *
     * @param loweredName the already-lowercase header name
     * @return The String value of the header, or null if the header isn't found
     */
    public String valueLowered(String loweredName) {
        return headers.get(loweredName);
    }

    /**
     * Get the header values for the provided header name. Null will be returned if
     * the header name isn't found.
     *
     * @param name the name of the header to look for
     * @return the values of the header, or null if the header isn't found
     */
    public String[] values(String name) {
        final String val = value(name);
        return val == null ? null : StringUtils.split(val, ",");
    }

    /**
     * Get {@link Map} representation of the HttpHeaders collection.
     *
     * @return the headers as map (keys are lowercase)
     */
    public Map<String, String> toMap() {
        return new HashMap<>(headers);
    }

    /**
     * Get {@link Map} representation of the HttpHeaders collection with lower casing header name.
     *
     * @return the headers as map
     */
    public Map<String, String> toLowerCaseMap() {
        return new HashMap<>(headers);
    }

    /**
     * Populates the provided arrays with lowercased header names and their values
     * directly from the internal map, avoiding intermediate HashMap allocation.
     *
     * @param names  array to populate with lowercased header names (must be at least size() long)
     * @param values array to populate with header values (must be at least size() long)
     */
    public void populateLowerCaseHeaders(String[] names, String[] values) {
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            names[i] = entry.getKey();
            values[i] = entry.getValue();
            i++;
        }
    }

    @Override
    public Iterator<HttpHeader> iterator() {
        final Iterator<Map.Entry<String, String>> entries = headers.entrySet().iterator();
        return new Iterator<HttpHeader>() {
            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public HttpHeader next() {
                if (!entries.hasNext()) {
                    throw new NoSuchElementException();
                }
                Map.Entry<String, String> entry = entries.next();
                return new HttpHeader(entry.getKey(), entry.getValue());
            }
        };
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
