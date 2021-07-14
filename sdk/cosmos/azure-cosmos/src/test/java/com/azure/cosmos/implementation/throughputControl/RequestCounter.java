package com.azure.cosmos.implementation.throughputControl;

public class RequestCounter {
    private String id;
    private String lastName;
    private int counter;
    private long expireTime;

    public RequestCounter(){}

    public RequestCounter(String id, String lastName, int counter, long expireTime) {
        this.id = id;
        this.lastName = lastName;
        this.counter = counter;
        this.expireTime = expireTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }
}
