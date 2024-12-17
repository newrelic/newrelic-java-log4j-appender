package com.newrelic.labs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LogForwarder {
	private final BlockingQueue<LogEntry> logQueue;
	private final String apiKey;
	private final String apiURL;
	private final OkHttpClient client;
	private final ObjectMapper objectMapper;
	private final long maxMessageSize;
	// 1.0.5
	private final int maxRetries;
	private final long timeout; // New parameter for connection timeout
	// 1.0.5

	public LogForwarder(String apiKey, String apiURL, long maxMessageSize, BlockingQueue<LogEntry> logQueue,
			int maxRetries, long timeout) {
		this.apiKey = apiKey;
		this.apiURL = apiURL;
		this.maxMessageSize = maxMessageSize;
		this.logQueue = logQueue;
		this.maxRetries = maxRetries;
		this.timeout = timeout;
		this.client = new OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.MILLISECONDS).build();
		this.objectMapper = new ObjectMapper();

	}

	public boolean isInitialized() {
		return apiKey != null && apiURL != null;
	}

	public boolean flush(List<LogEntry> logEntries, boolean mergeCustomFields, Map<String, Object> customFields) {
		InetAddress localhost = null;
		boolean bStatus = false;
		try {
			localhost = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		String hostname = localhost != null ? localhost.getHostName() : "unknown";

		try {
			List<Map<String, Object>> logEvents = new ArrayList<>();
			for (LogEntry entry : logEntries) {
				Map<String, Object> logEvent = objectMapper.convertValue(entry, LowercaseKeyMap.class);
				logEvent.put("hostname", hostname);
				logEvent.put("logtype", entry.getLogType());
				logEvent.put("timestamp", entry.getTimestamp());
				logEvent.put("applicationName", entry.getApplicationName());
				logEvent.put("name", entry.getName());
				logEvent.put("source", "NRBatchingAppender");

				// Add custom fields
				if (customFields != null) {
					if (mergeCustomFields) {
						// Traverse all keys and add each field separately
						Map<String, Object> customFields1 = customFields;
						for (Map.Entry<String, Object> field : customFields1.entrySet()) {
							logEvent.put(field.getKey(), field.getValue());
						}
					} else {
						// Directly add the custom fields as a single entry
						logEvent.put("custom", customFields);
					}
				}

				logEvents.add(logEvent);
			}

			String jsonPayload = objectMapper.writeValueAsString(logEvents);
			byte[] compressedPayload = gzipCompress(jsonPayload);

			if (compressedPayload.length > maxMessageSize) {
				// System.out.println("splitAndSendLogs: Called size exceeded " +
				// compressedPayload.length);
				bStatus = splitAndSendLogs(logEntries, mergeCustomFields, customFields);
			} else {
				bStatus = sendLogs(logEvents);
			}
		} catch (IOException e) {
			System.err.println("Error during log forwarding: " + e.getMessage());
			bStatus = false;
		}
		return bStatus;
	}

	private boolean splitAndSendLogs(List<LogEntry> logEntries, boolean mergeCustomFields,
			Map<String, Object> customFields) throws IOException {

		List<LogEntry> subBatch = new ArrayList<>();
		int currentSize = 0;
		boolean bStatus = false;
		for (LogEntry entry : logEntries) {
			Map<String, Object> logEvent = objectMapper.convertValue(entry, LowercaseKeyMap.class);
			logEvent.put("hostname", InetAddress.getLocalHost().getHostName());
			logEvent.put("logtype", entry.getLogType());
			logEvent.put("timestamp", entry.getTimestamp());
			logEvent.put("applicationName", entry.getApplicationName());
			logEvent.put("name", entry.getName());
			logEvent.put("source", "NRBatchingAppender");

			// Add custom fields
			if (customFields != null) {
				if (mergeCustomFields) {
					// Traverse all keys and add each field separately
					Map<String, Object> customFields1 = customFields;
					for (Map.Entry<String, Object> field : customFields1.entrySet()) {
						logEvent.put(field.getKey(), field.getValue());
					}
				} else {
					// Directly add the custom fields as a single entry
					logEvent.put("custom", customFields);
				}
			}

			String entryJson = objectMapper.writeValueAsString(logEvent);
			int entrySize = gzipCompress(entryJson).length;
			if (currentSize + entrySize > maxMessageSize) {
				bStatus = sendLogs(convertToLogEvents(subBatch, mergeCustomFields, customFields));
				subBatch.clear();
				currentSize = 0;
			}
			subBatch.add(entry);
			currentSize += entrySize;
		}
		if (!subBatch.isEmpty()) {
			bStatus = sendLogs(convertToLogEvents(subBatch, mergeCustomFields, customFields));
		}
		return bStatus;
	}

	private List<Map<String, Object>> convertToLogEvents(List<LogEntry> logEntries, boolean mergeCustomFields,
			Map<String, Object> customFields) {
		List<Map<String, Object>> logEvents = new ArrayList<>();
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			String hostname = localhost.getHostName();

			for (LogEntry entry : logEntries) {
				Map<String, Object> logEvent = objectMapper.convertValue(entry, LowercaseKeyMap.class);
				logEvent.put("hostname", hostname);
				logEvent.put("logtype", entry.getLogType());
				logEvent.put("timestamp", entry.getTimestamp());
				logEvent.put("applicationName", entry.getApplicationName());
				logEvent.put("name", entry.getName());
				logEvent.put("source", "NRBatchingAppender");

				// Add custom fields
				if (customFields != null) {
					if (mergeCustomFields) {
						// Traverse all keys and add each field separately
						Map<String, Object> customFields1 = customFields;
						for (Map.Entry<String, Object> field : customFields1.entrySet()) {
							logEvent.put(field.getKey(), field.getValue());
						}
					} else {
						// Directly add the custom fields as a single entry
						logEvent.put("custom", customFields);
					}
				}

				logEvents.add(logEvent);
			}
		} catch (UnknownHostException e) {
			System.err.println("Error resolving local host: " + e.getMessage());
		}
		return logEvents;
	}

	private boolean sendLogs(List<Map<String, Object>> logEvents) throws IOException {
		String jsonPayload = objectMapper.writeValueAsString(logEvents);
		byte[] compressedPayload = gzipCompress(jsonPayload);

		MediaType mediaType = MediaType.parse("application/json");

		RequestBody requestBody = RequestBody.create(compressedPayload, mediaType);
		Request request = new Request.Builder().url(apiURL).post(requestBody).addHeader("X-License-Key", apiKey)
				.addHeader("Content-Type", "application/json").addHeader("Content-Encoding", "gzip").build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.err.println("Failed to send logs to New Relic: " + response.code() + " - " + response.message());
				System.err.println("Response body: " + response.body().string());
				requeueLogs(logEvents); // Requeue logs if the response is not successful
				return false;
			} else {
				// Comment out the following lines to prevent infinite loop
				// LocalDateTime timestamp = LocalDateTime.now();
				// System.out.println("Logs sent to New Relic successfully: " + "at " +
				// timestamp + " size: "
				// + compressedPayload.length + " Bytes");
				// System.out.println("Response: " + response.body().string());
			}
		} catch (IOException e) {
			System.err.println("Error during log forwarding: " + e.getMessage());
			requeueLogs(logEvents); // Requeue logs if an exception occurs
			return false;
		}
		return true;
	}

	private void requeueLogs(List<Map<String, Object>> logEvents) {

		for (Map<String, Object> logEvent : logEvents) {
			try {
				// Log the contents of logEvent
				// System.out.println("logEvent: " + logEvent);

				// Convert logEvent to LogEntry
				LogEntry logEntry = objectMapper.convertValue(logEvent, LogEntry.class);

				// Log the contents of the converted LogEntry
				// System.out.println("Converted LogEntry: ");
				// System.out.println(" message: " + logEntry.getMessage());
				// System.out.println(" applicationName: " + logEntry.getApplicationName());
				// System.out.println(" name: " + logEntry.getName());
				// System.out.println(" logtype: " + logEntry.getLogType());
				// System.out.println(" timestamp: " + logEntry.getTimestamp());

				// Requeue the log entry
				logQueue.put(logEntry);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Failed to requeue log entry: " + logEvent);
			} catch (IllegalArgumentException e) {
				System.err.println("Failed to convert log event to LogEntry: " + logEvent);
			}
		}

		System.err.println("Network issue - NewRelicBatchingAppenderhas re-queued " + logEvents.size() + " entries"
				+ " : queue size " + logQueue.size());
	}

	private byte[] gzipCompress(String input) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
			gzipOS.write(input.getBytes());
		}
		return bos.toByteArray();
	}

	public void close(boolean mergeCustomFields, Map<String, Object> customFields) {
		List<LogEntry> remainingLogs = new ArrayList<>();
		logQueue.drainTo(remainingLogs);
		if (!remainingLogs.isEmpty()) {
			System.out.println("Flushing remaining " + remainingLogs.size() + " log events to New Relic...");
			flush(remainingLogs, mergeCustomFields, customFields);
		}
	}
}
