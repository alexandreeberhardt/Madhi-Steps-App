# Sauvegarde de la base serveur

Le volume `madhi_postgres_data` est la seule copie serveur des positions. Le
telephone garde les siennes, mais rien ne les renvoie une fois marquees
`SYNCED` : une base perdue est une base perdue.

`madhi-backup.sh` produit un `pg_dump` compresse et date, verifie qu'il est
relisible, puis fait tourner les anciennes copies.

## Installation sur le VPS

Le nom du repertoire du depot varie d'une machine a l'autre, donc l'unite
livree porte le marqueur `/CHEMIN/DU/DEPOT`. Le remplacer depuis la racine du
depot, ou `$PWD` donne le bon chemin :

    sudo cp tools/backup/madhi-backup.service /etc/systemd/system/
    sudo cp tools/backup/madhi-backup.timer   /etc/systemd/system/
    sudo sed -i "s|/CHEMIN/DU/DEPOT|$PWD|" /etc/systemd/system/madhi-backup.service
    sudo systemctl daemon-reload
    sudo systemctl enable --now madhi-backup.timer

Verifier que le chemin est bon avant d'aller plus loin :

    systemctl cat madhi-backup.service | grep ExecStart
    ls -l "$PWD/tools/backup/madhi-backup.sh"

Puis lancer une premiere sauvegarde a la main :

    sudo systemctl start madhi-backup.service
    sudo journalctl -u madhi-backup.service --no-pager -n 30
    ls -l /var/backups/madhi
    systemctl list-timers | grep madhi-backup

Le `sudo` devant `journalctl` n'est pas decoratif : sans appartenance au groupe
`adm` ou `systemd-journal`, le journal repond « No entries » au lieu d'afficher
l'erreur.

## Quand le service echoue

    sudo systemctl status madhi-backup.service --no-pager
    sudo journalctl -xeu madhi-backup.service

| Ce qui s'affiche | Cause |
|---|---|
| `status=203/EXEC` | `ExecStart` pointe dans le vide — le marqueur `/CHEMIN/DU/DEPOT` n'a pas ete remplace, ou le depot a ete deplace |
| `docker-compose.yml introuvable` | le script n'est pas a sa place dans le depot, ou le depot est incomplet |
| `pg_dump n'a pas abouti` | le conteneur `postgres` ne tourne pas, ou les identifiants du `.env` ne passent plus |
| `la table locations est absente du dump` | le dump a abouti sur une base vide ou non migree — les anciennes copies sont conservees |

## Ce que le script garantit

- **Rien n'est supprime avant qu'une sauvegarde valide soit ecrite.** La
  rotation ne s'execute qu'apres coup, et n'efface jamais la copie du jour.
- **Une archive tronquee ne compte pas comme une sauvegarde.** Le script relit
  le gzip et exige d'y trouver la table `locations` ; sinon il echoue en
  laissant les anciennes copies en place.
- **La rotation trie par nom, pas par date de modification.** L'horodatage UTC
  du nom est deja chronologique, alors qu'une copie de repertoire rebat les
  `mtime`.
- **Les dumps ne sont lisibles que par leur proprietaire.** Repertoire en 0700,
  fichiers en 0600, poses par le script et non herites de l'umask appelant : un
  dump est la trace complete des deplacements d'une personne pendant un an.

Sur une installation anterieure au 20 aout 2026, resserrer les droits des
copies deja ecrites, que le script laissait en 0644 :

    sudo find /var/backups/madhi -name '*.sql.gz' -exec chmod 600 {} +
    sudo chmod 700 /var/backups/madhi
    sudo ls -l /var/backups/madhi

L'ordre compte, et le motif est confie a `find` plutot qu'au shell : une fois
le repertoire en 0700, un shell non privilegie ne peut plus le lister, donc
`sudo chmod 600 /var/backups/madhi/*.sql.gz` echoue en « no matches found »
sans avoir rien change.

Un echec fait sortir le script en code 1, donc `systemctl status` passe en
`failed`. Ce n'est plus le seul signal : `tools/monitoring/` regarde l'age de la
sauvegarde la plus recente tous les quarts d'heure et alerte au-dela de 26 h.
C'est l'age du fichier qui est juge, pas l'etat du service — un timer qu'on
aurait oublie d'activer ne laisse aucune trace d'echec, alors qu'il est
exactement aussi grave qu'un `pg_dump` en erreur.

## Verifier qu'une sauvegarde contient bien tout

Le script controle que la table `locations` est presente, pas qu'elle est
complete. Confronter le dump a la base de temps en temps :

    sudo sh -c 'gunzip -c /var/backups/madhi/*.sql.gz' \
      | awk '/^COPY public\.locations/{f=1;next} f&&/^\\\.$/{f=0} f{n++} END{print n+0" positions dans le dump"}'

    docker compose -f server/docker-compose.yml exec postgres \
      psql -U madhi -d madhi_tracker -tAc "select count(*) from locations;"

Le dump est un instantane : tant que le telephone synchronise, la base a pris
quelques points d'avance depuis. Un ecart de quelques unites est normal, un
ecart large ne l'est pas.

Le motif passe par `sh -c` sous sudo, et non directement a `sudo` : le shell
appelant developpe les motifs **avant** d'invoquer sudo, donc il le fait sans
les droits de root et bute sur le repertoire en 0700. Il repond alors « no
matches found », `awk` tourne sans entree, et le compte s'affiche a zero sans
que rien n'ait ete lu — un dump vide et un dump jamais ouvert se ressemblent
beaucoup a l'ecran.

## Restaurer

Sur une base vide, le dump se rejoue tel quel — il porte `DROP TABLE IF EXISTS`
et recree tout :

    gunzip -c /var/backups/madhi/madhi-<horodatage>.sql.gz \
      | docker compose -f server/docker-compose.yml exec -T postgres \
          psql --username=madhi --dbname=madhi_tracker

Puis verifier le decompte :

    docker compose -f server/docker-compose.yml exec postgres \
      psql -U madhi -d madhi_tracker -c \
      "select count(*), min(recorded_at), max(recorded_at) from locations;"

Les positions restaurees gardent leur identifiant d'origine. Un telephone qui
rejouerait ensuite un lot deja recu ne cree donc pas de doublon : c'est
l'idempotence par UUID du contrat `arch/13`.

## Emporter une copie hors du VPS

Le script ne fait que des copies locales. Un VPS perdu emporte ses sauvegardes
avec lui. Tant que ce n'est pas automatise, tirer une copie de temps en temps :

    rsync -avz vps:/var/backups/madhi/ ~/madhi-backups/serveur/
