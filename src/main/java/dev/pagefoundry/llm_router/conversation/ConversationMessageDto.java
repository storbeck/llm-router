package dev.pagefoundry.llm_router.conversation;

public record ConversationMessageDto(
    String type,
    String message,
    ConversationMessageMetadataDto metadata
) {}
