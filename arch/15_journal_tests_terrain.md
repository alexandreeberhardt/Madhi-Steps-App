**15 — Journal des tests terrain**

*Ce qui a été réellement observé sur appareil, et ce qui reste à prouver*

# Session 1 — 19 août 2026, test câblé

## Conditions

| | |
|---|---|
| Appareil | OnePlus 8T KB2005, Android 14 (API 34), OxygenOS `KB2005_14.0.0.603` |
| Rôle | pré-validation — l'appareil du voyage est un Redmi Note 11 sous MIUI 14 |
| Build | `0.1.0-debug`, `minSdk 29` / `targetSdk 34` / `compileSdk 37` |
| Serveur | serveur de simulation local, via `adb reverse tcp:8080` |
| Connexion | débogage sans fil (le câble USB disponible ne transportait pas de données) |
| Durée | environ 15 minutes, application au premier plan |

L'appareil est sous Android 14, soit exactement le `targetSdk` du projet : les
restrictions conditionnées au `targetSdk` s'appliquent donc réellement, ce que
le Redmi sous Android 13 ne fera pas.

## Ce qui a été validé

**Chaîne complète, de bout en bout.**

    DEVICE_ACTIVATED
    LOCATION_ACQUIRED → LOCATION_SAVED → SYNC_STARTED → SYNC_SUCCESS
    TRACKING_STARTED

Côté serveur :

    activation acceptee : OnePlus KB2005 -> device-c47f390d
    lot #1 : 1 nouveaux, 0 deja connus, 0 refuses (total en base : 1)

**Au niveau système**, ce qui confirme la conception de l'ADR-002 :

    isForeground=true  foregroundId=1  types=00000008     → type LOCATION
    ELAPSED_WAKEUP  CaptureAlarmReceiver  windowLength=0  → alarme exacte
    exactAllowReason=policy_permission
    policyWhenElapsed: device_idle=--                     → le Doze ne la diffère pas

**Détection du constructeur** : l'écran d'économie d'énergie a affiché les
trois étapes propres à OxygenOS, pas des consignes génériques.

**Reprise après mise à jour d'APK**, observée sans l'avoir cherchée. La
réinstallation a produit :

    TRACKING_SERVICE_REVIVED PACKAGE_REPLACED

C'est le critère d'acceptation de `arch/01` §12 sur la mise à jour par APK.

## Défauts trouvés et corrigés pendant la session

**1. L'état système n'était pas relu au retour d'un écran Android.** Après
avoir accordé la localisation, l'onboarding affichait toujours « Autoriser »
sans statut. Il fallait appuyer sur « Vérifier à nouveau ». Corrigé par
`LifecycleEventEffect(ON_RESUME)`, puis revalidé deux fois — au retour de la
page de permission, et au retour des réglages de batterie.

**2. Le bouton principal passait sous la barre de navigation gestuelle.** Un
appui sur « Continuer » restait sans effet ; vingt-cinq pixels plus haut, il
fonctionnait. Les écrans sans `Scaffold` n'avaient aucune marge système.
Corrigé par `safeDrawingPadding()`. Aucun test automatisé ne pouvait le voir.

**3. Le verrou de synchronisation était documenté mais absent.** Le contrat
API §7 prévoit « une synchronisation à la fois, verrou applicatif ». Les logs
ont montré deux `SYNC_STARTED` concurrents. Corrigé par un `Mutex`.

**4. Les niveaux d'API du build ne correspondaient pas à l'ADR-007 §3.5.** Le
fichier portait `minSdk 26` / `targetSdk 36` depuis le premier commit alors
que la décision documentée est `29` / `34`. Corrigé et vérifié dans l'APK
installé.

## Anomalie non expliquée — à reprendre en priorité

