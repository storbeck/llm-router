package dev.pagefoundry.llm_router;

import org.springframework.stereotype.Service;

@Service
public class ProviderCredentialService {

    private final ProviderConfigStore providerConfigStore;

    public ProviderCredentialService(ProviderConfigStore providerConfigStore) {
        this.providerConfigStore = providerConfigStore;
    }

    public ProviderConfig getConfigForProvider(String provider) {
        ProviderConfig config = providerConfigStore.findByProvider(provider)
            .orElseThrow(() -> new IllegalArgumentException(
                "No config found for provider: " + provider
            ));

        if (requiresApiKey(config.provider())
            && (config.apiKey() == null || config.apiKey().isBlank())) {
            throw new IllegalStateException(
                "Missing API key for provider: " + config.provider()
            );
        }

        return config;
    }

    private boolean requiresApiKey(String provider) {
        return "openai".equalsIgnoreCase(provider)
            || "anthropic".equalsIgnoreCase(provider);
    }
}
