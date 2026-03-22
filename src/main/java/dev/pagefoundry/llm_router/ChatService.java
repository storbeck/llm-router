package dev.pagefoundry.llm_router;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatModelFactory chatModelFactory;
    private final ProviderCredentialService providerCredentialService;
    private final ConversationService conversationService;
    private final ChatMemory chatMemory;

    public ChatService(
        ChatModelFactory chatModelFactory,
        ProviderCredentialService providerCredentialService,
        ConversationService conversationService,
        ChatMemory chatMemory
    ) {
        this.chatModelFactory = chatModelFactory;
        this.providerCredentialService = providerCredentialService;
        this.conversationService = conversationService;
        this.chatMemory = chatMemory;
    }

    public ChatResponseDto chat(ChatRequest request) {
        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation(
                request.message(),
                request.provider(),
                request.model()
            );
        } else {
            conversationService.touchConversation(conversationId);
        }
        final String finalConversationId = conversationId;

        ProviderConfig config =
            providerCredentialService.getConfigForProvider(request.provider());

        ChatModel chatModel = chatModelFactory.create(
            request.provider(),
            request.model(),
            config.apiKey(),
            config.baseUrl()
        );

        ChatClient chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
            .build();

        ChatResponse response = chatClient.prompt()
            .user(request.message())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
            .call()
            .chatResponse();

        String message = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage != null ? usage.getPromptTokens() : null;
        Integer completionTokens = usage != null ? usage.getCompletionTokens() : null;

        return new ChatResponseDto(message, request.provider(), promptTokens, completionTokens);
    }
}
