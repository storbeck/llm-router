package dev.pagefoundry.llm_router.chat;

public record ChatResponseDto(
    String message,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens
) {
}
