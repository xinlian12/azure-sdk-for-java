package com.azure.cosmos.models;

public enum ServerErrorTypes {
    // TODO: what other common scenarios we want to add
    // limit the errors for the improvements
    BAD_REQUEST,
    CONFLICT,
    FORBIDDEN,
    PARTITION_SPLIT, // exclude it for now
    SERVER_410,
    INTERNAL_SERVER_ERROR,
    PRE_CONDITION_FAILED,
    SERVER_449,
    TOO_MANY_REQUEST,
    NOT_FOUND_0,
    NOT_FOUND_1002,
    SERVER_REQUEST_TIME_OUT; //client timeout will be on connection delay
}
