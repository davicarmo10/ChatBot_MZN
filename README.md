# Chatbot — Maternidade Bem Nascer

Chatbot administrativo para WhatsApp da Maternidade Bem Nascer, com saudações automáticas,
interpretação contextual por IA local e histórico limitado a sete dias.

A Evolution API recebe e envia as mensagens. O Ollama executa `qwen3:4b` no seu computador,
e o LangChain4j conecta o modelo ao Java. A IA identifica os assuntos da pergunta; as respostas
são montadas pelo Java com as informações cadastradas. Não há cobrança de tokens de uma API
de IA, mas o computador precisa ficar ligado e executar os serviços.

## Pré-requisitos

- Docker Engine em execução e Docker Compose (`docker compose`)
- JDK 21 e Maven, somente para rodar o chatbot sem Docker

## Estrutura

```text
.
├── evolution-api/          # Evolution API, PostgreSQL e Redis
├── src/                    # chatbot Spring Boot
├── docker-compose.yml      # chatbot Spring Boot + Ollama
└── .env.example            # configuração do chatbot
```

## Iniciar o ambiente

Primeiro suba a Evolution API. A pasta já tem um `.env` local, ignorado pelo Git, com credenciais aleatórias.

```bash
cd evolution-api
sudo docker compose up -d
sudo docker compose ps
```

Como o WhatsApp já foi conectado, mantenha a instalação existente da Evolution e seus volumes.
Na raiz do projeto, use o `.env` local já criado. Em uma nova instalação, copie `.env.example`
para `.env` e use a mesma `EVOLUTION_API_KEY` de `evolution-api/.env`. Não sobrescreva um `.env`
que já contenha suas credenciais.

### 1. Iniciar o modelo local

Na raiz do projeto:

```bash
sudo docker compose up -d ollama
sudo docker compose exec ollama ollama pull qwen3:4b
sudo docker compose exec ollama ollama list
```

O download do modelo ocupa aproximadamente 2,5 GB e fica no volume `ollama_data`.
Esta configuração usa CPU, um modelo carregado e uma inferência por vez. A velocidade depende
do computador. O primeiro teste também inclui o tempo de carregar o modelo na memória.

### 2. Iniciar o chatbot sem enviar mensagens

Mantenha estas variáveis no `.env` da raiz:

```env
EVOLUTION_SEND_ENABLED=false
BOT_ALLOWED_NUMBERS=
AI_ENABLED=true
OLLAMA_MODEL=qwen3:4b
AI_TIMEOUT=60s
```

```bash
sudo docker compose up -d --build chatbot-bem-nascer
sudo docker compose ps
```

A construção da imagem executa os testes automatizados antes de gerar o aplicativo.
A Evolution fica em `http://localhost:8080`, o chatbot em `http://localhost:8081` e o Ollama em
`http://localhost:11434`. As portas publicadas são acessíveis apenas neste computador.

### 3. Testar o contexto pelo endpoint local

O endpoint de teste não envia mensagens ao WhatsApp e independe da lista de contatos autorizados.
Use o mesmo `conversationId` para continuar uma conversa:

```bash
curl -sS http://localhost:8081/api/bot/test \
  -H 'Content-Type: application/json' \
  -d '{"message":"Bom dia!","conversationId":"teste-1"}'

curl -sS http://localhost:8081/api/bot/test \
  -H 'Content-Type: application/json' \
  -d '{"message":"Quais documentos preciso levar?","conversationId":"teste-1"}'

curl -sS http://localhost:8081/api/bot/test \
  -H 'Content-Type: application/json' \
  -d '{"message":"E meu acompanhante?","conversationId":"teste-1"}'
```

As respostas devem apresentar o assistente, listar os documentos da paciente e depois os do
acompanhante. Repita “oi” para verificar que a apresentação completa não é repetida. Teste também
“Bom dia! Qual o horário de visita?” e “Qual o horário de visita e quais documentos preciso levar?”.

A resposta mantém o formato `{"intent":"...","response":"..."}`. Para vários assuntos,
`intent` indica o primeiro e `response` combina as orientações. Sem `conversationId`, cada chamada
é isolada. IDs aceitam de 1 a 64 letras, números, `_` ou `-`. Conversas de teste ficam separadas das
conversas do WhatsApp, mas seguem os mesmos limites de armazenamento.

