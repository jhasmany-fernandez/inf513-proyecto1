#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -f "$project_dir/.env.remoto" ]]; then
    printf 'Falta correo_academia/.env.remoto\n' >&2
    exit 1
fi
if [[ $# -eq 0 ]]; then
    printf 'Indique --execute, --init o --once; no se inicia el buzon automaticamente.\n' >&2
    exit 1
fi
export ACADEMIA_ENV_FILE="$project_dir/.env.remoto"
exec bash "$project_dir/ejecutar-local.sh" "$@"
