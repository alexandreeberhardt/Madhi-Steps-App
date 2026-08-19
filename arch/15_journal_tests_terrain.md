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

À partir de 00:31:02 UTC, les positions ont été enregistrées **toutes les dix
secondes** au lieu de cinq minutes, et cela a duré environ treize minutes,
jusqu'à 00:44. Une quarantaine de positions inutiles.

    00:27:48   —        (test de fin d'onboarding)
    00:30:52   +184 s   (« Démarrer le suivi »)
    00:31:02   +10 s    ┐
    …                   ├ cadence de 9 à 11 s, sans interruption
    00:44:09   +10 s    ┘
    puis retour à 252 s après réinstallation de l'APK

Sur 35 écarts mesurés en base, 31 étaient compris entre 9 et 11 secondes.

**Ce qui est établi :**

- L'alarme était bien reprogrammée avec un délai proche de zéro. Le système
  affichait `whenElapsed=+2s177ms` pour la prochaine capture, et cinquante
  réveils cumulés du receveur d'alarme.
- Le phénomène a démarré pendant une séquence de réinstallation d'APK, arrêt
  forcé et relances rapprochées de l'application.
- Il **ne s'est pas arrêté seul** : il a cessé net à la réinstallation
  suivante, ce qui oriente vers un état accumulé plutôt que vers un défaut de
  la logique nominale.
- Après réinstallation, le délai demandé est correct : `CAPTURE_SCHEDULED dans
  252s`, soit l'intervalle de cinq minutes moins le temps déjà écoulé.

**Hypothèse écartée par la mesure :** une avance de l'horodatage GPS sur
l'horloge du téléphone, qui aurait fait renvoyer zéro à
`CaptureSchedule.delayUntilNext`. L'écart mesuré était de +1,8 s, donc positif.

**Hypothèses restantes**, par ordre de vraisemblance :

1. Plusieurs exécutions de `SyncWorker` empilées, chacune appelant
   `RestoreTracking(WATCHDOG)` donc `scheduleNext`. Le minimum de backoff de
   WorkManager est justement de dix secondes.
2. Des `PendingIntent` d'alarme dupliqués malgré `FLAG_UPDATE_CURRENT`, par
   exemple si l'ancien processus survivait à la réinstallation.
3. Un chevauchement entre `PACKAGE_REPLACED` et `APP_OPENED` produisant un
   délai calculé à partir d'un état de base non encore visible.

**Pourquoi c'est important** : à dix secondes, le GPS reste allumé en
permanence. Une nuit dans cet état vide la batterie.

**Instrumentation ajoutée pour trancher** : `AlarmCaptureScheduler` journalise
désormais le délai réellement demandé (`CAPTURE_SCHEDULED dans Xs`). Si le
phénomène revient, ce journal dira immédiatement si le délai demandé est
aberrant, ou si l'alarme se déclenche plus souvent que demandé — deux causes
très différentes.

**Comment reproduire** : réinstaller l'APK pendant que le suivi tourne, forcer
l'arrêt, relancer l'application plusieurs fois de suite, puis lire les délais
journalisés.

## Autres observations

**L'alarme a commencé à être différée.** En fin de session, le système
affichait `windowLength 31058` et un retard de vingt-cinq secondes sur notre
alarme exacte, alors qu'elle était à `windowLength=0` au démarrage. À
surveiller sur la durée : c'est le premier signe d'un throttling.

**Le filtre `-s` de logcat ne fonctionne pas sur cet appareil.** `adb logcat
-d -s MadhiTracker` ne rend rien alors que les lignes sont bien présentes.
Utiliser `adb logcat -d | grep -i madhitracker`. Cela m'a fait conclure à tort
que l'application n'écrivait plus rien.

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

# Session 2 — T1, nuit du 19 août 2026

## Point de départ

| | |
|---|---|
| Départ (UTC) | 2026-08-19T00:49:02Z |
| Positions déjà en base | 49 |
| Intervalle configuré | 5 min → 12 positions/heure attendues |
| Exemption batterie Android | accordée (`deviceidle whitelist`) |
| Bucket App Standby | 5, exempté |
| Service de premier plan | actif |
| Réglages OxygenOS | les trois appliqués à la main par Alexandre |

Les trois réglages propriétaires ne sont exposés par aucune API publique :
c'est le taux de couverture qui les jugera, pas une vérification directe.

## Résultat — T1 ÉCHOUÉ

**36 % de couverture sur la nuit entière.** 31 positions entre 00:58 et 08:10,
soit 7,2 heures, là où l'intervalle de cinq minutes en attendait 86.
L'écran Diagnostic affichait 25 % sur la dernière heure.

Le seuil d'échec du protocole est 66 %.

### Forme de la nuit

Écarts observés, en minutes :

    0,3  5,8  6,6  7,5  9,2  6,1  19,6  6,0  5,9  0,3  19,8  8,8  7,7
    5,6  8,9  8,9  5,7  5,0  5,1  89,5  20,0  7,0  29,8  9,3  10,0
    9,8  50,0  36,0  7,7  20,4

Deux régimes se superposent :

- une **dérive** de l'intervalle nominal, de 5 vers 6-10 minutes ;
- des **trous francs** de 20, 30, 36, 50 et jusqu'à 89 minutes.

Les écarts ne sont pas des multiples de cinq minutes. Ce n'est donc pas un
GPS qui échoue à intervalle régulier : c'est le déclenchement lui-même qui
dérive.

### Cause établie — l'alarme n'est plus exacte

    windowLength 224998        (elle était à 0 la veille)
    whenElapsed=+3m17s   maxWhenElapsed=+7m2s

Le système peut déclencher l'alarme n'importe quand dans une fenêtre de 225
secondes. Cela explique très exactement la dérive de 5 à 10 minutes.

Ce qui rend le cas intéressant, c'est que **l'application n'a aucun moyen de
le savoir** :

- `USE_EXACT_ALARM` est accordée ;
- `canScheduleExactAlarms()` renvoie `true`, et l'écran Diagnostic affiche
  donc « Alarmes exactes : oui » ;
- le code appelle bien `setExactAndAllowWhileIdle` ;
- et OxygenOS repose l'alarme avec une fenêtre de 225 secondes.

La valeur 225 000 ms n'est pas arbitraire : c'est celle qu'utilise aussi
`com.oplus.nhs`, le service de gestion d'énergie du constructeur. Trente-trois
autres alarmes du système ont bien `windowLength 0` au même moment : nous
sommes donc regroupés dans un lot de gestion d'énergie, pas victimes d'une
politique générale.

L'exemption d'optimisation de batterie a survécu à la nuit, le bucket App
Standby est resté à 5 (exempté), et le service de premier plan tournait
toujours au réveil. Aucun de ces garde-fous n'a suffi.

### Seconde cause probable — les acquisitions qui ne produisent rien

Le compteur de réveils d'alarme est passé d'environ 50 à 107, soit **environ
57 déclenchements** pour **31 positions enregistrées**. À peu près la moitié
des réveils n'aurait donc produit aucun point.

Cette lecture est à confirmer : le compteur de `dumpsys alarm` peut être
remis à zéro, et le journal de l'application avait été effacé du tampon
circulaire avant le réveil.

Un élément va dans le même sens : le système déclare une dernière position
GPS vieille de **sept jours**. Le téléphone a passé la nuit à l'intérieur, où
un fix GPS échoue très souvent.

**Conséquence** : deux problèmes distincts se superposent, et il ne faut pas
corriger le second en croyant corriger le premier.

## À relever au réveil

1. **Taux de couverture** sur l'écran Diagnostic — c'est le verdict.
2. **Nombre de positions** et écarts réels, via la base ou le serveur.
3. **Retour de l'anomalie de cadence** : chercher `CAPTURE_SCHEDULED` dans les
   journaux. Un délai demandé aberrant et une alarme trop fréquente sont deux
   causes différentes, et le journal les distingue désormais.
4. **Report de l'alarme par le système** : `windowLength` et `policyWhenElapsed`
   dans `dumpsys alarm` disent si OxygenOS a commencé à différer.

Critère : au-dessus de 90 % la stratégie d'acquisition tient ; en dessous de
66 % elle doit être revue avant d'aller plus loin.
