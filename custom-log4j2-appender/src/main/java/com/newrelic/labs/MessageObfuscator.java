package com.newrelic.labs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for obfuscating parts of a string based on regular expressions.
 */
public class MessageObfuscator {

	/**
	 * Obfuscates substrings in a given message that match any of the provided regex
	 * patterns. Matched substrings are replaced with 'X' characters at random
	 * positions, ensuring not all characters are replaced (unless the match length
	 * is 1).
	 *
	 * @param message                The input message string to be obfuscated.
	 * @param commaSeparatedPatterns A string of comma-separated regular expression
	 *                               patterns. Each pattern will be used to find and
	 *                               obfuscate matching parts of the message.
	 * @return The message string with all specified patterns obfuscated.
	 */

	public static String obfuscateMessageRandom(String message, String commaSeparatedPatterns) {
		if (message == null || message.isEmpty() || commaSeparatedPatterns == null
				|| commaSeparatedPatterns.isEmpty()) {
			return message;
		}

		// Split and trim patterns in one step
		String[] regexPatternsArray = commaSeparatedPatterns.split("\\^\\^");
		for (int i = 0; i < regexPatternsArray.length; i++) {
			regexPatternsArray[i] = regexPatternsArray[i].trim();
		}

		StringBuilder obfuscatedMessage = new StringBuilder(message);
		Random random = new Random();

		for (String regex : regexPatternsArray) {
			try {
				Pattern pattern = Pattern.compile(regex);
				Matcher matcher = pattern.matcher(obfuscatedMessage);

				List<int[]> matches = new ArrayList<>();
				while (matcher.find()) {
					matches.add(new int[] { matcher.start(), matcher.end() });
				}

				for (int i = matches.size() - 1; i >= 0; i--) {
					int start = matches.get(i)[0];
					int end = matches.get(i)[1];
					int length = end - start;

					if (length == 0)
						continue;

					char[] chars = obfuscatedMessage.substring(start, end).toCharArray();
					int numToObfuscate = (length == 1) ? 1 : 1 + random.nextInt(length - 1);

					// Generate unique random indices efficiently
					int[] indices = random.ints(0, length).distinct().limit(numToObfuscate).toArray();
					for (int index : indices) {
						chars[index] = 'X';
					}

					obfuscatedMessage.replace(start, end, new String(chars));
				}
			} catch (java.util.regex.PatternSyntaxException e) {
				// System.err.println("Warning: Invalid regex pattern '" + regex + "'. Skipping
				// this pattern. Error: "
				// + e.getMessage());
			}
		}
		// System.out.println("Obfuscated message: " + obfuscatedMessage.toString());
		// System.out.println("commaSeparatedPatterns: " + commaSeparatedPatterns);
		return obfuscatedMessage.toString();
	}

	public static String obfuscateMessage(String message, String commaSeparatedPatterns) {
		if (message == null || message.isEmpty() || commaSeparatedPatterns == null
				|| commaSeparatedPatterns.isEmpty()) {
			return message;
		}

		// Split and trim patterns in one step
		String[] regexPatternsArray = commaSeparatedPatterns.split("\\^\\^");
		for (int i = 0; i < regexPatternsArray.length; i++) {
			regexPatternsArray[i] = regexPatternsArray[i].trim();
		}

		StringBuilder obfuscatedMessage = new StringBuilder(message);

		for (String regex : regexPatternsArray) {
			try {
				Pattern pattern = Pattern.compile(regex);
				Matcher matcher = pattern.matcher(obfuscatedMessage);

				while (matcher.find()) {
					int start = matcher.start();
					int end = matcher.end();
					int length = end - start;

					// Replace with the same length of 'X' characters
					StringBuilder replacement = new StringBuilder();
					for (int i = 0; i < length; i++) {
						replacement.append('X');
					}
					obfuscatedMessage.replace(start, end, replacement.toString());
				}
			} catch (java.util.regex.PatternSyntaxException e) {
				// System.err.println("Warning: Invalid regex pattern '" + regex + "'. Skipping
				// this pattern. Error: "
				// + e.getMessage());
			}
		}

		return obfuscatedMessage.toString();
	}

