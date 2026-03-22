package dev.pagefoundry.llm_router;

import java.time.LocalDateTime;

public record ConversationSummaryDto(
    String id,
    String title,
    String provider,
    String model,
    LocalDateTime updatedAt
) {}