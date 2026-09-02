# Site familial

Une page : où est-elle, de quand date la position, quel trajet sur la période
choisie. Toucher un point du tracé dit quand on était là, et où. Rien d'autre.
Le plan d'exécution et les raisons de chaque choix sont dans
`arch/17_plan_implementation_site_poc.md`, le cahier des charges dans
`arch/05_site_POC.md`.

**En ligne depuis le 23 août 2026** sur `https://madhi.alexeber.fr`, derrière un
segment d'URL secret et un mot de passe familial. Le lien distribué à la famille
est `https://madhi.alexeber.fr/f/<segment>/` ; la racine du domaine, et tout
chemin hors du segment, répondent 404.

Tant que `trips.started_at` est nul, le site affiche « Le voyage n'a pas encore
commencé », sans carte. C'est le comportement voulu, pas une panne : les seules
positions en base sont celles des tests, prises à la maison.

## Pas d'étape de build

Le fichier du dépôt est exactement le fichier servi. Modules ES natifs, types
par JSDoc, Leaflet versionné dans `vendor/`. Aucun `npm`, aucun bundler,
aucun CDN.

C'est un choix de réparabilité : corriger un libellé depuis un hébergement en
Norvège doit rester « éditer un fichier, recharger », pas « réinstaller une
chaîne de build ». Vérification de types optionnelle, jamais nécessaire pour
déployer :

    npx tsc --noEmit --checkJs site/*.js site/**/*.js

## Organisation

    index.html            un seul écran
    installer.html        la notice d'installation de Madhi, page autonome
    style.css             mobile d'abord
    config.js             identifiant du voyage
    app.js                état, cycle charger -> état -> rendre
    types.js              miroir des modèles serveur
    api-client.js         les appels au serveur, et eux seuls
    features/trip-state.js     les huit états, calculés à un seul endroit
    features/period.js         les périodes et leurs bornes
    features/track-picking.js  quel point du tracé un doigt vise
    components/           carte, dernière position, bulle d'un point, bandeau
    utils/time.js         formatage français, absolu et relatif
    vendor/               Leaflet 1.9.4

Une règle porte tout le reste : `rendre` lit l'état et met le DOM à jour, elle
ne déclenche jamais d'appel réseau. Toute action modifie l'état puis rappelle
`rendre`. C'est ce qui remplace le moteur de rendu d'un framework.

## Développer

Le serveur de développement tient le rôle de nginx : chemin secret, fichiers
statiques, API relative.

    python3 tools/site-dev/serve.py

Il ouvre `http://127.0.0.1:8090/f/dev/`. L'option `--scenario` fabrique chacun
des états sans toucher à une base :

    python3 tools/site-dev/serve.py --scenario hors-ligne
    python3 tools/site-dev/serve.py --scenario aucune-position
    python3 tools/site-dev/serve.py --scenario sans-adresse
    python3 tools/site-dev/serve.py --scenario panne

Contre le vrai serveur, avec le token en ligne de commande — c'est ce mode qui
prouve que le site n'a besoin d'aucun secret :

    PUBLIC_READ_TOKEN=... python3 tools/site-dev/serve.py \
      --proxy https://madhi-server.alexeber.fr

Les vérifications de logique, sans navigateur :

    node tools/site-dev/verifier.mjs

Et, au plus près de la production — vrai nginx, vraie configuration, vraie
API — la stack elle-même :

    docker compose -f server/docker-compose.yml up -d
    # puis http://127.0.0.1:8112/f/<SITE_SECRET_SEGMENT>/

## Déployer

Le site tourne dans la stack, comme le reste : un service `site` de
`server/docker-compose.yml`, une image nginx qui **monte `site/` du dépôt en
lecture seule**. Il n'y a pas de copie dans `/var/www` : le fichier du dépôt est
littéralement le fichier servi, et `git pull` suffit à mettre à jour.

Le nginx de l'hôte garde le port 443 — un seul processus peut le tenir, et le
certificat de l'API y est déjà — et ne fait plus que relayer vers le conteneur.

Le domaine `madhi.alexeber.fr` doit pointer vers le VPS avant de commencer.

1. Les deux valeurs, dans `server/.env` :

        SITE_SECRET_SEGMENT=<openssl rand -hex 16>
        PUBLIC_READ_TOKEN=<la valeur qui y est deja>

   `SITE_SECRET_SEGMENT` est **obligatoire** : sans elle, `docker compose`
   refuse de démarrer plutôt que de publier un site à une adresse devinable.

2. Le mot de passe familial, sans rien installer sur l'hôte :

        docker run --rm -it httpd:2.4-alpine htpasswd -nB famille > server/madhi.htpasswd
        chmod 644 server/madhi.htpasswd

   Le fichier est ignoré par git. `chmod 644` n'est pas une négligence : c'est
   le worker nginx, non privilégié, qui le lit à chaque requête, et un fichier
   illisible pour lui donne une erreur 500 difficile à diagnostiquer. Il ne
   contient qu'une empreinte bcrypt.

   **À créer avant le premier démarrage.** Docker remplacerait un fichier
   manquant par un répertoire vide, et nginx refuserait de démarrer.

3. Vérifier la configuration avant de toucher à quoi que ce soit :

        docker compose -f server/docker-compose.yml run --rm --no-deps site nginx -t

   Cette commande applique la substitution des variables puis valide le
   résultat, sans publier de port ni démarrer les autres services.

4. Démarrer :

        docker compose -f server/docker-compose.yml up -d
        docker compose -f server/docker-compose.yml ps
        curl -s -o /dev/null -w '%{http_code}\n' -u famille:MDP \
          http://127.0.0.1:8112/f/<segment>/          # 200 attendu

