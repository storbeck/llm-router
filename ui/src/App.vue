<template>
  <v-app>
    <v-main class="app-shell">
      <v-container class="app-shell__container">
        <v-card
          class="app-frame"
          variant="flat"
        >
          <header class="app-toolbar">
            <div class="app-toolbar__actions">
              <v-btn
                icon="mdi-plus"
                size="small"
                variant="text"
                @click="startNewConversation"
              />

              <v-menu location="bottom end">
                <template #activator="{ props }">
                  <v-btn
                    icon="mdi-history"
                    size="small"
                    variant="text"
                    v-bind="props"
                  />
                </template>

                <v-card
                  min-width="320"
                  rounded="xl"
                >
                  <v-list density="comfortable" nav>
                    <v-list-subheader>Chat history</v-list-subheader>

                    
                    <v-alert
                      v-if="sidebarError"
                      class="mx-4 mb-3"
                      density="comfortable"
                      type="error"
                      variant="tonal"
                    >
                      {{ sidebarError }}
                    </v-alert>

                    <div
                      v-else-if="isLoadingConversations"
                      class="history-menu__loading"
                    >
                      <v-progress-circular
                        indeterminate
                        size="20"
                      />
                      <span>Loading chats...</span>
                    </div>

                    <v-list-item
                      v-for="conversation in conversations"
                      v-else
                      :key="conversation.id"
                      :active="conversation.id === selectedConversationId"
                      rounded="xl"
                      :title="conversation.title"
                      @click="selectConversation(conversation.id)"
                    />

                    <v-list-item
                      v-if="!isLoadingConversations && !sidebarError && conversations.length === 0"
                      rounded="xl"
                      title="No saved chats yet"
                    />
                  </v-list>
                </v-card>
              </v-menu>

              <v-menu location="bottom end">
                <template #activator="{ props }">
                  <v-btn
                    icon="mdi-cog-outline"
                    size="small"
                    variant="text"
                    v-bind="props"
                  />
                </template>

                <v-list density="comfortable" min-width="220">
                  <v-list-item
                    prepend-icon="mdi-tune-variant"
                    title="Preferences"
                  />
                  <v-list-item
                    prepend-icon="mdi-bell-outline"
                    title="Notifications"
                  />
                  <v-list-item
                    prepend-icon="mdi-shield-crown-outline"
                    title="Workspace policy"
                  />
                </v-list>
              </v-menu>
            </div>
          </header>

          <v-divider />

          <div class="chat-pane">
            <ChatMessages
              :default-model="DEFAULT_MODEL"
              :default-provider="DEFAULT_PROVIDER"
              :error="chatError"
              :loading="isLoadingMessages"
              :messages="messages"
              :sending="isSending"
            />

            <v-divider />

            <ChatComposer
              v-model:draft="draft"
              v-model:model="selectedModel"
              v-model:provider="selectedProvider"
              :model-options="availableModels"
              :provider-options="PROVIDER_OPTIONS"
              :selected-conversation-id="selectedConversationId"
              :sending="isSending"
              @send="sendMessage"
            />
          </div>
        </v-card>
      </v-container>
    </v-main>
  </v-app>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ChatComposer from '@/components/ChatComposer.vue'
import ChatMessages from '@/components/ChatMessages.vue'
import type {
  ChatResponse,
  ConversationMessage,
  ConversationSummary,
  UiMessage,
} from '@/components/chat-types'

const DEFAULT_PROVIDER = 'openai'
const DEFAULT_MODEL = 'gpt-5.4-mini'
const PROVIDER_MODELS: Record<string, string[]> = {
  openai: ['gpt-5.4-mini', 'gpt-5.4', 'gpt-5.4-nano'],
  anthropic: ['claude-3-5-sonnet-latest'],
  ollama: ['llama3.2:latest', 'mistral:latest'],
}
const PROVIDER_OPTIONS = Object.keys(PROVIDER_MODELS)

const conversations = ref<ConversationSummary[]>([])
const messages = ref<UiMessage[]>([])
const selectedConversationId = ref('')
const draft = ref('')
const selectedModel = ref(DEFAULT_MODEL)
const selectedProvider = ref(DEFAULT_PROVIDER)
const isLoadingConversations = ref(false)
const isLoadingMessages = ref(false)
const isSending = ref(false)
const sidebarError = ref('')
const chatError = ref('')

function getConversationIdFromLocation() {
  return window.location.pathname.replace(/^\/+|\/+$/g, '')
}

const availableModels = computed(() => PROVIDER_MODELS[selectedProvider.value] ?? [])

watch(selectedProvider, (provider) => {
  const models = PROVIDER_MODELS[provider] ?? []
  if (!models.includes(selectedModel.value)) {
    selectedModel.value = models[0] ?? ''
  }
})

async function readJson<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type') || ''

  if (!response.ok) {
    throw new Error(buildHttpErrorMessage(response.status))
  }

  if (!contentType.includes('application/json')) {
    throw new Error('The UI expected JSON from the backend. Make sure the Spring Boot app is running on http://localhost:8080.')
  }

  return response.json() as Promise<T>
}

function buildHttpErrorMessage(status: number) {
  if (status === 502 || status === 503 || status === 504) {
    return `Backend unavailable (${status}). Make sure the Spring Boot app is running on http://localhost:8080.`
  }

  return `Request failed (${status})`
}

