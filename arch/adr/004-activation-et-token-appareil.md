**ADR-004 — Activation de l'appareil et stockage du token**

*Statut : Accepté — 2026-08-18*

# Contexte

`arch/00` §9, `arch/01` §5 et `arch/03` §4 posent le mécanisme : le serveur génère
un code d'activation court et temporaire, la voyageuse le saisit dans l'application
au premier lancement, l'application l'échange contre un token appareil long utilisé
ensuite en `Authorization: Bearer`.

Deux contraintes encadrent ce choix : le token ne doit jamais être codé dans l'APK
release (`arch/01` §5), et le dépôt GitHub public ne doit contenir aucun secret
(`arch/00` §2).

Aucun document ne définissait l'endpoint d'activation. Il fallait le figer.

# Options

Sur le stockage local du token :

**1. `SharedPreferences` / DataStore en clair.** Le stockage privé d'une
application est isolé sur un téléphone non rooté. Simple, zéro dépendance.

**2. `androidx.security:security-crypto` (EncryptedSharedPreferences).**
Bibliothèque dépréciée, à éviter pour un projet qui doit vivre un an.

**3. DataStore + chiffrement AES-GCM avec une clé de l'Android Keystore.**
Une soixantaine de lignes, aucune dépendance supplémentaire, clé non extractible.

# Décision

**Endpoint d'activation, ajouté au contrat API** (voir `arch/13_contrat_api_android_v1.md`) :

    POST /api/v1/devices/activate
    Body      { "activationCode": "...", "deviceName": "...", "appVersion": "..." }
    200       { "deviceId": "...", "deviceToken": "...", "tripId": "..." }
    400 / 410 code invalide ou expiré

**Stockage : option 3** — DataStore chiffré par une clé AES-GCM détenue par
l'Android Keystore. Le token autorise l'écriture de positions dans le voyage :
il vaut mieux qu'il ne soit pas lisible par une extraction de sauvegarde.

**Encapsulation.** Le token est accessible uniquement via le port
`DeviceCredentials`. Aucune autre couche ne le manipule : ni le domaine, ni les
use cases, ni l'UI. Seul `HttpLocationSyncGateway` le lit, au moment de construire
l'en-tête. Il n'apparaît dans aucun log, aucun message d'erreur, aucun écran de
diagnostic — le diagnostic affiche « appareil activé : oui/non », jamais la valeur.

L'URL de l'API vient de `BuildConfig`, alimentée par `local.properties` ou une
variable d'environnement, jamais versionnée. Le dépôt ne contient que des valeurs
factices.

# Conséquences

- L'application est inutilisable tant qu'elle n'est pas activée : l'onboarding
  doit donc traiter le code d'activation comme une étape de premier plan, et le
  message d'erreur doit distinguer « code invalide » de « pas de réseau ».
- La rotation du token (`arch/02` §2) est possible sans republier d'APK : elle
  consiste à réactiver l'appareil avec un nouveau code.
- `deviceId` reste présent dans le payload `LocationPointV1` conformément au
  contrat figé de `arch/00` §5, mais le serveur fait autorité sur le **token**.
  En cas de divergence entre les deux, c'est le token qui décide.
- L'application stocke le `tripId` reçu à l'activation. Elle ne s'en sert pas en
  V1 (aucun appel `GET`), mais il sera nécessaire dès que l'application lira des
  données côté serveur.
- Perte du téléphone : révoquer le token côté serveur suffit, sans toucher au
  voyage (`arch/03` §4).
