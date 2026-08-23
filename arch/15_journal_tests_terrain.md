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

# Session 3 — T1-bis, avec la cadence par le flux de localisation

## Ce qui a changé depuis T1

La cadence n'est plus portée par `AlarmManager` mais par le fournisseur de
localisation (ADR-008). L'alarme subsiste comme filet de sécurité à trois fois
l'intervalle.

Confirmé sur l'appareil avant le départ :

    STREAM_STARTED gps+network
    CAPTURE_SCHEDULED dans 900s
    ProviderRequest[@+5m0s0ms, WorkSource{10416 com.madhi.tracker.debug}]

Le tampon de logs a été porté à 8 Mo (`adb logcat -G 8M`) : une nuit entière
tiendra, ce qui manquait à T1 pour départager les causes.

## Point de départ

| | |
|---|---|
| Heure (UTC) | 2026-08-19T08:41:19Z |
| **Batterie** | **77 %**, sur batterie |
| Positions en base | 82, dont 33 en attente |
| Service de premier plan | actif |
| Requête de localisation | active, intervalle 5 min |
| Exemption batterie | accordée |

## Ce qu'on cherche à mesurer

Deux chiffres, pas un :

1. **Couverture** — au-dessus de 90 %, la voie du flux est validée sur cet
   appareil. Entre 66 et 90 %, à confirmer sur plus long. En dessous de 66 %,
   il faudra reconsidérer, et l'option du verrou de réveil permanent
   reviendra sur la table malgré son coût.
2. **Consommation** — c'est le chiffre qui manquait à T1, et la raison même
   d'avoir écarté le verrou de réveil. Un suivi parfait qui vide la batterie
   en une nuit ne sert à rien.

## Résultat — le problème s'est inversé

**205 % de couverture, aucun trou.** 81 positions entre 08:42 et 12:00 UTC,
soit 3,30 heures, là où la cadence de cinq minutes en attendait 39.

    distribution des écarts, en minutes :
      2 min → 32 fois    0 min → 17 fois    4 min → 15 fois
      3 min →  8 fois    1 min →  5 fois    5 min →  3 fois

    écarts supérieurs à 15 minutes : aucun

La voie du flux **règle le problème de T1**. Là où la veille on relevait des
trous de 20, 30, 50 et 89 minutes, il n'en reste aucun au-delà de 15 minutes.
La cadence n'est plus trop lente : elle est deux fois trop rapide.

### Cause — deux abonnements, deux livraisons

    ProviderRequest[@+5m0s0ms, WorkSource{10416 com.madhi.tracker.debug}]
    ProviderRequest[@+5m0s0ms, WorkSource{10416 com.madhi.tracker.debug}]

L'application s'abonne aux deux fournisseurs, GPS et réseau, et chacun livre
indépendamment à la cadence demandée. D'où environ 2,5 minutes effectives.

Un second facteur venait de notre code : `setMinUpdateIntervalMillis` était
réglé à la moitié de l'intervalle, ce qui **autorise explicitement** une
livraison deux fois plus rapide. L'intention était l'inverse.

### La consommation observée n'est pas imputable au suivi

La batterie est passée de 77 % à 20 % pendant la mesure, ce qui semblait
accablant. Les statistiques disent autre chose :

    Screen on discharge:  8246 mAh      → 99 % de la consommation
    Screen off discharge:   88.0 mAh

Le téléphone a été utilisé écran allumé toute la matinée. La consommation
écran éteint, la seule qui concerne le suivi, est négligeable.

**La mesure de consommation reste donc entièrement à faire.** C'est le seul
point de l'architecture encore inconnu, et la raison même d'avoir écarté le
verrou de réveil permanent.

### Corrections apportées

- `CaptureThrottle` écarte une position arrivant à moins de 80 % de
  l'intervalle depuis la précédente, ainsi qu'une position antérieure au
  dernier point — un doublon livré en retard par l'autre fournisseur. Le
  seuil est volontairement tolérant : écarter un point de trop créerait un
  trou, alors qu'en écarter un est sans conséquence.
- `setMinUpdateIntervalMillis` passe à l'intervalle complet.

**Ces corrections ne sont pas vérifiées sur appareil.** Elles sont couvertes
par des tests unitaires et installées, mais une seule position a été
enregistrée depuis l'installation : trop peu pour mesurer quoi que ce soit.
C'est le premier point à contrôler à la prochaine session.

