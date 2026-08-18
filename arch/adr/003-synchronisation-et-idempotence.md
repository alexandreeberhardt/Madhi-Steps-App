**ADR-003 — Synchronisation, batch, idempotence et classification des erreurs**

*Statut : Accepté — 2026-08-18*

# Contexte

`arch/00` §3 pose le principe : une position est sauvegardée localement avant
toute tentative réseau, et le serveur n'est source de vérité qu'une fois le point
confirmé reçu. `arch/01` §8 exige que la synchronisation soit **indépendante** de
l'acquisition GPS : si des points sont en attente, l'envoi doit pouvoir être tenté
même si aucune nouvelle position n'est collectée, et même si le tracking est
désactivé.

Le téléphone peut rester sans réseau plusieurs jours. Aucun point ne doit être
perdu, y compris lorsque le serveur reçoit un batch mais que la réponse se perd.

# Options

Sur les états de synchronisation, deux modèles s'opposaient :

**1. `PENDING` / `SYNCED` / `ERROR`** (`arch/01` §9). Un état d'erreur persisté.

**2. `PENDING` / `SYNCED` seulement**, l'échec étant du diagnostic porté par des
colonnes (`attemptCount`, `lastAttemptAt`, `lastErrorCode`).

Le modèle 1 crée un état dont il faut penser à sortir. Un point oublié en `ERROR`
est un point perdu — exactement ce que le projet interdit.

# Décision

**Deux états seulement : `PENDING` et `SYNCED`.** Un point non confirmé par le
serveur reste `PENDING`, sans exception et sans limite de temps. L'information
d'échec est du diagnostic, pas un état de cycle de vie.

**Idempotence.** L'`id` est un UUID généré sur l'appareil au moment de la capture,
avant toute écriture. Il ne change jamais. Le serveur n'insère que les identifiants
inconnus (`arch/03` §9). Un batch rejoué ne crée donc aucun doublon.

**Déclencheurs de synchronisation** — trois entrées, un seul use case :

    1. après chaque capture, depuis le foreground service          (chemin rapide)
    2. SyncWorker WorkManager périodique 15 min, NetworkType.CONNECTED
       → fonctionne même tracking désactivé                        (filet de sécurité)
    3. au démarrage de l'application, après BOOT_COMPLETED,
       après MY_PACKAGE_REPLACED                                   (reprise)

                       tous ──► SyncPendingLocations

**Déroulé de `SyncPendingLocations`** :

    1. lire les N plus anciens PENDING (N = 200 par défaut)
    2. POST /api/v1/locations/batch
    3. marquer SYNCED uniquement les identifiants confirmés par le serveur
    4. classer l'échec éventuel, ne rien supprimer
    5. journaliser le diagnostic
    6. répéter tant qu'il reste des PENDING et que le batch précédent a réussi

**Classification des erreurs** — les catégories sont figées par `arch/03` §10 :

| Cas | Traitement | Points |
|---|---|---|
| Pas de réseau | attendre, aucune tentative | conservés |
| Timeout | retry avec backoff | conservés |
| `401` / `403` | arrêt des retries agressifs, état d'authentification signalé au diagnostic | conservés |
| `413` | réduction de la taille du batch, nouvelle tentative | conservés |
| `429` | respect de `Retry-After`, sinon backoff | conservés |
| `5xx` | retry avec backoff plafonné | conservés |
| `400` | pas de retry agressif, remontée au diagnostic — c'est un bug, pas un incident réseau | conservés |

Aucune de ces branches ne supprime un point. Il n'existe aucun chemin de code
menant à la suppression d'un point `PENDING`.

**Convention de retour d'erreur du projet** :

- Les ports ne lèvent pas d'exception métier. Ils retournent un type scellé quand
  l'échec est un cas normal, `null` quand l'absence est le seul échec possible.
- Les exceptions sont réservées aux erreurs de programmation et sont capturées à
  la frontière des adaptateurs, jamais laissées traverser un port.
- Les use cases retournent un résultat scellé, consommable aussi bien par l'UI que
  par un worker.

# Conséquences

- Le scénario « le serveur a reçu, la réponse s'est perdue » se résout seul :
  le point reste `PENDING`, le retry renvoie le même UUID, le serveur l'ignore,
  le point passe `SYNCED`. Aucun doublon, aucune perte.
- Un backlog de plusieurs milliers de points se vide par batchs successifs sans
  charger la mémoire ni saturer une seule requête.
- Le nombre de tentatives n'a aucune conséquence destructrice : `attemptCount`
  sert au diagnostic et au calcul du backoff, jamais à abandonner un point.
- La désactivation du tracking (`arch/01` §2) n'empêche pas la vidange du backlog,
  puisque le worker est indépendant du service d'acquisition.
