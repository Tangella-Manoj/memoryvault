package io.memoryvault.service.intelligence;

import java.util.List;

/**
 * Parsed result of a successful {@link ClaudeIntelligenceClient#summarizeAndTag} call.
 *
 * @param summary a 3-sentence summary of the saved page
 * @param tags    short descriptive tags Claude extracted for the page; may be empty
 */
public record ClaudeSummaryResult(String summary, List<String> tags) {
}
