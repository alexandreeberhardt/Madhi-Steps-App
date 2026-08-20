#!/usr/bin/env bash
#
# Sauvegarde la base de positions du serveur POC.
#
# Le volume `madhi_postgres_data` est la seule copie serveur des positions. Le
# telephone garde les siennes, mais rien ne les renvoie une fois marquees
# SYNCED : une base perdue est une base perdue.
#
# Variables reconnues, toutes optionnelles :
#   MADHI_COMPOSE_FILE   chemin du docker-compose.yml       (defaut : deduit du script)
#   MADHI_BACKUP_DIR     repertoire des sauvegardes         (defaut : /var/backups/madhi)
#   MADHI_BACKUP_KEEP    nombre de sauvegardes conservees   (defaut : 30)

set -euo pipefail

# Un dump, c'est la trace complete des deplacements d'une personne pendant un
# an. Les fichiers naissent en 0600 et le repertoire en 0700, sans dependre de
# l'umask du shell qui a lance le script ni de celui de systemd.
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(dirname -- "$(dirname -- "$script_dir")")"

compose_file="${MADHI_COMPOSE_FILE:-$repo_root/server/docker-compose.yml}"
backup_dir="${MADHI_BACKUP_DIR:-/var/backups/madhi}"
keep="${MADHI_BACKUP_KEEP:-30}"

log() {
    printf '%s madhi-backup %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

fail() {
    log "ECHEC $*"
    exit 1
}

[ -f "$compose_file" ] || fail "docker-compose.yml introuvable : $compose_file"

# Les identifiants vivent dans le .env a cote du compose, jamais dans ce script.
env_file="$(dirname -- "$compose_file")/.env"
db_user="madhi"
db_name="madhi_tracker"
if [ -f "$env_file" ]; then
    db_user="$(sed -n 's/^POSTGRES_USER=//p' "$env_file" | tail -n 1 || true)"
    db_name="$(sed -n 's/^POSTGRES_DB=//p' "$env_file" | tail -n 1 || true)"
    db_user="${db_user:-madhi}"
    db_name="${db_name:-madhi_tracker}"
fi

mkdir -p "$backup_dir"
chmod 700 "$backup_dir"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$backup_dir/madhi-$stamp.sql.gz"
partial="$target.partial"

log "debut, base $db_name vers $target"

# --clean --if-exists pour qu'une restauration reparte d'une base propre sans
# demander de supprimer la base a la main.
if ! docker compose -f "$compose_file" exec -T postgres \
        pg_dump --username="$db_user" --dbname="$db_name" --clean --if-exists \
        | gzip -9 > "$partial"; then
    rm -f "$partial"
    fail "pg_dump n'a pas abouti"
fi

# Une sauvegarde n'a de valeur que relue. Un gzip tronque passe inapercu
# jusqu'au jour ou on en a besoin.
gzip -t "$partial" || { rm -f "$partial"; fail "archive illisible"; }
zgrep -q 'COPY public.locations' "$partial" \
    || { rm -f "$partial"; fail "la table locations est absente du dump"; }

mv "$partial" "$target"
size="$(wc -c < "$target" | tr -d ' ')"
log "termine, $size octets"

# La rotation ne s'execute qu'apres une sauvegarde reussie : jamais de fenetre
# ou l'on a supprime les anciennes sans avoir ecrit la nouvelle.
#
# Le tri porte sur le nom, pas sur la date de modification : l'horodatage UTC
# du nom est deja chronologique, alors qu'une copie ou une restauration de
# repertoire rebat les mtime et ferait supprimer n'importe quoi. La sauvegarde
# qui vient d'etre ecrite est exclue explicitement, quoi qu'il arrive.
surplus="$(ls -1 "$backup_dir"/madhi-*.sql.gz 2>/dev/null | sort -r | tail -n "+$((keep + 1))" || true)"
if [ -n "$surplus" ]; then
    printf '%s\n' "$surplus" | while IFS= read -r obsolete; do
        [ "$obsolete" = "$target" ] && continue
        log "rotation, suppression de $(basename -- "$obsolete")"
        rm -f -- "$obsolete"
    done
fi
