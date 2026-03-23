package dev.pagefoundry.llm_router.conversation;

public record ConversationMessageMetadataDto(
    String provider,
    String model,
    ConversationMessageTokenUsageDto tokenUsage
) {}
