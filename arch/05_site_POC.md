**05 — Site familial — POC / POW**

*Une seule page claire : où est-elle, quand la position a-t-elle été
reçue, quel trajet a-t-elle parcouru ?*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. But du POC

Le site POC doit rendre l’information immédiatement compréhensible par
la famille, sur mobile comme sur ordinateur, sans fonctionnalités
secondaires.

# 2. Écran principal

- Titre du voyage.

- Dernière position connue sur carte.

- Heure/date exacte du point.

- Texte relatif : “il y a 8 min”.

- Polyline du trajet de la période sélectionnée.

- Indication claire si la dernière position est ancienne.

# 3. Filtres minimum

- Aujourd’hui.

- Les 24 dernières heures. *Ajouté le 30 août 2026, en même temps que dans
  l’application : à une heure du matin, « aujourd’hui » ne montre plus qu’une
  heure de trajet. La période qui manquait est une durée, pas une date.*

- 7 derniers jours.

- Tout le voyage si les performances restent acceptables ; sinon période
  limitée. *Servable depuis le 26 août 2026 : le serveur échantillonne au lieu
  de tronquer (`arch/17` §4.1).*

# 4. Architecture frontend

> pages/routes\
> components/map\
> features/trip\
> api-client\
> types\
> utils/time

# 5. Contrat API

- Le frontend ne connaît jamais la structure PostgreSQL.

- Un module api-client centralise toutes les requêtes.

- Les types LocationPointV1 sont définis dans un fichier unique.

- **Le site ne montre le voyage qu'à partir de `startedAt`.** Le trip contient
  aussi les positions de pré-validation et des tests terrain T1 à T6, prises à
  la maison avant le départ : elles ne sont pas supprimées, elles sont
  antérieures à `started_at` (voir `SERVER_DEPLOYMENT.md`, « Le jour du
  départ »). L'historique se demande donc avec `from=startedAt`, lu depuis
  `/trips/{id}/status`.

- À corriger côté serveur en même temps : `latest_location` ignore `started_at`
  et renvoie le point le plus récent quel qu'il soit. Avant le départ, la
  « dernière position » serait donc une position prise à la maison.

# 6. Carte

- MapLibre ou Leaflet.

- Tuiles/fournisseur encapsulés.

- Marker dernière position.

- Polyline historique.

- Ajustement automatique du viewport.

# 7. Accès privé POC

Pour le POC, utiliser soit une authentification simple, soit un lien
privé suffisamment aléatoire. Ne jamais rendre la localisation précise
indexable publiquement.

Même en POC, le lien privé doit être révocable. Prévoir `Referrer-Policy:
no-referrer`, `X-Robots-Tag: noindex`, un rate-limit, et idéalement un
mot de passe familial simple si le lien donne accès à la position
précise.

# 8. Confidentialité et cartes

- Le site est servi depuis le domaine du projet sur le VPS.

- Aucun Google Analytics, Tag Manager, pixel publicitaire, outil de
  heatmap ou script social n’est intégré.

- Le fournisseur de tuiles cartographiques doit être choisi pour limiter
  l’exposition de la position consultée. Si possible, utiliser des
  tuiles compatibles avec MapLibre/Leaflet et une politique de logs
  acceptable, ou prévoir une option d’auto-hébergement/proxy.

- Choix POC : utiliser OpenStreetMap ou un fournisseur de tuiles basé sur
  OpenStreetMap acceptable. Le fait que le serveur de tuiles voie les
  zones consultées est accepté pour le POC.

- Garder l’encapsulation du fournisseur de tuiles pour pouvoir ajouter
  plus tard un proxy/cache via le VPS ou changer de fournisseur sans
  réécrire la carte.

- Le frontend ne doit pas appeler directement un service GAFAM avec les
  coordonnées précises du trajet.

- Les URLs publiques nécessaires au frontend passent par une
  configuration de build. Le dépôt public ne contient pas d’URL privée,
  de token de partage réel, ni de lien familial réel.

# 9. Critères d’acceptation

- Fonctionne sur smartphone familial.

- Dernière position et timestamp visibles sans ambiguïté.

- Pas de fausse notion de “temps réel”.

- Trajet d’une journée fluide.

- Erreur serveur présentée proprement.

- État “aucune position reçue” prévu.

- Vérification réseau dans le navigateur : seules les requêtes vers le
  domaine du projet et le fournisseur de tuiles accepté sont présentes.

- Vérification qu’aucun secret n’est inclus dans le bundle frontend.

# 10. Préparation V2

- Composant MapProvider abstrait.

- Sélecteur de période indépendant de la carte.

- API client centralisé.

- Design responsive dès le POC.

- Pas de calcul de distance métier uniquement côté navigateur.
