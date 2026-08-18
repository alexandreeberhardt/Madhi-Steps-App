**ADR-005 — Rétention locale et migrations Room**

*Statut : Accepté — 2026-08-18*

# Contexte

La base locale est le seul endroit où une position existe entre sa capture et sa
confirmation par le serveur. Une mise à jour d'APK ne doit jamais effacer
silencieusement un point non synchronisé (`arch/01` §12, `arch/02` §2).

Volume attendu : environ 12 points par heure, 288 par jour, à peu près 105 000 sur
un an. Chaque ligne pèse de l'ordre de la centaine d'octets. L'année complète tient
donc dans une dizaine de mégaoctets.

# Options

**1. Purger les points `SYNCED` au bout de N jours.** Prévu en V2 par `arch/02` §2
comme rétention configurable. Économise un espace qui n'est pas contraint, et
introduit du code destructeur dans le chemin critique dès la V1.

**2. Ne rien supprimer en V1.** La base grossit de quelques mégaoctets par an.
Le tracé local complet reste disponible pour l'affichage et pour un éventuel
réimport si le serveur perdait des données.

# Décision

**Aucune suppression en V1.** Aucune requête `DELETE` sur la table `locations`
n'existe dans le code de la V1.

**Schéma Room** :

    locations(
      id                TEXT PRIMARY KEY,   -- UUID généré à la capture
      latitude          REAL NOT NULL,
      longitude         REAL NOT NULL,
      recorded_at       INTEGER NOT NULL,   -- epoch millis UTC
      accuracy_m        REAL,
      altitude_m        REAL,
      speed_mps         REAL,
      battery_percent   INTEGER,
      sync_state        TEXT NOT NULL,      -- PENDING | SYNCED
      attempt_count     INTEGER NOT NULL,
      last_attempt_at   INTEGER,
      last_error_code   TEXT
    )
    INDEX (sync_state, recorded_at)

**Règles de migration** :

- `exportSchema = true`, schémas JSON **commités** dans le dépôt.
- Migrations explicites, une par version, avec test de migration associé.
- `fallbackToDestructiveMigration()` est **interdit**, y compris en build debug.
  Un jour de fatigue suffit à laisser passer un build qui efface le voyage.
- Toute migration est testée avec une base contenant des points `PENDING` avant
  d'être livrée sur le téléphone réel.

# Conséquences

- La mise à jour par APK avec des points en attente devient un critère
  d'acceptation testable (`arch/01` §12) et non un espoir.
- La rétention configurable de la V2 reste facile à ajouter : c'est une méthode
  supplémentaire derrière le port `LocationStore`, sans impact sur le domaine.
  Elle ne devra jamais pouvoir viser un point `PENDING`.
- Si la base devait un jour être corrompue, le point de récupération est la copie
  serveur des points déjà `SYNCED` ; les `PENDING` seraient perdus. C'est une
  raison de plus pour que la synchronisation soit agressive dès que le réseau
  revient.
