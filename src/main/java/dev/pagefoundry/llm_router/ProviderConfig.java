package dev.pagefoundry.llm_router;

public record ProviderConfig(
    String provider,
    String apiKey,
    String baseUrl
) {
}