## Travail de test conduit en parallèle

Un agent a ajouté 49 tests et trouvé **trois défauts réels**, tous sur le
chemin anti-perte :

| Défaut | Ce qu'il cassait |
|---|---|
| `WHERE sync_state = 'PENDING'` dans le DAO | une ligne au `sync_state` corrompu devenait invisible pour `oldestPending` et `pendingCount` : jamais envoyée, silencieusement. Le mapping domaine se défendait déjà, la requête SQL non. Remplacé par `!= 'SYNCED'` |
| `"Bearer null"` | un token indéchiffrable produisait littéralement cette chaîne dans l'en-tête. Le serveur aurait répondu 401 et le diagnostic aurait accusé l'authentification au lieu d'un token corrompu |
| `TrackLocations` ignorait `enabled` | si le service survivait à une désactivation, le flux aurait continué d'enregistrer — en violation directe de « désactiver le tracking arrête la collecte » |

Il a également protégé l'appel à `recordLocation` dans le `collect` : une
exception y aurait terminé le flux, donc arrêté le suivi en silence.

Réserve à traiter : ce `runCatching` n'écrit rien dans le journal. Si la
planification échouait systématiquement, personne ne le saurait — exactement
le type de panne muette que le projet cherche à éliminer.

## Points de vigilance

- Le test précédent s'est déroulé à l'intérieur, où aucun fix GPS n'a abouti
  de la nuit. Placer le téléphone près d'une fenêtre rendra la mesure plus
  représentative d'un usage à vélo.
- Vérifier au réveil que l'abonnement au flux a survécu : chercher
  `STREAM_STOPPED` sans `STREAM_STARTED` qui suive.
- Vérifier si le filet a dû intervenir : la présence de `STREAM_SILENT`
  signalerait que le flux se tait par moments.

# Session 4 — 19 août 2026, premier contact avec le serveur réel

*Première session sur un build release, et première fois que l'application
parle au vrai serveur plutôt qu'au simulateur.*

## Conditions

| | |
|---|---|
| Appareil | OnePlus 8T KB2005, Android 14 |
| Rôle | pré-validation — l'appareil du voyage reste le Redmi Note 11 |
| Build | `0.1.0` **release**, signé, minifié par R8 — jamais construit auparavant |
| Serveur | `https://madhi-server.alexeber.fr`, POC réel en HTTPS |
| Connexion | débogage sans fil |
| Durée | environ 18 h 00 à 18 h 35, application au premier plan |

Les deux jalons de la session sont indépendants et se valident mutuellement :
l'APK qui part réellement en voyage, et le serveur qui recevra ses positions
pendant un an. Ni l'un ni l'autre n'avait jamais servi.

## Ce qui a été validé

**Le serveur déployé respecte le contrat `arch/13`**, sondé endpoint par
endpoint depuis l'extérieur :

    code d'activation malformé   → 400 invalid_activation_code
    code inconnu                 → 410 expired_or_unknown_code
    batch sans token / token faux→ 401 unauthorized
    lecture d'un trip au hasard  → 403 forbidden
    payload invalide             → 400 invalid_payload
    /_control/state              → 404  (le simulateur ne répond plus)
    http://                      → 301 vers https, certificat Let's Encrypt

**La chaîne complète, contre le serveur réel, deux fois** — une fois sur le
build debug, une fois sur le release :

    DEVICE_ACTIVATED
    LOCATION_ACQUIRED → LOCATION_SAVED → SYNC_STARTED → SYNC_SUCCESS
    TRACKING_STARTED → STREAM_STARTED gps+network

**Le backlog part en une seule fois.** Le build debug portait 104 positions en
attente, accumulées depuis le matin et destinées au simulateur devenu
injoignable. Après réactivation contre le serveur réel, elles sont toutes
parties en **moins de 300 ms**, en un seul lot. La base est passée de 104 en
attente à zéro. Cela éprouve d'un coup le backlog, le découpage en lots,
l'idempotence et le remplacement du token — avec du volume réel.

**R8 ne casse rien.** C'était l'inconnue principale : kotlinx.serialization,
OkHttp, Room, Hilt et le chiffrement du token par le Keystore survivent tous à
la minification. L'APK passe de 36,9 Mo à 3,3 Mo. L'étiquette `MadhiTracker`
survit aussi dans Logcat, ce qui n'était pas acquis et conditionne toute
l'observabilité des tests terrain.

