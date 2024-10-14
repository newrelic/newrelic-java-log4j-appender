package com.newrelic.labs;

public class LogEntry {
	private final String message;
	private final String applicationName;
	private final String name;
	private final String logtype;
	private final long timestamp;

	public LogEntry(String message, String applicationName, String name, String logtype, long timestamp) {
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