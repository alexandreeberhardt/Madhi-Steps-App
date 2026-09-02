**00 — Architecture maître & stratégie d’évolution**

*Contrats stables, versions progressives et règles communes*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. Objectif

Ce document définit les frontières entre l’application Android, le
serveur, le site web et l’infrastructure. Le POC doit rester minimal,
mais toutes les décisions structurantes doivent permettre une évolution
vers une V2 sans réécriture complète.

# 2. Contraintes fermes du projet

- L’application Android est distribuée par APK signé, installé
  manuellement par la voyageuse. Le projet ne dépend pas du Play Store
  pour installer, tester ou mettre à jour la version critique du voyage.

- Le serveur, le site familial et la base de données sont hébergés sur
  un VPS contrôlé par le propriétaire du projet, avec un nom de domaine
  qui lui appartient.

- Les données de localisation précises ne doivent pas être envoyées à
  des services GAFAM ou à des outils tiers d’analytics, publicité, crash
  reporting, tracking produit ou cartographie, sauf exception documentée
  et acceptée.

- Le système doit rester réparable par une seule personne : peu de
  services, peu de comptes externes, peu de magie d’infrastructure.

- Le code et les documents seront hébergés dans un dépôt GitHub public.
  Le dépôt ne contient donc jamais de secrets, tokens, mots de passe,
  clés privées, keystores Android, URLs privées d’administration ou
  coordonnées personnelles non destinées à être publiques.

- Toute configuration dépendante de l’environnement passe par des
  variables d’environnement ou des fichiers `.env` non versionnés. Le
  dépôt public peut contenir uniquement des fichiers `.env.example` avec
  des valeurs factices.

# 3. Principe d’architecture

- Offline-first sur Android : une position est enregistrée localement
  avant toute tentative réseau.

- API versionnée dès le POC : /api/v1/.

- Identifiant unique par point pour rendre les envois idempotents.

- Le serveur est source de vérité pour le partage familial ; Android
  reste source de vérité temporaire tant qu’un point n’est pas confirmé
  reçu.

- Le site ne dépend jamais du stockage interne du serveur : il ne
  consomme que des endpoints documentés.

- Le fournisseur de carte est encapsulé côté web pour pouvoir être
  remplacé.

- Le fournisseur de carte est choisi avec une contrainte de
  confidentialité : pas de SDK cartographique qui impose de la
  télémétrie ou transmet la position précise à un acteur non choisi.

- La sécurité et les logs sont ajoutés progressivement, sans modifier le
  modèle métier central.

# 4. Découpage des livrables

| **Brique** | **POC / POW** | **V2** |
|----|----|----|
| Android | Tracking fiable, stockage local, sync, statut | Diagnostic avancé, adaptation batterie, résilience, mises à jour |
| Serveur | Ingestion, latest, historique, auth appareil | Comptes famille, agrégations, alertes, observabilité, admin |
| Site | Carte, dernière position, trajet, accès privé | Historique riche, stats, filtres, UX mobile, partage différencié |
| Infra / Ops | Déploiement simple, HTTPS, backup | Monitoring, alertes, staging, automatisation, PRA |

# 5. Contrat Position v1

> LocationPointV1 {\
> id: UUID\
> deviceId: string\
> latitude: number\
> longitude: number\
> recordedAt: ISO-8601 UTC\
> accuracyMeters?: number\
> altitudeMeters?: number\
> speedMps?: number\
> batteryPercent?: number\
> }

Les champs obligatoires ne doivent pas changer en V2. Les nouveaux
champs sont ajoutés comme optionnels. Une V2 du payload n’est créée que
si la compatibilité ascendante devient impossible.

# 6. Endpoints stables

> POST /api/v1/devices/activate\
> POST /api/v1/locations/batch\
> GET /api/v1/trips/{tripId}/latest-location\
> GET /api/v1/trips/{tripId}/locations?from=&to=\
> GET /api/v1/trips/{tripId}/status\
> GET /api/v1/reverse-geocode?lat=&lon=

Les deux endpoints encadrants ont été ajoutés après la rédaction initiale, et
cette liste corrigée en conséquence. L’activation vient d’ADR-004 le 18 août
2026 : elle est la conséquence directe de §9, qui interdit de coder un token
appareil dans l’APK. Le relais de géocodage inverse vient du 29 août 2026 ; il
sert la bulle d’un point du trajet, dans l’application comme sur le site, et il
existe pour que ni le téléphone ni le navigateur de la famille n’aille parler
directement à un géocodeur tiers. Il est éteint par défaut côté serveur :
allumer une sortie vers un tiers est une décision qui se prend explicitement.

