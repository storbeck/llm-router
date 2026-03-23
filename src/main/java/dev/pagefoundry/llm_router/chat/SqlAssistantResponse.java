package dev.pagefoundry.llm_router.chat;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SqlAssistantResponse(
    @JsonPropertyDescription("A plain English explanation of what the SQL query does and why it was generated.")
    String explanation,

    @JsonPropertyDescription("A valid SQL query only. No markdown fences or commentary.")
    String sqlQuery
) {
}