5. Le vhost de l'hôte, qui ne fait que relayer :

        sudo cp tools/nginx/madhi.alexeber.fr /etc/nginx/sites-available/
        sudo ln -s /etc/nginx/sites-available/madhi.alexeber.fr /etc/nginx/sites-enabled/
        sudo nginx -t && sudo systemctl reload nginx
        sudo certbot --nginx -d madhi.alexeber.fr

   Certbot réécrit ce fichier. À partir de là, toute modification part du
   fichier déployé, jamais du fichier versionné.

6. Vérifier que `TRIP_ID` dans `site/config.js` correspond à `INITIAL_TRIP_ID`
   de `server/.env`. S'ils divergent, le site affiche « Ce voyage est
   introuvable ».

Mise à jour ultérieure du site : `git pull`, et rien d'autre — les fichiers sont
montés, pas copiés. Une modification du gabarit nginx demande en plus
`docker compose -f server/docker-compose.yml up -d site`.

## Vérifier après déploiement

    # sans mot de passe, tout est ferme
    curl -s -o /dev/null -w '%{http_code}\n' https://madhi.alexeber.fr/f/<segment>/        # 401

    # avec, l'URL racine sert la page et non un 403 de repertoire
    curl -s -o /dev/null -w '%{http_code}\n' -u '<user>:<motdepasse>' \
      https://madhi.alexeber.fr/f/<segment>/                                              # 200

    # le lien sans slash final, tel que la famille le collera
    curl -s -o /dev/null -w '%{http_code}\n' -u '<user>:<motdepasse>' \\
      https://madhi.alexeber.fr/f/<segment>                                               # 308

    # hors du chemin secret, le domaine ne dit rien
    curl -s -o /dev/null -w '%{http_code}\n' https://madhi.alexeber.fr/                   # 404
    curl -s -o /dev/null -w '%{http_code}\n' https://madhi.alexeber.fr/_up                # 404

    # en-tetes de confidentialite
    curl -sI -u '<user>:<motdepasse>' https://madhi.alexeber.fr/f/<segment>/ \
      | grep -iE 'referrer-policy|x-robots-tag|content-security-policy'

    # le token n'a pas fuite dans les fichiers servis
    docker compose -f server/docker-compose.yml exec site \
      grep -rn "$PUBLIC_READ_TOKEN" /usr/share/nginx/site/ ; echo "code $?"

Attendu : `401` sans mot de passe, `200` avec, `404` et `403` hors du chemin, et
un `grep` qui ne trouve rien (code 1).

Dans le navigateur, onglet réseau : seules des requêtes vers
`madhi.alexeber.fr` et `tile.openstreetmap.org` doivent apparaître.

## Ce que le navigateur applique lui-même

La configuration nginx envoie une `Content-Security-Policy` qui n'autorise que
le domaine du projet et `tile.openstreetmap.org`. La règle « aucun GAFAM sur le
chemin des positions » cesse d'être une discipline d'écriture : un appel
extérieur, ou un pixel de mesure d'audience portant des coordonnées, est refusé
par le navigateur même si une ligne de code s'y essayait un jour.

Vérifiable dans la console : `connect-src` et `img-src` refusent tout hôte
extérieur, tuiles exceptées. L'adresse affichée dans la bulle d'un point ne
fait pas exception : elle est demandée au serveur du voyage, qui relaie la
question au géocodeur. Le navigateur de la famille ne parle jamais à ce
dernier — il ne le pourrait pas, la CSP le lui interdit.

Deux choix explicites, à relire avant de les changer :

- **Pas de HSTS.** Il transformerait un certificat expiré en blocage total pour
  la famille, alors que l'avertissement du navigateur reste contournable. Or
  l'expiration en plein voyage est un risque identifié.
- **Pas de `limit_req` par défaut.** Le segment secret de 128 bits garde déjà la
  demande de mot de passe. Les deux lignes à ajouter si besoin sont en tête de
  `tools/nginx/madhi.alexeber.fr`.

## Révoquer l'accès

Changer `SITE_SECRET_SEGMENT` dans `server/.env`, puis :

    docker compose -f server/docker-compose.yml up -d site

L'ancien lien répond 404 en quelques secondes. Le mot de passe familial n'a pas
besoin de changer, et inversement — c'est la raison d'avoir les deux.

## Réparations courantes

| Symptôme | Cause probable |
|---|---|
| « Ce voyage est introuvable » | `TRIP_ID` de `config.js` ≠ `INITIAL_TRIP_ID` du serveur |
| « Cet accès n'est plus valide » | `PUBLIC_READ_TOKEN` absent ou faux dans `server/.env` |
| « Le serveur ne répond pas » | conteneur `api` arrêté ; `docker compose ps` |
| 404 sur le lien familial | `SITE_SECRET_SEGMENT` ne correspond plus au lien distribué |
| Le conteneur `site` ne démarre pas | `server/madhi.htpasswd` manquant : Docker l'a créé en répertoire |
| 500 à la demande de mot de passe | `madhi.htpasswd` illisible par le worker nginx ; `chmod 644` |
| Page d'accueil nginx au lieu du site | le gabarit n'a pas remplacé `conf.d/default.conf` |
| Carte grise, reste affiché | tuiles injoignables ; la dernière position reste juste |
| « Adresse non configurée. » dans la bulle | `REVERSE_GEOCODE_ENABLED` est à `false`, l'état par défaut ; voir `SERVER_DEPLOYMENT.md` |
| « Le voyage n'a pas encore commencé » | `trips.started_at` est nul, voir `SERVER_DEPLOYMENT.md` |

## Hors périmètre

Calendrier, distances et statistiques, étapes quotidiennes, état de batterie,
vue publique retardée, masquage de zones, pagination de l'historique complet,
polylines simplifiées, cache ou CDN. Tout cela est `arch/06_site_V2.md`.
