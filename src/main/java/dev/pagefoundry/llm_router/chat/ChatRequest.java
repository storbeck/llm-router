package dev.pagefoundry.llm_router.chat;

public record ChatRequest(
    String message,
    String provider,
    String model,
    String conversationId,
    String ontology
) {}
