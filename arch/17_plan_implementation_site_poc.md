**17 — Plan d'implémentation du site familial POC**

*De quoi construire le site sans avoir à redécouvrir les contraintes du serveur*

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# 1. Statut de ce document

`arch/05_site_POC.md` dit **ce que** le site doit faire et fait foi. Ce
document dit **comment**, et complète `arch/05` sur trois points qui n'étaient
pas connus quand il a été écrit : le serveur existe désormais, son API impose
des contraintes précises, et deux de ses comportements sont des pièges.

En cas de divergence, `arch/05` et `arch/00` font foi.

Ce document est un plan d'exécution complet : il doit permettre à quelqu'un qui
n'a pas suivi les sessions précédentes de livrer le POC sans arbitrage
supplémentaire.

**Le site a été construit selon ce plan** et vit dans `site/`, avec sa
procédure de déploiement dans `site/README.md`. Le plan reste la référence des
raisons ; il n'a pas été réécrit après coup. Trois écarts assumés :

- `site/config.js` s'ajoute à l'arborescence du §5 pour porter l'identifiant du
  voyage, qui n'est pas un secret.
- Le bloc « dernière position » se tait lorsqu'il n'y a rien à montrer, le
  message qui occupe la place de la carte le disant déjà.
- **Le §9 place la configuration nginx sur l'hôte, avec les fichiers dans
  `/var/www/madhi`. L'implémentation la met dans la stack** : un service `site`
  de `server/docker-compose.yml` monte `site/` en lecture seule et pose le
  token. Les responsabilités et les pièges décrits au §9 sont inchangés — la
  directive `index`, `X-Real-IP`, la non-héritance de `add_header` — mais ils
  s'appliquent désormais dans `tools/nginx/site.conf.template`. L'hôte ne garde
  qu'un relais, parce qu'un seul processus peut écouter le port 443 et que le
  certificat de l'API y est déjà. Conséquence heureuse : le token de lecture ne
  vit plus qu'à un seul endroit, `server/.env`.

# 2. Décisions techniques et leurs raisons

## 2.1 JavaScript en modules ES natifs, sans étape de build

Le site est un ensemble de fichiers statiques servis par nginx. Le fichier du
dépôt est exactement le fichier servi.

*Pourquoi.* Le site doit survivre un an pendant que la personne qui le
maintient est sur la route. Une chaîne `npm` est une dépendance vivante :
versions de Node qui bougent, avis de sécurité, `npm ci` qui casse au mauvais
moment. Corriger un libellé depuis un hébergement en Norvège doit rester
« éditer un fichier, recharger », pas « réinstaller une chaîne de build ». Le
README fixe le principe : réparable par une seule personne.

*Ce qu'on renonce à avoir.* Pas de JSX, pas de minification, pas de hash dans
les noms de fichiers. Les mises à jour du DOM s'écrivent à la main.

*Pourquoi c'est tenable.* L'état du site tient en cinq valeurs (période,
trip, points, statut de chargement, erreur). Un rendu redéclenché à chaque
changement d'état suffit ; les huit états du §6 se traitent mieux en `switch`
explicite qu'en conditions de rendu dispersées.

*Pourquoi pas un fichier unique.* `arch/05` §10 et `arch/06` §6 demandent un
`MapProvider` abstrait, un sélecteur de période indépendant de la carte, et des
composants réutilisés en V2. Ce sont des frontières de modules. Les modules ES
les donnent sans bundler.

## 2.2 Types par JSDoc, pas TypeScript

`arch/05` §5 demande que les types `LocationPointV1` vivent dans un fichier
unique. TypeScript compilé réintroduirait l'étape de build que §2.1 supprime.

JSDoc donne la complétion et la vérification dans l'éditeur. Pour une
vérification en dur : `npx tsc --noEmit --checkJs`. C'est un outil de
développement optionnel, jamais un maillon du déploiement — le site fonctionne
le jour où `tsc` ne s'installe pas.

## 2.3 Le token de lecture n'entre jamais dans le navigateur

`arch/05` §8 et `arch/06` §7 interdisent tout secret dans le bundle. Or l'API
exige `Authorization: Bearer <PUBLIC_READ_TOKEN>` sur les trois endpoints de
lecture.

