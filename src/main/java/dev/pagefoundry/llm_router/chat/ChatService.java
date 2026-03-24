package dev.pagefoundry.llm_router.chat;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int MAX_CONTEXT_CHARACTERS = 60_000;
    private static final Pattern EXPLANATION_PATTERN =
        Pattern.compile("\"explanation\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern MERMAID_QUERY_PATTERN =
        Pattern.compile("\"mermaidQuery\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

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
                    truncateContext(request.context())
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
            - Output exactly one Mermaid diagram type
            - Prefer flowcharts for general process or how-to requests
            - For flowcharts, use valid flowchart syntax only
            - For flowcharts, use arrows like -->, --- or -.-> and never sequence-diagram arrows like ->> or -->> 
            - For flowcharts, define nodes with stable identifiers like A[Start] and put each statement on its own line
            - Do a final syntax check before returning mermaidQuery
            - Prefer top down instead of left to right orientation for flowcharts, unless the request explicitly suggests otherwise

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
            parseAssistantResponse(outputConverter, response.getResult().getOutput().getText());
        assistantResponse = new MermaidAssistantResponse(
            assistantResponse.explanation(),
            normalizeMermaidQuery(assistantResponse.mermaidQuery())
        );
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

        if (response.mermaidQuery().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Model did not return a Mermaid query."
            );
        }

        if (response.mermaidQuery().contains("```")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mermaid query must not contain markdown fences");
        }
    }

    private MermaidAssistantResponse parseAssistantResponse(
        BeanOutputConverter<MermaidAssistantResponse> outputConverter,
        String rawOutput
    ) {
        try {
            return outputConverter.convert(rawOutput);
        } catch (RuntimeException exception) {
            MermaidAssistantResponse extractedResponse = extractStructuredFields(rawOutput);
            if (extractedResponse != null) {
                return extractedResponse;
            }

            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Model returned an unstructured response without a Mermaid query.",
                exception
            );
        }
    }

    private MermaidAssistantResponse extractStructuredFields(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return null;
        }

        String explanation = extractField(rawOutput, EXPLANATION_PATTERN);
        String mermaidQuery = extractField(rawOutput, MERMAID_QUERY_PATTERN);
        if (explanation == null || mermaidQuery == null) {
            return null;
        }

        return new MermaidAssistantResponse(explanation, mermaidQuery);
    }

    private String extractField(String rawOutput, Pattern pattern) {
        Matcher matcher = pattern.matcher(rawOutput);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1)
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim();
    }

    private String truncateContext(String context) {
        String trimmed = context.trim();
        if (trimmed.length() <= MAX_CONTEXT_CHARACTERS) {
            return trimmed;
        }

        return """
            %s

            [...context truncated for size...]

            %s
            """.formatted(
            trimmed.substring(0, 40_000).trim(),
            trimmed.substring(trimmed.length() - 20_000).trim()
        );
    }

    private String normalizeMermaidQuery(String mermaidQuery) {
        if (mermaidQuery == null) {
            return null;
        }

        String normalized = mermaidQuery.trim();
        normalized = normalized.replace("```mermaid", "").replace("```", "").trim();

        if (normalized.startsWith("graph ") || normalized.startsWith("flowchart ")) {
            normalized = normalized
                .replace("->>", "-->")
                .replace("-->>", "-->")
                .replaceAll(";\\s*", ";\n");
        }

        return normalized;
    }
}
