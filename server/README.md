# Serveur POC Madhi Tracker

Serveur FastAPI + PostgreSQL pour recevoir les positions Android, les stocker
sans doublon et exposer la derniere position et l'historique au futur site.

## Lancer en local

```bash
docker compose -f server/docker-compose.yml up --build
```

L'API ecoute `http://localhost:8080/api/v1`. Le code d'activation par defaut
est `XXXX-XXXX` en developpement uniquement.

Le volume Docker `madhi_postgres_data` contient les donnees PostgreSQL. C'est le
volume a sauvegarder en production.

Le journal d'acces ecarte les appels a `/health` : Docker sonde cet endpoint
toutes les dix secondes, soit environ trois millions de lignes sur l'annee du
voyage, dans lesquelles les evenements reels seraient introuvables.

## Migrations

Les migrations sont separees du demarrage API :

```bash
docker compose -f server/docker-compose.yml run --rm migrate
```

Le `docker-compose.yml` lance aussi le service `migrate` avant `api` pour qu'une
machine vierge puisse demarrer en une commande. L'API elle-meme n'execute jamais
les migrations.

## Tests d'integration

```bash
docker compose -f server/docker-compose.yml --profile test run --rm test
```

Ces tests verifient l'import de 10 000 points, le rejeu sans doublon,
`latest-location`, le filtre d'historique et le `413` pour lot trop gros.

## Production

Definir `APP_ENV=production` et fournir de vraies valeurs pour :

- `DATABASE_URL`
- `DEVICE_TOKEN_HASH_SECRET`
- `ACTIVATION_CODE_HASH_SECRET`
- `INITIAL_TRIP_ID`
- `INITIAL_ACTIVATION_CODE`
- `PUBLIC_READ_TOKEN`

Le serveur refuse de demarrer si un secret de production manque, garde une
valeur d'exemple ou est trop court.

L'API est prevue pour etre derriere un reverse proxy HTTPS. Elle identifie le
client par `X-Real-IP` pour le rate limiting, et par lui seul : `Forwarded` et
`X-Forwarded-For` sont fabricables par le client et permettraient de changer de
compartiment a chaque requete. Le proxy doit donc poser `X-Real-IP`, sans quoi
tout le trafic tombe dans le meme compartiment. L'API ne construit pas d'URL
absolue depuis l'en-tete `Host`.
