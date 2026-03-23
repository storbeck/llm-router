package dev.pagefoundry.llm_router.conversation;

import java.time.LocalDateTime;

public record Conversation(
    String id,
    String title,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
