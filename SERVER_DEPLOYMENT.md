# Deploiement serveur POC

## Etat

Le serveur accessible publiquement est disponible ici :

    https://madhi-server.alexeber.fr

L'API Android utilise cette base :

    https://madhi-server.alexeber.fr/api/v1

Le serveur POC tourne avec Docker Compose :

    docker compose -f server/docker-compose.yml up -d

L'API est publiee localement sur :

    http://127.0.0.1:8111

Nginx sert de reverse proxy HTTPS entre `madhi-server.alexeber.fr` et
`127.0.0.1:8111`.

Le serveur de simulation `madhi-mock-server.service` doit rester arrete et
desactive.

## Verification

Verifier que les conteneurs tournent :

    docker compose -f server/docker-compose.yml ps

Verifier l'API locale :

    curl http://127.0.0.1:8111/health

Verifier l'API publique :

    curl https://madhi-server.alexeber.fr/health

Reponse attendue :

    {"status":"ok"}

Verifier que le domaine ne pointe plus vers le mock :

    curl https://madhi-server.alexeber.fr/_control/state

La reponse attendue est une erreur 404, car `/_control/state` n'existe que sur
le serveur de simulation.

Lire les logs :

    docker compose -f server/docker-compose.yml logs --tail=100 api

Redemarrer le serveur POC :

    docker compose -f server/docker-compose.yml up -d

## Activation

Le code d'activation est configure dans `server/.env` via :

    INITIAL_ACTIVATION_CODE

Le format attendu par le serveur POC est `XXXX-XXXX` avec quatre caracteres,
un tiret, puis quatre caracteres.

L'activation est a usage unique. Une fois consomme, il faut creer un nouveau
code dans `server/.env`, puis reseeder la base.

Ne pas versionner le code d'activation. Le traiter comme un secret temporaire.

## Donnees

PostgreSQL est lance par Docker Compose et stocke les donnees dans le volume :

    server_madhi_postgres_data

Ce volume contient les positions serveur et doit etre sauvegarde.

## Sauvegarde

Une sauvegarde quotidienne est fournie par `tools/backup/` : un `pg_dump`
compresse, verifie relisible, conserve trente jours dans `/var/backups/madhi`.
L'installation du timer systemd et la procedure de restauration sont dans
`tools/backup/README.md`.

Verifier qu'elle tourne :

    systemctl list-timers | grep madhi-backup
    sudo journalctl -u madhi-backup.service --no-pager -n 20
    ls -l /var/backups/madhi

Le `sudo` devant `journalctl` est necessaire : sans appartenance au groupe
`adm`, le journal repond « No entries » meme quand le service a echoue.

Reserve connue : les copies restent sur le VPS. Perdre la machine, c'est perdre
la base et ses sauvegardes en meme temps. Tirer une copie chez soi de temps en
temps, voir `tools/backup/README.md`.

## Le jour du depart

Toutes les positions vivent dans un seul trip, y compris celles qui ne sont pas
le voyage : la pre-validation sur le OnePlus, puis les tests T1 a T6 du Redmi,
soit une semaine et demie de points pris a la maison.

Elles ne sont pas supprimees. Le depart se marque par une date, pas par une
purge : `trips.started_at` existe depuis la premiere migration et n'est ecrit
par rien pour l'instant. Le site filtre l'historique avec `from=startedAt`.

Garder les points de test a un interet direct : ce sont eux qui documentent les
anomalies de cadence, et les jeter reviendrait a jeter les donnees qui servent a
les comprendre.

Le jour du depart, une seule commande :

    docker compose -f server/docker-compose.yml exec postgres \
      psql -U madhi -d madhi_tracker \
      -c "update trips set started_at = now() where id = '<INITIAL_TRIP_ID>';"

Verifier :

    curl -H "Authorization: Bearer <PUBLIC_READ_TOKEN>" \
      https://madhi-server.alexeber.fr/api/v1/trips/<INITIAL_TRIP_ID>/status

`startedAt` ne doit plus etre `null`.

Depuis le correctif livre avec le site, `latest_location` filtre sur
`started_at` quand il est renseigne : la « derniere position » ne peut plus etre
une position prise a la maison. Tant que `started_at` est nul, le filtre ne
s'applique pas et le site n'affiche volontairement aucune position precise.

## Site familial

Le site que regarde la famille est un second vhost nginx sur la meme machine :

    https://madhi.alexeber.fr

Les fichiers statiques vivent dans `/var/www/madhi/`, servis derriere un segment
d'URL secret et un mot de passe `auth_basic`. Le meme vhost relaie
`/f/<segment>/api/` vers `http://127.0.0.1:8111/api/v1/` en posant lui-meme
l'en-tete `Authorization`.

Consequence a retenir : **le `PUBLIC_READ_TOKEN` existe desormais a deux
endroits**, `server/.env` et la configuration nginx du site. Le changer d'un
cote sans l'autre coupe le site sans toucher a la synchronisation du telephone,
et la panne se lit comme « Cet acces n'est plus valide ».

Le certificat de ce domaine est distinct de celui de l'API, avec sa propre date
d'expiration. La procedure complete et les verifications sont dans
`site/README.md`.

## Nginx et HTTPS

Le domaine est servi en HTTPS par un certificat Let's Encrypt obtenu avec
`certbot --nginx`, et le port 80 redirige en 301 vers le 443.

Le fichier versionne :

    tools/nginx/madhi-server.alexeber.fr

n'est que la configuration d'amorcage en HTTP seul, celle qui a servi a obtenir
le premier certificat. **Certbot a depuis reecrit le fichier sur le VPS.** Le
recopier par-dessus supprimerait l'ecoute en 443 : le contrat API impose HTTPS
et l'application refuse le trafic en clair, donc la synchronisation s'arreterait.

Pour modifier la configuration, partir du fichier deploye, pas du fichier
versionne :

    sudo cat /etc/nginx/sites-available/madhi-server.alexeber.fr
    sudo nginx -t
    sudo systemctl reload nginx

`X-Real-IP` doit rester pose par le proxy : c'est le seul en-tete sur lequel le
serveur compte pour le rate limiting.

## Renouvellement du certificat

Le certificat courant expire le **17 novembre 2026**, soit pendant le voyage.
S'il n'est pas renouvele, l'application ne peut plus synchroniser : les
positions restent en attente sur le telephone au lieu d'etre perdues, mais la
panne est silencieuse cote famille.

Verifier que le renouvellement automatique est arme :

    systemctl list-timers | grep certbot
    sudo certbot renew --dry-run

Verifier la date d'expiration a distance, sans acces au VPS :

    echo | openssl s_client -connect madhi-server.alexeber.fr:443 \
      -servername madhi-server.alexeber.fr 2>/dev/null \
      | openssl x509 -noout -dates
