# Evolution API local

Esta pasta executa a Evolution API, PostgreSQL e Redis para o chatbot. Os dados de sessões do WhatsApp e do banco ficam em volumes Docker persistentes.

## Preparar e iniciar

1. Copie `.env.example` para `.env` e substitua os dois valores secretos.
2. Inicie os serviços:

   ```bash
   cd evolution-api
   sudo docker compose up -d
   ```

3. Confira a API em `http://localhost:8080` e acompanhe sua inicialização:

   ```bash
   sudo docker compose logs -f evolution-api
   ```

Depois, em outro terminal e na raiz do projeto, inicie o chatbot:

```bash
sudo docker compose up -d --build
```

Os dois serviços usam a rede Docker `chatbot-evolution-network`. A Evolution encaminha mensagens para `http://chatbot-bem-nascer:8081/webhook` e o chatbot chama `http://evolution-api:8080` para responder.

## Conectar o WhatsApp

Crie a instância e receba o QR Code:

```bash
cd evolution-api
set -a; . ./.env; set +a
curl -X POST "http://localhost:${EVOLUTION_PORT}/instance/create" \
  -H "Content-Type: application/json" \
  -H "apikey: ${EVOLUTION_API_KEY}" \
  -d '{"instanceName":"MZN-LOCAL","integration":"WHATSAPP-BAILEYS","qrcode":true}'
```

Leia o valor de `base64` retornado pelo comando como QR Code no WhatsApp: **Dispositivos conectados → Conectar dispositivo**. Para buscá-lo novamente:

```bash
curl "http://localhost:${EVOLUTION_PORT}/instance/connect/MZN-LOCAL" \
  -H "apikey: ${EVOLUTION_API_KEY}"
```

Mantenha `EVOLUTION_SEND_ENABLED=false` no `.env` da raiz enquanto testa. Siga o [guia do chatbot](../README.md)
para iniciar o Ollama, testar a IA local e preencher `BOT_ALLOWED_NUMBERS` antes de ativar o envio.
Os logs do chatbot mostram as intenções, sem imprimir as conversas completas.
