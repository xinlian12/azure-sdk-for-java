// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.communication.callingserver.models;

import com.azure.core.annotation.Fluent;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The options for join call. */
@Fluent
public final class JoinCallOptions {
    /*
     * The subject.
     */
    @JsonProperty(value = "subject")
    private String subject;

    /*
     * The callback URI.
     */
    @JsonProperty(value = "callbackUri", required = true)
    private String callbackUri;

    /*
     * The requested MediaTypes.
     */
    @JsonProperty(value = "requestedMediaTypes", required = true)
    private MediaType[] requestedMediaTypes;

    /*
     * The requested call events to subscribe to.
     */
    @JsonProperty(value = "requestedCallEvents", required = true)
    private EventSubscriptionType[] requestedCallEvents;

    /**
     * Get the subject property: The subject.
     *
     * @return the subject value.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Set the subject property: The subject.
     *
     * @param subject the subject value to set.
     * @return the JoinCallOptions object itself.
     */
    public JoinCallOptions setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    /**
     * Get the callbackUri property: The callback URI.
     *
     * @return the callbackUri value.
     */
    public String getCallbackUri() {
        return callbackUri;
    }

    /**
     * Set the callbackUri property: The callback URI.
     *
     * @param callbackUri the callbackUri value to set.
     * @return the JoinCallOptions object itself.
     */
    public JoinCallOptions setCallbackUri(String callbackUri) {
        this.callbackUri = callbackUri;
        return this;
    }

    /**
     * Get the requestedMediaTypes property: The requested MediaTypes.
     *
     * @return the requestedMediaTypes value.
     */
    public MediaType[] getRequestedMediaTypes() {
        return requestedMediaTypes == null ? new MediaType[0] : requestedMediaTypes.clone();
    }

    /**
     * Set the requestedMediaTypes property: The requested MediaTypes.
     *
     * @param requestedMediaTypes the requestedModalities value to set.
     * @return the JoinCallOptions object itself.
     */
    public JoinCallOptions setRequestedMediaTypes(MediaType[] requestedMediaTypes) {
        this.requestedMediaTypes = requestedMediaTypes == null ? new MediaType[0] : requestedMediaTypes.clone();
        return this;
    }

    /**
     * Get the requestedCallEvents property: The requested call events to subscribe
     * to.
     *
     * @return the requestedCallEvents value.
     */
    public EventSubscriptionType[] getRequestedCallEvents() {
        return requestedCallEvents == null ? new EventSubscriptionType[0] : requestedCallEvents.clone();
    }

    /**
     * Set the requestedCallEvents property: The requested call events to subscribe
     * to.
     *
     * @param requestedCallEvents the requestedCallEvents value to set.
     * @return the JoinCallOptions object itself.
     */
    public JoinCallOptions setRequestedCallEvents(EventSubscriptionType[] requestedCallEvents) {
        this.requestedCallEvents = requestedCallEvents == null ? new EventSubscriptionType[0] : requestedCallEvents.clone();
        return this;
    }

    /**
     * Initializes a new instance of JoinCallOptions.
     *
     * @param callbackUri the callback URI.
     * @param requestedMediaTypes the requested media types.
     * @param requestedCallEvents the requested call events to subscribe to.
     * @throws IllegalArgumentException if any parameters are null.
     */
    public JoinCallOptions(
        String callbackUri,
        MediaType[] requestedMediaTypes,
        EventSubscriptionType[] requestedCallEvents) {
        if (callbackUri == null) {
            throw new IllegalArgumentException("object callbackUri cannot be null");
        }

        if (requestedMediaTypes == null) {
            throw new IllegalArgumentException("object requestedMediaTypes cannot be null");
        }
        if (requestedCallEvents == null) {
            throw new IllegalArgumentException("object requestedCallEvents cannot be null");
        }

        this.callbackUri = callbackUri;

        this.requestedMediaTypes = requestedMediaTypes.clone();
        this.requestedCallEvents = requestedCallEvents.clone();
    }
}
