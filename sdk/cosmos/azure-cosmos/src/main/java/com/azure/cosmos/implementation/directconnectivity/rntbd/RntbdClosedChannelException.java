package com.azure.cosmos.implementation.directconnectivity.rntbd;

public class RntbdClosedChannelException extends java.io.IOException {
    /**
     * Constructs an instance of this class.
     */
    public RntbdClosedChannelException() { }

    public RntbdClosedChannelException(String message) {
        super(message);
    }
}
