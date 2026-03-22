package dev.pagefoundry.llm_router;

public record ChatRequest(
    String message,
    String provider,
    String model,
    String conversationId
) {}
