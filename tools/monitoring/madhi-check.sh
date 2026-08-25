#!/usr/bin/env bash
#
# Surveillance minimale du serveur POC : les cinq points de `arch/07` §6.
#
#   1. sante de l'API
#   2. disponibilite du site familial
#   3. espace disque
#   4. derniere sauvegarde reussie
#   5. derniere position recue
#
# Le script existe pour une raison precise : jusqu'ici, une panne ne se voyait
# que si quelqu'un allait la chercher. Le script de sauvegarde pouvait echouer
# trente jours d'affilee sans que personne le sache. Pendant un an d'absence,
# personne ne va rien chercher.
#
# Ce qu'il ne fait pas, et qu'il faut savoir : il tourne sur la machine qu'il
# surveille. Un VPS eteint n'alerte pas -- il se tait, et le silence ressemble a
# tout va bien. MADHI_HEARTBEAT_URL repond a ca, voir README.md.
#
# Aucune alerte ne contient de coordonnees. C'est une contrainte de `arch/08`
# §5, et c'est pour ca que la sonde de position interroge /status, qui renvoie
# des horodatages, et jamais /latest-location, qui renvoie une position.
#
# Variables reconnues, toutes optionnelles, lues dans le fichier de
# configuration (defaut : /etc/madhi/monitoring.env) ou dans l'environnement :
#
#   MADHI_ALERT_URL              ou POSTer les alertes (ntfy ou tout webhook)
#   MADHI_HEARTBEAT_URL          a pinguer quand tout va bien (sonde externe)
#   MADHI_API_URL                defaut : https://madhi-server.alexeber.fr
#   MADHI_SITE_URL               defaut : https://madhi.alexeber.fr
#   MADHI_COMPOSE_FILE           defaut : deduit de la position du script
#   MADHI_BACKUP_DIR             defaut : /var/backups/madhi
#   MADHI_STATE_DIR              defaut : /var/lib/madhi-monitoring
#   MADHI_DISK_MAX_PERCENT       defaut : 85
#   MADHI_BACKUP_MAX_AGE_HOURS   defaut : 26
#   MADHI_POSITION_MAX_AGE_HOURS defaut : 24
#   MADHI_ALERT_REPEAT_HOURS     defaut : 24
#
# Options : --dry-run (ne rien envoyer ni ecrire), --test-alert (prouver que le
# canal d'alerte fonctionne), --help.

set -euo pipefail
umask 077

dry_run=0
test_alert=0

for argument in "$@"; do
    case "$argument" in
        --dry-run)    dry_run=1 ;;
        --test-alert) test_alert=1 ;;
        -h|--help)
            sed -n '2,50p' "${BASH_SOURCE[0]}" | sed 's/^#\{0,1\} \{0,1\}//'
            exit 0
            ;;
        *)
            printf 'option inconnue : %s\n' "$argument" >&2
            exit 2
            ;;
    esac
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(dirname -- "$(dirname -- "$script_dir")")"

# La configuration d'abord, l'environnement ensuite : une variable posee a la
# main sur la ligne de commande doit pouvoir l'emporter le temps d'un essai.
config_file="${MADHI_MONITORING_ENV:-/etc/madhi/monitoring.env}"
if [ -f "$config_file" ]; then
    posees_a_la_main="$(export -p | grep ' MADHI_[A-Z_]*=' || true)"
    # shellcheck disable=SC1090
    . "$config_file"
    [ -z "$posees_a_la_main" ] || eval "$posees_a_la_main"
fi

alert_url="${MADHI_ALERT_URL:-}"
heartbeat_url="${MADHI_HEARTBEAT_URL:-}"
api_url="${MADHI_API_URL:-https://madhi-server.alexeber.fr}"
site_url="${MADHI_SITE_URL:-https://madhi.alexeber.fr}"
compose_file="${MADHI_COMPOSE_FILE:-$repo_root/server/docker-compose.yml}"
backup_dir="${MADHI_BACKUP_DIR:-/var/backups/madhi}"
state_dir="${MADHI_STATE_DIR:-/var/lib/madhi-monitoring}"
disk_max_percent="${MADHI_DISK_MAX_PERCENT:-85}"
backup_max_age_hours="${MADHI_BACKUP_MAX_AGE_HOURS:-26}"
position_max_age_hours="${MADHI_POSITION_MAX_AGE_HOURS:-24}"
alert_repeat_hours="${MADHI_ALERT_REPEAT_HOURS:-24}"