**Le `CaptureThrottle` de T1-bis, enfin confirmé sur appareil.** Il restait
« non vérifié » depuis la session 3. Sur le release, deux captures
consécutives espacées de **4 min 49 s**, avec `CAPTURE_SCHEDULED dans 299s`.
Plus trace du doublement de cadence dû aux deux fournisseurs.

**Recoupement côté serveur**, indispensable parce que tout le reste est vu du
téléphone :

    OnePlus KB2005 | 107 points | 09:31:17Z → 16:19:08Z
    OnePlus KB2005 |   1 point  | 16:28:57Z

Aucune divergence avec ce que l'application annonçait.

**Deux confirmations au passage** : `TRACKING_SERVICE_REVIVED PACKAGE_REPLACED`
à chaque réinstallation, et l'onboarding qui se rafraîchit seul au retour de
chaque écran système — le correctif `LifecycleEventEffect(ON_RESUME)` de la
session 1 tient dans le release.

## Défauts trouvés et corrigés pendant la session

**1. Le build release ne passait pas du tout.** `arch/01` §2 prévoit que le
téléphone du voyage porte un APK release signé. Personne ne l'avait jamais
construit : `assembleRelease` échouait à la sérialisation du cache de
configuration, parce que la tâche de garde `validateReleaseConfig` lisait
`releaseApiBaseUrl` depuis son `doLast`, ce qui capture le script de build.
L'échec arrivait **avant** R8, donc la minification n'avait jamais tourné non
plus. Corrigé par une copie locale de la valeur.

**2. Le journal de synchronisation n'était jamais écrit.** Après la
synchronisation réussie des 104 points, l'écran Réglages affichait toujours
« Dernier envoi réussi : aucune », alors que le compteur de points en attente,
lui, était bien passé à zéro. `SyncJournalStore` était entièrement en place —
implémentation DataStore, port, fake de test, trois lecteurs qui l'affichent —
et **aucun appelant n'écrivait dedans**. `recordAttempt`, `recordSuccess` et
`recordFailure` n'étaient référencés nulle part hors de leur déclaration.

C'est la panne muette que le projet cherche à éliminer, en pire : pendant un
an, le seul écran qui répond à « est-ce que ça envoie ? » aurait répondu
« jamais rien envoyé » même quand tout marche. `SyncPendingLocations` l'écrit
désormais sur ses quatre sorties, avec quatre tests dont un qui vérifie qu'une
file vide ne fait pas croire à un envoi réussi.

**3. Le rate limiting du serveur se contournait avec un en-tête.** Le limiteur
identifiait le client par `Forwarded` puis `X-Forwarded-For`. Or nginx n'émet
pas le premier et *ajoute* au second : la valeur envoyée par le client se
retrouvait en tête de liste, donc n'importe qui pouvait changer de
compartiment à chaque requête. Il ne lit plus que `X-Real-IP`.

**4. La procédure nginx documentée aurait supprimé HTTPS.** Certbot a réécrit
la configuration sur le VPS pour y ajouter le 443 et la redirection. Le fichier
versionné était resté celui d'amorçage, en HTTP seul, et `SERVER_DEPLOYMENT.md`
disait de le recopier par-dessus.

**5. Le protocole de test visait le mauvais build** — voir ci-dessous.

## Ce qui change le protocole de test

Constaté en installant le release, et reporté dans `arch/14` §4 :

- **L'applicationId du release n'a pas le suffixe `.debug`.** Il s'installe
  donc **à côté** du build de développement au lieu de le remplacer. Deux
  traqueurs se disputeraient le GPS : désinstaller le debug d'abord.
- **Les réglages propriétaires du constructeur sont attachés au paquet.** Ceux
  appliqués au build debug ne protègent pas le release. Les cinq réglages de
  `arch/14` §3 sont à refaire après chaque passage debug → release.
- **`run-as` échoue sur un paquet non débogable.** La procédure de copie de
  `madhi-tracker.db` documentée en session 1 ne s'applique donc plus au build
  qui part. Le taux de couverture se lira sur l'écran Diagnostic, et le
  décompte réel côté serveur.

Ce dernier point est structurant : à partir de maintenant, la base de
l'appareil n'est plus une source d'observation pendant les tests.

