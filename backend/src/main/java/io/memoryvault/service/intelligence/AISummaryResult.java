package io.memoryvault.service.intelligence;

import java.util.List;

/**
 * @param summary a three-sentence summary of the saved content
 * @param tags    up to ten short tags describing the content
 */
public record AISummaryResult(String summary, List<String> tags) {
}
