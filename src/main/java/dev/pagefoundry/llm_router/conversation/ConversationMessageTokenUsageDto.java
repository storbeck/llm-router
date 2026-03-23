package dev.pagefoundry.llm_router.conversation;

public record ConversationMessageTokenUsageDto(
    Integer input,
    Integer output
) {}
