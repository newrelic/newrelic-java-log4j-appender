package com.newrelic.labs;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {
	private final String message;
	private final String applicationName;
	private final String name;
	private final String logtype;
	private final long timestamp;
	private final Map<String, Object> properties; // Add custom fields

	public LogEntry(String message, String applicationName, String name, String logtype, long timestamp,
			Map<String, Object> properties, boolean mergeCustomFields) {
		this.message = message;
		this.applicationName = applicationName;
		this.name = name;
		this.logtype = logtype;
		this.timestamp = timestamp;
		this.properties = properties; // Initialize custom fields
	}

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

	public String getMessage() {
		return message;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public String getName() {
		return name;
	}

	public String getLogType() {
		return logtype;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Map<String, Object> getProperties() { // Add getter for custom
		return properties;
	}
}
