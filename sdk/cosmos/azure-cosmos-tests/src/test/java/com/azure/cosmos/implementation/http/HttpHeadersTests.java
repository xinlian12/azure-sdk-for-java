// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpHeadersTests {

    @Test(groups = "unit")
    public void caseInsensitiveToMap() {
        String headerName = "Etag";
        String headerValue = "123";

        HttpHeaders headers = new HttpHeaders();
        headers.set(headerName, headerValue);

        Map<String, String> lowerCaseMap = headers.toLowerCaseMap();
        assertThat(lowerCaseMap.get(headerName.toLowerCase())).isEqualTo(headerValue);

        // toMap() now also returns lowered keys (internal storage optimization)
        Map<String, String> map = headers.toMap();
        assertThat(map.get(headerName.toLowerCase())).isEqualTo(headerValue);
    }

    @Test(groups = "unit")
    public void setLoweredSkipsToLowerCase() {
        HttpHeaders headers = new HttpHeaders();
        headers.setLowered("x-ms-request-charge", "3.57");

        assertThat(headers.value("x-ms-request-charge")).isEqualTo("3.57");
        assertThat(headers.valueLowered("x-ms-request-charge")).isEqualTo("3.57");
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test(groups = "unit")
    public void valueLoweredFastPath() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        assertThat(headers.valueLowered("content-type")).isEqualTo("application/json");
        assertThat(headers.valueLowered("Content-Type")).isNull(); // not lowered, won't match
    }
}
