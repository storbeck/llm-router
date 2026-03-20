package dev.pagefoundry.llm_router;

public record ChatResponseDto(
    String message,
    String provider,
    Integer promptTokens,
    Integer completionTokens
) {
}
