<template>
  <v-sheet
    border="t"
    class="chat-composer"
    :class="{ 'chat-composer--drag-over': isDragOver }"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="handleDragOver"
    @dragleave.prevent="handleDragLeave"
    @drop.prevent="handleDrop"
  >
    <v-container class="py-4">
      <v-form @submit.prevent="$emit('send')">
        <div
          v-if="isDragOver"
          class="composer-dropzone"
        >
          Drop a context file here
        </div>

        <div
          v-if="contextFileName"
          class="composer-context"
        >
          <div class="composer-context__meta">
            <v-chip
              color="primary"
              size="small"
              variant="tonal"
            >
              Context: {{ contextFileName }}
            </v-chip>

            <span
              v-if="contextNotice"
              class="composer-context__notice"
            >
              {{ contextNotice }}
            </span>
          </div>

          <v-btn
            size="x-small"
            variant="text"
            @click="$emit('clear:context')"
          >
            Clear
          </v-btn>
        </div>

        <v-textarea
          :model-value="draft"
          auto-grow
          class="composer-input"
          hide-details
          max-rows="8"
          placeholder="Message LLM Router..."
          rows="2"
          variant="outlined"
          @update:model-value="$emit('update:draft', $event ?? '')"
          @keydown.enter.exact.prevent="$emit('send')"
        />
        <div class="composer-controls">
          <v-select
            :items="providerOptions"
            :model-value="provider"
            density="compact"
            hide-details
            label="Provider"
            variant="plain"
            @update:model-value="$emit('update:provider', $event ?? '')"
          />

          <v-select
            :items="modelOptions"
            :model-value="model"
            density="compact"
            hide-details
            label="Model"
            variant="plain"
            @update:model-value="$emit('update:model', $event ?? '')"
          />
        </div>
      </v-form>
    </v-container>
  </v-sheet>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

defineProps<{
  draft: string
  contextFileName: string
  contextNotice: string
  model: string
  modelOptions: string[]
  provider: string
  providerOptions: string[]
  selectedConversationId: string
  sending: boolean
}>()

const emit = defineEmits<{
  send: []
  'clear:context': []
  'drop:context': [file: File]
  'update:draft': [value: string]
  'update:model': [value: string]
  'update:provider': [value: string]
}>()

const isDragOver = ref(false)
let dragDepth = 0

function handleDragEnter() {
  dragDepth += 1
  isDragOver.value = true
}

function handleDragOver(event: DragEvent) {
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

function handleDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) {
    isDragOver.value = false
  }
}

function handleDrop(event: DragEvent) {
  dragDepth = 0
  isDragOver.value = false

  const file = event.dataTransfer?.files?.[0]
  if (!file) {
    return
  }

  emit('drop:context', file)
}
</script>

<style scoped>
.chat-composer {
  flex: 0 0 auto;
  position: relative;
  transition: background-color 160ms ease, box-shadow 160ms ease;
}

.chat-composer--drag-over {
  background: rgba(var(--v-theme-primary), 0.05);
  box-shadow: inset 0 0 0 1px rgba(var(--v-theme-primary), 0.28);
}

.chat-composer :deep(.v-container) {
  max-width: 960px;
}

.chat-composer :deep(.v-form) {
  width: 100%;
}

.composer-dropzone {
  margin-bottom: 12px;
  padding: 16px;
  border: 1px dashed rgba(var(--v-theme-primary), 0.45);
  border-radius: 12px;
  color: rgba(var(--v-theme-primary), 0.92);
  font-size: 0.9rem;
  font-weight: 600;
  text-align: center;
  background: rgba(var(--v-theme-primary), 0.06);
}

.composer-controls {
  display: grid;
  gap: 12px;
  grid-template-columns: 180px minmax(220px, 320px);
  margin-top: 20px;
}

.composer-context {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.composer-context__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.composer-context__notice {
  color: rgba(var(--v-theme-on-surface), 0.7);
  font-size: 0.8rem;
  line-height: 1.3;
}

.composer-input {
  margin-top: 4px;
}

.composer-status {
  display: inline-block;
}

@media (max-width: 720px) {
  .composer-controls {
    grid-template-columns: 1fr;
  }
}
</style>
