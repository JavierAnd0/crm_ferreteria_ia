#!/usr/bin/env bash
# Hook PreToolUse: impide que un agente edite una migración Flyway que ya existe en el repositorio.
# Flyway valida el checksum de cada migración aplicada; cambiarla rompe todas las bases ya desplegadas.
entrada=$(cat)
ruta=$(printf '%s' "$entrada" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))')
case "$ruta" in
  */db/migration/V*__*.sql|*/db/demo/V*__*.sql)
    if [ -f "$ruta" ] && git -C "$(dirname "$ruta")" ls-files --error-unmatch "$ruta" >/dev/null 2>&1; then
      echo "La migración $(basename "$ruta") ya está versionada y no se puede modificar. Cree una nueva V{n+1}__descripcion.sql." >&2
      exit 2
    fi
    ;;
esac
exit 0
