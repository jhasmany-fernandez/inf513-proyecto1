#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi
maven="$project_dir/.tools/apache-maven-3.9.9/bin/mvn"
if [[ ! -x "$maven" ]]; then
    printf 'Instale Maven o extraiga Maven 3.9.9 en correo_academia/.tools/.\n' >&2
    exit 1
fi
exec "$maven" "$@"
