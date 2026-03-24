<template>
  <v-sheet
    border="t"
    class="chat-composer"
  >
    <v-container class="py-4">
      <v-form @submit.prevent="$emit('send')">
        <div
          v-if="contextFileName"
          class="composer-context"
        >
          <v-chip
            color="primary"
            size="small"
            variant="tonal"
          >
            Context: {{ contextFileName }}
          </v-chip>

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
defineProps<{
  draft: string
  contextFileName: string
  model: string
  modelOptions: string[]
  provider: string
  providerOptions: string[]
  selectedConversationId: string
  sending: boolean
}>()

defineEmits<{
  send: []
  'clear:context': []
  'update:draft': [value: string]
  'update:model': [value: string]
  'update:provider': [value: string]
}>()
</script>

<style scoped>
.chat-composer {
  flex: 0 0 auto;
}

.chat-composer :deep(.v-container) {
  max-width: 960px;
}

.chat-composer :deep(.v-form) {
  width: 100%;
}

.composer-controls {
  display: grid;
  gap: 12px;
  grid-template-columns: 180px minmax(220px, 320px);
  margin-top: 20px;
}

.composer-context {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
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
