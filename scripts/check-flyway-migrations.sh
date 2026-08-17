#!/usr/bin/env bash
set -Eeuo pipefail

shopt -s nullglob
has_duplicates=false

for migration_dir in services/*/src/main/resources/db/migration; do
  declare -A versions=()

  for migration in "$migration_dir"/V*__*.sql; do
    filename="${migration##*/}"
    version="${filename%%__*}"

    if [[ -n "${versions[$version]:-}" ]]; then
      echo "Versão Flyway duplicada em $migration_dir: $version" >&2
      echo "  - ${versions[$version]}" >&2
      echo "  - $filename" >&2
      has_duplicates=true
    else
      versions[$version]="$filename"
    fi
  done

  unset versions
done

if [[ "$has_duplicates" == true ]]; then
  exit 1
fi

echo "Versões Flyway únicas em todos os serviços"
