package dev.pagefoundry.llm_router.chat;

import java.time.LocalDateTime;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import dev.pagefoundry.llm_router.conversation.ConversationMessageMetadataDto;
import dev.pagefoundry.llm_router.conversation.ConversationMessageTokenUsageDto;
import dev.pagefoundry.llm_router.conversation.ConversationService;
import dev.pagefoundry.llm_router.conversation.MessageEntryResponseDto;
import dev.pagefoundry.llm_router.provider.ProviderConfig;
import dev.pagefoundry.llm_router.provider.ProviderCredentialService;

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
            conversationId = conversationService.createConversation(request.message());
        } else {
            conversationService.touchConversation(conversationId);
        }
        final String finalConversationId = conversationId;
        LocalDateTime promptedAt = LocalDateTime.now();

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
        LocalDateTime respondedAt = LocalDateTime.now();

        conversationService.saveMessageEntry(
            finalConversationId,
            null,
            request.message(),
            promptedAt,
            new MessageEntryResponseDto(
                null,
                message,
                new ConversationMessageMetadataDto(
                    request.provider(),
                    request.model(),
                    new ConversationMessageTokenUsageDto(
                        promptTokens,
                        completionTokens
                    )
                )
            ),
            respondedAt
        );
        conversationService.touchConversation(finalConversationId);

        return new ChatResponseDto(
            message,
            request.provider(),
            request.model(),
            promptTokens,
            completionTokens
        );
    }
}
