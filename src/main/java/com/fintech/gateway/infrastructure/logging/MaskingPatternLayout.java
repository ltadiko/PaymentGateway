package com.fintech.gateway.infrastructure.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Logback layout that masks sensitive PII/PCI data in log output.
 *
 * <p>Prevents accidental exposure of sensitive information in logs, even if
 * a developer logs raw account numbers, card numbers, or JWT tokens. This
 * is a defence-in-depth measure complementing the application-level masking
 * in {@link com.fintech.gateway.infrastructure.crypto.DataMaskingUtil}.
 *
 * <p><strong>Patterns masked:</strong>
 * <ul>
 *   <li><strong>IBAN account numbers:</strong> {@code NL91ABNA0417164300} → {@code ****4300}</li>
 *   <li><strong>Card numbers (PAN):</strong> {@code 4111-1111-1111-1111} → {@code ****1111}</li>
 *   <li><strong>JSON account fields:</strong> {@code "creditorAccount":"NL91..."} → masked value</li>
 *   <li><strong>JWT tokens:</strong> {@code Bearer eyJhbG...} → {@code Bearer ****}</li>
 * </ul>
 *
 * <p>Configured via {@code logback-spring.xml} as the layout for all appenders.
 *
 * @see ch.qos.logback.classic.PatternLayout
 */
public class MaskingPatternLayout extends PatternLayout {

    private final List<ReplacementRule> rules = new ArrayList<>();

    /**
     * Constructs the masking layout and registers all masking rules.
     */
    public MaskingPatternLayout() {
        super();

        // Order matters: most specific patterns first to prevent
        // broader patterns from corrupting more specific matches.

        // 1. JWT tokens: Bearer eyJ... (must be before IBAN which could match parts)
        rules.add(new ReplacementRule(
                Pattern.compile("Bearer\\s+eyJ[A-Za-z0-9_.\\-]{20,}"),
                match -> "Bearer ****"));

        // 2. JSON fields containing account data (before standalone IBAN matching)
        rules.add(new ReplacementRule(
                Pattern.compile("\"(creditorAccount|debtorAccount|accountNumber)\"\\s*:\\s*\"([^\"]+)\""),
                MaskingPatternLayout::maskJsonAccountField));

        // 3. IBAN: 2 uppercase letters + 2 digits + 4-30 alphanumeric chars
        rules.add(new ReplacementRule(
                Pattern.compile("\\b([A-Z]{2}\\d{2}[A-Z0-9]{4,30})\\b"),
                MaskingPatternLayout::maskIban));

        // 4. Card numbers: 13-19 digits, optionally separated by spaces or dashes
        rules.add(new ReplacementRule(
                Pattern.compile("\\b(\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{1,7})\\b"),
                MaskingPatternLayout::maskCardNumber));
    }

    /**
     * Formats a logging event and masks any sensitive data in the output.
     *
     * @param event the logging event
     * @return the formatted and masked log line
     */
    @Override
    public String doLayout(ILoggingEvent event) {
        String original = super.doLayout(event);
        return mask(original);
    }

    /**
     * Applies all masking rules to the given text.
     *
     * <p>This method is package-private to allow unit testing without
     * requiring a full Logback event.
     *
     * @param message the raw log message
     * @return the masked message
     */
    String mask(String message) {
        String result = message;
        for (ReplacementRule rule : rules) {
            result = rule.apply(result);
        }
        return result;
    }

    /**
     * Masks an IBAN by keeping only the last 4 characters.
     * Example: {@code NL91ABNA0417164300} → {@code ****4300}
     */
    private static String maskIban(Matcher matcher) {
        String iban = matcher.group(1);
        if (iban.length() <= 4) {
            return "****";
        }
        return "****" + iban.substring(iban.length() - 4);
    }

    /**
     * Masks a card number by keeping only the last 4 digits.
     * Example: {@code 4111-1111-1111-1111} → {@code ****1111}
     */
    private static String maskCardNumber(Matcher matcher) {
        String card = matcher.group(1).replaceAll("[\\s-]", "");
        if (card.length() <= 4) {
            return "****";
        }
        return "****" + card.substring(card.length() - 4);
    }

    /**
     * Masks a JSON account field value.
     * Example: {@code "creditorAccount":"NL91ABNA0417164300"} → {@code "creditorAccount":"****4300"}
     */
    private static String maskJsonAccountField(Matcher matcher) {
        String fieldName = matcher.group(1);
        String value = matcher.group(2);
        String masked = value.length() > 4
                ? "****" + value.substring(value.length() - 4)
                : "****";
        return "\"" + fieldName + "\":\"" + masked + "\"";
    }

    /**
     * A masking rule consisting of a regex pattern and a replacement function.
     */
    private record ReplacementRule(Pattern pattern, java.util.function.Function<Matcher, String> replacer) {

        /**
         * Applies this rule to the given text, replacing all matches.
         *
         * @param text the input text
         * @return the text with all matches replaced
         */
        String apply(String text) {
            Matcher matcher = pattern.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(matcher)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
    }
}

