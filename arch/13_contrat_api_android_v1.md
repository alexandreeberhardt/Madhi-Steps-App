**13 — Contrat API v1 — client Android**

*Contrat figé entre l'application Android et le serveur*

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# 1. Statut de ce document

`arch/00_architecture_maitre.md` §5 et §6 figent le modèle `LocationPointV1` et la
liste des endpoints. `arch/03_serveur_POC.md` §4, §9 et §10 figent
l'authentification, l'idempotence et la sémantique HTTP.

Ce document complète ces deux sources avec le **détail exact des payloads**, qui
n'était pas écrit et dont le client Android a besoin pour être développé avant le
serveur. Il ne contredit rien : en cas de divergence, `arch/00` et `arch/03` font foi.

Le serveur POC devra implémenter ce contrat tel quel.

# 2. Règles générales

- Transport : **HTTPS uniquement**. Aucun trafic en clair, y compris en debug
  contre un serveur local — utiliser un certificat local si nécessaire.
- Encodage : UTF-8, `Content-Type: application/json`.
- Base : `{API_BASE_URL}` = `https://<domaine>/api/v1`, injectée au build, jamais
  versionnée.
- En-tête `User-Agent: MadhiTracker/<versionName> (Android <sdkInt>)` — permet au
  serveur de tracer la version sans champ applicatif supplémentaire.
- Horodatages : **ISO-8601 UTC avec suffixe `Z`**, précision seconde ou
  milliseconde, par exemple `2026-08-18T14:32:07Z`.
- Le serveur ignore les champs inconnus (compatibilité ascendante, `arch/00` §5).
- Le client ignore les champs de réponse inconnus.

# 3. Authentification

Toutes les requêtes sauf l'activation portent :

    Authorization: Bearer <deviceToken>

Le token identifie l'appareil et, par lui, le voyage. Il n'est jamais présent dans
le dépôt, dans les logs, ni dans l'écran de diagnostic.

# 4. `POST /api/v1/devices/activate`

Premier lancement uniquement. **Sans** en-tête `Authorization`.

Requête :

    {
      "activationCode": "XXXX-XXXX",
      "deviceName": "Pixel de Madhi",
      "appVersion": "1.0.0"
    }

Réponse `200` :

    {
      "deviceId": "550e8400-e29b-41d4-a716-446655440000",
      "deviceToken": "<opaque, long, à usage exclusif de cet appareil>",
      "tripId": "8f14e45f-ceea-467a-9f4e-2b1c9a1a1a1a"
    }

Erreurs :

| Code | Signification | Comportement client |
|---|---|---|
| `400` | code malformé | message « code invalide », permettre une nouvelle saisie |
| `410` | code expiré ou déjà utilisé | message « code expiré », demander un nouveau code |
| `429` | trop de tentatives | message d'attente, respecter `Retry-After` |
| `5xx` | erreur serveur | message « serveur indisponible », permettre de réessayer |

Le code d'activation est à usage unique et expire rapidement (`arch/03` §4).
Le client ne réessaie **jamais** automatiquement une activation : c'est une action
utilisateur explicite.

# 5. `POST /api/v1/locations/batch`

Endpoint critique. Le seul appelé de façon répétée pendant le voyage.

Requête :

    {
      "points": [
        {
          "id": "3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071",
          "deviceId": "550e8400-e29b-41d4-a716-446655440000",
          "latitude": 48.85837,
          "longitude": 2.29448,
          "recordedAt": "2026-08-18T14:32:07Z",
          "accuracyMeters": 12.4,
          "altitudeMeters": 34.0,
          "speedMps": 4.7,
          "batteryPercent": 62
        }
      ]
    }

- `id`, `deviceId`, `latitude`, `longitude`, `recordedAt` sont **obligatoires**.
- `accuracyMeters`, `altitudeMeters`, `speedMps`, `batteryPercent` sont
  **optionnels** : absents du JSON s'ils sont inconnus, jamais `null` par défaut.
- Les points sont envoyés du plus ancien au plus récent.
- Taille : 200 points par défaut, réduite dynamiquement par le client sur `413`.
- `deviceId` est informatif. **Le serveur fait autorité sur le token** : en cas de
  divergence entre `deviceId` et le porteur du token, le token décide (ADR-004).

Réponse `200` :

    {
      "accepted":   ["<id>", "..."],
      "duplicates": ["<id>", "..."],
      "rejected":   [ { "id": "<id>", "reason": "invalid_coordinates" } ]
    }

Sémantique côté client :

| Liste | Signification | Traitement |
|---|---|---|
| `accepted` | nouvellement stocké par le serveur | passe en `SYNCED` |
| `duplicates` | déjà connu du serveur | passe en `SYNCED` — c'est ce qui rend le rejeu sûr |
| `rejected` | refusé point par point | reste `PENDING`, `lastErrorCode` renseigné, remonté au diagnostic |

**`accepted` et `duplicates` sont traités de la même manière.** C'est le cœur de
l'idempotence : un batch dont la réponse s'est perdue est renvoyé, tous ses points
reviennent en `duplicates`, et le client les confirme sans créer de doublon
(`arch/03` §9).

Un batch entièrement dupliqué reste un succès `200`, jamais une erreur.

Erreurs globales — sémantique figée par `arch/03` §10 :

| Code | Client |
|---|---|
| `400` | payload invalide : bug. Pas de retry agressif, remontée au diagnostic. Aucun point supprimé. |
| `401` / `403` | appareil non autorisé. Arrêt des retries agressifs, état d'authentification signalé. Aucun point supprimé. |
| `413` | batch trop volumineux. Réduction de la taille du batch, nouvelle tentative. Aucun point supprimé. |
| `429` | respect de `Retry-After` si présent, sinon backoff. Aucun point supprimé. |
| `5xx` | erreur temporaire. Retry avec backoff plafonné. Aucun point supprimé. |

**Aucun code de retour, quel qu'il soit, ne déclenche la suppression d'un point.**

# 6. Endpoints non utilisés par l'application V1

`GET /api/v1/trips/{tripId}/latest-location`,
`GET /api/v1/trips/{tripId}/locations?from=&to=` et
`GET /api/v1/trips/{tripId}/status` existent au contrat (`arch/00` §6) mais sont
consommés par le site familial, pas par l'application. L'application V1 n'émet
aucun `GET` : son écran principal lit exclusivement la base locale.

C'est un choix délibéré : l'application reste utile sans réseau, et le serveur ne
devient jamais un prérequis d'affichage.

# 7. Réseau côté client

- Timeouts : connexion 15 s, lecture 30 s, écriture 30 s.
- Aucune tentative parallèle : une synchronisation à la fois, verrou applicatif.
- Backoff exponentiel plafonné entre tentatives, avec jitter.
- Compression `gzip` de la requête acceptée si le serveur l'annonce ; non requise.
- Aucune coordonnée n'est écrite dans les logs, en aucune circonstance (`arch/01` §4,
  `arch/03` §5).

# 8. Évolution

Les champs obligatoires de `LocationPointV1` ne changent pas (`arch/00` §5). Les
métadonnées V2 (heartbeat, device health) passeront par un endpoint distinct et
non par une modification de ce payload, afin que le serveur V2 continue d'accepter
un client V1 et réciproquement (`arch/02` §7, `arch/04` §10).
