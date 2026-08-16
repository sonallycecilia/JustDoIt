#!/usr/bin/env bash
set -Eeuo pipefail

archive="${1:-}"
ssh_port="${VPS_SSH_PORT:-22}"
app_dir="${VPS_APP_DIR:-/opt/justdoit}"
ssh_key_path="${VPS_SSH_KEY_PATH:-}"

: "${RELEASE_SHA:?RELEASE_SHA não informado}"
: "${VPS_HOST:?VPS_HOST não informado}"
: "${VPS_USER:?VPS_USER não informado}"

if [[ ! -f "$archive" ]]; then
  echo "Pacote de release não encontrado: $archive" >&2
  exit 1
fi

if [[ ! "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "RELEASE_SHA inválido: $RELEASE_SHA" >&2
  exit 1
fi

if [[ ! "$ssh_port" =~ ^[0-9]+$ ]] || ((ssh_port < 1 || ssh_port > 65535)); then
  echo "VPS_SSH_PORT inválida: $ssh_port" >&2
  exit 1
fi

if [[ "$app_dir" != /* || "$app_dir" == "/" ]]; then
  echo "VPS_APP_DIR deve ser um caminho absoluto e específico: $app_dir" >&2
  exit 1
fi

ssh_target="$VPS_USER@$VPS_HOST"
remote_archive="/tmp/justdoit-release-$RELEASE_SHA.tar.gz"
ssh_identity_options=()
ssh_security_options=(-o BatchMode=yes -o StrictHostKeyChecking=yes)

if [[ -n "$ssh_key_path" ]]; then
  if [[ ! -f "$ssh_key_path" ]]; then
    echo "VPS_SSH_KEY_PATH não encontrado: $ssh_key_path" >&2
    exit 1
  fi
  ssh_identity_options=(-i "$ssh_key_path" -o IdentitiesOnly=yes)
fi

scp -P "$ssh_port" "${ssh_security_options[@]}" \
  "${ssh_identity_options[@]}" \
  "$archive" "$ssh_target:$remote_archive"

ssh -p "$ssh_port" "${ssh_security_options[@]}" \
  "${ssh_identity_options[@]}" "$ssh_target" bash -s -- \
  "$RELEASE_SHA" "$app_dir" "$remote_archive" <<'REMOTE_SCRIPT'
set -Eeuo pipefail

release_sha="$1"
app_dir="$2"
archive="$3"
release_dir="$app_dir/releases/$release_sha"
current_link="$app_dir/current"
services=(auth task schedule notification)
artifacts=(auth-service.jar task-service.jar schedule-service.jar notification-service.jar)
ports=(8080 8081 8082 8083)
temp_dir=""

if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "SHA recebido pela VPS é inválido" >&2
  exit 1
fi

if [[ "$app_dir" != /* || "$app_dir" == "/" ]]; then
  echo "Diretório de aplicação inseguro: $app_dir" >&2
  exit 1
fi

cleanup() {
  rm -f -- "$archive"
  if [[ -n "$temp_dir" && -d "$temp_dir" ]]; then
    rm -rf -- "$temp_dir"
  fi
}
trap cleanup EXIT

atomic_link() {
  local target="$1"
  local link="$2"
  local next_link="$link.next"

  rm -f -- "$next_link"
  ln -s -- "$target" "$next_link"
  mv -Tf -- "$next_link" "$link"
}

activate_links() {
  local target_release="$1"
  local index

  atomic_link "$target_release" "$current_link"
  for index in "${!services[@]}"; do
    # Mantém compatibilidade com as unidades systemd antigas enquanto elas
    # ainda apontarem para /opt/justdoit/<serviço>.jar.
    atomic_link "$target_release/${artifacts[$index]}" \
      "$app_dir/${artifacts[$index]}"
  done
}

wait_for_health() {
  local service="$1"
  local port="$2"
  local attempt
  local response

  for attempt in {1..30}; do
    response="$(curl --silent --show-error --max-time 3 \
      "http://127.0.0.1:$port/actuator/health" 2>/dev/null || true)"
    if grep -q '"status":"UP"' <<<"$response"; then
      echo "$service saudável na porta $port"
      return 0
    fi
    sleep 2
  done

  echo "$service não ficou saudável na porta $port" >&2
  sudo -n journalctl -u "justdoit-$service.service" -n 80 --no-pager || true
  return 1
}

restart_and_check() {
  local index

  for index in "${!services[@]}"; do
    if ! sudo -n systemctl restart "justdoit-${services[$index]}.service"; then
      return 1
    fi
    if ! wait_for_health "${services[$index]}" "${ports[$index]}"; then
      return 1
    fi
  done
}

mkdir -p -- "$app_dir/releases"

backup_dir="$app_dir/backups"
backup_timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
database_backup="$backup_dir/justdoit-pre-deploy-$backup_timestamp-$release_sha.sql.gz"
db_password="$(grep -m1 '^SPRING_DATASOURCE_PASSWORD=' "$app_dir/.env" | cut -d= -f2-)"

if [[ -z "$db_password" ]]; then
  echo "SPRING_DATASOURCE_PASSWORD não encontrado em $app_dir/.env" >&2
  exit 1
fi

mkdir -p -- "$backup_dir"
umask 077
docker exec -e MYSQL_PWD="$db_password" db_justdoit \
  mysqldump -uroot --single-transaction --quick --routines --triggers \
  --no-tablespaces justdoit_db | gzip -c > "$database_backup"
gzip -t "$database_backup"
echo "Backup do banco criado e validado em $database_backup"

if [[ ! -d "$release_dir" ]]; then
  temp_dir="$app_dir/releases/.$release_sha.tmp.$$"
  mkdir -- "$temp_dir"
  tar -xzf "$archive" -C "$temp_dir"

  for artifact in "${artifacts[@]}"; do
    if [[ ! -s "$temp_dir/$artifact" ]]; then
      echo "JAR ausente ou vazio no pacote: $artifact" >&2
      exit 1
    fi
  done

  if [[ "$(tr -d '\r\n' < "$temp_dir/REVISION")" != "$release_sha" ]]; then
    echo "A revisão do pacote não corresponde ao commit solicitado" >&2
    exit 1
  fi

  mv -- "$temp_dir" "$release_dir"
  temp_dir=""
fi

previous_release=""
if [[ -L "$current_link" ]]; then
  previous_release="$(readlink -f -- "$current_link" || true)"
fi

# No primeiro deploy versionado, preserva os JARs atuais para permitir rollback.
if [[ -z "$previous_release" ]]; then
  legacy_release="$app_dir/releases/pre-deploy-$(date -u +%Y%m%dT%H%M%SZ)"
  legacy_complete=true
  for artifact in "${artifacts[@]}"; do
    if [[ ! -f "$app_dir/$artifact" ]]; then
      legacy_complete=false
      break
    fi
  done

  if [[ "$legacy_complete" == true ]]; then
    mkdir -- "$legacy_release"
    for artifact in "${artifacts[@]}"; do
      cp -L -- "$app_dir/$artifact" "$legacy_release/$artifact"
    done
    previous_release="$legacy_release"
  fi
fi

activate_links "$release_dir"

if restart_and_check; then
  echo "Release $release_sha implantada com sucesso"
  exit 0
fi

echo "Falha na release $release_sha; iniciando rollback" >&2
if [[ -n "$previous_release" && -d "$previous_release" ]]; then
  activate_links "$previous_release"
  if restart_and_check; then
    echo "Rollback concluído para $previous_release" >&2
  else
    echo "Rollback executado, mas um ou mais serviços continuam indisponíveis" >&2
  fi
else
  echo "Não existe release anterior para rollback automático" >&2
fi

exit 1
REMOTE_SCRIPT
