package com.newrelic.labs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LogForwarder {
	private final NRBufferWithFifoEviction<LogEntry> logQueue;
	private final String apiKey;
	private final String apiURL;
	private final OkHttpClient client;
	private final ObjectMapper objectMapper;
	private final long maxMessageSize;
	// 1.0.5
	private final int maxRetries;
	private final int connPoolSize;
	private final long timeout; // New parameter for connection timeout
	// 1.0.5

	public LogForwarder(String apiKey, String apiURL, long maxMessageSize, NRBufferWithFifoEviction<LogEntry> queue,
			int maxRetries, long timeout, int connPoolSize) {
		this.apiKey = apiKey;
		this.apiURL = apiURL;
		this.maxMessageSize = maxMessageSize;
		this.logQueue = queue;
		this.maxRetries = maxRetries;
		this.timeout = timeout;
		this.connPoolSize = connPoolSize;
		// Configure connection pooling 1.0.6
		ConnectionPool connectionPool = new ConnectionPool(connPoolSize, 5, TimeUnit.MINUTES); // 10 connections,
																								// 5-minute
		// keep-alive

		// Initialize OkHttpClient with connection pooling 1.0.6
		this.client = new OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.MILLISECONDS)
				.connectionPool(connectionPool).build();
		// this.client = new OkHttpClient.Builder().connectTimeout(timeout,
		// TimeUnit.MILLISECONDS).build();
		this.objectMapper = new ObjectMapper();

	}

	public boolean isInitialized() {
		return apiKey != null && apiURL != null;
	}

	public boolean flush(List<LogEntry> logEntries, boolean mergeCustomFields, Map<String, Object> customFields) {
		boolean bStatus = false;

		try {
			List<Map<String, Object>> logEvents = convertToLogEvents(logEntries, mergeCustomFields, customFields);

			String jsonPayload = objectMapper.writeValueAsString(logEvents);
			byte[] compressedPayload = gzipCompress(jsonPayload);

			// System.out.println("compressedPayload size " + compressedPayload.length);
			// System.out.println("jsonPayload size " + jsonPayload.length());

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

	public void flushAsync(List<LogEntry> logEntries, boolean mergeCustomFields, Map<String, Object> customFields,
			FlushCallback callback) {
		List<Map<String, Object>> logEvents = convertToLogEvents(logEntries, mergeCustomFields, customFields);

		if (logEvents.size() > maxMessageSize) {
			try {
				splitAndSendLogsAsync(logEntries, mergeCustomFields, customFields, callback);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			sendLogsAsync(logEvents, callback);
		}
	}

	private boolean splitAndSendLogs(List<LogEntry> logEntries, boolean mergeCustomFields,
			Map<String, Object> customFields) throws IOException {
		List<LogEntry> subBatch = new ArrayList<>();
		int currentSize = 0;
		boolean bStatus = false;

		for (LogEntry entry : logEntries) {
			subBatch.add(entry);
			String entryJson = objectMapper
					.writeValueAsString(convertToLogEvent(entry, mergeCustomFields, customFields));
			int entrySize = gzipCompress(entryJson).length;

			if (currentSize + entrySize > maxMessageSize) {
				bStatus = sendLogs(convertToLogEvents(subBatch, mergeCustomFields, customFields));
				subBatch.clear();
				currentSize = 0;
			}

			currentSize += entrySize;
		}

		if (!subBatch.isEmpty()) {
			bStatus = sendLogs(convertToLogEvents(subBatch, mergeCustomFields, customFields));
		}

		return bStatus;
	}

	private void splitAndSendLogsAsync(List<LogEntry> logEntries, boolean mergeCustomFields,
			Map<String, Object> customFields, FlushCallback callback) throws IOException {
		List<LogEntry> subBatch = new ArrayList<>();
		int currentSize = 0;

		for (LogEntry entry : logEntries) {
			subBatch.add(entry);
			String entryJson = objectMapper
					.writeValueAsString(convertToLogEvent(entry, mergeCustomFields, customFields));
			int entrySize = gzipCompress(entryJson).length;

			if (currentSize + entrySize > maxMessageSize) {
				sendLogsAsync(convertToLogEvents(subBatch, mergeCustomFields, customFields), callback);
				subBatch.clear();
				currentSize = 0;
			}

			currentSize += entrySize;
		}

		if (!subBatch.isEmpty()) {
			sendLogsAsync(convertToLogEvents(subBatch, mergeCustomFields, customFields), callback);
		}
	}

	private List<Map<String, Object>> convertToLogEvents(List<LogEntry> logEntries, boolean mergeCustomFields,
			Map<String, Object> customFields) {
		List<Map<String, Object>> logEvents = new ArrayList<>();
		for (LogEntry entry : logEntries) {
			logEvents.add(convertToLogEvent(entry, mergeCustomFields, customFields));
		}
		return logEvents;
	}

	private Map<String, Object> convertToLogEvent(LogEntry entry, boolean mergeCustomFields,
			Map<String, Object> customFields) {
		Map<String, Object> logEvent = objectMapper.convertValue(entry, LowercaseKeyMap.class);
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			String hostname = localhost != null ? localhost.getHostName() : "unknown";
			logEvent.put("hostname", hostname);
		} catch (UnknownHostException e) {
			System.err.println("Error resolving local host: " + e.getMessage());
		}
		logEvent.put("logtype", entry.getLogType());
		logEvent.put("timestamp", entry.getTimestamp());
		logEvent.put("applicationName", entry.getApplicationName());
		logEvent.put("name", entry.getName());
		logEvent.put("source", "NRBatchingAppender");

		// Add custom fields
		if (customFields != null) {
			if (mergeCustomFields) {
				for (Map.Entry<String, Object> field : customFields.entrySet()) {
					logEvent.put(field.getKey(), field.getValue());
				}
			} else {
				logEvent.put("custom", customFields);
			}
		}

		return logEvent;
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
				boolean added = logQueue.add(logEntry); // 1.0.6

				if (!added) {
					System.err.println("Failed to add log entry to the queue, possibly due to size constraints.");
				}
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

	public void close(boolean mergeCustomFields, Map<String, Object> customFields) { // 1.0.6
		List<LogEntry> remainingLogs = new ArrayList<>();

		// Assuming queue is an instance of BufferWithFifoEviction<LogEntry>
		int drained = logQueue.drainTo(remainingLogs, Integer.MAX_VALUE); // Drain all remaining logs

		if (!remainingLogs.isEmpty()) {
			System.out.println("Flushing remaining " + remainingLogs.size() + " log events to New Relic...");

			boolean success = flush(remainingLogs, mergeCustomFields, customFields);

			if (!success) {
				System.err.println("Failed to flush remaining log events to New Relic.");
			}
		} else {
			System.out.println("No remaining log events to flush.");
		}
	}

	private void sendLogsAsync(List<Map<String, Object>> logEvents, FlushCallback callback) {
		try {
			String jsonPayload = objectMapper.writeValueAsString(logEvents);
			byte[] compressedPayload = gzipCompress(jsonPayload);

			MediaType mediaType = MediaType.parse("application/json");
			RequestBody requestBody = RequestBody.create(compressedPayload, mediaType);
			Request request = new Request.Builder().url(apiURL).post(requestBody).addHeader("X-License-Key", apiKey)
					.addHeader("Content-Type", "application/json").addHeader("Content-Encoding", "gzip").build();

			client.newCall(request).enqueue(new Callback() {
				@Override
				public void onFailure(Call call, IOException e) {
					System.err.println("Failed to send logs asynchronously: " + e.getMessage());
					callback.onFailure(logEvents); // Requeue logs if the request fails
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException {
					try {
						if (!response.isSuccessful()) {
							System.err.println("Failed to send logs asynchronously: " + response.code() + " - "
									+ response.message());
							callback.onFailure(logEvents); // Requeue logs if the response is not successful
						} else {
							callback.onSuccess();
						}
					} finally {
						response.close();
					}
				}
			});
		} catch (IOException e) {
			System.err.println("Error during log forwarding: " + e.getMessage());
			callback.onFailure(logEvents); // Requeue logs if an exception occurs
		}
	}

	// Method to convert a map to a LogEntry
	public LogEntry convertToLogEntry(Map<String, Object> logEvent) {
		try {
			return objectMapper.convertValue(logEvent, LogEntry.class);
		} catch (IllegalArgumentException e) {
			System.err.println("Failed to convert log event to LogEntry: " + e.getMessage());
			return null; // Handle conversion failure as needed
		}
	}
}