Le frontend appelle donc un chemin **relatif** du même domaine, et nginx pose
l'en-tête en le relayant vers l'API. Le token vit dans la configuration nginx
du VPS, comme les autres secrets du projet.

## 2.4 Accès : lien secret révocable **et** mot de passe familial

`arch/05` §7 rend le mot de passe souhaitable dès lors que le lien donne accès
à la position précise, ce qui est le cas. Un lien seul ne survit pas à une
capture d'écran dans un groupe familial.

Le lien secret reste utile : il permet de révoquer sans changer le mot de
passe, et il prépare `arch/06` §3, où vue famille et vue publique retardée
deviennent deux `location` nginx partageant les mêmes fichiers statiques.

# 3. Contrat API, tel qu'implémenté

Vérifié dans `server/app/main.py` et `server/app/db.py`. Les trois endpoints de
lecture exigent tous `Authorization: Bearer <PUBLIC_READ_TOKEN>`, sans quoi ils
répondent `403 {"error": "forbidden"}`.

## 3.1 `GET /api/v1/trips/{tripId}/status`

    {
      "tripId": "8f14e45f-...",
      "name": "Madhi 2026",
      "startedAt": null,
      "endedAt": null,
      "totalLocations": 288,
      "latestRecordedAt": "2026-08-20T08:40:00Z",
      "latestReceivedAt": "2026-08-20T08:40:03Z"
    }

`404 {"error": "unknown_trip"}` si le trip est inconnu. C'est l'appel à faire
**en premier** : il donne `startedAt`, dont dépend tout le reste.

## 3.2 `GET /api/v1/trips/{tripId}/latest-location`

Renvoie un `LocationResponse`, ou **`null` avec un code `200`** quand le trip
n'a aucune position. Ce n'est pas une erreur : c'est l'état « aucune position
reçue » de `arch/05` §9. Un client qui suppose un objet plantera ici.

## 3.3 `GET /api/v1/trips/{tripId}/locations`

Paramètres : `from`, `to`, `limit`.

- `from` et `to` sont des ISO-8601 UTC **avec suffixe `Z` obligatoire**. Le
  serveur rejette tout le reste par `400 {"error": "invalid_time_range"}` —
  `parse_recorded_at` exige littéralement que la chaîne se termine par `Z`.
  `Date.prototype.toISOString()` produit le bon format.
- `limit` doit être entre 1 et 10 000, sinon `400 {"error": "invalid_limit"}`.
  Le défaut est 10 000.
- Le tri est `recorded_at asc`.

## 3.4 Forme d'un point

Recopiée de `LocationResponse` dans `server/app/models.py` :

    id, deviceId : string
    latitude, longitude : number
    recordedAt, receivedAt : string ISO-8601 UTC suffixe Z
    accuracyMeters, altitudeMeters, speedMps : number | null
    batteryPercent : number | null, 0 à 100

`recordedAt` est l'instant de la capture par le téléphone, `receivedAt` celui
de l'arrivée au serveur. **Les deux sont nécessaires** : l'écart entre eux est
la seule façon de distinguer « le téléphone n'a rien enregistré » de « le
téléphone enregistre mais ne parvient pas à envoyer », deux pannes que la
famille ne doit pas confondre.

# 4. Trois pièges à traiter explicitement

Ce sont les points sur lesquels une implémentation naïve échoue en silence.

## 4.1 `limit` tronque les points les **plus récents**

`location_history` ordonne par `recorded_at asc` **puis** applique `limit`. Une
requête sur tout le voyage avec `limit=10000` renvoie donc les 10 000 points
les **plus anciens**, et coupe tout ce qui suit — sans erreur.

Conséquence : au bout de 35 jours de voyage, un site qui demande naïvement tout
l'historique afficherait une « dernière position » figée au 35ᵉ jour, avec un
statut vert. C'est exactement la panne muette que le projet cherche à éliminer.

À 288 points par jour, une année vaut environ 105 000 points, soit dix fois le
plafond. Trois conséquences fermes :

