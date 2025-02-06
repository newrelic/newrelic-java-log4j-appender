package com.newrelic.labs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.OkHttpPoster;
import com.newrelic.telemetry.SenderConfiguration;
import com.newrelic.telemetry.TelemetryClient;
import com.newrelic.telemetry.logs.Log;
import com.newrelic.telemetry.logs.LogBatch;
import com.newrelic.telemetry.logs.LogBatchSender;

public class LogForwarder {
    private final NRBufferWithFifoEviction<LogEntry> logQueue;
    private final String apiKey;
    private final String apiURL;
    // private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final long maxMessageSize;
    // 1.0.5
    private final int maxRetries;
    // private final int connPoolSize;
    private final long timeout; // New parameter for connection timeout
    // 1.0.5
    // 1.0.9
    private final TelemetryClient telemetryClient;

    public LogForwarder(String apiKey, String apiURL, long maxMessageSize, NRBufferWithFifoEviction<LogEntry> queue,
            int maxRetries, long timeout, int connPoolSize) {
        this.apiKey = apiKey;
        this.apiURL = apiURL;
        this.maxMessageSize = maxMessageSize;
        this.logQueue = queue;
        this.maxRetries = maxRetries;
        this.timeout = timeout;

        SenderConfiguration config = null;
        try {
            config = LogBatchSender.configurationBuilder()
                    .apiKey(this.apiKey)
                    .useLicenseKey(true)
                    .endpoint(new URL(this.apiURL))
                    .httpPoster(new OkHttpPoster(Duration.of(timeout, ChronoUnit.MILLIS)))
                    .build();
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        LogBatchSender sender = LogBatchSender.create(config);
        this.telemetryClient = new TelemetryClient(null, null, null, sender);

        this.objectMapper = new ObjectMapper();

    }

    public void close(boolean mergeCustomFields, Map<String, Object> customFields) { // 1.0.6
        List<LogEntry> remainingLogs = new ArrayList<>();

        // Assuming queue is an instance of BufferWithFifoEviction<LogEntry>
        int drained = logQueue.drainTo(remainingLogs, Integer.MAX_VALUE); // Drain all remaining logs

        if (!remainingLogs.isEmpty()) {
            System.out.println("Flushing remaining " + remainingLogs.size() + " log events to New Relic...");

            {
                flushAsync(remainingLogs, mergeCustomFields, customFields, new FlushCallback() {
                    @Override
                    public void onFailure(List<Map<String, Object>> failedLogEvents) {
                        System.err.println("Failed to flush remaining log events to New Relic.");
                    }

                    @Override
                    public void onSuccess() {
                        System.out.println("No remaining log events to flush.");
                    }
                });
            }
        }
        shutdown();

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

    private List<Map<String, Object>> convertToLogEvents(List<LogEntry> logEntries, boolean mergeCustomFields,
            Map<String, Object> customFields) {
        List<Map<String, Object>> logEvents = new ArrayList<>();
        for (LogEntry entry : logEntries) {
            logEvents.add(convertToLogEvent(entry, mergeCustomFields, customFields));
        }
        return logEvents;
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
            sendLogsAsync(logEntries, callback, mergeCustomFields, customFields);
        }
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

    private byte[] gzipCompress(String input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(input.getBytes());
        }
        return bos.toByteArray();
    }

    public boolean isInitialized() {
        return apiKey != null && apiURL != null;
    }

    public void onFaolit(List<LogEntry> logEntries, boolean mergeCustomFields, Map<String, Object> customFields,
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
            sendLogsAsync(logEntries, callback, mergeCustomFields, customFields);
        }
    }

    private void requeueLogs(List<Map<String, Object>> logEvents) {

        for (Map<String, Object> logEvent : logEvents) {
            try {

                // Convert logEvent to LogEntry
                LogEntry logEntry = objectMapper.convertValue(logEvent, LogEntry.class);

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

    private void sendLogsAsync(List<LogEntry> logEntries, FlushCallback callback, boolean mergeCustomFields,
            Map<String, Object> customFields) {
        try {
            // Convert LogEntry objects to Log objects
            List<Log> logs = logEntries.stream()
                    .map(entry -> Log.builder()
                            .message(entry.getMessage())
                            .timestamp(entry.getTimestamp())
                            .attributes(new Attributes().put("applicationName", entry.getApplicationName())
                                    .put("logtype", entry.getLogType())
                                    .put("name", entry.getName())
                                    .put("hostname", getHostname())
                                    .put("source", "NRBatchingAppender"))
                            .build())
                    .collect(Collectors.toList());

            // Create a LogBatch with common attributes
            // Add custom fields
            Attributes commonAttributes = new Attributes();
            if (customFields != null) {
                if (mergeCustomFields) {
                    for (Map.Entry<String, Object> field : customFields.entrySet()) {
                        commonAttributes.put(field.getKey(), (String) field.getValue());
                    }
                } else {

                    for (Map.Entry<String, Object> field : customFields.entrySet()) {
                        commonAttributes.put("custom." + field.getKey(), (String) field.getValue());
                    }

                }
            }
            commonAttributes.put("source", "NRBatchingAppender");
            commonAttributes.put("version", "1.0.9");
            LogBatch logBatch = new LogBatch(logs, commonAttributes);

            // Send the batch
            telemetryClient.sendBatch(logBatch);

            callback.onSuccess();
        } catch (Exception e) {
            System.err.println("Failed to send logs asynchronously: " + e.getMessage());

            callback.onFailure(convertToLogEvents(logEntries, mergeCustomFields, customFields));
        }
    }

    public void shutdown() {
        telemetryClient.shutdown();
        System.out.println("Shutting down telemetry client ");
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
                sendLogsAsync(logEntries, callback, mergeCustomFields, customFields);
                subBatch.clear();
                currentSize = 0;
            }

            currentSize += entrySize;
        }

        if (!subBatch.isEmpty()) {
            sendLogsAsync(logEntries, callback, mergeCustomFields, customFields);
        }
    }
}