Le détail des payloads est dans `arch/13_contrat_api_android_v1.md` §4 et §6.

# 7. Modèle de données minimal

> trip 1 ─── n device\
> trip 1 ─── n location\
> device 1 ─── n location

# 8. Règles d’évolution

1\. Ne jamais coupler le suivi GPS à l’interface Android : le moteur de
tracking doit être un module/service séparé.

2\. Ne jamais faire calculer au mobile une donnée métier qui doit être
partagée comme vérité commune (distance totale, état du voyage, etc.).

3\. Ne jamais exposer directement PostgreSQL au frontend.

4\. Ne jamais supprimer un champ ou endpoint utilisé sans période de
compatibilité.

5\. Toute nouvelle capacité V2 doit pouvoir être activée indépendamment
lorsque possible.

6\. Les migrations de base doivent être versionnées et réversibles ou
accompagnées d’un plan de rollback.

7\. Ne jamais introduire Firebase, Google Analytics, Crashlytics, Google
Maps SDK, service cloud managé ou outil de télémétrie externe sans
validation explicite de l’impact sur la confidentialité.

8\. Ne pas transformer les responsabilités V2 en microservices. Elles
restent des dossiers/modules internes tant qu’un besoin d’exploitation
ne justifie pas une séparation réelle.

9\. Ne jamais coder en dur dans l’app, le site ou le serveur : domaine de
production, URL API privée, token appareil, secret JWT, mot de passe DB,
clé de backup, clé de signature, webhook d’alerte ou accès admin.

10\. Toute URL publique nécessaire au fonctionnement peut exister sous
forme de variable de configuration documentée. Les vraies valeurs de
production sont injectées au build, au déploiement ou via le VPS.

# 9. Décisions POC par défaut

- Serveur : monolithe REST simple sur le VPS.

- Base : PostgreSQL sur le VPS, sauvegardée hors machine principale.

- Site : frontend statique ou application web légère servie derrière le
  reverse proxy du VPS.

- Android : APK signé, versionné, testable sans compte Google
  développeur et sans dépendance Play Store pour les fonctions
  critiques.

- Localisation Android : `LocationManager` comme choix POC, avec
  optimisation batterie progressive sur le téléphone réel.

- Fréquence de localisation : configurable dans les réglages Android, 5
  minutes par défaut, valeurs bornées pour protéger batterie et serveur.

- Tracking GPS : désactivable dans les réglages Android sans supprimer
  les points locaux en attente.

- Synchronisation Android : indépendante de l’acquisition GPS, relancée
  après redémarrage, mise à jour APK ou ouverture de l’app, et surveillée
  par un watchdog local si des points restent pending.

- Résilience sans surcharge UI : ces mécanismes ne doivent pas ajouter de
  nouvel écran ni transformer l’accueil. Les détails restent dans le
  diagnostic existant des réglages.

- Activation appareil : code temporaire généré côté serveur, saisi dans
  l’application au premier lancement, puis échangé contre un token
  appareil long.

- Cartographie : Leaflet ou MapLibre avec fournisseur de tuiles
  compatible avec l’objectif de confidentialité, idéalement remplaçable
  ou auto-hébergeable.

- Tuiles : OpenStreetMap ou fournisseur de tuiles basé sur OpenStreetMap
  accepté au POC. Le fournisseur reste encapsulé pour permettre un
  proxy/cache VPS plus tard si nécessaire.

- Configuration : `.env.example` versionné, `.env`, `.env.production`,
  keystores, dumps, backups et fichiers de secrets exclus du dépôt.

# 10. Convention de configuration

Variables minimales à prévoir :

- `API_BASE_URL`

- `PUBLIC_SITE_URL`

- `DATABASE_URL`

- `DEVICE_TOKEN_HASH_SECRET` ou secret équivalent si nécessaire

- `JWT_SECRET` ou secret de session si comptes famille

- `ANDROID_API_BASE_URL`

- `ANDROID_SIGNING_STORE_FILE`

- `ANDROID_SIGNING_STORE_PASSWORD`

- `ANDROID_SIGNING_KEY_ALIAS`

- `ANDROID_SIGNING_KEY_PASSWORD`

- `MAP_TILES_URL`

- `BACKUP_ENCRYPTION_KEY`

Les noms exacts peuvent évoluer selon la stack, mais la règle ne change
pas : valeurs réelles hors Git, exemples factices dans Git.

# 11. Ordre global recommandé

1\. Serveur POC + base

2\. Android POC

3\. Site POC

4\. Tests terrain 24 h puis 7 jours

5\. Infra POC finalisée

6\. Android V2

7\. Serveur V2

8\. Site V2

9\. Infra/Ops V2