1. **La dernière position ne vient jamais de l'historique.** Elle vient de
   `latest-location`, qui n'a pas de `limit`.
2. **Toute requête d'historique est bornée par une fenêtre de dates**, jamais
   par le seul `limit`.
3. Le filtre « tout le voyage » de `arch/05` §3 n'est **pas** un seul appel. Au
   POC, le limiter à 30 jours et afficher explicitement la période réellement
   couverte. `arch/05` §3 l'autorise (« sinon période limitée ») et `arch/06` §5
   renvoie la pagination et les polylines simplifiées à la V2. Ne pas inventer
   de pagination ici.

De plus : si une réponse contient exactement `limit` points, considérer qu'elle
est probablement tronquée et le signaler dans l'interface plutôt que de
l'ignorer.

## 4.2 `latest-location` ignore `started_at`

Défaut connu, déjà noté dans `SERVER_DEPLOYMENT.md`. La requête ne filtre pas
sur `started_at` et renvoie le point le plus récent quel qu'il soit.

Avant le départ, le trip contient les positions de pré-validation et des tests
T1 à T6, prises à la maison — elles ne sont pas supprimées, c'est une décision
figée du projet. La « dernière position » afficherait donc le domicile.

**À corriger côté serveur en même temps que le site** (`arch/05` §5) : filtrer
`latest_location` sur `started_at` quand il est non nul. Tant que ce n'est pas
fait, le site ne doit rien afficher de précis si `startedAt` est `null`, voir
§6.

*Fait.* Le filtre est en place et couvert par deux tests d'intégration. La
règle §6 reste appliquée : `startedAt` nul n'affiche toujours aucune position
précise, parce que le filtre ne peut alors s'appuyer sur rien.

## 4.3 Le référent fuiterait le lien secret vers le serveur de tuiles

Le lien familial contient un segment secret dans son URL. Chaque tuile
cartographique est une requête vers un tiers, qui emporterait cette URL dans
l'en-tête `Referer`. Le secret serait alors dans les journaux du fournisseur de
tuiles.

C'est la raison d'être du `Referrer-Policy: no-referrer` exigé par `arch/05`
§7. Il n'est pas décoratif et ne doit pas être affaibli en
`strict-origin-when-cross-origin`, qui enverrait encore l'origine.

# 5. Arborescence

    site/
      index.html
      style.css
      app.js                point d'entree, etat, rendu
      types.js              typedefs JSDoc, miroir des modeles serveur
      api-client.js         les trois appels, et eux seuls
      features/
        trip-state.js       machine d'etat, calcul du statut
        period.js           definition des periodes, bornes UTC
      components/
        map.js              MapProvider encapsule, marker et polyline
        latest-location.js  bloc derniere position
        status-banner.js    bandeau d'etat et messages d'erreur
      utils/
        time.js             formatage absolu et relatif, en francais
      vendor/
        leaflet.js
        leaflet.css
        images/             marqueurs Leaflet

Les noms de `features/` et `components/` reprennent `arch/05` §4 et
`arch/06` §6 : `Map`, `LatestLocation` et `TripTimeline` sont conservés en V2,
donc leurs frontières sont posées maintenant.

`vendor/` est **versionné**. Pas de CDN : `arch/05` §9 fait vérifier dans
l'onglet réseau que seules les requêtes vers le domaine du projet et le
fournisseur de tuiles apparaissent.

# 6. États de l'interface

`arch/06` §4 en liste six, auxquels s'ajoutent les deux que `arch/05` §9 impose
au POC : « aucune position reçue », et l'avant-départ qui découle du piège
§4.2. Huit au total. Ils sont dérivés d'un seul endroit,
`features/trip-state.js`, et jamais recalculés dans les composants.

L'ordre du tableau est celui de l'évaluation : le premier état dont la
condition est vraie l'emporte. `SERVEUR_INDISPONIBLE` passe donc avant tout le
reste, et `AVANT_DEPART` avant les états d'ancienneté.

