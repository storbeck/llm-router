# LLM Router

Simple LLM Router using Spring AI

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
```
./mvnw spring-boot:run
```

## Example curl
```
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "Say hello in one sentence.",
    "provider": "openai",
    "model": "gpt-4o-mini"
  }'
```
