# Surveillance minimale

Les cinq points de `arch/07` §6, controles tous les quarts d'heure, avec une
alerte qui part de la machine : sante de l'API, disponibilite du site familial,
espace disque, derniere sauvegarde reussie, derniere position recue.

Ce qui existait avant : rien. Le script de sauvegarde pouvait sortir en erreur
trente jours d'affilee sans que personne le sache, parce que son seul signal
etait un `systemctl status` que personne n'allait lire. Pendant un an
d'absence, personne ne va rien lire.

## Ce qui declenche une alerte

| Sonde | Rouge quand |
|---|---|
| `api` | `https://madhi-server.alexeber.fr/health` ne repond pas `{"status":"ok"}` |
| `site` | le lien familial ne repond ni 401 ni 200 |
| `disque` | un systeme de fichiers depasse 85 % |
| `sauvegarde` | la sauvegarde la plus recente a plus de 26 h |
| `position` | aucune position recue depuis plus de 24 h |

Trois details expliquent les choix :

**L'API est sondee de l'exterieur, en HTTPS**, et non sur `127.0.0.1:8111`.
C'est le chemin qu'emprunte le telephone. Un certificat expire ou un nginx muet
sont des pannes de synchronisation a part entiere, meme quand le conteneur se
porte tres bien.

**Le site est declare vivant sur un 401.** Le defi d'authentification prouve que
le chemin secret a ete reconnu par le conteneur, donc que TLS, le nginx de
l'hote, le conteneur et le segment sont tous en place — et il le prouve sans
manipuler le mot de passe familial. Un 404 signifie que le segment ne correspond
plus, un 502 que le conteneur ne repond pas.

**La sauvegarde est jugee sur l'age du fichier**, jamais sur l'etat du service.
Un timer qu'on aurait oublie d'activer ne laisse aucune trace d'echec, alors
qu'il est exactement aussi grave qu'un `pg_dump` en erreur. Le fichier, lui, ne
ment pas.

## Aucune coordonnee ne sort du VPS

La sonde de position interroge `/api/v1/trips/<id>/status`, qui renvoie des
compteurs et des horodatages, et jamais `/latest-location`, qui renvoie une
position. Une alerte dit « aucune position depuis 31h20 », jamais ou. C'est la
regle de `arch/08` §5, tenue par construction plutot que par discipline.

Le token de lecture n'apparait pas non plus dans `ps` : il est passe a curl par
son fichier de configuration, sur l'entree standard, et non sur la ligne de
commande.

## Le bruit, et pourquoi il n'y en a pas

Une sonde qui alerte a chaque passage n'est plus lue au bout d'une semaine. Le
script tient un etat par sonde dans `/var/lib/madhi-monitoring` et n'ecrit que
sur les transitions :

- au basculement en panne, une fois ;
- une repetition par jour tant que la panne dure ;
- au retablissement, une fois — sans quoi on ne saurait jamais qu'il a eu lieu.

Le journal, lui, recoit une ligne par sonde a chaque passage :

    sudo journalctl -u madhi-check.service --no-pager -n 20

## Installation sur le VPS

Comme pour la sauvegarde, l'unite livree porte le marqueur `/CHEMIN/DU/DEPOT`,
a remplacer depuis la racine du depot :

    sudo mkdir -p /etc/madhi
    sudo cp tools/monitoring/monitoring.env.example /etc/madhi/monitoring.env
    sudo chmod 600 /etc/madhi/monitoring.env
    sudo nano /etc/madhi/monitoring.env      # y mettre la vraie adresse d'alerte

    sudo cp tools/monitoring/madhi-check.service /etc/systemd/system/
    sudo cp tools/monitoring/madhi-check.timer   /etc/systemd/system/
    sudo sed -i "s|/CHEMIN/DU/DEPOT|$PWD|" /etc/systemd/system/madhi-check.service
    sudo systemctl daemon-reload

