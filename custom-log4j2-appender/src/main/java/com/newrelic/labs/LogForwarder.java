
package com.newrelic.labs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
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
	private final OkHttpClient client = new OkHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final long maxMessageSize;

	public LogForwarder(String apiKey, String apiURL, long maxMessageSize, BlockingQueue<LogEntry> logQueue) {
		this.apiKey = apiKey;
		this.apiURL = apiURL;
		this.maxMessageSize = maxMessageSize;
		this.logQueue = logQueue;
	}

	public void addToQueue(List<String> lines, String applicationName, String name, String logtype) {
		for (String line : lines) {
			logQueue.add(new LogEntry(line, applicationName, name, logtype, System.currentTimeMillis()));
		}
	}

	public boolean isInitialized() {
		return apiKey != null && apiURL != null;
	}

	public void flush(List<LogEntry> logEntries) {
		InetAddress localhost = null;
		try {
			localhost = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		String hostname = localhost != null ? localhost.getHostName() : "unknown";

		@SuppressWarnings("unused")
		MediaType mediaType = MediaType.parse("application/json");

		try {
			List<Map<String, Object>> logEvents = new ArrayList<>();
			for (LogEntry entry : logEntries) {
				Map<String, Object> logEvent = objectMapper.convertValue(entry, LowercaseKeyMap.class);
				logEvent.put("hostname", hostname);
				logEvent.put("logtype", entry.getLogType());
				logEvent.put("timestamp", entry.getTimestamp()); // Use log creation timestamp
				logEvent.put("applicationName", entry.getApplicationName());
				logEvent.put("name", entry.getName());
				logEvent.put("source", "NRBatchingAppender"); // Add custom field
				logEvents.add(logEvent);
			}

			String jsonPayload = objectMapper.writeValueAsString(logEvents);
			byte[] compressedPayload = gzipCompress(jsonPayload);

			if (compressedPayload.length > maxMessageSize) { // Configurable size limit
				System.err.println("Batch size exceeds limit, splitting batch...");
				List<LogEntry> subBatch = new ArrayList<>();
				int currentSize = 0;
				for (LogEntry entry : logEntries) {
					String entryJson = objectMapper.writeValueAsString(entry);
					int entrySize = gzipCompress(entryJson).length;
					if (currentSize + entrySize > maxMessageSize) {
						sendLogs(subBatch);
						subBatch.clear();
						currentSize = 0;
					}
					subBatch.add(entry);
					currentSize += entrySize;
				}
				if (!subBatch.isEmpty()) {
					sendLogs(subBatch);
				}
			} else {
				sendLogs(logEntries);
			}
		} catch (IOException e) {
			System.err.println("Error during log forwarding: " + e.getMessage());
		}
	}

	private void sendLogs(List<LogEntry> logEntries) throws IOException {
		InetAddress localhost = InetAddress.getLocalHost();
		String hostname = localhost.getHostName();

		List<Map<String, Object>> logEvents = new ArrayList<>();
		for (LogEntry entry : logEntries) {
			Map<String, Object> logEvent = objectMapper.convertValue(entry, LowercaseKeyMap.class);
			logEvent.put("hostname", hostname);
			logEvent.put("logtype", entry.getLogType());
			logEvent.put("applicationName", entry.getApplicationName());
			logEvent.put("name", entry.getName());
			logEvent.put("timestamp", entry.getTimestamp()); // Use log creation timestamp
			logEvent.put("source", "NRBatchingAppender"); // Add custom field
			logEvents.add(logEvent);
		}

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
				requeueLogs(logEntries); // Requeue logs if the response is not successful
			} else {
				LocalDateTime timestamp = LocalDateTime.now();
				System.out.println("Logs sent to New Relic successfully: " + "at " + timestamp + " size: "
						+ compressedPayload.length + " Bytes");
				System.out.println("Response: " + response.body().string());
			}
		} catch (IOException e) {
			System.err.println("Error during log forwarding: " + e.getMessage());
			requeueLogs(logEntries); // Requeue logs if an exception occurs
		}
	}

	private void requeueLogs(List<LogEntry> logEntries) {
		for (LogEntry entry : logEntries) {
			try {
				logQueue.put(entry); // Requeue the log entry
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Failed to requeue log entry: " + entry.getMessage());
			}
		}
	}

	private byte[] gzipCompress(String input) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
			gzipOS.write(input.getBytes());
		}
		return bos.toByteArray();
	}

	public void close() {
		List<LogEntry> remainingLogs = new ArrayList<>();
		logQueue.drainTo(remainingLogs);
		if (!remainingLogs.isEmpty()) {
			System.out.println("Flushing remaining " + remainingLogs.size() + " log events to New Relic...");
			flush(remainingLogs);
		}
	}
}