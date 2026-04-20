// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity;

import com.azure.cosmos.implementation.HttpConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Locale;

import static com.azure.cosmos.implementation.Utils.getUTF8BytesOrNull;
import static org.assertj.core.api.Assertions.assertThat;

public class StoreResponseTest {
    @Test(groups = { "unit" })
    public void stringContent() {
        String content = "I am body";
        String jsonContent = "{\"id\":\"" + content + "\"}";
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("key1", "value1");
        headerMap.put("key2", "value2");

        ByteBuf buffer = getUTF8BytesOrNull(jsonContent);
        StoreResponse sp = new StoreResponse(null, 200, headerMap, new ByteBufInputStream(buffer, true), buffer.readableBytes());

        assertThat(sp.getStatus()).isEqualTo(200);
        assertThat(sp.getResponseBodyAsJson().get("id").asText()).isEqualTo(content);
        assertThat(sp.getHeaderValue("key1")).isEqualTo("value1");
    }

    @Test(groups = { "unit" })
    public void headerNamesAreCaseInsensitive() {
        String content = "I am body";
        String jsonContent = "{\"id\":\"" + content + "\"}";
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("key1", "value1");
        headerMap.put("key2", "value2");
        headerMap.put("KEY3", "value3");

        ByteBuf buffer = getUTF8BytesOrNull(jsonContent);
        StoreResponse sp = new StoreResponse(null, 200, headerMap, new ByteBufInputStream(buffer, true), buffer.readableBytes());

        assertThat(sp.getStatus()).isEqualTo(200);
        assertThat(sp.getResponseBodyAsJson().get("id").asText()).isEqualTo(content);
        assertThat(sp.getHeaderValue("keY1")).isEqualTo("value1");
        assertThat(sp.getHeaderValue("kEy2")).isEqualTo("value2");
        assertThat(sp.getHeaderValue("KEY3")).isEqualTo("value3");
    }

    @Test(groups = { "unit" })
    public void withRemappedStatusCode_updatesStatusAndCharge() {
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put(HttpConstants.HttpHeaders.REQUEST_CHARGE.toLowerCase(Locale.ROOT), "5.0");
        headerMap.put("x-ms-activity-id", "test-activity");

        StoreResponse original = new StoreResponse("endpoint", 200, headerMap, null, 0);
        StoreResponse remapped = original.withRemappedStatusCode(201, 2.0);

        assertThat(remapped.getStatus()).isEqualTo(201);
        assertThat(remapped.getRequestCharge()).isEqualTo(7.0);
        assertThat(remapped.getHeaderValue("x-ms-activity-id")).isEqualTo("test-activity");

        // original is unmodified
        assertThat(original.getStatus()).isEqualTo(200);
        assertThat(original.getRequestCharge()).isEqualTo(5.0);
    }

    @Test(groups = { "unit" })
    public void withRemappedStatusCode_doesNotInsertChargeWhenAbsent() {
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("x-ms-activity-id", "test-activity");

        StoreResponse original = new StoreResponse("endpoint", 200, headerMap, null, 0);
        StoreResponse remapped = original.withRemappedStatusCode(304, 1.0);

        assertThat(remapped.getStatus()).isEqualTo(304);
        assertThat(remapped.getHeaderValue(HttpConstants.HttpHeaders.REQUEST_CHARGE)).isNull();
    }

    @Test(groups = { "unit" })
    public void getResponseHeaders_isUnmodifiable() {
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("key1", "value1");

        StoreResponse sp = new StoreResponse("endpoint", 200, headerMap, null, 0);

        try {
            sp.getResponseHeaders().put("key2", "value2");
            assertThat(false).as("Expected UnsupportedOperationException").isTrue();
        } catch (UnsupportedOperationException e) {
            // expected
        }

        // internal state is not affected
        assertThat(sp.getHeaderValue("key2")).isNull();
    }
}
