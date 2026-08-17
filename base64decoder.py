python3 - <<'PY'
import json
import base64

with open("instancia.json", encoding="utf-8") as f:
    data = json.load(f)

b64 = data["qrcode"]["base64"]

if "," in b64:
    b64 = b64.split(",", 1)[1]

with open("qrcode.png", "wb") as f:
    f.write(base64.b64decode(b64))

print("QR Code criado em: qrcode.png")
PY