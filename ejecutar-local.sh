#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
config="${ACADEMIA_ENV_FILE:-$project_dir/.env.remoto}"
if [[ ! -f "$config" ]]; then
    printf 'Falta configuracion: %s\n' "$config" >&2
    exit 1
fi
if [[ $# -eq 0 ]]; then
    printf 'Use --init, --execute, --once o --listen para iniciar el receptor continuo.\n' >&2
    exit 1
fi
set -a
source "$config"
set +a
cd "$project_dir"
bash "$project_dir/mvn-local.sh" -q compile dependency:copy-dependencies -DincludeScope=runtime
if [[ "$1" == --listen ]]; then shift; fi
exec java -cp 'target/classes:target/dependency/*' org.academia.correo.ServicioEmail "$@"