	/**
	 * Main client program to test the obfuscateMessage method.
	 */
	public static void main(String[] args) {
		System.out.println("--- Message Obfuscation Test Cases ---");

		// Test Case 1: Obfuscate credit card numbers
		String message1 = "INFO  MyAPI [[MuleRuntime].uber.01: [samplemuleapp].samplemuleappFlow.BLOCKING @24508028]: {\"glossary\":{\"title\":\"My credit card number is 1234-5678-9012-3456 and expires on 12/25\",\"GlossDiv\":{\"title\":\"S\",\"GlossList\":{\"GlossEntry\":{\"ID\":\"SGML\",\"SortAs\":\"SGML\",\"GlossTerm\":\"Standard Generalized Markup Language\",\"Acronym\":\"SGML\",\"Abbrev\":\"ISO 8879:1986\",\"GlossDef\":{\"para\":\"A meta-markup language, used to create markup languages such as DocBook.\",\"GlossSeeAlso\":[\"GML\",\"XML\"]},\"GlossSee\":\"markup\"}}}}}.";
		String patterns1 = "\\d{4}-\\d{4}-\\d{4}-\\d{4}^^\\d{2}/\\d{2}"; // Changed separator to ^^
		String obfuscated1 = obfuscateMessage(message1, patterns1);
		System.out.println("\n---Confugured patterns---" + patterns1);
		System.out.println("\nOriginal Message 1: " + message1);
		System.out.println("Obfuscated Message 1: " + obfuscated1);
		// System.out.println(
		// "Expected: My credit card number is partially obfuscated (e.g.,
		// 1X34-56X8-901X-34X6) and expires on partially obfuscated (e.g., 1X/2X).");

		// Test Case 2: Obfuscate email addresses and phone numbers
		String message2 = "Contact me at john.doe@example.com or call 123-456-7890.";
		String patterns2 = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b^^\\d{3}-\\d{3}-\\d{4}"; // Changed
																											// separator
																											// to ^^
		String obfuscated2 = obfuscateMessage(message2, patterns2);
		System.out.println("\n--- Confugured patterns ---" + patterns2);
		System.out.println("\nOriginal Message 2: " + message2);
		System.out.println("Obfuscated Message 2: " + obfuscated2);
		// System.out.println(
		// "Expected: Contact me at partially obfuscated email (e.g.,
		// joXn.doe@eXample.com) or call partially obfuscated phone (e.g.,
		// 12X-456-78X0).");

		// Test Case 3: Multiple occurrences of the same pattern
		String message3 = "The secret code is CODE123 and another is CODE789. Also, CODE000.";
		String patterns3 = "CODE\\d{3}"; // No change needed here as it's a single pattern
		String obfuscated3 = obfuscateMessage(message3, patterns3);
		System.out.println("\n--- Confugured patterns ---" + patterns3);
		System.out.println("\nOriginal Message 3: " + message3);
		System.out.println("Obfuscated Message 3: " + obfuscated3);
		// System.out.println(
		// "Expected: The secret code is partially obfuscated (e.g., COXX123) and
		// another is partially obfuscated (e.g., CODEX89). Also, partially obfuscated
		// (e.g., CODX000).");

		// Test Case 4: No match found
		String message4 = "This message has no sensitive data.";
		String patterns4 = "\\d{5}";
		String obfuscated4 = obfuscateMessage(message4, patterns4);
		System.out.println("\n--- Confugured patterns ---" + patterns4);
		System.out.println("\nOriginal Message 4: " + message4);
		System.out.println("Obfuscated Message 4: " + obfuscated4);
		// System.out.println("Expected: This message has no sensitive data.");

		// Test Case 5: Empty message
		String message5 = "";
		String patterns5 = ".*";
		String obfuscated5 = obfuscateMessage(message5, patterns5);
		System.out.println("\n--- Confugured patterns ---" + patterns5);
		System.out.println("\nOriginal Message 5: \"" + message5 + "\"");
		System.out.println("Obfuscated Message 5: \"" + obfuscated5 + "\"");
		// System.out.println("Expected: \"\"");

		// Test Case 6: Null message
		String message6 = null;
		String patterns6 = ".*";
		System.out.println("\n---Confugured patterns ---" + patterns6);
		String obfuscated6 = obfuscateMessage(message6, patterns6);
		System.out.println("\nOriginal Message 6: " + message6);
		System.out.println("Obfuscated Message 6: " + obfuscated6);
		// System.out.println("Expected: null");

		// Test Case 7: Null regex patterns
		String message7 = "Some text with a secret.";
		String patterns7 = null;
		String obfuscated7 = obfuscateMessage(message7, patterns7);
		System.out.println("\n--- Confugured patterns ---" + patterns7);
		System.out.println("\nOriginal Message 7: " + message7);
		System.out.println("Obfuscated Message 7: " + obfuscated7);
		// System.out.println("Expected: Some text with a secret.");

		// Test Case 8: Empty regex patterns
		String message8 = "Some text with another secret.";
		String patterns8 = "";
		String obfuscated8 = obfuscateMessage(message8, patterns8);
		System.out.println("\n--- Confugured patterns ---" + patterns8);
		System.out.println("\nOriginal Message 8: " + message8);
		System.out.println("Obfuscated Message 8: " + obfuscated8);
		// System.out.println("Expected: Some text with another secret.");

		// Test Case 9: Overlapping patterns
		String message9 = "The value is ABCDE12345.";
		String patterns9 = "ABCDE\\d{5}^^\\d{5}"; // Changed separator to ^^
		String obfuscated9 = obfuscateMessage(message9, patterns9);
		System.out.println("\n--- Confugured patterns ---" + patterns9);
		System.out.println("\nOriginal Message 9: " + message9);
		System.out.println("Obfuscated Message 9: " + obfuscated9);
		// System.out.println("Expected: The value is partially obfuscated (e.g.,
		// ABCDX12X45).");

		// Test Case 10: Invalid regex pattern
		String message10 = "This message has an [invalid regex pattern].";
		// This test case will now correctly split, but the first pattern will still be
		// invalid.
		String patterns10 = "[^^pattern"; // Changed separator to ^^
		System.out.println("\n--- Confugured patterns ---" + patterns10);
		String obfuscated10 = obfuscateMessage(message10, patterns10);
		System.out.println("\nOriginal Message 10: " + message10);
		System.out.println("Obfuscated Message 10: " + obfuscated10);
		// System.out.println("Expected: (Warning about invalid regex) This message has
		// an [invalid pXXXXrn].");

		// Test Case 11: Single character match
		String message11 = "A B C D E";
		String patterns11 = "B^^D"; // Changed separator to ^^
		String obfuscated11 = obfuscateMessage(message11, patterns11);
		System.out.println("\n--- Confugured patterns ---" + patterns11);
		System.out.println("\nOriginal Message 11: " + message11);
		System.out.println("Obfuscated Message 11: " + obfuscated11);
		// System.out.println("Expected: A X C X E");
	}
}