| État | Origine | Condition | Ce que voit la famille |
|---|---|---|---|
| `SERVEUR_INDISPONIBLE` | `arch/06` §4 | échec réseau, timeout ou 5xx | Message d'erreur, et **la dernière donnée obtenue reste affichée** si elle existe, datée. |
| `AVANT_DEPART` | `arch/05` §5 | `startedAt` est `null` | « Le voyage n'a pas encore commencé. » Ni carte ni position : voir §4.2. |
| `AUCUNE_POSITION` | `arch/05` §9 | `latest-location` renvoie `null` | « Aucune position reçue pour l'instant. » Pas de carte centrée au hasard. |
| `VOYAGE_TERMINE` | `arch/06` §4 | `endedAt` non nul | Trajet complet, sans notion d'actualité. |
| `RECENT` | `arch/06` §4 | dernier `recordedAt` < 1 h | Position, carte, horodatage. État nominal. |
| `ANCIEN` | `arch/06` §4 | dernier `recordedAt` entre 1 h et 12 h | Position affichée, avec l'ancienneté mise en avant. |
| `HORS_LIGNE` | `arch/06` §4 | dernier `recordedAt` > 12 h | Position affichée comme dernière connue, ancienneté dominante. |

Un huitième état est **indépendant** des sept précédents, parce qu'il porte sur
la période sélectionnée et non sur le voyage :

| État | Origine | Condition | Ce que voit la famille |
|---|---|---|---|
| `HISTORIQUE_VIDE` | `arch/06` §4 | `getLocations` renvoie une liste vide | « Aucun déplacement enregistré sur cette période. » La dernière position reste affichée : elle vient d'ailleurs. |

Il se combine avec n'importe lequel des autres — une dernière position récente
et une semaine sans trajet est un cas normal si le téléphone vient d'être
réactivé. Le confondre avec `AUCUNE_POSITION` ferait dire au site que rien n'a
jamais été reçu.

Deux exigences de `arch/05` §9 pèsent sur ce tableau :

- **Pas de fausse notion de temps réel.** Aucun libellé « en direct », aucune
  pastille verte clignotante. Le téléphone envoie toutes les cinq minutes au
  mieux, et peut être hors réseau des jours entiers.
- **Une position ancienne ne se distingue pas d'un appareil hors ligne par la
  seule couleur.** L'ancienneté est écrite en toutes lettres.

Les seuils d'1 h et 12 h sont des constantes nommées en tête de
`trip-state.js`, pas des nombres dispersés.

# 7. Modules, un par un

## 7.1 `api-client.js`

Le seul module qui connaît des URLs. Aucun autre fichier ne fait de `fetch`.

    getTripStatus(tripId)            -> Promise<TripStatusV1>
    getLatestLocation(tripId)        -> Promise<LocationPointV1 | null>
    getLocations(tripId, from, to)   -> Promise<LocationPointV1[]>

Règles :

- Les URLs sont **relatives** : `./api/trips/...`. Jamais de domaine en dur,
  jamais de token — voir §2.3 et §9.
- `from` et `to` sont des `Date`, converties par `toISOString()`.
- Une réponse non-`ok` lève une erreur portant le code HTTP, pour que le
  bandeau distingue `403` (lien ou mot de passe périmé) de `404` (trip inconnu)
  de `5xx` (serveur en panne).
- `getLatestLocation` renvoie `null` sans erreur quand le corps est `null`.
- Un `timeout` de 10 s via `AbortController` : sans lui, un serveur injoignable
  laisse le site en chargement indéfini, ce qui se lit comme un site cassé.

## 7.2 `features/period.js`

Les périodes de `arch/05` §3, chacune produisant `{ from: Date, to: Date }` :

    AUJOURDHUI      minuit local -> maintenant
    SEPT_JOURS      maintenant - 7 j -> maintenant
    TRENTE_JOURS    maintenant - 30 j -> maintenant

`TRENTE_JOURS` est la version bornée du « tout le voyage » de `arch/05` §3, que
le plafond de 10 000 points interdit de servir en un appel (§4.1). Son libellé
doit dire ce qu'il montre — « 30 derniers jours », pas « tout le voyage » — et
l'interface affiche la période réellement couverte.

`from` est toujours borné par `startedAt` quand il existe : demander
l'historique avant le départ ramènerait les points de test.

