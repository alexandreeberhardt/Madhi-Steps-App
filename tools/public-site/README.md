# Site public : travel.alexeber.fr

Le site familial reste sur son domaine et sa stack existants. Le site public
partage la carte, Leaflet et les styles, mais lit uniquement des fichiers JSON.
L'application Android et les routes API familiales ne changent pas.

## Confidentialité

Le délai est fixe : **168 heures**, calculées en UTC avec l'horloge PostgreSQL.
Il porte sur `recorded_at`, la date d'enregistrement fournie par l'application.
Un ancien point reçu tardivement devient donc publiable dès l'export suivant.
Les changements d'heure ne modifient pas cette durée.

Le rôle `madhi_public_export` lit uniquement deux vues du schéma `publication`,
restreintes au voyage configuré dans `INITIAL_TRIP_ID` :

- `trip` masque les dates de départ et de fin encore trop récentes ;
- `locations` exclut les points antérieurs au départ, postérieurs à la fin,
  plus récents que la limite, et les points de précision supérieure à 165 m,
  comme le tracé familial. Un départ nul interdit toute position publique.

Ces vues ne contiennent ni téléphone, ni batterie, ni date de réception.
Le rôle n'a aucun droit de lecture sur les tables privées ni d'écriture.
L'exporteur applique à nouveau les bornes et construit chaque objet à partir
d’une liste explicite de champs. Les identifiants publics sont dérivés des
coordonnées et de l'horodatage, pas des identifiants internes.

Le conteneur nginx public possède son propre réseau Docker. Il ne monte que
les ressources nécessaires au site et les exports terminés en lecture seule.
Il n'a ni secret ni relais vers l'API familiale. Sa liste de routes autorisées
exclut notamment les diagnostics, l'installation Android et la configuration
familiale. Les deux stacks peuvent être démarrées et arrêtées indépendamment.

## Fichiers publiés

`server/public-data/` est ignoré par Git. L'exporteur écrit d'abord un
répertoire temporaire hors du répertoire servi, puis le déplace vers
`live/versions/<version>/`. Il remplace enfin `live/manifest.json` atomiquement.
Le navigateur garde la même version pour toutes les requêtes d'un chargement.

Une version contient :

- `snapshot.json` : dates de génération et de coupure, voyage, dernière
  position autorisée, compte de points et liste des jours disponibles ;
- `overview.json` : tracé de fond, au plus 1 500 points ;
- `track.json` : voyage complet, au plus 10 000 points, répartis sur sa durée ;
- `days/YYYY-MM-DD.json` : points de chaque journée UTC, sans troncature.

