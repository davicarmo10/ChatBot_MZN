# Chatbot — Maternidade Bem Nascer

Chatbot administrativo para WhatsApp da Maternidade Bem Nascer. Ele usa a Evolution API para receber mensagens e enviar as respostas.

## Pré-requisitos

- Docker Engine em execução e Docker Compose v2 (`docker compose`)
- JDK 21 e Maven, somente para rodar o chatbot sem Docker

## Estrutura

```text
.
├── evolution-api/          # Evolution API, PostgreSQL e Redis
├── src/                    # chatbot Spring Boot
├── docker-compose.yml      # chatbot Spring Boot
└── .env.example            # configuração do chatbot
```

## Iniciar o ambiente

Primeiro suba a Evolution API. A pasta já tem um `.env` local, ignorado pelo Git, com credenciais aleatórias.

```bash
cd evolution-api
sudo docker compose up -d
sudo docker compose ps
```

Depois, volte à raiz, crie o arquivo de configuração do chatbot e inicie-o:

```bash
cd ..
cp .env.example .env
# Edite .env e copie a EVOLUTION_API_KEY de evolution-api/.env
sudo docker compose up -d --build
sudo docker compose ps
```

O serviço da Evolution fica disponível somente neste computador em `http://localhost:8080`; o chatbot fica em `http://localhost:8081`.

## Conectar o WhatsApp

Após os dois serviços estarem em execução, siga as instruções de [evolution-api/README.md](evolution-api/README.md) para criar a instância `MZN-LOCAL`, obter o QR Code e vinculá-lo pelo WhatsApp.

Mantenha `EVOLUTION_SEND_ENABLED=false` no `.env` da raiz durante o primeiro teste. Nessa etapa, as mensagens recebidas são interpretadas e a resposta aparece apenas nos logs:

```bash
sudo docker logs -f chatbot_bem_nascer
```

Quando as mensagens estiverem corretas, altere para `EVOLUTION_SEND_ENABLED=true` e aplique:

```bash
sudo docker compose up -d --force-recreate chatbot-bem-nascer
```

## Testar o chatbot

```bash
curl http://localhost:8081/api/bot/status
curl -X POST http://localhost:8081/api/bot/test \
  -H 'Content-Type: application/json' \
  -d '{"message":"qual o horario da visita?"}'
```

## Informações respondidas

- documentos da paciente e do acompanhante;
- horário e regras de visitas;
- horário do setor administrativo.

As informações institucionais ficam em `src/main/resources/application.yml`.
