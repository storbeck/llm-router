package dev.pagefoundry.llm_router;

import java.time.LocalDateTime;

public record Conversation(
    String id,
    String title,
    String provider,
    String model,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}