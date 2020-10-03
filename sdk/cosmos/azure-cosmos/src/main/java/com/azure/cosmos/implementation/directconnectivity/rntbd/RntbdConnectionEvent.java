//------------------------------------------------------------
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------

package com.azure.cosmos.implementation.directconnectivity.rntbd;

public enum RntbdConnectionEvent {

    CHANNEL_EXCEPTION,
    REPLICA_RECONFIG;

    public static final int SIZE = Integer.SIZE;
}
