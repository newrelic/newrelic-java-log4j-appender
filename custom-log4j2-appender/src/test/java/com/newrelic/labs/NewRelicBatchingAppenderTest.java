package com.newrelic.labs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Covers neutralizeJsonKeys and resolvePreserveJsonKeys, which back the preservePayloadJson and
 * preserveJsonKeys appender attributes. The two "payload"-shaped inputs below correspond to two
 * real, distinct Mule flow conventions observed in practice: one embeds "payload" as a live
 * nested JSON object, the other embeds it as an already-serialized JSON string (with its own
 * escaped whitespace/newlines).
 */
class NewRelicBatchingAppenderTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String MARKER = "##";
	private static final Set<String> PAYLOAD_KEY = Collections.singleton("payload");

	@Test
	void payloadAsLiveObject_isMarkedButStaysCompactAndValid() throws Exception {
		String message = "{\"correlationId\":\"abc-123\",\"sourceApp\":\"ODX App\","
				+ "\"payload\":{\"messageId\":\"f56f7330\",\"statusCode\":\"Success\","
				+ "\"rooms\":[{\"roomId\":\"180\",\"roomNumber\":\"0908\"}]}}";

		String result = NewRelicBatchingAppender.neutralizeJsonKeys(message, PAYLOAD_KEY);

		JsonNode root = MAPPER.readTree(result); // whole message must still be valid JSON
		assertEquals("abc-123", root.get("correlationId").asText());
		assertEquals("ODX App", root.get("sourceApp").asText());

		String payloadValue = root.get("payload").asText();
		assertTrue(payloadValue.startsWith(MARKER), "payload value should be marker-prefixed: " + payloadValue);

		String stripped = payloadValue.substring(MARKER.length());
		assertTrue(isValidJson(stripped), "stripped payload should parse back to the original JSON");
		JsonNode recoveredPayload = MAPPER.readTree(stripped);
		assertEquals("f56f7330", recoveredPayload.get("messageId").asText());
		assertEquals("Success", recoveredPayload.get("statusCode").asText());

		// No stray extra quoting/escaping: exactly one JSON-encode round trip, not two.
		assertFalse(stripped.startsWith("\""), "payload text should not be double-encoded: " + payloadValue);
	}

	@Test
	void payloadAsPreStringifiedJson_isMarkedWithoutDoubleEncoding() throws Exception {
		// Mirrors the real customer case: DataWeave wrote payload as a pretty-printed JSON
		// *string* (embedded literal newlines), not a live nested object.
		String prettyPayloadJson = "{\n  \"messageId\": \"f56f7330\",\n  \"statusCode\": \"Success\"\n}";
		String messageWithStringPayload = "{\"correlationId\":\"abc-123\",\"payload\":"
				+ MAPPER.writeValueAsString(prettyPayloadJson) + "}";

		String result = NewRelicBatchingAppender.neutralizeJsonKeys(messageWithStringPayload, PAYLOAD_KEY);

		JsonNode root = MAPPER.readTree(result);
		String payloadValue = root.get("payload").asText();
		assertTrue(payloadValue.startsWith(MARKER), "payload value should be marker-prefixed: " + payloadValue);

		String stripped = payloadValue.substring(MARKER.length());

		// This is the regression this test guards against: a double-encoded value looks like
		// ##"{\n  \"messageId\": ...  (an extra leading quote, escaped newlines preserved as
		// literal backslash-n) instead of the clean, once-encoded original text.
		assertFalse(stripped.startsWith("\""),
				"payload was double-encoded - got: " + payloadValue);

		JsonNode recoveredPayload = MAPPER.readTree(stripped);
		assertEquals("f56f7330", recoveredPayload.get("messageId").asText());
		assertEquals("Success", recoveredPayload.get("statusCode").asText());
	}

	@Test
	void caseInsensitiveKeyMatch_matchesDifferentCasing() throws Exception {
		// Configured key is lowercase "payload", but the actual JSON field is "Payload".
		String message = "{\"correlationId\":\"abc-123\",\"Payload\":{\"messageId\":\"f56f7330\"}}";

		String result = NewRelicBatchingAppender.neutralizeJsonKeys(message, PAYLOAD_KEY);

		JsonNode root = MAPPER.readTree(result);
		String payloadValue = root.get("Payload").asText();
		assertTrue(payloadValue.startsWith(MARKER), "differently-cased key should still be matched: " + payloadValue);
	}

	@Test
	void multipleConfiguredKeys_areProtected_othersStillFlatten() throws Exception {
		String message = "{\"correlationId\":\"abc-123\",\"sourceApp\":\"ODX App\","
				+ "\"payload\":{\"a\":1},\"requestBody\":{\"b\":2}}";
		Set<String> keys = new java.util.HashSet<>(java.util.Arrays.asList("payload", "requestbody"));

		String result = NewRelicBatchingAppender.neutralizeJsonKeys(message, keys);

		JsonNode root = MAPPER.readTree(result);
		assertTrue(root.get("payload").asText().startsWith(MARKER));
		assertTrue(root.get("requestBody").asText().startsWith(MARKER));
		// sourceApp was never a target key - stays a plain, untouched string value.
		assertEquals("ODX App", root.get("sourceApp").asText());
	}

	@Test
	void emptyKeysSet_returnsMessageUnchanged() {
		String message = "{\"payload\":{\"a\":1}}";
		assertEquals(message, NewRelicBatchingAppender.neutralizeJsonKeys(message, Collections.emptySet()));
	}

	@Test
	void messageWithoutMatchingKey_isReturnedUnchanged() {
		String message = "{\"correlationId\":\"abc-123\",\"sourceApp\":\"ODX App\"}";
		assertEquals(message, NewRelicBatchingAppender.neutralizeJsonKeys(message, PAYLOAD_KEY));
	}

	@Test
	void nonJsonMessage_isReturnedUnchanged() {
		String message = "plain text log line, not JSON";
		assertEquals(message, NewRelicBatchingAppender.neutralizeJsonKeys(message, PAYLOAD_KEY));
	}

	@Test
	void nullMessage_isReturnedUnchanged() {
		assertEquals(null, NewRelicBatchingAppender.neutralizeJsonKeys(null, PAYLOAD_KEY));
	}

	@Test
	void resolvePreserveJsonKeys_payloadFlagOnly_impliesPayload() {
		Set<String> keys = NewRelicBatchingAppender.resolvePreserveJsonKeys(true, null);
		assertEquals(Collections.singleton("payload"), keys);
	}

	@Test
	void resolvePreserveJsonKeys_csvOnly_returnsLowercasedTrimmedKeys() {
		Set<String> keys = NewRelicBatchingAppender.resolvePreserveJsonKeys(false, " RequestBody , ResponseData ");
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("requestbody", "responsedata")), keys);
	}

	@Test
	void resolvePreserveJsonKeys_bothSet_unionsRatherThanOverrides() {
		Set<String> keys = NewRelicBatchingAppender.resolvePreserveJsonKeys(true, "requestBody");
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("payload", "requestbody")), keys);
	}

	@Test
	void resolvePreserveJsonKeys_neitherSet_returnsEmptySet() {
		Set<String> keys = NewRelicBatchingAppender.resolvePreserveJsonKeys(null, null);
		assertTrue(keys.isEmpty());
	}

	@Test
	void resolvePreserveJsonKeys_csvWithBlankEntries_ignoresThem() {
		Set<String> keys = NewRelicBatchingAppender.resolvePreserveJsonKeys(false, "payload,,  ,requestBody");
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("payload", "requestbody")), keys);
	}

	private static boolean isValidJson(String text) {
		try {
			MAPPER.readTree(text);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