L'interface reprend les libellés du site familial, sans mention visible du délai.
Le pays vient du dernier point autorisé. La distance totale est estimée depuis
le tracé complet (jusqu'à 10 000 points), comme sur le site familial. La distance
du jour utilise les points entre minuit local à la date de coupure et cette
coupure. Le numéro du jour se termine à la coupure ou à la fin du voyage déjà
publiable, jamais à l'heure réelle du visiteur.

« Aujourd'hui » désigne donc la journée à la date de coupure. Les périodes de
24 h, 7 jours et 30 jours se terminent également à cette date.
Seuls les fichiers quotidiens correspondants sont téléchargés. Le navigateur
limite le tracé à 10 000 points en conservant les extrémités et le signale.
Les horodatages sont affichés dans le fuseau du visiteur avec leur vraie date.

Si le géocodage est configuré, seul le dernier point publié est géocodé lors
de l'export, avec réutilisation du résultat précédent si ce point est inchangé.
Les autres bulles affichent leurs coordonnées. Aucune requête du navigateur
public ne part vers l'API familiale ou le géocodeur ; seules les tuiles
OpenStreetMap sont externes.

Les anciennes versions sont conservées deux jours pour les onglets ouverts,
puis supprimées après un export réussi. Une panne conserve la dernière
publication complète. Le navigateur réessaie toutes les cinq minutes lorsqu'il
est visible et signale un export de plus de trois heures.

## Première installation

Depuis la racine du dépôt, avec la stack familiale déjà en fonctionnement :

```sh
python3 tools/public-site/provision.py

docker compose --env-file server/.env --env-file server/public-export.env \
  -f server/compose.public.yml build exporter

docker compose --env-file server/.env --env-file server/public-export.env \
  -f server/compose.public.yml run --rm --no-deps exporter

docker compose --env-file server/.env --env-file server/public-export.env \
  -f server/compose.public.yml up -d --no-deps site

python3 tools/public-site/verify.py
```

Le provisionnement utilise la connexion propriétaire déjà configurée dans le
conteneur API, sans l'afficher. Il crée les vues et un mot de passe aléatoire
pour le rôle restreint. Le fichier `server/public-export.env`, en mode `0600`,
contient ce mot de passe ainsi que l'UID/GID du propriétaire des exports.
**Ne pas lancer ce provisionnement avec sudo.** Le relancer renouvelle le mot
de passe du rôle ; les prochains exports liront le nouveau fichier.

Le réseau source par défaut est `server_default`. Pour une stack familiale
avec un autre nom de projet, définir `MADHI_SOURCE_NETWORK` dans
`server/public-export.env`. Le site public écoute uniquement `127.0.0.1:8114`.

Après ces validations, installer l'intégration sur l'hôte :

```sh
sudo ./tools/public-site/install-host.sh
```

Cette commande installe le vhost HTTP si absent, valide et recharge nginx,
obtient le certificat de `travel.alexeber.fr` avec le compte Certbot existant,
active la redirection HTTPS, puis installe et active :

- `madhi-public-export.timer` : export à chaque heure UTC ou locale selon
  l'horloge systemd ; le calcul du délai reste toujours en UTC ;
- `madhi-public-check.timer` : contrôle de fraîcheur et d'accès HTTPS toutes
  les quinze minutes. Un contrôle rouge laisse son service en état `failed`
  avec un journal explicite ; aucun message externe n'est envoyé.

Le script conserve un vhost déjà installé, notamment ses ajouts TLS.
Le DNS A de `travel.alexeber.fr` doit pointer vers le VPS avant son exécution.

## Vérifier et mettre à jour

```sh
python3 tools/public-site/verify.py https://travel.alexeber.fr
systemctl status madhi-public-export.timer madhi-public-check.timer
journalctl -u madhi-public-export.service -u madhi-public-check.service --since today
```

Les fichiers frontend sont montés depuis le dépôt : une modification apparaît
au rechargement. Après une modification Python, reconstruire `exporter` puis
lancer un export manuel. Après une modification de la configuration nginx :

```sh
docker compose --env-file server/.env --env-file server/public-export.env \
  -f server/compose.public.yml exec site nginx -t

docker compose --env-file server/.env --env-file server/public-export.env \
  -f server/compose.public.yml restart site
```

Une modification des montages ou du réseau demande `up -d --no-deps site`.
Une modification des unités systemd demande de relancer `install-host.sh`.
Ne jamais copier le vhost HTTP du dépôt par-dessus le vhost HTTPS déployé.

## Suspendre, fermer et revenir en arrière

Suspendre les prochains exports en conservant le dernier trajet publié :

```sh
sudo systemctl disable --now madhi-public-export.timer
sudo systemctl stop madhi-public-export.service
```

Pour reprendre : `sudo systemctl enable --now madhi-public-export.timer`, puis
`sudo systemctl start madhi-public-export.service`.

Fermer aussi le site public :

```sh
sudo systemctl disable --now madhi-public-check.timer

docker compose --env-file server/.env --env-file server/public-export.env \
  -f server/compose.public.yml stop site
```

Le domaine répond alors avec une erreur de passerelle, sans trajet. Les
services familiaux restent disponibles. Les copies déjà téléchargées par des
visiteurs ne peuvent pas être révoquées.

Pour revenir au code précédent, rétablir les commits concernés, reconstruire
l'exporteur et régénérer les fichiers avant de redémarrer le site. Les exports
sont dérivés de PostgreSQL : ce n'est pas une base à sauvegarder séparément.
Ne pas supprimer les exports courants avant d'avoir produit leur remplacement.

## Tests

```sh
node tools/site-dev/verifier.mjs
node tools/public-site/test-data.mjs
UV_CACHE_DIR=$PWD/.cache/uv uv run --project server \
  --with-requirements server/requirements-dev.txt pytest \
  server/tests/test_public_export.py
```

`test_public_access.py` attend `PUBLIC_EXPORT_TEST_DATABASE_URL` vers une base
**jetable**, jamais la base du voyage. Il crée ses tables, ses vues et son rôle
dans une transaction finalement annulée, vérifie les bornes et essaie de lire
et modifier les tables privées sous le rôle public.

`verify.py` télécharge chaque fichier de la version courante, contrôle ses
champs et ses dates, puis vérifie le refus des chemins privés.

`test-browser.mjs` utilise Playwright disponible dans l'environnement de test.
On peut fournir le chemin de son module avec `PLAYWRIGHT_MODULE` et celui des
navigateurs avec `PLAYWRIGHT_BROWSERS_PATH`. `PUBLIC_SITE_URL` permet de tester
HTTPS au lieu du port local. Les captures sont écrites dans `/tmp`, hors Git.