function normalizeMessage(message: ConversationMessage, index: number): UiMessage {
  return {
    id: `${message.type}-${index}-${message.message}`,
    role: message.type === 'ASSISTANT' ? 'assistant' : 'user',
    text: message.message,
    metadata: message.metadata,
  }
}

function setBrowserPath(conversationId = '') {
  const nextPath = conversationId ? `/${conversationId}` : '/'
  window.history.pushState({}, '', nextPath)
}

async function loadConversations() {
  isLoadingConversations.value = true
  sidebarError.value = ''

  try {
    const response = await fetch('/api/conversations')
    conversations.value = await readJson<ConversationSummary[]>(response)
  } catch (error) {
    sidebarError.value = error instanceof Error ? error.message : 'Unable to load conversations.'
  } finally {
    isLoadingConversations.value = false
  }
}

async function loadMessages(conversationId: string) {
  if (!conversationId) {
    messages.value = []
    return
  }

  isLoadingMessages.value = true
  chatError.value = ''

  try {
    const response = await fetch(`/api/conversations/${conversationId}/messages`)
    const data = await readJson<ConversationMessage[]>(response)
    messages.value = data.map(normalizeMessage)
    syncSelectionsFromMessages()
  } catch (error) {
    messages.value = []
    chatError.value = error instanceof Error ? error.message : 'Unable to load messages.'
  } finally {
    isLoadingMessages.value = false
  }
}

async function selectConversation(conversationId = '') {
  selectedConversationId.value = conversationId
  setBrowserPath(conversationId)

  if (!conversationId) {
    selectedProvider.value = DEFAULT_PROVIDER
    selectedModel.value = DEFAULT_MODEL
    chatError.value = ''
    messages.value = []
    return
  }

  await loadMessages(conversationId)
}

async function startNewConversation() {
  await selectConversation('')
}

async function sendMessage() {
  const text = draft.value.trim()
  if (!text || isSending.value) return

  chatError.value = ''
  isSending.value = true

  const optimisticUserMessage: UiMessage = {
    id: `user-${Date.now()}`,
    role: 'user',
    text,
    metadata: {
      provider: selectedProvider.value,
      model: selectedModel.value,
    },
  }

  messages.value = [...messages.value, optimisticUserMessage]
  draft.value = ''

  const requestedConversationId = selectedConversationId.value

  try {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: text,
        provider: selectedProvider.value,
        model: selectedModel.value,
        conversationId: requestedConversationId || null,
      }),
    })

    const data = await readJson<ChatResponse>(response)

    messages.value = [
      ...messages.value,
      {
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        text: data.message,
        metadata: {
          provider: data.provider,
          model: data.model,
          tokenUsage: {
            input: data.promptTokens,
            output: data.completionTokens,
          },
        },
      },
    ]

    await loadConversations()

    const createdConversation = conversations.value[0] ?? null
    const nextConversationId = requestedConversationId || createdConversation?.id || ''
    if (!requestedConversationId && nextConversationId) {
      selectedConversationId.value = nextConversationId
      setBrowserPath(nextConversationId)
      return
    }

    if (requestedConversationId) {
      await loadMessages(requestedConversationId)
    }
  } catch (error) {
    messages.value = messages.value.filter((message) => message.id !== optimisticUserMessage.id)
    chatError.value = error instanceof Error ? error.message : 'Unable to send message.'
  } finally {
    isSending.value = false
  }
}

function syncSelectionsFromMessages() {
  const lastWithMetadata = [...messages.value]
    .reverse()
    .find((message) => message.metadata?.provider && message.metadata?.model)

  if (!lastWithMetadata?.metadata?.provider || !lastWithMetadata.metadata.model) {
    return
  }

  selectedProvider.value = lastWithMetadata.metadata.provider
  const providerModels = PROVIDER_MODELS[selectedProvider.value] ?? []
  selectedModel.value = providerModels.includes(lastWithMetadata.metadata.model)
    ? lastWithMetadata.metadata.model
    : providerModels[0] ?? lastWithMetadata.metadata.model
}

async function syncFromLocation() {
  await selectConversation(getConversationIdFromLocation())
}

onMounted(async () => {
  selectedConversationId.value = getConversationIdFromLocation()
  await loadConversations()
  if (selectedConversationId.value) {
    await loadMessages(selectedConversationId.value)
  }

  window.addEventListener('popstate', syncFromLocation)
})

onBeforeUnmount(() => {
  window.removeEventListener('popstate', syncFromLocation)
})
</script>

<style scoped>
.app-shell {
  height: 100dvh;
  overflow: hidden;
  background: rgb(var(--v-theme-surface));
}

.app-shell__container {
  height: 100%;
  max-width: none;
  padding: 0;
}

.app-frame {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  width: 100%;
  border-radius: 0;
}

.app-toolbar {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: flex-end;
  padding: 12px 20px;
}

.app-toolbar__actions {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.chat-pane {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.history-menu__loading {
  align-items: center;
  display: flex;
  gap: 12px;
  padding: 16px 20px;
}

@media (max-width: 720px) {
  .app-frame {
    min-height: 100vh;
  }

  .app-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding-inline: 12px;
  }

  .app-toolbar__actions {
    align-self: flex-end;
  }
}
</style>
