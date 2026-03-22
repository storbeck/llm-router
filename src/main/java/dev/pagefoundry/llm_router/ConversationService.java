package dev.pagefoundry.llm_router;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final JdbcTemplate jdbcTemplate;

    public ConversationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String createConversation(String firstMessage, String provider, String model) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String title = generateTitle(firstMessage);

        jdbcTemplate.update("""
            INSERT INTO conversation (id, title, provider, model, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """, id, title, provider, model, Timestamp.valueOf(now), Timestamp.valueOf(now));

        return id;
    }

    public void touchConversation(String conversationId) {
        jdbcTemplate.update("""
            UPDATE conversation
            SET updated_at = ?
            WHERE id = ?
        """, Timestamp.valueOf(LocalDateTime.now()), conversationId);
    }

    public Optional<Conversation> findById(String conversationId) {
        List<Conversation> results = jdbcTemplate.query("""
            SELECT id, title, provider, model, created_at, updated_at
            FROM conversation
            WHERE id = ?
        """, (rs, rowNum) -> new Conversation(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("provider"),
            rs.getString("model"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ), conversationId);

        return results.stream().findFirst();
    }

    public List<ConversationSummaryDto> listConversations() {
        return jdbcTemplate.query("""
            SELECT id, title, provider, model, updated_at
            FROM conversation
            ORDER BY updated_at DESC
        """, (rs, rowNum) -> new ConversationSummaryDto(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("provider"),
            rs.getString("model"),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ));
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "New Conversation";
        }

        String trimmed = firstMessage.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "...";
    }

    public List<ConversationMessageDto> getMessages(String conversationId) {
        return jdbcTemplate.query("""
            SELECT type, content
            FROM SPRING_AI_CHAT_MEMORY
            WHERE conversation_id = ?
            ORDER BY timestamp ASC
        """, (rs, rowNum) -> new ConversationMessageDto(
            rs.getString("type"),
            rs.getString("content")
        ), conversationId);
    }
}
