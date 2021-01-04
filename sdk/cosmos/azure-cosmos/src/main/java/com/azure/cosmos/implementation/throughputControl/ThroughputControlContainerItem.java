package com.azure.cosmos.implementation.throughputControl;

public abstract class ThroughputControlContainerItem {
    private final String id;
    private final String group;
    private String _etag;

    public ThroughputControlContainerItem(String id, String group) {
        this.id = id;
        this.group = group;
    }

    public String getEtag() {
        return _etag;
    }

    public void setEtag(String etag) {
        this._etag = etag;
    }

    public String getId() {
        return id;
    }

    public String getGroup() {
        return group;
    }
}
