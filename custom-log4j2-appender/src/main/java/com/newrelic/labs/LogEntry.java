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

	public LogEntry(String message, String applicationName, String name, String logtype, long timestamp,
			Map<String, Object> custom, boolean mergeCustomFields) {
		this.message = message;
		this.applicationName = applicationName;
		this.name = name;
		this.logtype = logtype;
		this.timestamp = timestamp;

	}

	// Default constructor for Jackson
	public LogEntry() {
		this.message = null;
		this.applicationName = null;
		this.name = null;
		this.logtype = null;
		this.timestamp = 0L;
	}

	@JsonCreator
	public LogEntry(@JsonProperty("message") String message, @JsonProperty("applicationname") String applicationName,
			@JsonProperty("name") String name, @JsonProperty("logtype") String logtype,
			@JsonProperty("timestamp") long timestamp) {
		this.message = message;
		this.applicationName = applicationName;
		this.name = name;
		this.logtype = logtype;
		this.timestamp = timestamp;
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
}