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

Le code actuellement configure sur le VPS est :

    233B-F3D6

L'activation est a usage unique. Une fois consomme, il faut creer un nouveau
code ou reseeder la base avec un nouveau `INITIAL_ACTIVATION_CODE`.

## Donnees

PostgreSQL est lance par Docker Compose et stocke les donnees dans le volume :

    server_madhi_postgres_data

Ce volume contient les positions serveur et doit etre sauvegarde.

## Nginx

La configuration versionnee est :

    tools/nginx/madhi-server.alexeber.fr

Appliquer la configuration sur le VPS :

    sudo cp tools/nginx/madhi-server.alexeber.fr /etc/nginx/sites-available/madhi-server.alexeber.fr
    sudo nginx -t
    sudo systemctl reload nginx
