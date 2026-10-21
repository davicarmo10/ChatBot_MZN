# Chatbot — Maternidade Bem Nascer

Primeiro MVP do chatbot administrativo da Maternidade Bem Nascer.

## O que esta versão responde

- Documentos da paciente: RG, CPF e Cartão do SUS.
- Documentos do acompanhante: RG e CPF.
- Horário de visitas: 15h às 17h.
- Regras de visita:
  - não entrar com capacete;
  - não levar comida;
  - na troca de acompanhante, levar roupa para a paciente.
- Horário do setor administrativo: 8h às 18h.
- Atendimento 24h: **não cadastrado**, portanto o chatbot não inventa uma resposta.

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Spring MVC
- Bean Validation
- Spring Boot Actuator
- Maven

O chatbot roda na porta **8081**, deixando a porta **8080** livre para a Evolution API.

## Estrutura

```text
src/main/java/br/com/bemnascer/chatbot
├── config
│   ├── BemNascerProperties.java
│   └── EvolutionProperties.java
├── controller
│   ├── ApiExceptionHandler.java
│   ├── BotController.java
│   └── WhatsAppWebhookController.java
├── model
│   ├── BotRequest.java
│   ├── BotResponse.java
│   └── Intent.java
├── service
│   ├── BotService.java
│   ├── EvolutionClient.java
│   ├── EvolutionWebhookParser.java
│   ├── IntentService.java
│   └── TextNormalizer.java
└── ChatbotBemNascerApplication.java
```

## 1. Rodar com IntelliJ / Maven

Requisitos:

- JDK 21
- Maven 3.6.3+

Na raiz do projeto:

```bash
mvn spring-boot:run
```

## 2. Rodar com Docker

Como o projeto contém Dockerfile, você não precisa instalar Maven no Windows para executar pelo Docker:

```bash
docker compose up --build -d
```

Ver logs:

```bash
docker logs -f chatbot_bem_nascer
```

Parar:

```bash
docker compose down
```

## 3. Testar antes de conectar ao WhatsApp

### Status

Abra:

```text
http://localhost:8081/api/bot/status
```

Resposta esperada:

```json
{
  "status": "ok",
  "service": "chatbot-bem-nascer"
}
```

### Pergunta de teste

PowerShell:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/bot/test" `
  -ContentType "application/json" `
  -Body '{"message":"qual o horario da visita?"}'
```

Resposta esperada:

```json
{
  "intent": "HORARIO_VISITA",
  "response": "O horário de visitas da Maternidade Bem Nascer é das 15h às 17h."
}
```

Outros testes:

```json
{"message":"quais documentos preciso levar?"}
{"message":"quais documentos meu acompanhante precisa levar?"}
{"message":"posso levar comida?"}
{"message":"qual o horário do setor administrativo?"}
{"message":"funciona 24 horas?"}
```

## 4. Configurar a Evolution API

Copie `.env.example` para `.env` e preencha a chave da Evolution API:

```env
SERVER_PORT=8081
EVOLUTION_BASE_URL=http://localhost:8080
EVOLUTION_INSTANCE=MZN-LOCAL
EVOLUTION_API_KEY=SUA_CHAVE
EVOLUTION_SEND_ENABLED=false
```

**Não coloque a API key em arquivos que serão enviados ao Git.** O `.gitignore` já ignora `.env`.

Se o chatbot estiver rodando em Docker e a Evolution API estiver exposta no Windows pela porta 8080, o `docker-compose.yml` usa por padrão:

```text
http://host.docker.internal:8080
```

## 5. Webhook

O endpoint que deve receber o evento `MESSAGES_UPSERT` é:

```text
http://host.docker.internal:8081/webhook
```

O webhook ignora:

- mensagens enviadas pelo próprio número do bot (`fromMe=true`);
- mensagens de grupos (`@g.us`);
- eventos que não sejam `MESSAGES_UPSERT`;
- mensagens sem texto reconhecível.

Primeiro mantenha:

```env
EVOLUTION_SEND_ENABLED=false
```

Assim o chatbot recebe e interpreta as mensagens, mas apenas imprime a resposta nos logs.

Quando os logs estiverem corretos, altere para:

```env
EVOLUTION_SEND_ENABLED=true
```

E reinicie o container.

## 6. Endpoint de envio usado

O cliente está preparado para chamar:

```text
POST {EVOLUTION_BASE_URL}/message/sendText/{EVOLUTION_INSTANCE}
```

com o header `apikey` e corpo:

```json
{
  "number": "5596...",
  "text": "Resposta do chatbot"
}
```

## 7. Onde alterar as informações da maternidade

Edite:

```text
src/main/resources/application.yml
```

A seção `bem-nascer` é a base de conhecimento inicial.

## Próximas evoluções

1. Confirmar o atendimento 24h e cadastrar a resposta oficial.
2. Testar o webhook real da Evolution API.
3. Ampliar as intenções administrativas.
4. Persistir conversas, se houver necessidade e base legal/operacional definida.
5. Só depois adicionar um modelo de IA para interpretação de linguagem natural, mantendo as respostas institucionais ancoradas em dados oficiais.

---

## Ambiente integrado: Chatbot + Evolution + PostgreSQL + Redis

O `docker-compose.yml` deste pacote sobe os quatro serviços na mesma rede Docker:

```text
WhatsApp
   ↓
Evolution API :8080
   ↓ MESSAGES_UPSERT
Spring Boot :8081

Evolution API ── PostgreSQL
             └─ Redis
```

O webhook global da Evolution já aponta internamente para:

```text
http://chatbot-bem-nascer:8081/webhook
```

E o Spring Boot acessa a Evolution por:

```text
http://evolution-api:8080
```

### Subir tudo

Na raiz do projeto:

```bash
sudo docker compose up -d --build
```

Confira:

```bash
sudo docker compose ps
```

Logs da Evolution:

```bash
sudo docker logs -f evolution_api
```

Logs do chatbot:

```bash
sudo docker logs -f chatbot_bem_nascer
```

### API key local

Este pacote já contém um `.env` de desenvolvimento com uma chave aleatória. Para visualizar:

```bash
grep EVOLUTION_API_KEY .env
```

O `.env` está ignorado pelo Git. Antes de qualquer uso fora do ambiente local, troque a API key e a senha do PostgreSQL.

### Criar a instância do WhatsApp

Depois que `evolution_api` estiver rodando:

```bash
source .env
curl -X POST "http://localhost:8080/instance/create" \
  -H "Content-Type: application/json" \
  -H "apikey: $EVOLUTION_API_KEY" \
  -d '{
    "instanceName": "MZN-LOCAL",
    "integration": "WHATSAPP-BAILEYS",
    "qrcode": true
  }'
```

Mantenha `EVOLUTION_SEND_ENABLED=false` durante o primeiro teste. Depois de confirmar nos logs que as mensagens chegam e são classificadas corretamente, altere para `true` e recrie o chatbot:

```bash
sudo docker compose up -d --force-recreate chatbot-bem-nascer
```
=======
