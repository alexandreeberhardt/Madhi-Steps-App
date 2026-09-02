**03 — Serveur — POC / POW**

*API minimale, stockage robuste et contrat stable pour Android et le
site*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. But du POC

Le serveur POC doit recevoir les positions sans doublon, les conserver
durablement et fournir au site la dernière position ainsi qu’un
historique temporel.

# 2. Stack indicative

- API REST simple.

- PostgreSQL.

- Framework simple et maîtrisé. Décision recommandée pour éviter le
  flou : FastAPI/Python ou NestJS/TypeScript, mais un seul choix doit
  être figé avant le début du code.

- Migrations SQL versionnées.

- Déploiement cible : VPS personnel derrière le domaine du projet.

- Pas de service GAFAM obligatoire pour recevoir, stocker, lire ou
  surveiller les positions.

- Configuration par variables d’environnement. Le dépôt public ne
  contient que des exemples sans secret.

# 3. Endpoints POC

> POST /api/v1/devices/activate\
> POST /api/v1/locations/batch\
> GET /api/v1/trips/{tripId}/latest-location\
> GET /api/v1/trips/{tripId}/locations?from=&to=\
> GET /api/v1/trips/{tripId}/status\
> GET /api/v1/reverse-geocode?lat=&lon=

L’endpoint d’activation met en œuvre le mécanisme décrit au §4 ; il a été figé
par ADR-004. Le relais de géocodage inverse, ajouté le 29 août 2026, est le
seul endpoint qui fasse sortir une donnée du VPS vers un tiers : il est éteint
par défaut, et ses deux appelants — l’application avec son token appareil, le
site avec le token de lecture — sont authentifiés comme le reste.

Un `GET /health` existe aussi, sans authentification et sans donnée métier : il
sert la sonde de conteneur et la surveillance de `arch/07` §6.

# 4. Authentification appareil

- Token unique par device.

- Token stocké haché côté serveur si possible.

- Authorization: Bearer \<device-token\>.

- Révocation possible sans supprimer le voyage.

- Génération d’un code d’activation court et temporaire côté serveur.
  L’application le saisit au premier lancement et reçoit en échange le
  token appareil long utilisé pour `Authorization: Bearer`.

- Un code d’activation est à usage unique, expire rapidement, et ne doit
  pas être stocké en clair côté serveur.

# 5. Données et confidentialité serveur

- Le serveur du VPS est la seule destination normale des positions
  précises.

- Pas d’analytics tiers, pas de logs applicatifs envoyés à un service
  externe non maîtrisé, pas de crash reporting SaaS contenant des
  payloads de localisation.

- Les logs ne doivent pas contenir de coordonnées précises sauf besoin
  de diagnostic temporaire et documenté.

- Les accès famille passent par le domaine du projet, avec HTTPS.

- Les tokens appareil et liens privés sont révocables.

# 6. Configuration et dépôt public

- `DATABASE_URL`, secrets de session, tokens admin, sel/secret de hash,
  webhooks d’alerte et credentials de backup restent hors Git.

- Le dépôt contient au maximum `.env.example` avec des valeurs factices.

- Le serveur refuse de démarrer en production si une variable obligatoire
  manque.

- Le serveur refuse de démarrer en production si un secret vaut encore
  une valeur placeholder de `.env.example`, si un secret est trop court,
  ou si `NODE_ENV`/`APP_ENV` indique production avec une configuration de
  développement.

- Les logs de démarrage ne doivent pas afficher les secrets ni les URLs
  privées complètes.

# 7. Tables POC

> trips(id, name, started_at, ended_at, created_at)\
> activation_codes(code_hash, trip_id, created_at, expires_at, used_at)\
> devices(id, trip_id, name, token_hash, last_seen_at, app_version,
> revoked_at, created_at)\
> locations(id, trip_id, device_id, latitude, longitude, accuracy,
> altitude, speed, battery_percent, recorded_at, received_at)

`activation_codes` porte le mécanisme du §4 : le code n’y est stocké que haché,
et `used_at` est ce qui le rend à usage unique. `devices.revoked_at` porte la
révocation exigée au §4, sans supprimer le voyage. `battery_percent` appartient
au contrat `LocationPointV1` de `arch/00` §5 et manquait ici.

# 8. Contraintes de données

- locations.id unique.

- Coordonnées dans les bornes géographiques valides.

- Index sur (trip_id, recorded_at).

- received_at défini par le serveur.

- recorded_at transmis par le téléphone et conservé tel quel après
  validation.

# 9. Idempotence

Un batch peut être envoyé plusieurs fois. Le serveur insère uniquement
les points inconnus et retourne la liste ou le compte des IDs acceptés.
Un timeout côté mobile ne doit jamais créer de doublons.

# 10. Réponses et erreurs

- 200/201 : batch accepté.

- 400 : payload invalide.

- 401/403 : appareil non autorisé.

- 413 : batch trop volumineux.

- 429 : trop de requêtes.

- 5xx : erreur temporaire, le mobile doit réessayer.

# 11. Critères d’acceptation

- 10 000 points importés sans erreur.

- Réinjection du même fichier : aucune duplication.

- GET latest renvoie réellement le point au recorded_at le plus récent.

- Historique filtrable par intervalle.

- Backup manuel restauré avec succès avant mise en production.

- Déploiement sur le VPS cible validé avec le vrai domaine et HTTPS.

- Vérification qu’aucune donnée de localisation n’est envoyée à un outil
  tiers pendant l’ingestion et la consultation.

- Vérification qu’un scan du dépôt public ne révèle aucun secret,
  credential ou dump de base.

# 12. Préparation V2

- Services séparés : ingest, query, auth.

- DTO versionnés.

- Migrations automatisées.

- Logs structurés avec requestId et deviceId.

- Ne pas mettre les calculs de statistiques dans les contrôleurs.
