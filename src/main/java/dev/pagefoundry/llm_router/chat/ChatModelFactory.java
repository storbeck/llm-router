package dev.pagefoundry.llm_router.chat;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Component
public class ChatModelFactory {

    public ChatModel create(String provider, String model, String apiKey, String baseUrl) {
        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAi(model, apiKey, baseUrl);
            case "anthropic" -> createAnthropic(model, apiKey);
            case "ollama" -> createOllama(model, baseUrl);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private ChatModel createOpenAi(String model, String apiKey, String baseUrl) {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey);

        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model)
            .build();

        return OpenAiChatModel.builder()
            .openAiApi(apiBuilder.build())
            .defaultOptions(options)
            .build();
    }

    private ChatModel createAnthropic(String model, String apiKey) {
        throw new UnsupportedOperationException("Anthropic not implemented yet");
    }

    private ChatModel createOllama(String model, String baseUrl) {
        OllamaApi.Builder apiBuilder = OllamaApi.builder();

        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }

        OllamaChatOptions options = OllamaChatOptions.builder()
            .model(model)
            .build();

        return OllamaChatModel.builder()
            .ollamaApi(apiBuilder.build())
            .defaultOptions(options)
            .build();
    }
}
