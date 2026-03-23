package dev.pagefoundry.llm_router.provider;

public record ProviderConfig(
    String provider,
    String apiKey,
    String baseUrl
) {
}
