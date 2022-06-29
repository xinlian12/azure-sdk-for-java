// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class Uri {
    private static Duration DEFAULT_UNHEALTHY_EFFECTIVE_TIME = Duration.ofMinutes(1);

    private final String uriAsString;
    private final URI uri;
    private final AtomicReference<HealthStatus> healthStatus;
    private volatile Instant failedStatusResetTime;


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
        this.failedStatusResetTime = null;
    }

    public URI getURI() {
        return this.uri;
    }

    public String getURIAsString() {
        return this.uriAsString;
    }

    public void setHealthStatus(HealthStatus status) {
        this.healthStatus.updateAndGet(currentStatus -> {
            switch (status) {
                case Unhealthy:
                    this.healthStatus.set(HealthStatus.Unhealthy);
                    this.failedStatusResetTime = Instant.now().plusMillis(DEFAULT_UNHEALTHY_EFFECTIVE_TIME.toMillis());
                    return HealthStatus.Unhealthy;
                case Healthy:
                    if (this.healthStatus.get() == HealthStatus.Unknown
                            || this.healthStatus.get() == HealthStatus.Healthy
                            || Instant.now().compareTo(this.failedStatusResetTime) > 0) {

                        this.failedStatusResetTime = null; // reset failed time
                        return HealthStatus.Healthy;
                    }
                    return currentStatus;
                case Unknown:
                    throw new IllegalStateException("It is impossible to set to unknown status");
                default:
                    throw new IllegalStateException("Unsupported health status: " + status);
            }
        });
    }

    public void setRefreshed() {
        // if the health status is unhealthy, it will extend the refresh time
        this.setHealthStatus(this.healthStatus.get());
    }

    public HealthStatus getHealthStatus() {
        return this.healthStatus.get();
    }

    public boolean shouldRefreshHeathStatus() {
        return this.failedStatusResetTime != null && Instant.now().compareTo(this.failedStatusResetTime) > 0;
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
