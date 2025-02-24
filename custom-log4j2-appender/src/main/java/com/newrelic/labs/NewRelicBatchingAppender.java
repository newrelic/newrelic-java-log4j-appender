package com.newrelic.labs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.status.StatusLogger;

import com.newrelic.labs.NRCostBoundedConcurrentQueue.CostAssigner;

@Plugin(name = "NewRelicBatchingAppender", category = "Core", elementType = "appender", printObject = true)
public class NewRelicBatchingAppender extends AbstractAppender {

    private final NRBufferWithFifoEviction<LogEntry> queue; // 1.1.0

    private final String apiKey;
    private final String apiUrl;
    private final String applicationName;
    private final String logType;
    private final boolean mergeCustomFields;
    private final String name;
    private final LogForwarder logForwarder;
    private static final Logger logger = StatusLogger.getLogger();
    private int attempt = 0; // Track attempts across harvest cycles

    private final int batchSize;
    private final int connPoolSize;
    private final long maxMessageSize;
    private final long flushInterval;
    private final long queueCapacity;
    private final Map<String, Object> customFields;
    private final int maxRetries;

    private static final int DEFAULT_BATCH_SIZE = 2000;
    private static final int DEFAULT_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_MAX_MESSAGE_SIZE = 1048576; // 1 MB
    private static final long DEFAULT_FLUSH_INTERVAL = 120000; // 2 minutes
    private static final String LOG_TYPE = "muleLog"; // defaultType
    private static final boolean MERGE_CUSTOM_FIELDS = false; // by default there will be a separate field custom block
    // for custom fields i.e. custom.attribute1
    private static final long DEFAULT_MAX_QUEUE_SIZE_BYTES = 2097152; // 2 MB // 1.1.0
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // 1.1.0

    protected NewRelicBatchingAppender(String name, Filter filter, Layout<? extends Serializable> layout,
	    final boolean ignoreExceptions, String apiKey, String apiUrl, String applicationName, Integer batchSize,
	    Long maxMessageSize, Long flushInterval, Long queueCapacity, String logType, String customFields,
	    Boolean mergeCustomFields, int maxRetries, long timeout, Integer connPoolSize) {
	super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);

	this.queueCapacity = queueCapacity != null && queueCapacity > 0 ? queueCapacity : DEFAULT_MAX_QUEUE_SIZE_BYTES;

	// Define the cost assigner for LogEntry
	CostAssigner<LogEntry> logEntryCostAssigner = logEntry -> {
	    long cost = 0;

	    // Include the length of the message
	    if (logEntry.getMessage() != null) {
		cost += logEntry.getMessage().length();
	    }

	    // Include the length of the application name
	    if (logEntry.getApplicationName() != null) {
		cost += logEntry.getApplicationName().length();
	    }

	    // Include the length of the log type
	    if (logEntry.getLogType() != null) {
		cost += logEntry.getLogType().length();
	    }

	    // Include the length of the name
	    if (logEntry.getName() != null) {
		cost += logEntry.getName().length();
	    }

	    // Include the size of the timestamp (8 bytes for a long)
	    cost += Long.BYTES;

	    // Include the cost of the custom fields
	    if (logEntry.getProperties() != null) {
		for (Map.Entry<String, Object> entry : logEntry.getProperties().entrySet()) {
		    // Add the length of the key
		    cost += entry.getKey().length();

		    // If the value is a string, add its length
		    if (entry.getValue() instanceof String) {
			cost += ((String) entry.getValue()).length();
		    } else if (entry.getValue() != null) {
			// If the value is not a string, add a generic cost
			// This can be adjusted based on your specific use case
			cost += entry.getValue().toString().length();
		    }
		}
	    }

	    return cost;
	};

	this.queue = new NRBufferWithFifoEviction<>(this.queueCapacity, logEntryCostAssigner);// 1.1.0
	this.apiKey = apiKey;
	this.apiUrl = apiUrl;
	this.applicationName = applicationName;
	this.name = name;
	this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;

