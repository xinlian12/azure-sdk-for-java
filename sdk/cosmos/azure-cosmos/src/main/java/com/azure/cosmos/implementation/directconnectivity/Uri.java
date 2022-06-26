// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class Uri {
    private final String uriAsString;
    private final URI uri;
    private final AtomicReference<HealthStatus> healthStatus;

    public static Uri create(String uriAsString) {
        return new Uri(uriAsString);
    }

    public Uri(String uri) {
        this.uriAsString = uri;

        URI uriValue = null;
        try {
            uriValue = URI.create(uri);
        } catch (IllegalArgumentException e) {
            uriValue = null;
        }
        this.uri = uriValue;
        this.healthStatus = new AtomicReference<>(HealthStatus.Unknown);
    }

    public URI getURI() {
        return this.uri;
    }

    public String getURIAsString() {
        return this.uriAsString;
    }

    public void setHealthStatus(HealthStatus status) {
        this.healthStatus.set(status);
    }

    public HealthStatus getHealthStatus() {
        return this.healthStatus.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Uri uri1 = (Uri) o;
        return uriAsString.equals(uri1.uriAsString) &&
            uri.equals(uri1.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uriAsString, uri);
    }

    @Override
    public String toString() {
        return this.uriAsString;
    }

    public enum HealthStatus {
        Healthy(0),
        Unknown(1),
        Unhealthy(2);

        private int value;
        HealthStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }
}
