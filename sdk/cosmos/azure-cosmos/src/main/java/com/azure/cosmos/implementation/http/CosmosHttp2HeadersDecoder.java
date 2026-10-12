// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.azure.cosmos.implementation.HttpConstants;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Headers;

/**
 * Normalizes the service-version header before HTTP/2 value validation, without relaxing validation
 * of other headers or modifying the HPACK dynamic table.
 */
final class CosmosHttp2HeadersDecoder extends DefaultHttp2HeadersDecoder {
    CosmosHttp2HeadersDecoder(long maxHeaderListSize) {
        super(true, true, maxHeaderListSize);
    }

    @Override
    protected Http2Headers newHeaders() {
        return new DefaultHttp2Headers(true, true, numberOfHeadersGuess()) {
            @Override
            public Http2Headers add(CharSequence name, CharSequence value) {
                if (HttpConstants.HttpHeaders.SERVER_VERSION.contentEquals(name)) {
                    int start = 0;
                    int end = value.length();
                    while (start < end && isOptionalWhitespace(value.charAt(start))) {
                        start++;
                    }
                    while (end > start && isOptionalWhitespace(value.charAt(end - 1))) {
                        end--;
                    }
                    if (start != 0 || end != value.length()) {
                        value = value.subSequence(start, end);
                    }
                }
                return super.add(name, value);
            }
        };
    }

    private static boolean isOptionalWhitespace(char value) {
        return value == ' ' || value == '\t';
    }
}