Les trois périodes tiennent sous le plafond : 288 points par jour, donc environ
2 000 sur sept jours et 8 640 sur trente. La marge sur `TRENTE_JOURS` est
mince — si l'intervalle de capture descendait un jour sous cinq minutes, elle
disparaîtrait. C'est la raison du contrôle de troncature du §4.1.

Le sélecteur est **indépendant de la carte** (`arch/05` §10) : il produit des
bornes, il ne touche jamais à Leaflet.

## 7.3 `components/map.js`

Encapsule le fournisseur de tuiles (`arch/05` §8), de sorte qu'ajouter plus
tard un proxy de tuiles sur le VPS ne touche pas au reste.

    creerCarte(element)                 -> handle
    afficherDernierePosition(handle, p)
    afficherTrajet(handle, points)
    ajusterVue(handle)

L'URL de tuiles et l'attribution sont deux constantes en tête du fichier, et le
seul endroit du dépôt qui les mentionne. Choix POC : OpenStreetMap, avec
l'attribution obligatoire. `arch/05` §8 accepte que le serveur de tuiles voie
les zones consultées.

`ajusterVue` fait tenir la polyline dans le viewport. Sur un seul point,
choisir un zoom fixe raisonnable plutôt qu'un `fitBounds` dégénéré.

## 7.4 `utils/time.js`

    formaterAbsolu(iso)   -> "20 août 2026 à 08:35"
    formaterRelatif(iso)  -> "il y a 8 min"

Les deux sont affichés ensemble, jamais le relatif seul : `arch/05` §2 demande
l'heure exacte, et « il y a 3 jours » ne permet pas de recouper avec un message
reçu. Le relatif est en français, sans bibliothèque — quelques seuils suffisent.

Les instants arrivent en UTC et s'affichent dans le fuseau du navigateur. Pour
la famille en France et la voyageuse plus au nord, l'écart existe mais reste
faible ; ne pas afficher de fuseau au POC.

# 8. index.html et rendu

Un seul écran, conforme à `arch/05` §2 : titre du voyage, carte, dernière
position avec horodatage absolu et relatif, indication d'ancienneté, polyline
de la période, sélecteur de période.

Le rendu suit un cycle unique dans `app.js` :

    charger -> etat -> rendre

`rendre(etat)` est une fonction pure du point de vue de l'appelant : elle lit
l'état et met le DOM à jour, elle ne déclenche aucun appel réseau. Toute action
utilisateur modifie l'état puis rappelle `rendre`. C'est ce qui remplace le
moteur de rendu d'un framework, et ça ne tient que si la règle n'est jamais
contournée.

Pas de rafraîchissement automatique agressif : un rechargement des données
toutes les 2 minutes au maximum, et seulement si l'onglet est visible
(`document.visibilityState`). Le rate limiting du serveur est à 120 requêtes
par minute et par IP, mais l'argument est ailleurs : rafraîchir plus vite que
le téléphone n'envoie fabrique une illusion de temps réel.

Mobile d'abord : `arch/05` §9 exige que ça marche sur un smartphone familial.
Carte lisible sans zoom manuel, sélecteur de période atteignable au pouce.

# 9. nginx

Trois responsabilités : servir les fichiers statiques, injecter le token, poser
les en-têtes de confidentialité.

    location /f/<SEGMENT_SECRET>/ {
        alias /var/www/madhi/;
        index index.html;
        auth_basic            "Madhi";
        auth_basic_user_file  /etc/nginx/madhi.htpasswd;

        add_header Referrer-Policy "no-referrer"        always;
        add_header X-Robots-Tag    "noindex, nofollow"  always;
        add_header X-Content-Type-Options "nosniff"     always;
    }

    location /f/<SEGMENT_SECRET>/api/ {
        auth_basic            "Madhi";
        auth_basic_user_file  /etc/nginx/madhi.htpasswd;

        proxy_pass http://127.0.0.1:8111/api/v1/;
        proxy_set_header Authorization "Bearer <PUBLIC_READ_TOKEN>";
        proxy_set_header X-Real-IP $remote_addr;

        add_header Referrer-Policy "no-referrer" always;
    }

