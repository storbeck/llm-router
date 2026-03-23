package dev.pagefoundry.llm_router.chat;

public record ChatResponseDto(
    String explanation,
    String query,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens
) {
}