host_name="$(hostname -s 2>/dev/null || hostname)"
now="$(date -u +%s)"
failures=0

log() {
    printf '%s madhi-check %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

# curl est bavard sur deux canaux : la reponse sur la sortie standard, la raison
# de l'echec sur l'erreur. Les melanger collerait le code HTTP au message
# d'echec, et une alerte sur deux tiendrait sur trois lignes.
curl_error_file="$(mktemp)"
trap 'rm -f "$curl_error_file"' EXIT

curl_error() {
    tr '\n\r' '  ' < "$curl_error_file" | sed 's/  */ /g; s/ *$//'
}

# Le journal reste le premier destinataire : meme sans canal d'alerte
# configure, `journalctl -u madhi-check.service` raconte toute l'histoire.
alert() {
    local text
    text="[madhi/$host_name] $(printf '%s' "$1" | tr '\n\r' '  ' | sed 's/  */ /g; s/ *$//')"
    log "ALERTE $text"
    if [ "$dry_run" = "1" ]; then
        log "--dry-run, alerte non transmise"
        return 0
    fi
    if [ -z "$alert_url" ]; then
        log "MADHI_ALERT_URL n'est pas configure, alerte non transmise"
        return 0
    fi
    # Une alerte qui ne part pas est le genre de panne qui se decouvre le jour
    # ou elle comptait : elle est donc journalisee, sans faire echouer le reste.
    if ! curl -fsS --max-time 15 -H "Title: Madhi Tracker" \
            --data-binary "$text" "$alert_url" >/dev/null 2>&1; then
        log "ECHEC de transmission de l'alerte vers MADHI_ALERT_URL"
    fi
}

# Les identifiants du .env ne transitent jamais par la ligne de commande : sur
# une machine partagee, `ps` les afficherait a qui regarde.
curl_with_token() {
    local token="$1"
    shift
    printf 'header = "Authorization: Bearer %s"\n' "$token" | curl -K - "$@"
}

read_env_value() {
    local name="$1" file="$2"
    [ -f "$file" ] || return 0
    sed -n "s/^$name=//p" "$file" | tail -n 1 | tr -d '"'"'"'' || true
}

epoch_of_iso() {
    # GNU date sur le VPS, BSD date sur le Mac de developpement : le script doit
    # rester essayable la ou il est ecrit, pas seulement la ou il tourne.
    local iso="$1"
    date -u -d "$iso" +%s 2>/dev/null \
        || date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "${iso%%.*}Z" +%s 2>/dev/null \
        || return 1
}

humanise_age() {
    local seconds="$1"
    if [ "$seconds" -lt 3600 ]; then
        printf '%d min' "$((seconds / 60))"
    else
        printf '%dh%02d' "$((seconds / 3600))" "$(((seconds % 3600) / 60))"
    fi
}

# Une sonde qui alerte a chaque passage n'est plus lue au bout d'une semaine.
# On alerte au basculement, on repete une fois par jour tant que ca dure, et on
# annonce le retablissement -- sans quoi on ne saurait jamais qu'il a eu lieu.
record() {
    local name="$1" status="$2" message="$3"
    local state_file="$state_dir/$name"
    local previous_status="ok" last_alert=0

    log "$status $name : $message"
    [ "$status" = "ok" ] || failures=$((failures + 1))

    if [ -r "$state_file" ]; then
        read -r previous_status last_alert < "$state_file" || true
        previous_status="${previous_status:-ok}"
        last_alert="${last_alert:-0}"
    fi

    if [ "$status" = "ok" ]; then
        [ "$previous_status" = "ko" ] && alert "RETABLI -- $name : $message"
        last_alert=0
    elif [ "$previous_status" != "ko" ]; then
        alert "PANNE -- $name : $message"
        last_alert="$now"
    elif [ "$((now - last_alert))" -ge "$((alert_repeat_hours * 3600))" ]; then
        alert "TOUJOURS EN PANNE -- $name : $message"
        last_alert="$now"
    fi

    [ "$dry_run" = "1" ] || printf '%s %s\n' "$status" "$last_alert" > "$state_file"
}

# --- 1. sante de l'API ------------------------------------------------------
#
# Sondee depuis l'exterieur, en HTTPS, et non sur 127.0.0.1:8111 : c'est le
# chemin qu'emprunte le telephone. Un certificat expire ou un nginx muet sont
# des pannes de synchronisation, meme quand le conteneur se porte bien.
check_api() {
    local body
    if ! body="$(curl -fsS --max-time 10 "$api_url/health" 2>"$curl_error_file")"; then
        record api ko "$api_url/health injoignable -- $(curl_error)"
        return
    fi
    case "$body" in
        *'"status"'*'"ok"'*) record api ok "$api_url/health repond ok" ;;
        *) record api ko "reponse inattendue de $api_url/health : $body" ;;
    esac
}

