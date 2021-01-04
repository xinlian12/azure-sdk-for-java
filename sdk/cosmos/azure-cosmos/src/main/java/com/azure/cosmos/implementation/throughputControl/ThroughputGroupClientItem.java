package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.implementation.Constants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@JsonSerialize(using = ThroughputGroupClientItem.ThroughputBudgetGroupClientItemJsonSerializer.class)
public class ThroughputGroupClientItem extends ThroughputControlContainerItem {
    private static final String PROPERTY_NAME_GROUP = "group";
    private static final String PROPERTY_NAME_HOSTNAME = "hostname";
    private static final String PROPERTY_NAME_INITIALIZATION_TIME = "initializeTime";
    private static final String PROPERTY_NAME_LOAD_FACTOR = "loadFactor";

    private static final ZonedDateTime UNIX_START_TIME = ZonedDateTime.parse("1970-01-01T00:00:00.0Z[UTC]");

    private final String initializeTime;
    private double loadFactor;
    private Integer ttl;

    public ThroughputGroupClientItem(
        String id,
        String group) {
        super(id, group);

        ZonedDateTime currentTime = ZonedDateTime.now(ZoneId.of("UTC"));
        this.initializeTime = currentTime.toString();
    }

    public String getInitializeTime() {
        return initializeTime;
    }

    public double getLoadFactor() {
        return loadFactor;
    }

    public void setLoadFactor(double loadFactor) {
        this.loadFactor = loadFactor;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    static final class ThroughputBudgetGroupClientItemJsonSerializer extends StdSerializer<ThroughputGroupClientItem> {
        // this value should be incremented if changes are made to the ServiceItemLease class members
        private static final long serialVersionUID = 1L;

        protected ThroughputBudgetGroupClientItemJsonSerializer() { this(null); }

        protected ThroughputBudgetGroupClientItemJsonSerializer(Class<ThroughputGroupClientItem> t) {
            super(t);
        }

        @Override
        public void serialize(ThroughputGroupClientItem item, JsonGenerator writer, SerializerProvider serializerProvider) {
            try {
                writer.writeStartObject();
                writer.writeStringField(Constants.Properties.ID, item.getId());
                writer.writeStringField(Constants.Properties.E_TAG, item.getEtag());
                writer.writeStringField(PROPERTY_NAME_GROUP, item.getGroup());
                writer.writeStringField(PROPERTY_NAME_INITIALIZATION_TIME, item.initializeTime);
                writer.writeStringField(PROPERTY_NAME_LOAD_FACTOR, String.valueOf(item.loadFactor));
                writer.writeEndObject();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
