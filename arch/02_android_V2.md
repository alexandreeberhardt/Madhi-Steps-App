**02 — Application Android — V2**

*Durcir le tracking pour un voyage d’un an et améliorer le diagnostic à
distance*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. Objectif

La V2 transforme le POC fiable en application de voyage durable :
meilleure gestion d’énergie, diagnostic, auto-récupération et
observabilité côté appareil.

# 1 bis. Ce qui est déjà là, et ce qui ne l’est pas

*Ajouté le 2 septembre 2026. Ce document est un plan, pas un état — mais une
partie de son contenu est arrivée en V1, souvent parce que le terrain l’a
exigé plus tôt que prévu. Sans ce repère, il se lit comme un inventaire de
choses à faire dont la moitié est faite.*

| §2 — Amélioration | État |
|---|---|
| Écran diagnostic complet | **Fait en V1.** ADR-007 l’a rendu nécessaire : c’est par lui que la voyageuse apprend, seule, que son suivi s’est arrêté. |
| Persistance de la configuration utilisateur | **Fait en V1.** |
| Fréquence modifiable depuis les réglages | **Fait en V1**, avec des bornes plutôt qu’une liste fermée (`arch/01` §2). |
| Désactivation complète du tracking sans perte locale | **Fait en V1.** |
| Gestion explicite des états de permissions | **Fait en V1.** |
| Détection des synchronisations bloquées | **Fait en V1.** Le watchdog d’ADR-003 et d’ADR-007 §3.2. |
| Optimisation progressive de `LocationManager` | **Fait autrement.** ADR-008 a confié la cadence au flux de localisation après l’échec de T1, ce qui n’était pas le réglage progressif prévu ici mais un changement de mécanisme. |
| Rotation du token appareil | **Fait en V1**, par réactivation (ADR-004, `arch/20` §2.2). |
| Diagnostic de confidentialité | **Fait en V1.** Aucun SDK tiers, `EventLog` incapable de recevoir une coordonnée, tâche `checkCoreIsFrameworkFree`. |
| Migrations Room versionnées | **À moitié.** `exportSchema` est actif et le schéma v1 est commité ; aucune migration n’a encore été écrite, faute de v2. |
| Stratégie adaptée au mouvement et à la batterie | **Non fait.** Aucune `SamplingPolicy` n’existe. |
| Heartbeat périodique indépendant des points GPS | **Non fait.** Il n’existe pas d’endpoint serveur pour le recevoir (`arch/04` §4). |
| Rétention locale configurable | **Non fait, et délibérément.** ADR-005 interdit toute suppression en V1 ; le DAO n’expose même pas de `DELETE`. |
| Journal technique local limité et exportable | **Non fait.** `EventLog` écrit dans le journal Android, rien ne le persiste ni ne l’exporte. |
| Mécanisme de mise à jour testé avant le départ | **Non fait sur l’appareil du voyage.** `PackageReplacedReceiver` existe, mais `arch/20` §1 rappelle qu’aucun test T1-T4 n’a été passé sur le Redmi. |

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
