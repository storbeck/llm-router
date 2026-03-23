package dev.pagefoundry.llm_router.conversation;

public record MessageEntryResponseDto(
    String query,
    String explanation,
    ConversationMessageMetadataDto metadata
) {}
