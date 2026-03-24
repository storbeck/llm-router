package dev.pagefoundry.llm_router.chat;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record MermaidAssistantResponse(
    @JsonPropertyDescription("A plain English explanation of the Mermaid diagram and any assumptions made.")
    String explanation,

    @JsonPropertyDescription("A valid Mermaid diagram definition only. No markdown fences or commentary.")
    String mermaidQuery
) {
}
