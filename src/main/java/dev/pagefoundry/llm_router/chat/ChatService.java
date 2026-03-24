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
    private static final String QUERY_LANGUAGE = "mermaid";

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

        BeanOutputConverter<MermaidAssistantResponse> outputConverter =
            new BeanOutputConverter<>(MermaidAssistantResponse.class);

        String contextSection = """
            No reference context was provided. Build the diagram directly from the user's request.
            """;
        if (request.context() != null && !request.context().isBlank()) {
            contextSection = """
                %s
                """.formatted(
                    request.context()
                );
        }

        String systemPrompt = """
            You are a Mermaid diagram assistant.

            Your primary job is to produce a beautiful, valid Mermaid diagram that satisfies the user's request.
            Use the optional reference context below as supporting material. Treat it as additional content that can
            refine names, attributes, and relationships when relevant, but do not let it block diagram creation.
            %s

            Requirements:
            - explanation: plain English explanation for a human
            - mermaidQuery: valid Mermaid diagram source only
            - No markdown fences
            - Do not include commentary inside mermaidQuery
            - If assumptions are needed, put them in explanation
            - Prefer concise diagrams that Mermaid can render directly
            - Prioritize the user's requested subject matter and diagram quality over strict grounding
            - If reference context is relevant, incorporate compatible details from it
            - If reference context conflicts with the request or is unrelated, follow the user's request and mention the mismatch briefly in explanation
            - When context is missing or incomplete, make reasonable assumptions and state them in explanation
            - mermaidQuery should still be non-empty whenever a reasonable diagram can be produced from the request alone

            %s
            """.formatted(contextSection, outputConverter.getFormat());

        ChatResponse response;
        try {
            response = chatClient.prompt()
                .system(systemPrompt)
                .user(request.message())
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

        MermaidAssistantResponse assistantResponse =
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
                assistantResponse.mermaidQuery(),
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
            assistantResponse.mermaidQuery(),
            QUERY_LANGUAGE,
            request.provider(),
            request.model(),
            promptTokens,
            completionTokens
        );
    }

    private void validateResponse(MermaidAssistantResponse response) {
        if (response.explanation() == null || response.explanation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing explanation in model response");
        }

        if (response.mermaidQuery() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Mermaid query field in model response");
        }

        if (!response.mermaidQuery().isBlank() && response.mermaidQuery().contains("```")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mermaid query must not contain markdown fences");
        }
    }
}
