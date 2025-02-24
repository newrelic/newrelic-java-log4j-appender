package com.newrelic.labs;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.newrelic.telemetry.Attributes;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {
    private final String message;
    private final String applicationName;
    private final String name;
    private final String logtype;
    private final long timestamp;
    private final Map<String, Object> properties; // Add custom fields

    // Default constructor for Jackson
    public LogEntry() {
        this.message = null;
        this.applicationName = null;
        this.name = null;
        this.logtype = null;
        this.timestamp = 0L;
        this.properties = null; // Initialize custom fields
    }

    @JsonCreator
    public LogEntry(@JsonProperty("message") String message, @JsonProperty("applicationname") String applicationName,
            @JsonProperty("name") String name, @JsonProperty("logtype") String logtype,
            @JsonProperty("timestamp") long timestamp, @JsonProperty("custom") Map<String, Object> properties) { // Add

        this.message = message;
        this.applicationName = applicationName;
        this.name = name;
        this.logtype = logtype;
        this.timestamp = timestamp;
        this.properties = properties; // Initialize custom fields
    }

    public LogEntry(String message, String applicationName, String name, String logtype, long timestamp,
            Map<String, Object> properties, boolean mergeCustomFields) {
        this.message = message;
        this.applicationName = applicationName;
        this.name = name;
        this.logtype = logtype;
        this.timestamp = timestamp;
        this.properties = properties; // Initialize custom fields
    }

    public String getApplicationName() {
        return applicationName;
    }

    private String getHostname() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            return localhost != null ? localhost.getHostName() : "unknown";
        } catch (UnknownHostException e) {
            System.err.println("Error resolving local host: " + e.getMessage());
            return "unknown";
        }
    }

    public String getLogType() {
        return logtype;
    }

    public String getMessage() {
        return message;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getProperties() { // Add getter for custom
        return properties;
    }

    public Attributes getPropertiesAttributes() // Add getter for custom
    {
        Attributes attributes = new Attributes();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() instanceof String) {
                attributes.put("properties." + entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                attributes.put("properties." + entry.getKey(), (Number) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                attributes.put("properties." + entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() != null) {
                // Convert other types to string
                attributes.put("properties." + entry.getKey(), entry.getValue().toString());
            }
            attributes.put("applicationName", this.applicationName);
            attributes.put("logtype", this.logtype);
            attributes.put("name", this.name);
            attributes.put("hostname", getHostname());
            attributes.put("source", "NRBatchingAppender");
        }
        return attributes;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
