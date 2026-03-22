package dev.pagefoundry.llm_router;

public record ConversationMessageDto(
    String type,
    String message
) {}
