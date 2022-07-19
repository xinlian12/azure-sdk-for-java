package com.azure.cosmos.models;

public class TestObject {
    String id;
    String mypk;
    String prop;

    public TestObject() {
    }

    public TestObject(String id, String mypk, String prop) {
        this.id = id;
        this.mypk = mypk;
        this.prop = prop;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMypk() {
        return mypk;
    }

    public void setMypk(String mypk) {
        this.mypk = mypk;
    }

    public String getProp() {
        return prop;
    }

    public void setProp(String prop) {
        this.prop = prop;
    }
}
