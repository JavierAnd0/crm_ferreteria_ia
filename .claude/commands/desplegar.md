---
description: Verifica que main está lista y explica o ejecuta el despliegue con docker compose.
argument-hint: [local | servidor]
---
Destino: $ARGUMENTS (por defecto, local).

1. Confirme que está en `main` actualizada y que `mvn -q verify` pasa.
2. Local: `docker compose up -d --build` con `.env` y espere a que `app` quede `healthy`; pruebe
   `curl -s localhost:8080/actuator/health` e inicie sesión con `POST /api/auth/login`.
3. Servidor: el despliegue lo hace GitHub Actions al fusionar en `main` (job `desplegar`). Muestre el estado del
   último flujo con `gh run list --workflow ci.yml --limit 3` y no despliegue a mano sin confirmación de una persona.
4. Informe la versión (SHA corto de la imagen) y el estado de salud.
