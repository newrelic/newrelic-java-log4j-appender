package com.newrelic.labs;

import java.util.Map;

public class LogEntry {
	private final String message;
	private final String applicationName;
	private final String name;
	private final String logtype;
	private final long timestamp;
	private final Map<String, Object> custom; // Add custom fields

	public LogEntry(String message, String applicationName, String name, String logtype, long timestamp,
			Map<String, Object> custom) {
		this.message = message;
		this.applicationName = applicationName;
		this.name = name;
		this.logtype = logtype;
		this.timestamp = timestamp;
		this.custom = custom; // Initialize custom fields
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

	public Map<String, Object> getcustom() {
		return custom;
	}
}