## Points en suspens

**`CAPTURE_SCHEDULED dans 0s` au démarrage du suivi.** Au `TRACKING_STARTED`,
un délai nul apparaît, puis `dans 900s` cinq secondes plus tard, et aucune
position n'est enregistrée entre les deux. Le résultat est bon — pas de point
parasite — mais les journaux ne disent pas si le `CaptureThrottle` a écarté la
capture ou si l'alarme a été replanifiée avant de se déclencher. Ce sont deux
causes différentes, et c'est exactement la distinction que l'instrumentation
ajoutée en session 3 devait permettre. À reprendre si l'anomalie de cadence
revient.

**Une centaine de positions ne sera jamais sur le serveur.** Environ 106 points
étaient déjà marqués `SYNCED` contre le simulateur : l'application ne les
renvoie donc jamais. Ils ne vivent plus que dans
`~/madhi-backups/debug-db-final-1820/`. Sans conséquence — ce sont des points
pris à la maison, que `started_at` écartera de toute façon — mais le serveur ne
contient pas tout ce que le téléphone a enregistré ce jour-là.

**Le certificat expire le 17 novembre 2026**, pendant le voyage. Le
`certbot.timer` est armé et a tourné, mais la panne serait silencieuse côté
famille : les positions resteraient en attente sur le téléphone.

**Le healthcheck noie les journaux applicatifs.** Il tape `/health` toutes les
dix secondes ; sur un an, les vrais événements deviendront illisibles.

## État à la clôture

- Build release actif, suivi en cours, service de premier plan (`types=00000008`).
- Exemption d'optimisation de batterie accordée, bucket App Standby à 5.
- **Les trois réglages OxygenOS ne sont pas appliqués** au nouveau paquet
  `com.madhi.tracker`, et cela se voit déjà : l'alarme porte
  `windowLength 674999`, soit une fenêtre de 675 secondes. C'est le symptôme
  exact qui a fait échouer T1 à 36 %. Lancer T1 dans cet état reproduirait
  l'échec sans rien apprendre.
- Batterie descendue de 53 % à 35 % pendant la manipulation, écran allumé.
- 277 tests unitaires verts, `check` et `assembleRelease` passent.

## Reprendre la session

Le mDNS découvre l'appareil deux fois, ce qui fait échouer toute commande sans
sélecteur. Fixer le serial :

    export ANDROID_SERIAL="adb-7130b82a-vwbrhL._adb-tls-connect._tcp"

Un code d'activation est à usage unique et expire en 60 minutes. Le semer
**juste avant** d'en avoir besoin, en recréant le conteneur — un simple
`restart` ne relit pas le `.env` :

    # INITIAL_ACTIVATION_CODE=XXXX-XXXX dans server/.env
    docker compose -f server/docker-compose.yml up -d --force-recreate api
    docker compose -f server/docker-compose.yml logs --tail=50 api | grep server_started

Décompte réel côté serveur, sans token de lecture :

    docker compose -f server/docker-compose.yml exec postgres \
      psql -U madhi -d madhi_tracker -c \
      "select d.name, count(l.id), min(l.recorded_at), max(l.recorded_at)
         from devices d left join locations l on l.device_id = d.id
        group by d.id, d.name order by 3;"

## Avant de lancer T1

1. Appliquer les trois réglages OxygenOS au paquet `com.madhi.tracker`.
2. Charger à 90 %, puis débrancher — c'est la décharge écran éteint qu'on mesure.
3. Poser le téléphone près d'une fenêtre : la nuit de T1 s'est déroulée à
   l'intérieur, où aucun fix GPS n'aboutissait.
4. Relever l'heure de départ, `windowLength` et le compteur de positions.

# Session 5 — 23 août 2026, le trou serveur de trois jours

*Pas une session de test : un relevé ADB pour expliquer un trou de trois jours
côté serveur. Il a livré la première observation directe du défaut le plus
coûteux du projet.*

## Conditions

| | |
|---|---|
| Appareil | OnePlus 8T KB2005, Android 14 |
| Rôle | pré-validation — l'appareil du voyage reste le Redmi Note 11 |
| Build | `0.1.0` release, signé, celui de la session 4 |
| Serveur | `https://madhi-server.alexeber.fr` |
| Objet | comprendre l'absence de positions du 20 au 23 août |

