#!/usr/bin/env bash
set -Eeuo pipefail

output_root="${1:-deploy}"
release_sha="${2:-$(git rev-parse HEAD)}"
release_dir="$output_root/release"

if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "SHA da release inválido: $release_sha" >&2
  exit 1
fi

if [[ -e "$release_dir" ]]; then
  echo "Diretório da release já existe: $release_dir" >&2
  exit 1
fi

mkdir -p "$release_dir"

for service in auth-service task-service schedule-service notification-service; do
  mapfile -t jars < <(
    find "services/$service/build/libs" -maxdepth 1 -type f \
      -name '*.jar' ! -name '*-plain.jar'
  )

  if [[ "${#jars[@]}" -ne 1 ]]; then
    echo "Esperado exatamente um bootJar para $service; encontrados: ${#jars[@]}" >&2
    exit 1
  fi

  cp "${jars[0]}" "$release_dir/$service.jar"
done

printf '%s\n' "$release_sha" > "$release_dir/REVISION"
archive="$output_root/justdoit-release-$release_sha.tar.gz"
tar -czf "$archive" -C "$release_dir" .
sha256sum "$archive" > "$archive.sha256"

printf '%s\n' "$archive"
