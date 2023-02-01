package com.azure.cosmos.models;

import java.time.Duration;

public class FaultInectionConnectionErrorRule {
    private ConnectionErrorTypes connectionErrorTypes;
    private Duration interval;


    // the addresses will also be referred by partition key
    // Interval, duration
    // single connection per endpoint or percentage
    // connection reset /close
    // connection -slow -. per request level
}