Points de vigilance :

- **`index index.html;` est nécessaire.** Avec `alias`, une requête sur l'URL
  racine `/f/<segment>/` désigne un répertoire ; sans directive `index` héritée
  du contexte englobant, nginx répond `403 Forbidden` et le lien familial paraît
  cassé alors que tout est en place.
- **`X-Real-IP` doit être posé.** C'est le seul en-tête sur lequel le rate
  limiter du serveur compte, depuis le correctif de la session 4. Sans lui, tous
  les visiteurs tombent dans le même compartiment.
- **`add_header` ne s'hérite pas** entre blocs dès qu'un bloc enfant en déclare
  un. D'où la répétition, et le `always` qui les maintient sur les réponses
  d'erreur — une 403 doit être aussi discrète que le reste.
- `<SEGMENT_SECRET>` et `<PUBLIC_READ_TOKEN>` **ne sont pas versionnés**
  (`arch/05` §8). Le dépôt porte un gabarit avec des marqueurs, comme
  `tools/backup/madhi-backup.service` porte `/CHEMIN/DU/DEPOT`.
- Le fichier nginx déployé a été réécrit par certbot. Partir du fichier du VPS,
  jamais du fichier versionné : `SERVER_DEPLOYMENT.md` explique pourquoi le
  recopier supprimerait HTTPS.

# 10. Ce qui doit changer côté serveur

Deux changements, à livrer avec le site :

1. **`latest_location` filtre sur `started_at`** quand il est non nul (§4.2).
   Un test doit couvrir le cas « des points existent avant `started_at` ».
   *Fait*, avec deux tests : des points de part et d'autre du départ, et le cas
   où tous les points précèdent le départ — `latest-location` renvoie alors
   `null`, ce que le site traite comme « aucune position reçue ».
2. **`/health` reste hors du chemin protégé.** Il ne doit pas passer derrière
   l'authentification, sinon le healthcheck Docker échoue.

Rien d'autre. Le contrat `LocationPointV1` ne bouge pas (`arch/06` §6).

# 11. Critères d'acceptation

Repris de `arch/05` §9, rendus vérifiables. État au 23 août 2026, jour de la
mise en ligne. `[x]` vaut pour « constaté », pas pour « écrit avec soin » :

- [x] L'horodatage absolu **et** l'ancienneté relative sont affichés ensemble.
- [x] Aucun libellé suggérant du temps réel — vérifié par un test qui parcourt
      les huit états et refuse « en direct », « temps réel », « live ».
- [x] `latest-location` à `null` donne un message clair, pas une carte vide
      centrée sur l'océan.
- [x] `startedAt` à `null` n'affiche aucune position précise.
- [x] Serveur arrêté : message d'erreur explicite, et la dernière donnée connue
      reste visible et datée. Éprouvé en coupant le serveur **après** le
      chargement, cas que les scénarios figés n'atteignent pas.
- [x] Onglet réseau : uniquement le domaine du projet et le serveur de tuiles.
      Désormais imposé par une `Content-Security-Policy`, et non plus seulement
      par discipline : un `fetch` externe et un pixel de mesure portant une
      latitude sont refusés par le navigateur.
- [x] Recherche de `PUBLIC_READ_TOKEN` et du segment secret dans tous les
      fichiers de `site/` : aucune occurrence.
- [x] Requête directe sur l'API sans en-tête : `403`. Vérifié contre le serveur
      de production, pas seulement en local.
- [x] Réponse d'historique de taille exactement `limit` : l'interface signale
      une troncature possible.
- [x] Période sans aucun point, alors qu'une position récente existe :
      l'historique est annoncé vide **sans** effacer la dernière position, et
      sans dire que rien n'a jamais été reçu.
- [x] Le libellé de la période la plus longue annonce 30 jours, pas le voyage
      entier.
- [~] Sur smartphone, la dernière position est lisible sans zoomer. Rendu
      vérifié à 360 px et à 500 px, sans débordement horizontal — mais dans un
      navigateur sans appareil. Reste à ouvrir le lien sur un vrai téléphone.
