package dev.pagefoundry.llm_router;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class ProviderConfigStore {

    private final Map<String, ProviderConfig> configs;

    public ProviderConfigStore() {
        this.configs = Map.of(
            "openai", new ProviderConfig(
                "openai",
                System.getenv("OPENAI_API_KEY"),
                System.getenv("OPENAI_BASE_URL")
            ),
            "anthropic", new ProviderConfig(
                "anthropic",
                System.getenv("ANTHROPIC_API_KEY"),
                System.getenv("ANTHROPIC_BASE_URL")
            ),
            "ollama", new ProviderConfig(
                "ollama",
                System.getenv("OLLAMA_API_KEY"),
                System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434")
            )
        );
    }

    public Optional<ProviderConfig> findByProvider(String provider) {
        if (provider == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(configs.get(provider.toLowerCase()));
    }
}