Entre 00:31:02 et 00:31:43 UTC, cinq positions ont été enregistrées **à dix
secondes d'intervalle** au lieu de cinq minutes :

    00:27:48   —        (test de fin d'onboarding)
    00:30:52   +184 s   (« Démarrer le suivi »)
    00:31:02   +10 s    ┐
    00:31:12   +10 s    │
    00:31:22   +10 s    ├ rafale
    00:31:32   +10 s    │
    00:31:43   +11 s    ┘
    puis cadence redevenue normale (9 points à 00:37:31)

La rafale s'est arrêtée seule et coïncide exactement avec une séquence de
réinstallation d'APK, arrêt forcé et relances rapprochées de l'application.

**La cause n'est pas établie.** Hypothèses à départager, par ordre de
vraisemblance :

1. Le backoff minimal de WorkManager est de dix secondes. Si `SyncWorker`
   renvoyait `retry` en boucle, chaque réessai appellerait
   `RestoreTracking(WATCHDOG)`, donc `scheduleNext`. Reste à expliquer
   pourquoi cela déclencherait une capture immédiate.
2. Plusieurs appels rapprochés à `RestoreTracking` — `PACKAGE_REPLACED` puis
   `APP_OPENED` — se seraient chevauchés avec une alarme déjà en retard.
3. Un `PendingIntent` dupliqué malgré `FLAG_UPDATE_CURRENT`.

**Pourquoi c'est important** : à dix secondes, le GPS reste allumé en
permanence et l'autonomie s'effondre. Même transitoire, le cas doit être
compris avant le départ.

**Comment le reproduire** : réinstaller l'APK pendant que le suivi tourne,
puis relancer l'application plusieurs fois de suite, et relire les
horodatages en base.

## Autres observations

**L'alarme a commencé à être différée.** En fin de session, le système
affichait `windowLength 31058` et un retard de vingt-cinq secondes sur notre
alarme exacte, alors qu'elle était à `windowLength=0` au démarrage. À
surveiller sur la durée : c'est le premier signe d'un throttling.

**Logcat est devenu muet** en fin de session alors que l'application
continuait d'enregistrer des points. Le filtre `-s MadhiTracker` ne rendait
plus rien. Cause inconnue ; à vérifier avant de s'appuyer sur les logs pour
un test long.

**L'acquisition a réussi instantanément**, `LocationManager` ayant livré un
fix en cache. Une acquisition à froid n'a donc pas encore été éprouvée, et
c'est elle qui décide du délai de quatre-vingt-dix secondes.

**Le repli sur le fournisseur réseau ne se déclenche jamais si le GPS est
activé.** En intérieur ou en canyon urbain, cela peut donner zéro position là
où le réseau en aurait donné une approximative. `arch/01` §4 prévoit cet
ajustement sur l'appareil réel ; attendre les chiffres de couverture de T1
avant d'y toucher.

## État à la clôture

- Suivi actif, service de premier plan en cours, alarme programmée.
- Exemption d'optimisation de batterie accordée.
- Trois réglages OxygenOS **non appliqués** : optimisation avancée,
  lancement automatique, verrou dans les applications récentes.
- Neuf positions enregistrées, toutes confirmées par le serveur, aucune perte.
- `adb reverse tcp:8080` actif ; il tombera à la déconnexion, et les
  positions s'accumuleront alors en attente — ce qui teste le mode hors ligne.

## Tests restants

T1 à T6 du protocole `arch/14_protocole_test_terrain.md` n'ont pas été
exécutés. Le test câblé de cette session ne les remplace pas : il valide la
chaîne fonctionnelle, pas le comportement en veille.

# Reprendre la session

    # serveur de simulation — il affiche un nouveau code d'activation au démarrage
    python3 tools/mock-server/server.py

    # connexion sans fil : associer depuis Options pour développeurs, puis
    adb pair <ip>:<port-association> <code>
    adb connect <ip>:<port-connexion>      # ou laisser mDNS le découvrir
    adb reverse tcp:8080 tcp:8080

    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Le code d'activation est à usage unique : redémarrer le serveur en génère un
nouveau, et l'écran Réglages › Appareil permet de réactiver sans réinstaller.

Pour lire la base de l'appareil, copier **aussi** le fichier `-wal`, sinon la
table paraît vide :

    adb shell "run-as com.madhi.tracker.debug cat databases/madhi-tracker.db" > madhi.db
    adb shell "run-as com.madhi.tracker.debug cat databases/madhi-tracker.db-wal" > madhi.db-wal
