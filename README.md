# LLM Router

Simple LLM Router using Spring AI

## API overview

Base URL: `http://localhost:8080`

Current endpoints:

- `POST /api/chat`
- `GET /api/conversations`
- `GET /api/conversations/{conversationId}/messages`

## Environment variables

This app reads provider credentials from process environment variables via `System.getenv(...)`. It does not load a `.env` file automatically, so either export the values in your shell before starting the app or source a `.env` file yourself.

Example:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Provider-specific values:

### OpenAI

```bash
OPENAI_API_KEY=your_openai_api_key
# Optional. Set this if you want to use an OpenAI-compatible endpoint instead of api.openai.com.
OPENAI_BASE_URL=https://api.openai.com
```

### Anthropic

```bash
ANTHROPIC_API_KEY=your_anthropic_api_key
# Optional.
ANTHROPIC_BASE_URL=https://api.anthropic.com
```

### Ollama

```bash
# Optional. Not required by the current credential check.
OLLAMA_API_KEY=
# Optional. Defaults to http://localhost:11434
OLLAMA_BASE_URL=http://localhost:11434
```

Current status:

- `openai` is the only provider implemented in `ChatModelFactory`.
- `anthropic` and `ollama` have environment variable slots configured, but requests to those providers are not implemented yet.

## Start

```bash
./mvnw spring-boot:run
```

## API

### `POST /api/chat`

Send a message to the configured provider. If you omit `conversationId`, the app creates a new conversation automatically. If you include `conversationId`, the message is added to that existing conversation's memory.

Request body:

```json
{
  "message": "Say hello in one sentence.",
  "provider": "openai",
  "model": "gpt-4o-mini",
  "conversationId": "optional-existing-conversation-id"
}
```

Fields:

- `message`: required user prompt
- `provider`: required provider name, currently `openai`
- `model`: required model name for the provider
- `conversationId`: optional existing conversation ID to continue a thread

Example:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "Say hello in one sentence.",
    "provider": "openai",
    "model": "gpt-4o-mini",
    "conversationId": "optional-existing-conversation-id"
  }'
```

Example response:

```json
{
  "message": "Hello from the assistant.",
  "provider": "openai",
  "promptTokens": 12,
  "completionTokens": 8
}
```

Notes:

- When `conversationId` is missing, a new conversation record is created and titled from the first message.
- The chat response does not currently include the `conversationId`.
- To discover a newly created conversation ID, call `GET /api/conversations`.

### `GET /api/conversations`

List saved conversations ordered by most recently updated first.

Example:

```bash
curl http://localhost:8080/api/conversations
```

Example response:

```json
[
  {
    "id": "b4f1ca08-0f57-4b1f-a67d-6cc7b42b7f8b",
    "title": "Say hello in one sentence.",
    "provider": "openai",
    "model": "gpt-4o-mini",
    "updatedAt": "2026-03-22T14:35:12.123"
  }
]
```

Use this endpoint to:

- find the `conversationId` for a newly created conversation
- show a conversation list in a client UI
- choose a conversation to continue by sending its `id` back to `POST /api/chat`

### `GET /api/conversations/{conversationId}/messages`

Return the stored chat memory entries for a conversation in chronological order.

Example:

```bash
curl http://localhost:8080/api/conversations/b4f1ca08-0f57-4b1f-a67d-6cc7b42b7f8b/messages
```

Example response:

```json
[
  {
    "type": "USER",
    "message": "Say hello in one sentence."
  },
  {
    "type": "ASSISTANT",
    "message": "Hello from the assistant."
  }
]
```

This endpoint returns structured messages from Spring AI chat memory in chronological order.

## Conversation workflow

### Start a new conversation

1. Send `POST /api/chat` without `conversationId`.
2. Call `GET /api/conversations`.
3. Use the newest conversation's `id` as the thread ID for later requests.

### Continue an existing conversation

1. Find the conversation ID from `GET /api/conversations`.
2. Send `POST /api/chat` with that `conversationId`.

### Read a conversation's messages

1. Find the conversation ID from `GET /api/conversations`.
2. Call `GET /api/conversations/{conversationId}/messages`.