# --- 2. disponibilite du site familial --------------------------------------
#
# On attend un 401, et c'est volontaire : le defi d'authentification prouve que
# le chemin secret a bien ete reconnu par le conteneur, donc que TLS, le nginx
# de l'hote, le conteneur et le segment sont tous en place -- le tout sans
# manipuler le mot de passe familial. Un 404 signifie que le segment ne
# correspond plus, un 502 que le conteneur ne repond pas.
check_site() {
    local segment code
    segment="$(read_env_value SITE_SECRET_SEGMENT "$(dirname -- "$compose_file")/.env")"
    if [ -z "$segment" ]; then
        record site ko "SITE_SECRET_SEGMENT introuvable dans server/.env, sonde impossible"
        return
    fi
    if ! code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
            "$site_url/f/$segment/" 2>"$curl_error_file")"; then
        record site ko "$site_url injoignable -- $(curl_error)"
        return
    fi
    case "$code" in
        401|200) record site ok "le lien familial repond $code" ;;
        404) record site ko "reponse 404 : le segment secret ne correspond plus" ;;
        *)   record site ko "reponse $code sur le lien familial" ;;
    esac
}

# --- 3. espace disque -------------------------------------------------------
#
# Deux repertoires comptent : celui des sauvegardes et celui des volumes
# Docker, ou vit la base. Ils partagent presque toujours le meme systeme de
# fichiers, d'ou la deduplication : une seule alerte pour un seul disque plein.
check_disk() {
    local seen="" worst=0 detail="" path percent mount
    for path in "$backup_dir" /var/lib/docker; do
        [ -d "$path" ] || continue
        read -r percent mount <<< "$(df -P "$path" | awk 'NR==2 {gsub("%","",$5); print $5, $6}')" || true
        [ -n "${percent:-}" ] || continue
        case " $seen " in *" $mount "*) continue ;; esac
        seen="$seen $mount"
        detail="$detail $mount a $percent%,"
        [ "$percent" -gt "$worst" ] && worst="$percent"
    done
    detail="${detail%,}"
    if [ -z "$seen" ]; then
        record disque ko "aucun point de montage a mesurer"
    elif [ "$worst" -ge "$disk_max_percent" ]; then
        record disque ko "seuil de $disk_max_percent% atteint --$detail"
    else
        record disque ok "sous le seuil de $disk_max_percent% --$detail"
    fi
}

