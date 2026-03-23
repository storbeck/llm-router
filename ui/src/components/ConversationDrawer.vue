<template>
  <div>

    <v-alert
      v-if="error"
      class="mb-4"
      density="comfortable"
      type="error"
      variant="tonal"
    >
      {{ error }}
    </v-alert>

    <v-progress-linear
      v-if="loading"
      indeterminate
    />

    <v-list
      v-else
      class="conversation-history"
      nav
      density="comfortable"
    >

      <v-list-item
        v-for="conversation in conversations"
        :key="conversation.id"
        :active="conversation.id === selectedConversationId"
        @click="$emit('navigate', conversation.id)"
      >
        <template #prepend>
          <v-icon icon="mdi-chat-outline" />
        </template>

        <v-list-item-title>{{ conversation.title }}</v-list-item-title>
        <v-list-item-subtitle>
          {{ formatTimestamp(conversation.updatedAt) }}
        </v-list-item-subtitle>
      </v-list-item>
    </v-list>
  </div>
</template>

<script lang="ts" setup>
import type { ConversationSummary } from './chat-types'

defineProps<{
  conversations: ConversationSummary[]
  error: string
  loading: boolean
  selectedConversationId: string
}>()

defineEmits<{
  navigate: [conversationId?: string]
}>()

function formatTimestamp(value?: string | null) {
  if (!value) return 'No activity'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.conversation-history {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.conversation-history__header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.conversation-history__list {
  border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  border-radius: 24px;
  padding: 8px;
}
</style>
