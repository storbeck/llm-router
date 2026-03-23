package dev.pagefoundry.llm_router.conversation;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ConversationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String createConversation(String firstMessage) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String title = generateTitle(firstMessage);

        jdbcTemplate.update("""
            INSERT INTO conversation (id, title, created_at, updated_at)
            VALUES (?, ?, ?, ?)
        """, id, title, Timestamp.valueOf(now), Timestamp.valueOf(now));

        return id;
    }

    public void saveMessageEntry(
        String conversationId,
        String queryLanguage,
        String prompt,
        LocalDateTime promptedAt,
        MessageEntryResponseDto response,
        LocalDateTime respondedAt
    ) {
        String responseJson = writeResponse(response);

        jdbcTemplate.update("""
            INSERT INTO message_entry (
                id,
                conversation_id,
                query_language,
                prompt,
                prompted_at,
                response,
                responded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
            UUID.randomUUID().toString(),
            conversationId,
            queryLanguage,
            prompt,
            Timestamp.valueOf(promptedAt),
            responseJson,
            Timestamp.valueOf(respondedAt)
        );
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
            SELECT id, title, created_at, updated_at
            FROM conversation
            WHERE id = ?
        """, (rs, rowNum) -> new Conversation(
            rs.getString("id"),
            rs.getString("title"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ), conversationId);

        return results.stream().findFirst();
    }

    public List<ConversationSummaryDto> listConversations() {
        List<ConversationSummaryRow> rows = jdbcTemplate.query("""
            SELECT
                c.id,
                c.title,
                last_message.response,
                c.updated_at
            FROM conversation c
            LEFT JOIN message_entry last_message
                ON last_message.id = (
                    SELECT me.id
                    FROM message_entry me
                    WHERE me.conversation_id = c.id
                    ORDER BY me.prompted_at DESC, me.id DESC
                    LIMIT 1
                )
            ORDER BY c.updated_at DESC
        """, (rs, rowNum) -> new ConversationSummaryRow(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("response"),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ));

        return rows.stream()
            .map(row -> {
                ConversationMessageMetadataDto metadata = extractMetadata(row.responseJson());
                return new ConversationSummaryDto(
                    row.id(),
                    row.title(),
                    metadata != null ? metadata.provider() : null,
                    metadata != null ? metadata.model() : null,
                    row.updatedAt()
                );
            })
            .toList();
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "New Conversation";
        }

        String trimmed = firstMessage.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "...";
    }

    public List<ConversationMessageDto> getMessages(String conversationId) {
        List<MessageEntryRow> rows = jdbcTemplate.query("""
            SELECT prompt, response
            FROM message_entry
            WHERE conversation_id = ?
            ORDER BY prompted_at ASC, id ASC
        """, (rs, rowNum) -> new MessageEntryRow(
            rs.getString("prompt"),
            rs.getString("response")
        ), conversationId);

        List<ConversationMessageDto> messages = new ArrayList<>();
        for (MessageEntryRow row : rows) {
            MessageEntryResponseDto response = readResponse(row.responseJson());
            ConversationMessageMetadataDto metadata = response != null
                ? response.metadata()
                : null;

            messages.add(new ConversationMessageDto(
                "USER",
                row.prompt(),
                metadata
            ));

            if (response != null && response.explanation() != null) {
                messages.add(new ConversationMessageDto(
                    "ASSISTANT",
                    response.explanation(),
                    metadata
                ));
            }
        }

        return messages;
    }

    private String writeResponse(MessageEntryResponseDto response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize message response", exception);
        }
    }

    private MessageEntryResponseDto readResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(responseJson, MessageEntryResponseDto.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize message response", exception);
        }
    }

    private ConversationMessageMetadataDto extractMetadata(String responseJson) {
        MessageEntryResponseDto response = readResponse(responseJson);
        return response != null ? response.metadata() : null;
    }

    private record ConversationSummaryRow(
        String id,
        String title,
        String responseJson,
        LocalDateTime updatedAt
    ) {}

    private record MessageEntryRow(
        String prompt,
        String responseJson
    ) {}
}
