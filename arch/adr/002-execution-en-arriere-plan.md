**ADR-002 — Stratégie d'exécution en arrière-plan**

*Statut : Accepté — 2026-08-18*

# Contexte

L'exigence est : une position environ toutes les 5 minutes, écran éteint,
application fermée, pendant un an, en survivant au Doze, à l'App Standby, aux
redémarrages et aux mises à jour d'APK.

Trois faits vérifiés sur la documentation Android officielle conditionnent la
décision :

1. Recevoir `ACTION_BOOT_COMPLETED` **est** une exemption autorisant le démarrage
   d'un foreground service depuis l'arrière-plan.
2. Depuis Android 15, un receiver `BOOT_COMPLETED` ne peut plus démarrer un FGS de
   type `dataSync`, `mediaPlayback`, `mediaProjection` ou `phoneCall`.
   **Le type `location` n'est pas restreint.**
3. Depuis Android 15/16, un FGS de type `location` ne peut pas être créé alors que
   l'application est en arrière-plan **sauf si `ACCESS_BACKGROUND_LOCATION` est
   accordée**.

Un point souvent mal compris et qui écarte la solution naïve : un foreground
service **n'empêche pas la suspension CPU** de l'appareil. Une coroutine qui fait
`delay(5.minutes)` dans un service dérive de plusieurs dizaines de minutes en Doze
profond. Le service maintient le processus vivant et la capacité d'accès
« while-in-use », il ne fournit aucune garantie de réveil.

# Options

**1. Foreground service seul, cadencé par une boucle interne.** Simple, mais la
cadence n'est pas garantie appareil endormi. Rejeté.

**2. `WorkManager` périodique.** L'intervalle minimal d'un `PeriodicWorkRequest`
est de 15 minutes : incompatible avec les valeurs 2, 5 et 10 minutes exigées par
`arch/01` §2. De plus, depuis Android 16, les jobs lancés depuis un foreground
service sont soumis à leurs quotas d'exécution. Rejeté pour l'acquisition,
retenu pour la synchronisation.

**3. `AlarmManager` seul, sans service.** L'alarme réveille l'appareil, mais sans
FGS de type `location` actif, l'accès à la localisation en arrière-plan est
soumis aux restrictions « while-in-use » et devient peu fiable. Rejeté.

**4. FGS `location` persistant + `AlarmManager` comme métronome + acquisition
ponctuelle.** Le service porte la capacité d'accès et la visibilité utilisateur ;
l'alarme porte la garantie de réveil ; l'acquisition est brève et bornée.

# Décision

**Option 4.**

    trackingEnabled = true
            │
            ▼
    TrackingForegroundService  (type "location", notification permanente)
       maintient le processus et la capacité d'accès — aucune logique métier
            │
       programme
            ▼
    AlarmManager.setExactAndAllowWhileIdle(now + interval)   ← traverse le Doze
            │
       déclenche
            ▼
    CaptureAlarmReceiver ──► CaptureLocation (use case)
            │                   acquisition bornée (timeout ~60-90 s)
            │                   validation
            │                   Room : INSERT PENDING     ← la position est sauvée ici
            │                   demande une tentative de synchronisation
            │
       reprogramme l'alarme suivante — toujours, même si l'acquisition a échoué

Un `PARTIAL_WAKE_LOCK` est pris **uniquement** pour la durée acquisition +
écriture, avec un plafond, jamais en permanence.

Permissions retenues et assumées :

| Permission | Justification |
|---|---|
| `ACCESS_FINE_LOCATION` | acquisition GPS |
| `ACCESS_BACKGROUND_LOCATION` | redémarrer le FGS après reboot sans ouvrir l'application (fait 3) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Android 14+ |
| `POST_NOTIFICATIONS` | sans elle, la notification du service est masquée et l'état devient invisible |
| `RECEIVE_BOOT_COMPLETED` | reprise après redémarrage |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | métronome fiable en Doze |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | lever le throttling Doze et les quotas de jobs |
| `INTERNET`, `ACCESS_NETWORK_STATE` | synchronisation |

L'exemption d'optimisation batterie et l'alarme exacte sont des accès sensibles
côté Android : l'application ne peut que **guider** l'utilisatrice vers l'écran
système correspondant, jamais les accorder elle-même. C'est le rôle de l'écran 4
de l'onboarding (`arch/09` §6).

# Conséquences

- Le service Android reste volontairement anémique : il démarre, affiche une
  notification, programme une alarme, s'arrête. Toute la logique vit dans
  `CaptureLocation`, testable sur JVM sans émulateur.
- La reprise après redémarrage est légale sur Android 15+ **à condition** que le
  service reste de type `location`. Ne jamais le déclarer `dataSync`.
- L'exemption d'optimisation batterie est le point de défaillance le plus
  probable du projet. Si l'utilisatrice la refuse ou si le constructeur la
  réinitialise, la cadence se dégrade sans que rien ne plante. Le diagnostic doit
  donc afficher cet état en permanence, et le jalon terrain de la phase 6 doit le
  vérifier sur le téléphone réel.
- L'arrêt forcé (« Forcer l'arrêt » dans les paramètres, ou balayage agressif chez
  certains constructeurs) coupe alarmes et service jusqu'à réouverture manuelle de
  l'application. C'est une limite Android connue, à documenter dans le diagnostic
  sans ajouter d'écran (`arch/01` §8).
- La cadence réelle est « environ » l'intervalle demandé, jamais exactement.
  C'est conforme à l'objectif produit.

# Références

- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/15/changes/foreground-service-types
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/develop/background-work/services/alarms

# Amendement — 2026-08-18

L'appareil cible étant un Xiaomi sous MIUI 14, cet ADR est complété par
[ADR-007](007-contraintes-miui-redmi-note-11.md), qui ajoute la détection du
blocage de démarrage automatique, la résurrection du service par WorkManager et
l'onboarding spécifique MIUI. Les décisions ci-dessus restent valables telles
quelles ; MIUI ne les remet pas en cause, il les rend insuffisantes à elles seules.

# Amendement — 2026-08-19

Le métronome décrit ci-dessus **ne fonctionne pas** sur les surcouches
constructeur. Le test T1 a mesuré 36 % de couverture sur OxygenOS, l'alarme
demandée exacte étant reposée avec une fenêtre de 225 secondes.

La cadence est désormais confiée au fournisseur de localisation :
voir [ADR-008](008-cadence-par-le-flux-de-localisation.md). L'alarme décrite
ici subsiste comme filet de sécurité, plus comme mécanisme principal.

Le reste de cet ADR — service de premier plan de type `location`, permissions,
exemption d'optimisation de batterie, reprise après redémarrage — reste
valable et nécessaire.
