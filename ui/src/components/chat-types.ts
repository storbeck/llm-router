export interface ConversationSummary {
  id: string
  title: string
  provider: string | null
  model: string | null
  updatedAt: string
}

export interface TokenUsage {
  input: number | null
  output: number | null
}

export interface MessageMetadata {
  provider: string | null
  model: string | null
  tokenUsage?: TokenUsage | null
}

export interface ConversationMessage {
  type: 'USER' | 'ASSISTANT'
  message: string
  query: string | null
  queryLanguage?: string | null
  metadata: MessageMetadata | null
}

export interface ChatResponse {
  explanation: string
  query: string
  queryLanguage?: string | null
  provider: string
  model: string
  promptTokens: number | null
  completionTokens: number | null
}

export interface UiMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  query?: string | null
  queryLanguage?: string | null
  metadata: MessageMetadata | null
}