**Prouver que l'alerte arrive avant de croire qu'on est surveille.** Une chaine
de surveillance dont le dernier maillon ne fonctionne pas est un placebo :

    sudo tools/monitoring/madhi-check.sh --test-alert

Le message doit arriver sur le telephone. Sinon, rien de ce qui suit ne sert.

Puis un passage complet a blanc, qui n'ecrit ni etat ni alerte :

    sudo tools/monitoring/madhi-check.sh --dry-run

Et enfin l'armement :

    sudo systemctl enable --now madhi-check.timer
    systemctl list-timers | grep madhi-check

## Verifier que ca surveille vraiment

Provoquer une panne pour de bon, et regarder si le telephone sonne :

    sudo systemctl stop docker
    sudo systemctl start madhi-check.service      # doit alerter, et sortir en 1
    sudo systemctl start docker
    sudo systemctl start madhi-check.service      # doit annoncer le retablissement

Le seuil de disque se verifie sans rien casser, en abaissant le seuil le temps
d'un appel :

    sudo MADHI_DISK_MAX_PERCENT=1 tools/monitoring/madhi-check.sh --dry-run

## Quand le service echoue

    sudo systemctl status madhi-check.service --no-pager
    sudo journalctl -xeu madhi-check.service

| Ce qui s'affiche | Cause |
|---|---|
| `status=203/EXEC` | `ExecStart` pointe dans le vide — marqueur `/CHEMIN/DU/DEPOT` non remplace, ou depot deplace |
| `SITE_SECRET_SEGMENT introuvable` | `server/.env` n'est pas lisible par root a cote du `docker-compose.yml`, ou la variable a disparu |
| `PUBLIC_READ_TOKEN ou INITIAL_TRIP_ID introuvable` | idem, pour la sonde de position |
| `reponse 403 sur le statut du voyage` | le token de `server/.env` n'est plus celui que l'API attend |
| `ECHEC de transmission de l'alerte` | le canal d'alerte est mort — la panne, elle, est bien detectee, mais personne ne l'apprend |

Cette derniere ligne merite qu'on s'y arrete : le script continue de
fonctionner, le journal dit tout, et pourtant la surveillance ne sert plus a
rien. Elle est la raison d'etre de `--test-alert`.

## Ce que cette surveillance ne couvre pas

**Un VPS eteint n'alerte pas.** Le script tourne sur la machine qu'il surveille :
si elle s'arrete, elle se tait, et le silence ressemble beaucoup a « tout va
bien ». C'est le trou principal, et `MADHI_HEARTBEAT_URL` est la pour le
combler : le script pingue cette URL quand les cinq sondes sont vertes, et une
sonde externe previent quand le battement s'arrete. Sans elle, la surveillance
suppose un VPS vivant.

**L'expiration des certificats n'est vue qu'une fois survenue.** Le jour ou le
certificat de l'API expire, la sonde `api` passe au rouge — mais c'est le jour
meme, pas quinze jours avant. Les deux dates et la procedure de renouvellement
sont dans `SERVER_DEPLOYMENT.md`.

**Les sauvegardes restent sur le VPS.** Les surveiller ne les met pas a l'abri
de la perte de la machine. Voir `tools/backup/README.md`.

## Essayer le script sans VPS

Il fonctionne aussi sur le Mac de developpement — la conversion d'horodatage
gere `date` GNU comme BSD. Avec la stack locale demarree :

    MADHI_MONITORING_ENV=/dev/null \
    MADHI_API_URL=http://127.0.0.1:8111 \
    MADHI_SITE_URL=http://127.0.0.1:8112 \
    MADHI_BACKUP_DIR=/tmp/madhi-backups \
    MADHI_STATE_DIR=/tmp/madhi-etat \
      tools/monitoring/madhi-check.sh --dry-run

Les sondes `sauvegarde` et `disque` parleront du Mac, pas du serveur. Les trois
autres disent la verite.
