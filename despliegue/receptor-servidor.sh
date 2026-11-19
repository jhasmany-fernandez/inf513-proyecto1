#!/usr/bin/env bash
# Ejecuta clases ya compiladas; no descarga dependencias ni instala paquetes.
set -euo pipefail
proyecto="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$proyecto"
modo="${1:---listen}"
case "$modo" in --listen|--once) ;; *) printf 'Use --listen o --once\n' >&2; exit 2;; esac
[[ $# -le 1 ]] || { printf 'Demasiados argumentos\n' >&2; exit 2; }
command -v java >/dev/null || { printf 'Falta Java 17 o superior\n' >&2; exit 1; }
command -v flock >/dev/null || { printf 'Falta flock; consulte al administrador\n' >&2; exit 1; }
[[ -f .env.remoto ]] || { printf 'Falta .env.remoto\n' >&2; exit 1; }
[[ -f target/classes/org/academia/correo/ServicioEmail.class ]] || { printf 'Compile primero el proyecto\n' >&2; exit 1; }
[[ -d target/dependency ]] || { printf 'Faltan dependencias runtime\n' >&2; exit 1; }
umask 077
mkdir -p logs
exec 9>.receptor-servidor.lock
flock -n 9 || { printf 'Ya existe un receptor iniciado con este lanzador\n' >&2; exit 1; }
set -a
source .env.remoto
set +a
exec java -cp 'target/classes:target/dependency/*' org.academia.correo.ServicioEmail "$modo"
