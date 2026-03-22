package dev.pagefoundry.llm_router;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/api/conversations")
    public List<ConversationSummaryDto> getConversations() {
        return conversationService.listConversations();
    }

    @GetMapping("/api/conversations/{conversationId}/messages")
    public List<String> getMessages(@PathVariable String conversationId) {
        return conversationService.getMessages(conversationId);
    }
}
