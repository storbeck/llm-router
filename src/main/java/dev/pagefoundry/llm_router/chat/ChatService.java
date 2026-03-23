package dev.pagefoundry.llm_router.chat;

import java.time.LocalDateTime;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.pagefoundry.llm_router.conversation.ConversationMessageMetadataDto;
import dev.pagefoundry.llm_router.conversation.ConversationMessageTokenUsageDto;
import dev.pagefoundry.llm_router.conversation.ConversationService;
import dev.pagefoundry.llm_router.conversation.MessageEntryResponseDto;
import dev.pagefoundry.llm_router.provider.ProviderConfig;
import dev.pagefoundry.llm_router.provider.ProviderCredentialService;

@Service
public class ChatService {
    private static final String QUERY_LANGUAGE = "sql";

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
        } else if (conversationService.findById(conversationId).isPresent()) {
            conversationService.touchConversation(conversationId);
        } else {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Conversation not found: " + conversationId
            );
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

        BeanOutputConverter<SqlAssistantResponse> outputConverter =
            new BeanOutputConverter<>(SqlAssistantResponse.class);

        String prompt = """
            You are a SQL assistant.

            User request:
            %s

            Requirements:
            - explanation: plain English explanation for a human
            - sqlQuery: valid SQL query only
            - No markdown fences
            - Do not include commentary inside sqlQuery
            - If assumptions are needed, put them in explanation

            %s
            """.formatted(request.message(), outputConverter.getFormat());

        ChatResponse response;
        try {
            response = chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                .call()
                .chatResponse();
        } catch (NonTransientAiException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                exception
            );
        }

        SqlAssistantResponse assistantResponse =
            outputConverter.convert(response.getResult().getOutput().getText());
        validateResponse(assistantResponse);

        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage != null ? usage.getPromptTokens() : null;
        Integer completionTokens = usage != null ? usage.getCompletionTokens() : null;
        LocalDateTime respondedAt = LocalDateTime.now();

        conversationService.saveMessageEntry(
            finalConversationId,
            QUERY_LANGUAGE,
            request.message(),
            promptedAt,
            new MessageEntryResponseDto(
                assistantResponse.sqlQuery(),
                assistantResponse.explanation(),
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
            assistantResponse.explanation(),
            assistantResponse.sqlQuery(),
            request.provider(),
            request.model(),
            promptTokens,
            completionTokens
        );
    }

    private void validateResponse(SqlAssistantResponse response) {
        if (response.explanation() == null || response.explanation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing explanation in model response");
        }

        if (response.sqlQuery() == null || response.sqlQuery().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing SQL query in model response");
        }

        if (response.sqlQuery().contains("```")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL query must not contain markdown fences");
        }
    }
}
