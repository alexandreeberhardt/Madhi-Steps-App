**14 — Protocole de test terrain**

*Prouver que le suivi survit à Android et aux surcouches constructeur*

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# 1. Pourquoi ce document

Les tests automatisés démontrent que la logique est correcte. Ils ne
démontrent pas qu'Android la laisse s'exécuter. Le Doze, l'App Standby et
les tueurs de processus des constructeurs ne sont simulables ni par
Robolectric ni par un émulateur.

Ce protocole est donc la seule preuve réelle que le projet fonctionne. Tant
qu'il n'est pas passé, tout le reste repose sur une hypothèse.

# 2. Appareils

| | Appareil de pré-validation | Appareil cible |
|---|---|---|
| Modèle | OnePlus 8T (KB2005) | Xiaomi Redmi Note 11 4G |
| Android | 14 (API 34) | 13 (API 33) |
| Surcouche | OxygenOS 14 | MIUI Global 14.0.1 |
| RAM | — | 4 Go |

Le OnePlus n'est pas l'appareil du voyage, mais il valide l'essentiel :

- Android 14 correspond exactement au `targetSdk 34` de l'application. Les
  restrictions conditionnées au `targetSdk` s'appliquent donc réellement,
  ce que le Redmi sous Android 13 ne fera pas. C'est le test **le plus
  sévère** des deux sur ce point.
- OxygenOS tue les processus en arrière-plan de façon comparable à MIUI.
  Les réglages diffèrent, le mécanisme non.

Ce que le OnePlus **ne** valide **pas** : le blocage du démarrage
automatique propre à MIUI, la pression mémoire à 4 Go, et le comportement
du Snapdragon 680. Ces points restent à vérifier sur le Redmi.

# 3. Réglages à appliquer avant tout test

## 3.1 OnePlus — OxygenOS 14

| # | Réglage | Chemin | Sans lui |
|---|---|---|---|
| 1 | **Optimisation de batterie : ne pas optimiser** | Paramètres › Batterie › Optimisation de la batterie › Toutes les applications › Madhi Tracker › Ne pas optimiser | cadence qui dérive, watchdog inopérant |
| 2 | **Optimisation avancée désactivée** | Paramètres › Batterie › Optimisation de la batterie › ⋮ › Optimisation avancée → désactiver « Optimisation poussée » et « Optimisation de la veille » | le principal tueur d'applications |
| 3 | **Lancement automatique autorisé** | Paramètres › Applications › Madhi Tracker › lancement automatique | pas de reprise après redémarrage |
| 4 | **Verrouiller dans les applications récentes** | applications récentes › appui long sur la vignette › cadenas | OxygenOS réinitialise parfois seul le réglage n° 1 ; le verrou l'en empêche |
| 5 | **Notifications autorisées** | demandé par l'application | l'état du suivi devient invisible |

## 3.2 Xiaomi — MIUI 14

Voir `arch/adr/007-contraintes-miui-redmi-note-11.md` §3.4. Le réglage
critique et propre à MIUI est le **démarrage automatique en arrière-plan**,
sans lequel `BOOT_COMPLETED` n'est jamais délivré.

## 3.3 Dans l'application

Ouvrir l'écran Diagnostic et vérifier que la carte **Système** affiche
`oui` partout. Toute ligne en rouge invalide le test qui suivrait.

# 4. Installation

    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Le build debug n'a pas besoin de serveur : sans activation, les positions
s'accumulent en `PENDING`. C'est volontaire — cela teste exactement le
scénario hors ligne prolongé.

# 5. Tests

Chaque test se lit sur l'écran Diagnostic. La ligne **Taux** de la carte
« Couverture » est le verdict : elle compare les points réellement
enregistrés au nombre attendu pour l'intervalle configuré.

## T1 — Quatre heures écran éteint

*Le test fondamental. Si celui-ci échoue, rien d'autre n'a d'importance.*

1. Intervalle : 5 minutes. Démarrer le suivi.
2. Vérifier qu'un premier point apparaît dans la minute.
3. Éteindre l'écran, poser le téléphone, ne pas y toucher pendant 4 h.
4. Rouvrir l'application et lire la couverture.

| Résultat | Verdict |
|---|---|
| ≥ 90 % | conforme |
| 66 – 90 % | acceptable, à surveiller sur une durée plus longue |
| < 66 % | **échec** — la stratégie d'acquisition doit être revue avant d'aller plus loin |

Poser le téléphone immobile est important : le Doze ne s'active pleinement
que sur un appareil stationnaire, écran éteint et débranché. Un téléphone
en mouvement dans une sacoche à vélo est un cas **plus** favorable.

## T2 — Mode avion prolongé

1. Suivi actif, mode avion pendant 4 h minimum.
2. Vérifier : les points continuent de s'enregistrer, `Points en attente`
   augmente régulièrement.
3. Désactiver le mode avion.
4. Sans serveur, les points restent en attente — c'est attendu. Le critère
   est qu'**aucun point ne disparaisse** : le compteur ne doit jamais
   diminuer.

## T3 — Redémarrage

1. Suivi actif, noter le nombre de points en attente.
2. Redémarrer le téléphone. Ne pas ouvrir l'application.
3. Attendre 30 minutes.
4. Ouvrir l'application.

| Vérification | Attendu |
|---|---|
| Points en attente | au moins égal à avant le redémarrage, jamais moins |
| Nouveaux points pendant les 30 min | présents si la reprise a fonctionné |
| Ligne « Action nécessaire » | si `AUTOSTART_BLOCKED` apparaît, la détection a fait son travail et le réglage n° 3 est en cause |

## T4 — Arrêt forcé

1. Paramètres › Applications › Madhi Tracker › Forcer l'arrêt.
2. Attendre 30 minutes sans ouvrir l'application.
3. Ouvrir l'application.

L'arrêt forcé coupe alarmes et service jusqu'à réouverture manuelle : c'est
une limite Android connue et documentée (`arch/01` §8), pas un défaut à
corriger. Le critère est qu'**aucun point ne soit perdu** et que le suivi
reprenne à l'ouverture.

## T5 — Économie d'énergie

1. Activer le mode économie d'énergie du système.
2. Suivi actif, écran éteint, 2 h.
3. Relever la couverture et la comparer à T1.

Ce test mesure une dégradation attendue. Il sert à savoir quoi dire à la
voyageuse, pas à faire échouer le build.

## T6 — Durée longue

Une fois T1 à T4 passés : laisser tourner **7 jours** en usage normal.

Relever chaque jour la couverture et le nombre de points en attente. C'est
le seul test qui révèle les dérives lentes — fuite de mémoire, alarme
perdue une fois par jour, réinitialisation d'un réglage par la surcouche.

# 6. Ce qu'on observe pendant les tests

L'application journalise ses événements sous une étiquette unique :

    adb logcat -s MadhiTracker

Les événements attendus sont `LOCATION_ACQUIRED`, `LOCATION_SAVED`,
`ACQUISITION_FAILED`, `SYNC_STARTED`, `SYNC_FAILED`. Aucune coordonnée n'y
figure jamais : la signature du journal l'interdit.

# 7. Critère de sortie

Le projet ne passe aux fonctionnalités suivantes que lorsque **T1, T2, T3
et T4 sont passés sur le Redmi Note 11 réel**, avec les cinq réglages MIUI
appliqués.

Une réussite sur le OnePlus est encourageante mais ne suffit pas : c'est le
Redmi qui part en voyage.