### 4. Autorizar os contatos de teste

No `.env`, preencha somente os números que participarão dos testes, com DDI e DDD:

```env
# Exemplo fictício; substitua pelo número do contato que vai enviar as perguntas.
BOT_ALLOWED_NUMBERS=5596999990001,5596999990002
EVOLUTION_SEND_ENABLED=false
```

Lista vazia bloqueia todos os contatos do WhatsApp. Não é uma configuração para responder a todos.
A instância recebida no webhook também precisa corresponder a `EVOLUTION_INSTANCE`.
Use o nome exato da instância já conectada na Evolution. Por exemplo, se ela se chama `test`,
configure `EVOLUTION_INSTANCE=test` no `.env` da raiz. Esse nome também é usado para enviar
as respostas; não é necessário criar outra instância ou conectar o WhatsApp novamente.

```bash
sudo docker compose up -d --force-recreate chatbot-bem-nascer
sudo docker compose logs -f chatbot-bem-nascer
```

Envie perguntas de um número autorizado para o número conectado à Evolution. Os logs mostram o
identificador do evento, a intenção e `envio=false`, sem o texto completo das conversas. Nesta
etapa, as respostas simuladas são registradas no histórico de teste daquela conversa.

### 5. Ativar as respostas no WhatsApp

Depois de validar os testes, altere `EVOLUTION_SEND_ENABLED=true` no `.env` e aplique:

```bash
sudo docker compose up -d --force-recreate chatbot-bem-nascer
```

Somente os números autorizados receberão respostas. Eventos recebidos enquanto o envio estava
desativado continuam simulados, mesmo se forem processados após essa alteração. Para pausar os
envios, volte a variável para `false` e recrie o chatbot.

## Conectar o WhatsApp

Após os dois serviços estarem em execução, siga as instruções de [evolution-api/README.md](evolution-api/README.md) para criar a instância `MZN-LOCAL`, obter o QR Code e vinculá-lo pelo WhatsApp.

O webhook global da Evolution aponta para `http://chatbot-bem-nascer:8081/webhook`.
Grupos, status, mensagens do próprio bot, eventos sem identificador e números não autorizados
são ignorados. Para mensagens identificadas por LID, é necessário que a Evolution também forneça
o número real em `remoteJidAlt`; o identificador LID nunca é usado como telefone.

## Testar o chatbot

```bash
curl http://localhost:8081/api/bot/status
curl -X POST http://localhost:8081/api/bot/test \
  -H 'Content-Type: application/json' \
  -d '{"message":"qual o horario da visita?"}'
```

## Informações respondidas nesta etapa

- documentos da paciente e do acompanhante;
- horário de visitas.

As informações institucionais ficam em `src/main/resources/application.yml`. As regras e os
horários administrativos já existentes no código continuam cadastrados, mas não são habilitados
pela nova automação. Pré e pós-parto ficam para uma etapa futura com conteúdo revisado.

As saudações “oi”, “olá”, “bom dia”, “boa tarde” e “boa noite” são reconhecidas no início da mensagem,
ignorando acentos, pontuação e maiúsculas. Uma saudação isolada apresenta o assistente no início
da conversa ou após 24 horas sem mensagens; nas demais vezes responde brevemente.

## Histórico, tamanho e falhas

- Histórico H2 no volume `chatbot_data`, preservado após reiniciar ou recriar o contêiner.
- Retenção de sete dias por mensagem, até 200 mensagens por contato e 2.000 no total, incluindo
  perguntas e respostas. Se atingir os limites, as mais antigas saem antes dos sete dias.
- Contexto do modelo limitado às últimas 12 mensagens e a 8.000 caracteres, incluindo a pergunta
  atual. Isso permite lembrar continuações recentes, sem prometer lembrar tudo da semana.
- Textos acima de 2.000 caracteres recebem um pedido de resumo e não são guardados integralmente.
- Limpeza na inicialização e a cada hora. O H2 reutiliza espaço livre; seu arquivo pode não diminuir
  imediatamente após a limpeza. O volume do modelo Ollama é separado do histórico.
- A retenção descrita aplica-se ao chatbot. A Evolution mantém seu próprio banco e suas próprias
  configurações de persistência, que não são apagados por essa limpeza.