## Le trou observé

Aucune position côté serveur entre le **20 août 14:56:34 UTC** et le
**23 août 13:25:12 UTC**, soit près de trois jours.

## Ce que le relevé a écarté

**Ce n'est pas une perte de points.** La base locale ne contenait aucun point
non synchronisé : le diagnostic affichait « Points en attente : 0 » et « Plus
ancien en attente : jamais ». Aucun échec consécutif, couverture 100 %. La
chaîne de synchronisation a fonctionné pendant tout l'intervalle — elle n'avait
simplement rien à envoyer.

## Ce que le relevé a établi

Trois horodatages suffisent :

    22 août 12:57:07 UTC   redémarrage du téléphone
    23 août 13:25:10 UTC   naissance du processus com.madhi.tracker
    23 août 13:25:11 UTC   création du service de suivi
    23 août 13:25:12 UTC   première position reçue par le serveur

Plus de vingt-quatre heures séparent le redémarrage de la naissance du
processus, et celle-ci coïncide avec un déverrouillage manuel. **L'application
n'a pas redémarré toute seule après le reboot**, et rien ne l'a relancée
jusqu'à ce qu'on la lance à la main.

C'est la première observation directe du défaut d'autostart après redémarrage
en conditions réelles. Jusque-là il n'était qu'une crainte documentée
(`arch/adr/007`).

## Conséquences pour le protocole

- **Le redémarrage automatique devient le critère bloquant de T1**, et doit
  être rejoué sur le Redmi Note 11, dont la surcouche MIUI est plus hostile que
  OxygenOS sur ce point précis.
- Sur un an de voyage, un reboot non rattrapé coûte tout le temps écoulé
  jusqu'au prochain déverrouillage. Ici, vingt-quatre heures. Personne ne
  déverrouille son téléphone tous les jours en bivouac.
- **Méthode à retenir** : devant un trou côté serveur, comparer d'abord l'heure
  de naissance du processus au début du trou. Chercher un bug de
  synchronisation sans avoir fait cette comparaison fait perdre du temps sur la
  mauvaise piste.

## Deux pièges d'exploitation confirmés

- `run-as` échoue sur le build release : la base de l'appareil n'est plus
  observable pendant les tests. Le diagnostic intégré devient la seule fenêtre.
- Le certificat TLS expire le **17 novembre 2026**, en plein voyage.

## Travail conduit le même jour

Le site familial est passé en ligne, et la carte embarquée a été écrite — voir
`arch/17_plan_implementation_site_poc.md` et `arch/18_carte_embarquee_v1.md`.
Aucun des deux ne touche au noyau de suivi.

## Installation de la carte sur le OnePlus, le soir même

Build release signé, installé par-dessus la version du 19 août. Le
`versionCode` n'a pas bougé ; `adb install -r` conserve la base locale, et le
schéma Room est inchangé.

**Le suivi ne repart pas tout seul après une réinstallation.** Android
force-stoppe le paquet à la mise à jour, et rien ne le relance tant que
l'application n'est pas ouverte. Deux réinstallations ont donc produit deux
interruptions de quelques minutes, refermées à la main en lançant l'activité.
C'est le même mécanisme que le défaut de la §« Ce que le relevé a établi »,
vu sous un autre angle : **toute mise à jour de l'APK pendant le voyage doit
être suivie d'une ouverture de l'application.** À écrire dans la procédure de
mise à jour.

**Ce que la carte a montré.** Le tracé réel s'affiche, « Suivi actif », âge de
la dernière position, échelle à 100 km, légende réduite à « Envoyé » — cohérent
avec zéro point en attente. Un défaut d'affichage trouvé et corrigé dans la
foulée : la légende masquait le marqueur de position actuelle.

**Une observation à trancher.** Le tracé affiche un unique segment droit
d'environ 440 km entre un petit groupe de points et la position actuelle, sans
point intermédiaire. Deux lectures possibles, que l'appareil ne permet pas de
départager : un déplacement réel non capturé entre deux positions, ou une
mesure grossière acceptée telle quelle. `LocationValidation` est délibérément
permissive et **ne filtre pas sur la précision** — une position imprécise vaut
mieux qu'un trou, c'est un choix assumé. La carte est simplement le premier
outil qui rend ces points visibles. À regarder sur le site familial, qui a les
`accuracy_m`.
