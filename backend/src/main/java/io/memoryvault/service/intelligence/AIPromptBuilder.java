package io.memoryvault.service.intelligence;

final class AIPromptBuilder {

    private AIPromptBuilder() {
    }

    static String summaryPrompt(String title, String description, String bodyText) {
        return """
                Given this saved web page, respond with ONLY a JSON object (no markdown fences, no prose) \
                in exactly this shape: {"summary": "<a 3 sentence summary>", "tags": ["tag1", "tag2", "tag3"]} \
                (at most 10 tags).

                Title: %s
                Description: %s
                Content: %s
                """.formatted(
                nullToEmpty(title),
                nullToEmpty(description),
                truncate(nullToEmpty(bodyText), 3000)
        );
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