- A fila persiste antes de confirmar o webhook e processa uma mensagem por vez. Eventos duplicados
  são ignorados por instância e ID. O limite é de 2.000 eventos pendentes e 20.000 IDs em sete dias;
  ao atingir a capacidade, o webhook retorna HTTP 503 em vez de perder eventos silenciosamente.
- Se a IA falhar ou demorar mais de 60 segundos, perguntas administrativas claras usam as regras
  locais. Continuação ambígua recebe uma resposta pedindo mais informações. `AI_ENABLED=false`
  permite testar esse funcionamento sem o modelo.
- Falha durante um envio pode significar entrega incerta. Esses eventos ficam como `UNCERTAIN`
  e não são reenviados automaticamente, inclusive após reiniciar. Confira a conversa no WhatsApp
  antes de responder manualmente. O histórico pode incluir uma resposta cuja entrega ficou incerta.

Não há painel de histórico nesta versão. Execute uma única instância do chatbot por volume H2.

## WhatsApp conectado, mas sem resposta

O endpoint `/api/bot/test` testa a interpretação, mas não verifica o recebimento do webhook,
a lista de contatos ou o envio ao WhatsApp. Primeiro aplique o código e o `.env` atuais,
executando na raiz do projeto:

```bash
sudo docker compose up -d --build --force-recreate chatbot-bem-nascer
sudo docker compose exec chatbot-bem-nascer printenv EVOLUTION_SEND_ENABLED EVOLUTION_INSTANCE BOT_ALLOWED_NUMBERS
sudo docker compose logs -f --tail=60 chatbot-bem-nascer
```

Envie uma mensagem privada de **outro número**, autorizado em `BOT_ALLOWED_NUMBERS`, para o
número conectado à Evolution. O número autorizado é o remetente que fará a pergunta.
Mensagens enviadas pelo próprio número conectado são ignoradas para evitar ciclos de respostas.

| Log observado | O que verificar |
|---|---|
| Nenhuma linha `Webhook recebido em /webhook` | A Evolution não entregou a requisição neste endpoint. Confira o webhook da instância, o evento `MESSAGES_UPSERT`, a URL `http://chatbot-bem-nascer:8081/webhook` e a rede compartilhada. |
| `FROM_ME` | A mensagem foi enviada pelo próprio número conectado; teste a partir de outro número. |
| `INSTANCE_MISMATCH` | O nome recebido está diferente de `EVOLUTION_INSTANCE`; ambos aparecem no log. |
| `EMPTY_ALLOWLIST` | O contêiner não recebeu nenhum contato autorizado. |
| `NUMBER_NOT_ALLOWED` | Compare o número de quem enviou a mensagem com a lista. O log mostra apenas os quatro últimos dígitos e o tamanho; 12 e 13 dígitos não são considerados iguais. |
| `LID_WITHOUT_PHONE` | O evento contém um LID sem número em `remoteJidAlt`. Precisamos verificar o formato fornecido pela Evolution antes de mapear esse contato. |
| `EVENT_NOT_SUPPORTED` ou `NO_TEXT` | O evento não é uma nova mensagem reconhecida ou não contém texto suportado. |
| `resultado=ACCEPTED; envio_habilitado=false` | A mensagem entrou na fila, mas o envio está desativado ou a configuração de envio está incompleta. Confira as variáveis carregadas no contêiner. |
| `resultado=DUPLICATE` | Esse identificador já foi recebido. Envie uma mensagem nova para testar. |
| `erro=HTTP_...` ou `erro=IO_...` | O chatbot tentou enviar, mas a chamada à Evolution falhou. Use o código mostrado para investigar autenticação, instância ou comunicação. |

Uma alteração no `.env` passa a valer no contêiner após sua recriação; `docker compose restart`
sozinho não reaplica as variáveis. Os logs de diagnóstico não imprimem mensagens nem API keys.

## Executar os testes

Com JDK 21 e Maven:

```bash
mvn verify
```

Os testes usam H2 temporário e um classificador simulado; não baixam o modelo nem enviam mensagens
ao WhatsApp. Também são executados em `sudo docker compose build chatbot-bem-nascer`.

Documentação das integrações: [LangChain4j + Ollama](https://docs.langchain4j.dev/integrations/language-models/ollama/),
[modelo Qwen3:4b](https://ollama.com/library/qwen3:4b),
[Ollama no Docker](https://docs.ollama.com/docker).
