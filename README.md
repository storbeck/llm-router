# LLM Router

Simple LLM Router using Spring AI

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
