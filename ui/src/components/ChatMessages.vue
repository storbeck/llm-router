<template>
  <div
    ref="messageViewport"
    class="chat-messages"
  >
    <v-container class="chat-messages__container py-4">
      <v-alert
        v-if="error"
        class="mb-4"
        density="comfortable"
        type="error"
        variant="tonal"
      >
        {{ error }}
      </v-alert>

      <div
        v-if="loading"
        class="d-flex align-center ga-3"
      >
        <v-progress-circular
          indeterminate
          size="24"
        />
        <span>Loading messages...</span>
      </div>

      <div
        v-else-if="messages.length === 0"
        class="text-body-1 text-medium-emphasis pt-8"
      >
        Start a new conversation or select one from the left.
      </div>

      <div
        v-else
        class="message-log"
      >
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role === 'assistant' ? 'message-row--assistant' : 'message-row--user'"
        >
          <div class="message-bubble">
            <div class="message-bubble__header">
              <div class="message-bubble__author">
                {{ message.role === 'assistant' ? 'Assistant' : 'You' }}
              </div>
              <div
                v-if="message.metadata"
                class="message-bubble__meta"
              >
                {{ message.metadata.provider }} / {{ message.metadata.model }}
                <template v-if="message.role === 'assistant' && message.metadata.tokenUsage">
                  · {{ message.metadata.tokenUsage.input ?? 0 }} in / {{ message.metadata.tokenUsage.output ?? 0 }} out
                </template>
              </div>
            </div>
            <div class="message-bubble__text">
              {{ message.text }}
            </div>
          </div>
        </div>

        <div
          v-if="sending"
          class="message-row message-row--assistant"
        >
          <div class="message-bubble">
            <div class="message-bubble__header">
              <div class="message-bubble__author">Assistant</div>
              <div class="message-bubble__meta">
                {{ defaultProvider }} / {{ defaultModel }}
              </div>
            </div>
            <div class="message-bubble__text">Waiting for model response...</div>
          </div>
        </div>
      </div>
    </v-container>
  </div>
</template>

<script lang="ts" setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import type { UiMessage } from './chat-types'

const props = defineProps<{
  defaultModel: string
  defaultProvider: string
  error: string
  loading: boolean
  messages: UiMessage[]
  sending: boolean
}>()

const messageViewport = ref<HTMLElement | null>(null)

async function scrollToBottom() {
  await nextTick()

  if (!messageViewport.value) {
    return
  }

  messageViewport.value.scrollTo({
    top: messageViewport.value.scrollHeight,
    behavior: 'smooth',
  })
}

watch(
  () => [props.messages.length, props.sending, props.loading],
  async () => {
    await scrollToBottom()
  },
  { flush: 'post' },
)

onMounted(async () => {
  await scrollToBottom()
})
</script>

<style scoped>
.chat-messages {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.chat-messages__container {
  max-width: 960px;
  width: 100%;
}

.message-log {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-row {
  display: flex;
  width: 100%;
}

.message-row--user {
  justify-content: flex-end;
}

.message-row--assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: min(780px, 100%);
  min-width: 240px;
  padding: 12px 14px;
  border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  border-radius: 16px;
  background: rgb(var(--v-theme-surface));
}

.message-row--user .message-bubble {
  background: rgba(var(--v-theme-primary), 0.10);
  border-color: rgba(var(--v-theme-primary), 0.28);
}

.message-row--assistant .message-bubble {
  background: rgb(var(--v-theme-surface));
}

.message-bubble__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 8px;
}

.message-bubble__author {
  font-size: 0.95rem;
  font-weight: 600;
}

.message-bubble__meta {
  color: rgba(var(--v-theme-on-surface), var(--v-medium-emphasis-opacity));
  font-size: 0.75rem;
  text-align: right;
}

.message-bubble__text {
  white-space: pre-wrap;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .message-bubble {
    min-width: 0;
    width: 100%;
  }

  .message-bubble__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
