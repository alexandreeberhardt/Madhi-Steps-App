# Site familial

Une page : où est-elle, de quand date la position, quel trajet sur la période
choisie. Rien d'autre. Le plan d'exécution et les raisons de chaque choix sont
dans `arch/17_plan_implementation_site_poc.md`, le cahier des charges dans
`arch/05_site_POC.md`.

Le site est servi sur `https://madhi.alexeber.fr`, derrière un segment d'URL
secret et un mot de passe familial.

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
    style.css             mobile d'abord
    config.js             identifiant du voyage
    app.js                état, cycle charger -> état -> rendre
    types.js              miroir des modèles serveur
    api-client.js         les trois appels de lecture, et eux seuls
    features/trip-state.js   les huit états, calculés à un seul endroit
    features/period.js       les périodes et leurs bornes
    components/           carte, dernière position, bandeau d'état
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
    python3 tools/site-dev/serve.py --scenario panne

Contre le vrai serveur, avec le token en ligne de commande — c'est ce mode qui
prouve que le site n'a besoin d'aucun secret :

    PUBLIC_READ_TOKEN=... python3 tools/site-dev/serve.py \
      --proxy https://madhi-server.alexeber.fr

Les vérifications de logique, sans navigateur :

    node tools/site-dev/verifier.mjs

## Déployer

Le domaine `madhi.alexeber.fr` doit pointer vers le VPS avant de commencer.

1. Copier les fichiers :

        rsync -av --delete site/ <utilisateur>@<vps>:/tmp/madhi-site/
        sudo rsync -av --delete /tmp/madhi-site/ /var/www/madhi/
        sudo chown -R www-data:www-data /var/www/madhi

2. Fabriquer le segment secret et le mot de passe familial :

        openssl rand -hex 16
        sudo htpasswd -c /etc/nginx/madhi.htpasswd famille

3. Installer la configuration nginx en remplaçant les deux marqueurs
   `<SEGMENT_SECRET>` et `<PUBLIC_READ_TOKEN>` :

        sudo cp tools/nginx/madhi.alexeber.fr /etc/nginx/sites-available/
        sudo nano /etc/nginx/sites-available/madhi.alexeber.fr
        sudo ln -s /etc/nginx/sites-available/madhi.alexeber.fr /etc/nginx/sites-enabled/
        sudo nginx -t && sudo systemctl reload nginx

4. Obtenir le certificat :

        sudo certbot --nginx -d madhi.alexeber.fr

   Certbot réécrit alors le fichier sur le VPS. **Toute modification
   ultérieure part du fichier déployé, jamais du fichier versionné** — le
   recopier supprimerait HTTPS.

5. Vérifier que `TRIP_ID` de `config.js` correspond à `INITIAL_TRIP_ID` de
   `server/.env`. S'ils divergent, le site affiche « Ce voyage est
   introuvable ».

Pour une mise à jour ultérieure, seule l'étape 1 est à refaire.

## Vérifier après déploiement

    # en-tetes de confidentialite, avec le mot de passe
    curl -sI -u '<user>:<motdepasse>' https://madhi.alexeber.fr/f/<segment>/ \
      | grep -iE 'referrer-policy|x-robots-tag'

    # l'URL racine sert la page, et non un 403 de repertoire
    curl -s -o /dev/null -w '%{http_code}\n' \
      -u '<user>:<motdepasse>' https://madhi.alexeber.fr/f/<segment>/

    # sans mot de passe, tout est ferme
    curl -s -o /dev/null -w '%{http_code}\n' https://madhi.alexeber.fr/f/<segment>/

    # le token n'a pas fuite dans les fichiers servis
    grep -rn "$PUBLIC_READ_TOKEN" /var/www/madhi/ ; echo "code $?"

Attendu : `200` avec mot de passe, `401` sans, et un `grep` qui ne trouve rien
(code 1).

Dans le navigateur, onglet réseau : seules des requêtes vers
`madhi.alexeber.fr` et `tile.openstreetmap.org` doivent apparaître.

## Ce que le navigateur applique lui-même

La configuration nginx envoie une `Content-Security-Policy` qui n'autorise que
le domaine du projet et `tile.openstreetmap.org`. La règle « aucun GAFAM sur le
chemin des positions » cesse d'être une discipline d'écriture : un appel
extérieur, ou un pixel de mesure d'audience portant des coordonnées, est refusé
par le navigateur même si une ligne de code s'y essayait un jour.

Vérifiable dans la console : `connect-src` et `img-src` refusent tout hôte
extérieur, tuiles exceptées.

Deux choix explicites, à relire avant de les changer :

- **Pas de HSTS.** Il transformerait un certificat expiré en blocage total pour
  la famille, alors que l'avertissement du navigateur reste contournable. Or
  l'expiration en plein voyage est un risque identifié.
- **Pas de `limit_req` par défaut.** Le segment secret de 128 bits garde déjà la
  demande de mot de passe. Les deux lignes à ajouter si besoin sont en tête de
  `tools/nginx/madhi.alexeber.fr`.

## Révoquer l'accès

Changer `<SEGMENT_SECRET>` dans la configuration nginx et recharger : l'ancien
lien répond 404. Le mot de passe familial n'a pas besoin de changer, et
inversement — c'est la raison d'avoir les deux.

## Réparations courantes

| Symptôme | Cause probable |
|---|---|
| « Ce voyage est introuvable » | `TRIP_ID` de `config.js` ≠ `INITIAL_TRIP_ID` du serveur |
| « Cet accès n'est plus valide » | token absent ou faux dans `proxy_set_header Authorization` |
| 403 sur l'URL du lien | directive `index index.html;` absente du bloc `location` |
| Carte grise, reste affiché | tuiles injoignables ; la dernière position reste juste |
| « Le voyage n'a pas encore commencé » | `trips.started_at` est nul, voir `SERVER_DEPLOYMENT.md` |

## Hors périmètre

Calendrier, distances et statistiques, étapes quotidiennes, état de batterie,
vue publique retardée, masquage de zones, pagination de l'historique complet,
polylines simplifiées, cache ou CDN. Tout cela est `arch/06_site_V2.md`.