- [~] Une journée de trajet (≈ 288 points) s'affiche sans saccade. La polyline
      était reconstruite toutes les 30 secondes ; elle ne l'est plus que
      lorsque les données changent, ce qui était le vrai risque sur un
      téléphone de 4 Go. La fluidité elle-même n'est pas mesurée sur appareil.

Vérifications qui ne se voient pas à l'œil nu :

    # en-tetes de confidentialite, avec le mot de passe
    curl -sI -u '<user>:<motdepasse>' https://<domaine>/f/<segment>/ \
      | grep -iE 'referrer-policy|x-robots-tag'

    # l'URL racine sert bien la page, et non un 403 de repertoire
    curl -s -o /dev/null -w '%{http_code}\n' \
      -u '<user>:<motdepasse>' https://<domaine>/f/<segment>/

    # sans mot de passe, tout est ferme
    curl -s -o /dev/null -w '%{http_code}\n' https://<domaine>/f/<segment>/

    # le token n'a pas fuite dans les fichiers servis
    grep -rn "$PUBLIC_READ_TOKEN" /var/www/madhi/ ; echo "code $?"

Attendu : `200` avec mot de passe, `401` sans, et un `grep` qui ne trouve rien
(code 1).

# 12. Hors périmètre du POC

À ne pas construire maintenant, pour éviter que le POC dérive : calendrier et
sélection de date, distances et statistiques, étapes quotidiennes, état de
batterie du téléphone, vue publique retardée, masquage de zones, pagination de
l'historique complet, polylines simplifiées, cache ou CDN.

Tout cela est `arch/06`, et une partie demande des endpoints qui n'existent pas.

# 13. Ordre de travail suggéré

1. `types.js` et `api-client.js`, éprouvés contre le serveur réel avec le token
   en ligne de commande, avant toute interface.
2. Le correctif serveur de `latest_location` (§10), avec son test.
3. `index.html` et `app.js` en texte brut : dernière position et horodatages,
   sans carte. C'est déjà utile à la famille.
4. Les états de §6, testés en coupant le serveur et en vidant la base.
5. La carte, puis le sélecteur de période.
6. nginx, l'accès privé, et les vérifications de §11.

L'ordre place l'interface la plus fruste en position utilisable très tôt. Si le
temps manque avant le départ, un site qui affiche « dernière position reçue à
telle heure, ici » sans carte vaut infiniment mieux qu'une carte inachevée.

# 14. Ce que seule la mise en ligne a montré

Le site est en ligne depuis le 23 août 2026. Deux défauts ont survécu à la
relecture, aux tests automatisés et au rendu en navigateur, et sont tombés dans
l'heure qui a suivi la mise en ligne. Ils se ressemblent : dans les deux cas, le
code était juste dans le monde que j'avais en tête, et faux dans le monde réel.

**Le lien familial sans slash final répondait 404.** `location /f/<segment>/`
ne couvre pas `/f/<segment>`. Or c'est très exactement ce qu'une personne colle
dans un message. Corrigé par une redirection `308` en correspondance exacte,
avec `absolute_redirect off` — sans quoi nginx fabriquerait une redirection
vers `http://`, le conteneur ignorant que le TLS existe en amont de lui.

**La sonde de vie `/_up` répondait `200` depuis Internet**, alors qu'elle était
fermée par `allow 127.0.0.1; deny all;`. Selon le mode réseau de Docker, le
conteneur voit la requête relayée par l'hôte arriver de sa propre boucle
locale : la règle n'excluait personne. Ce qu'elle révélait était mince — le nom
du domaine est déjà public dans les journaux de Certificate Transparency — mais
la documentation annonçait `403` là où la réalité donnait `200`, et une
vérification qui ment est pire que le défaut qu'elle prétend couvrir. La
présence de `X-Real-IP`, posée par le nginx de l'hôte, distingue ce que
`allow`/`deny` ne distinguait pas.

**Ce qu'il faut en retenir** : une règle d'accès écrite dans un conteneur ne
porte pas sur qui vous croyez. Toute règle de ce type doit être vérifiée depuis
l'extérieur, une fois en ligne, et pas seulement relue.
