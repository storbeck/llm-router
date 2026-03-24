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
            <div
              class="message-bubble__text"
              v-html="renderMarkdown(message.text)"
            />

            <div
              v-if="message.role === 'assistant' && hasRenderableQuery(message.query)"
              class="message-bubble__query"
            >
              <div class="message-bubble__query-label">Mermaid</div>
              <pre class="message-bubble__query-block"><code v-html="renderMermaid(message.query ?? '')" /></pre>
              <div class="message-bubble__query-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  @click="$emit('apply-query', message.query ?? '', false)"
                >
                  Apply
                </v-btn>
                <v-btn
                  size="small"
                  variant="flat"
                  @click="$emit('apply-query', message.query ?? '', true)"
                >
                  Apply &amp; Run
                </v-btn>
              </div>
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
            </div>
            <div class="message-bubble__text">Waiting for model response...</div>
          </div>
        </div>
      </div>
    </v-container>
  </div>
</template>

<script lang="ts" setup>
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import plaintext from 'highlight.js/lib/languages/plaintext'
import 'highlight.js/styles/github.css'
import { marked } from 'marked'
import { nextTick, onMounted, ref, watch } from 'vue'
import type { UiMessage } from './chat-types'

const props = defineProps<{
  error: string
  loading: boolean
  messages: UiMessage[]
  sending: boolean
}>()

defineEmits<{
  'apply-query': [query: string, runAfterApply: boolean]
}>()

const messageViewport = ref<HTMLElement | null>(null)

hljs.registerLanguage('plaintext', plaintext)

function renderMarkdown(text: string) {
  return DOMPurify.sanitize(
    marked.parse(text, {
      breaks: true,
      gfm: true,
    }) as string,
  )
}

function renderMermaid(query: string) {
  return DOMPurify.sanitize(hljs.highlight(query, { language: 'plaintext' }).value)
}

function hasRenderableQuery(query?: string | null) {
  return !!query?.trim()
}

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
  line-height: 1.6;
}

.message-bubble__query {
  margin-top: 0.9rem;
}

.message-bubble__query-label {
  margin-bottom: 0.35rem;
  color: rgba(var(--v-theme-on-surface), var(--v-medium-emphasis-opacity));
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.message-bubble__query-block {
  margin: 0;
  overflow-x: auto;
  padding: 0.875rem 1rem;
  border-radius: 12px;
  background: rgba(var(--v-theme-on-surface), 0.06);
}

.message-bubble__query-block code {
  display: block;
  font-family: 'Roboto Mono', monospace;
  font-size: 0.9rem;
  line-height: 1.6;
}

.message-bubble__query-actions {
  display: flex;
  gap: 8px;
  margin-top: 0.75rem;
}

.message-bubble__text :deep(p) {
  margin: 0;
}

.message-bubble__text :deep(p + p),
.message-bubble__text :deep(p + ul),
.message-bubble__text :deep(p + ol),
.message-bubble__text :deep(p + pre),
.message-bubble__text :deep(ul + p),
.message-bubble__text :deep(ol + p),
.message-bubble__text :deep(pre + p) {
  margin-top: 0.75rem;
}

.message-bubble__text :deep(ul),
.message-bubble__text :deep(ol) {
  margin: 0.75rem 0 0;
  padding-left: 1.25rem;
}

.message-bubble__text :deep(li + li) {
  margin-top: 0.25rem;
}

.message-bubble__text :deep(pre) {
  margin: 0.75rem 0 0;
  overflow-x: auto;
  padding: 0.875rem 1rem;
  border-radius: 12px;
  background: rgba(var(--v-theme-on-surface), 0.06);
}

.message-bubble__text :deep(code) {
  font-family: 'Roboto Mono', monospace;
  font-size: 0.92em;
}

.message-bubble__text :deep(:not(pre) > code) {
  padding: 0.15rem 0.35rem;
  border-radius: 6px;
  background: rgba(var(--v-theme-on-surface), 0.06);
}

.message-bubble__text :deep(a) {
  color: rgb(var(--v-theme-primary));
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