# --- 4. derniere sauvegarde reussie -----------------------------------------
#
# L'age du fichier, et non le statut du service : un timer jamais active ne
# laisse aucune trace d'echec, alors qu'il est exactement aussi grave qu'un
# `pg_dump` en erreur. Le fichier, lui, ne ment pas.
check_backup() {
    local newest age modified
    if [ ! -d "$backup_dir" ]; then
        record sauvegarde ko "$backup_dir n'existe pas -- la sauvegarde n'a jamais tourne"
        return
    fi
    newest="$(ls -1 "$backup_dir"/madhi-*.sql.gz 2>/dev/null | sort | tail -n 1 || true)"
    if [ -z "$newest" ]; then
        record sauvegarde ko "aucune sauvegarde dans $backup_dir"
        return
    fi
    modified="$(stat -c %Y "$newest" 2>/dev/null || stat -f %m "$newest")"
    age="$((now - modified))"
    if [ "$age" -ge "$((backup_max_age_hours * 3600))" ]; then
        record sauvegarde ko "la derniere date de $(humanise_age "$age"), seuil $backup_max_age_hours h -- $(basename -- "$newest")"
    else
        record sauvegarde ok "$(basename -- "$newest"), il y a $(humanise_age "$age")"
    fi
}

# --- 5. derniere position recue ---------------------------------------------
#
# /status et jamais /latest-location : cette sonde ne doit connaitre que des
# horodatages. Une alerte qui sortirait du VPS avec une coordonnee dedans
# annulerait tout ce que le projet s'impose par ailleurs.
check_position() {
    local env_file token trip_id response body code received age
    env_file="$(dirname -- "$compose_file")/.env"
    token="$(read_env_value PUBLIC_READ_TOKEN "$env_file")"
    trip_id="$(read_env_value INITIAL_TRIP_ID "$env_file")"
    if [ -z "$token" ] || [ -z "$trip_id" ]; then
        record position ko "PUBLIC_READ_TOKEN ou INITIAL_TRIP_ID introuvable dans server/.env"
        return
    fi

    if ! response="$(curl_with_token "$token" -sS --max-time 10 \
            -w '\n%{http_code}' "$api_url/api/v1/trips/$trip_id/status" 2>"$curl_error_file")"; then
        record position ko "statut du voyage injoignable -- $(curl_error)"
        return
    fi
    code="${response##*$'\n'}"
    body="${response%$'\n'*}"
    if [ "$code" != "200" ]; then
        record position ko "reponse $code sur le statut du voyage"
        return
    fi

    received="$(printf '%s' "$body" | sed -n 's/.*"latestReceivedAt": *"\([^"]*\)".*/\1/p')"
    if [ -z "$received" ]; then
        record position ko "aucune position recue depuis la creation du voyage"
        return
    fi
    if ! received="$(epoch_of_iso "$received")"; then
        record position ko "horodatage de derniere position illisible"
        return
    fi
    age="$((now - received))"
    if [ "$age" -ge "$((position_max_age_hours * 3600))" ]; then
        record position ko "aucune position depuis $(humanise_age "$age"), seuil $position_max_age_hours h"
    else
        record position ok "derniere position il y a $(humanise_age "$age")"
    fi
}

if [ "$test_alert" = "1" ]; then
    alert "essai du canal d'alerte, declenche a la main -- aucune panne en cours"
    log "essai envoye ; si rien n'arrive, le canal ne sert a rien le jour ou il comptera"
    exit 0
fi

[ "$dry_run" = "1" ] || mkdir -p "$state_dir"

check_api
check_site
check_disk
check_backup
check_position

# Le battement ne part que si les cinq sondes sont vertes : une sonde externe
# qui recoit ce ping sait alors que le VPS est vivant *et* en bon etat. C'est le
# seul moyen d'etre prevenu quand la machine, elle, ne peut plus prevenir.
if [ "$failures" -eq 0 ] && [ -n "$heartbeat_url" ] && [ "$dry_run" != "1" ]; then
    curl -fsS --max-time 10 "$heartbeat_url" >/dev/null 2>&1 \
        || log "battement non transmis vers MADHI_HEARTBEAT_URL"
fi

if [ "$failures" -eq 0 ]; then
    log "les cinq sondes sont vertes"
    exit 0
fi
log "$failures sonde(s) en panne"
exit 1
