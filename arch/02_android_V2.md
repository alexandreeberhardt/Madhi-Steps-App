**02 — Application Android — V2**

*Durcir le tracking pour un voyage d’un an et améliorer le diagnostic à
distance*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. Objectif

La V2 transforme le POC fiable en application de voyage durable :
meilleure gestion d’énergie, diagnostic, auto-récupération et
observabilité côté appareil.

# 2. Améliorations principales

- Stratégie de tracking adaptée au mouvement et à l’état batterie.

- Optimisation progressive de `LocationManager` sur le téléphone réel :
  fréquence, précision, provider GPS/réseau, backoff, comportement en
  batterie faible et réduction éventuelle en immobilité.

- Heartbeat périodique indépendant des points GPS si pertinent.

- Écran diagnostic complet.

- Persistance de la configuration utilisateur.

- Fréquence de localisation modifiable depuis les réglages, avec bornes
  claires et texte d’impact batterie/compréhension simple.

- Désactivation complète du tracking GPS depuis les réglages, sans perte
  des données locales non synchronisées.

- Gestion explicite des états de permissions.

- Détection des synchronisations bloquées.

- Rétention locale des points déjà synchronisés pendant une durée
  configurable.

- Journal technique local limité et exportable.

- Migrations Room versionnées.

- Mécanisme de mise à jour testé avant le départ.

- Procédure de mise à jour par APK testée sur le téléphone réel, sans
  dépendre du Play Store.

- Diagnostic de confidentialité : vérifier qu’aucun SDK externe ne
  reçoit les positions, logs ou identifiants de l’appareil.

- Rotation possible du token appareil sans publier de nouvelle valeur
  sensible dans GitHub.

# 3. Architecture V2

> TrackingCoordinator\
> ├─ LocationProvider\
> ├─ SamplingPolicy\
> ├─ LocalLocationRepository\
> ├─ SyncScheduler\
> ├─ DeviceHealthReporter\
> └─ PermissionStateMonitor

# 4. SamplingPolicy

- Mode normal : cible ~5 min.

- Fréquence utilisateur : valeur persistée, bornée, et utilisée comme
  base par `SamplingPolicy`.

- Mode batterie faible : fréquence potentiellement réduite si décidé.

- Mode immobile : possibilité de réduire les acquisitions après
  validation terrain.

- Mode rattrapage : envoi rapide des points pending lorsque le réseau
  revient.

# 5. Diagnostic utilisateur

| **Indicateur**      | **Exemple**  |
|---------------------|--------------|
| Dernier point GPS   | il y a 4 min |
| Dernier upload      | il y a 3 min |
| Queue               | 0 pending    |
| Permissions         | OK           |
| Tracking background | OK           |
| Batterie            | 62 %         |
| Version             | 1.3.0        |

# 6. Résilience

- Retry avec backoff et plafond.

- Aucun effacement de point pending sur erreur auth ou serveur.

- Watchdog local : si des points restent pending alors que le réseau est
  disponible et qu’aucune tentative récente n’existe, replanifier une
  synchronisation.

- Replanification automatique des workers après redémarrage téléphone,
  mise à jour APK et ouverture de l’application.

- Diagnostic interne conservé localement : `lastSyncAttemptAt`,
  `lastSyncSuccessAt`, dernier code HTTP, dernière erreur auth, taille du
  dernier batch, âge du plus vieux point pending.

- Les corrections de résilience ne changent pas l’interface principale.
  Elles utilisent les réglages/diagnostic existants uniquement si une
  information doit être consultée.

- Détection de base locale corrompue et stratégie de récupération.

- Limitation des batches pour éviter les payloads énormes.

- Protection contre les timestamps incohérents.

# 7. Compatibilité avec le POC

La V2 conserve le même modèle LocationPointV1 et le même endpoint batch.
Les métadonnées V2 sont ajoutées comme champs optionnels ou via un
endpoint de status/heartbeat. Le backend POC peut donc continuer à
recevoir les positions pendant la migration.

# 8. Tests V2

- 7 jours de fonctionnement réel.

- 24 h de réseau intermittent.

- Batterie faible / économie d’énergie.

- Redémarrage répété.

- Mise à jour de l’application avec points pending présents.

- Perte temporaire de GPS.

- Backlog de plusieurs centaines de points.

- Mise à jour par APK avec restrictions batterie déjà configurées.

- Installation sur un nouveau téléphone en cas de remplacement de
  l’appareil pendant le voyage.

- Build release reproductible depuis le dépôt public avec secrets fournis
  uniquement par l’environnement local.