	this.batchSize = batchSize != null && batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	this.connPoolSize = connPoolSize != null && connPoolSize > 0 ? connPoolSize : DEFAULT_POOL_SIZE;
	this.maxMessageSize = maxMessageSize != null && maxMessageSize > 0 ? maxMessageSize : DEFAULT_MAX_MESSAGE_SIZE;
	this.flushInterval = flushInterval != null && flushInterval > 0 ? flushInterval : DEFAULT_FLUSH_INTERVAL;
	this.logType = ((logType != null) && (logType.length() > 0)) ? logType : LOG_TYPE;
	this.customFields = parsecustomFields(customFields);
	this.mergeCustomFields = mergeCustomFields != null ? mergeCustomFields : MERGE_CUSTOM_FIELDS;
	this.logForwarder = new LogForwarder(apiKey, apiUrl, this.maxMessageSize, this.queue, maxRetries, timeout,
		connPoolSize);
	startFlushingTask();
    }

    private Map<String, Object> parsecustomFields(String customFields) {
	Map<String, Object> custom = new HashMap<>();
	if (customFields != null && !customFields.isEmpty()) {
	    String[] pairs = customFields.split(",");
	    for (String pair : pairs) {
		String[] keyValue = pair.split("=");
		if (keyValue.length == 2) {
		    custom.put(keyValue[0], keyValue[1]);
		}
	    }
	}
	return custom;
    }

    @PluginFactory
    public static NewRelicBatchingAppender createAppender(@PluginAttribute("name") String name,
	    @PluginElement("Layout") Layout<? extends Serializable> layout,
	    @PluginElement("Filter") final Filter filter, @PluginAttribute("apiKey") String apiKey,
	    @PluginAttribute("apiUrl") String apiUrl, @PluginAttribute("applicationName") String applicationName,
	    @PluginAttribute(value = "batchSize") Integer batchSize,
	    @PluginAttribute(value = "maxMessageSize") Long maxMessageSize, @PluginAttribute("logType") String logType,
	    @PluginAttribute(value = "flushInterval") Long flushInterval,
	    @PluginAttribute(value = "queueCapacity") Long queueCapacity,
	    @PluginAttribute("customFields") String customFields,
	    @PluginAttribute(value = "mergeCustomFields") Boolean mergeCustomFields,
	    @PluginAttribute(value = "maxRetries") Integer maxRetries, @PluginAttribute(value = "timeout") Long timeout,
	    @PluginAttribute(value = "connPoolSize") Integer connPoolSize) {

	if (name == null) {
	    logger.error("No name provided for NewRelicBatchingAppender");
	    return null;
	}

	if (layout == null) {
	    layout = PatternLayout.createDefaultLayout();
	}

	if (apiKey == null || apiUrl == null || applicationName == null) {
	    logger.error("API key, API URL, and application name must be provided for NewRelicBatchingAppender");
	    return null;
	}

	int retries = maxRetries != null ? maxRetries : 3; // Default to 3 retries if not specified
	long connectionTimeout = timeout != null ? timeout : 30000; // Default to 30 seconds if not specified

	return new NewRelicBatchingAppender(name, filter, layout, true, apiKey, apiUrl, applicationName, batchSize,
		maxMessageSize, flushInterval, queueCapacity, logType, customFields, mergeCustomFields, retries,
		connectionTimeout, connPoolSize);
    }

    public void appendOld(LogEvent event) {
	if (!checkEntryConditions()) {
	    logger.warn("Appender not initialized. Dropping log entry");
	    return;
	}

	String message = new String(getLayout().toByteArray(event));
	String loggerName = event.getLoggerName();
	long timestamp = event.getTimeMillis(); // Capture the log creation timestamp

	// Extract MuleAppName from the message
	String muleAppName = extractMuleAppName(message);

	logger.debug("Queueing message for New Relic: " + message);

	try {
	    // Extract custom fields from the event context
	    Map<String, Object> custom = new HashMap<>(extractcustom(event));
	    // Add static custom fields from configuration without a prefix
	    // for (Entry<String, Object> entry : this.customFields.entrySet()) {
	    // custom.putIfAbsent(entry.getKey(), entry.getValue());
	    // }
	    // Directly add to the queue
	    queue.add(
		    new LogEntry(message, applicationName, muleAppName, logType, timestamp, custom, mergeCustomFields));
	    // Check if the batch size is reached and flush immediately
	    if (queue.size() >= batchSize) {
		if (attempt == 0) {
		    boolean bStatus = flushQueue();
		    if (!bStatus) {
			attempt++;
			logger.warn("Attempt {} failed. Retrying in next harvest cycle...", attempt);
			logger.warn("batchsize check is now disabled due to unhealthy connection");
		    } else {
			logger.debug("Batchsize-check: Successfully sent logs.");
		    }
		} else {
		    logger.debug(
			    "Skipping {}/{} sending log entries to New Relic ( batchsize check )  - harvest cycle did not report healthy connection",
			    batchSize, queue.size());
		}
	    }
	} catch (Exception e) {
	    logger.error("Unable to insert log entry into log queue. ", e);
	}
    }

    @Override
    public void append(LogEvent event) {
	if (!checkEntryConditions()) {
	    logger.warn("Appender not initialized. Dropping log entry");
	    return;
	}

	String message = new String(getLayout().toByteArray(event));
	String loggerName = event.getLoggerName();
	long timestamp = event.getTimeMillis(); // Capture the log creation timestamp

	// Extract MuleAppName from the message
	String muleAppName = extractMuleAppName(message);

	logger.debug("Queueing message for New Relic: " + message);

	try {
	    // Extract custom fields from the event context
	    Map<String, Object> custom = new HashMap<>(extractcustom(event));
	    // Add static custom fields from configuration without a prefix
	    // for (Entry<String, Object> entry : this.customFields.entrySet()) {
	    // custom.putIfAbsent(entry.getKey(), entry.getValue());
	    // }
	    // Directly add to the queue
	    queue.add(
		    new LogEntry(message, applicationName, muleAppName, logType, timestamp, custom, mergeCustomFields));
	    // Check if the batch size is reached and flush asynchronously
	    if (queue.size() >= batchSize) {
		if (attempt == 0) {
		    flushQueueAsync();
		} else {
		    logger.debug(
			    "Skipping {}/{} sending log entries to New Relic ( batchsize check )  - harvest cycle did not report healthy connection",
			    batchSize, queue.size());
		}
	    }
	} catch (Exception e) {
	    logger.error("Unable to insert log entry into log queue. ", e);
	}
    }

    private boolean flushQueue() {
	List<LogEntry> batch = new ArrayList<>();
	boolean bStatus = false;
	queue.drainTo(batch, batchSize);
	if (!batch.isEmpty()) {
	    logger.debug("Flushing {}/{} log entries to New Relic", batch.size(), queue.size() + batch.size());
	    bStatus = logForwarder.flush(batch, mergeCustomFields, customFields);
	}
	return bStatus;
    }

    private void flushQueueAsync() {
	List<LogEntry> batch = new ArrayList<>();
	queue.drainTo(batch, batchSize);

	if (!batch.isEmpty()) {
	    logForwarder.flushAsync(batch, mergeCustomFields, customFields, new FlushCallback() {
		@Override
		public void onSuccess() {
		    logger.debug("Flush successful.");
		    attempt = 0; // Reset attempt counter on success
		}

		@Override
		public void onFailure(List<Map<String, Object>> failedLogEvents) {
		    logger.warn("flushQueueAsync- Flush failed. Requeuing logs...");
		    requeueLogs(failedLogEvents);
		    attempt++;
		    if (attempt >= maxRetries) {
			logger.error("flushQueueAsync- xhausted all retry attempts. Discarding logs.");
			queue.clear();
			attempt = 0; // Reset attempt counter after discarding
			logger.debug("flushQueueAsync - Discarded log entries - batch size {}/ queue size {}   ",
				batch.size(), queue.size());

		    }
		}
	    });
	}
    }

    private Map<String, Object> extractcustom(LogEvent event) {
	Map<String, Object> custom = new HashMap<>();
	event.getContextData().forEach(custom::put);
	return custom;
    }

    private String extractMuleAppName(String message) {
	Pattern pattern = Pattern.compile("\\[.*?\\]\\..*?\\[([^\\]]+)\\]");
	Matcher matcher = pattern.matcher(message);
	if (matcher.find()) {
	    return matcher.group(1);
	}
	return "generic";
    }

    private boolean checkEntryConditions() {
	boolean initialized = logForwarder != null && logForwarder.isInitialized();
	logger.debug("Check entry conditions: " + initialized);
	return initialized;
    }

    private void requeueLogs(List<Map<String, Object>> logEvents) {
	for (Map<String, Object> logEvent : logEvents) {
	    try {
		// Use LogForwarder to convert logEvent to LogEntry
		LogEntry logEntry = logForwarder.convertToLogEntry(logEvent);
		// Attempt to add the log entry back to the queue
		boolean added = queue.add(logEntry);
		if (!added) {
		    System.err.println("Failed to requeue log entry due to size constraints.");
		}
	    } catch (IllegalArgumentException e) {
		System.err.println("Failed to convert log event to LogEntry: " + logEvent);
	    }
	}
    }

    private void startFlushingTask() {
	Runnable flushTask = () -> {
	    try {
		logger.debug("Flushing task running... ");
		List<LogEntry> batch = new ArrayList<>();
		queue.drainTo(batch, batchSize);

		if (!batch.isEmpty()) {
		    logger.debug("Flushing {}/{} log entries to New Relic", batch.size(), queue.size() + batch.size());

		    logForwarder.flushAsync(batch, mergeCustomFields, customFields, new FlushCallback() {
			@Override
			public void onSuccess() {
			    logger.debug("Harvest Cycle: Successfully sent logs.");
			    attempt = 0; // Reset attempt counter on success
			}

			@Override
			public void onFailure(List<Map<String, Object>> failedLogEvents) {
			    logger.warn(" startFlushingTask- Flush failed. Requeuing logs...");
			    requeueLogs(failedLogEvents);
			    attempt++;
			    if (attempt >= maxRetries) {
				logger.error("startFlushingTask- Exhausted all retry attempts. Discarding logs.");
				queue.clear();
				attempt = 0; // Reset attempt counter after discarding
				logger.debug("Discarded log entries - batch size {}/ queue size {}   ", batch.size(),
					queue.size());

			    }
			}
		    });
		}
	    } catch (Exception e) {
		logger.error("Error during flushing task", e);
	    }
	};

	scheduler.scheduleAtFixedRate(flushTask, 0, flushInterval, TimeUnit.MILLISECONDS);

	// Log the configuration settings in use
	logger.info(
		"NewRelicBatchingAppender initialized with settings: batchSize={}, maxMessageSize={}, flushInterval={}, queueCapacity={}, maxRetries={}, mergeCustomFields={}, connPoolSize={}",
		batchSize, maxMessageSize, flushInterval, queueCapacity, maxRetries, mergeCustomFields, connPoolSize);
    }

    // Method to shut down the scheduler gracefully
    public void shutdown() {
	flushQueueAsync(); // Flush remaining logs
	scheduler.shutdown();
	try {
	    if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
		scheduler.shutdownNow();
	    }
	} catch (InterruptedException e) {
	    scheduler.shutdownNow();
	    Thread.currentThread().interrupt();
	}
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
	logger.debug("Stopping NewRelicBatchingAppender {}", getName());
	setStopping();
	final boolean stopped = super.stop(timeout, timeUnit, false);

	try {
	    // Close the log forwarder, flushing any remaining logs
	    logForwarder.close(mergeCustomFields, customFields);

	    // Shut down the ScheduledExecutorService
	    scheduler.shutdown();
	    if (!scheduler.awaitTermination(timeout, timeUnit)) {
		scheduler.shutdownNow(); // Force shutdown if not terminated within the timeout
		if (!scheduler.awaitTermination(timeout, timeUnit)) {
		    logger.error("Scheduler did not terminate");
		}
	    }
	} catch (Exception e) {
	    logger.error("Unable to close appender", e);
	}

	setStopped();
	logger.debug("NewRelicBatchingAppender {} has been stopped", getName());
	return stopped;
    }
}