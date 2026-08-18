**ADR-007 — Contraintes MIUI sur l'appareil cible**

*Statut : Accepté — 2026-08-18*

# Contexte

L'appareil réel est connu et c'est le pire cas de figure possible pour du suivi
en arrière-plan :

| | |
|---|---|
| Modèle | Xiaomi Redmi Note 11 4G |
| Android | 13 (API 33) |
| Surcouche | **MIUI Global 14.0.1** |
| SoC | Snapdragon 680 |
| RAM | **4 Go** |
| Stockage | 128 Go |

Trois faits documentés changent la conception :

**1. MIUI bloque `BOOT_COMPLETED` par défaut.** Tant que la permission
« Démarrage auto en arrière-plan » (*Background autostart*) n'est pas accordée
manuellement, le `BootReceiver` n'est **jamais** appelé. La reprise après
redémarrage décrite par `arch/01` §12 ne fonctionne tout simplement pas, sans
aucune erreur visible. Ce n'est pas une dégradation, c'est une panne silencieuse.

**2. MIUI empile sa propre couche d'économie d'énergie par-dessus celle d'Android.**
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ne la couvre pas. Il faut en plus régler
l'économiseur de batterie **par application** sur « Aucune restriction ».

**3. MIUI tue les services de premier plan lorsque l'application est balayée
depuis les applications récentes**, et le fait d'autant plus volontiers que la
RAM est faible. 4 Go partagés avec MIUI signifie une pression mémoire constante
et une mort de processus fréquente.

# Options

**1. Compter sur les permissions standard Android.** C'est ce qui était prévu par
ADR-002. Sur MIUI, cela produit une application qui semble fonctionner, s'arrête
au premier redémarrage, et ne le signale pas. Inacceptable pour un voyage d'un an.

**2. Détecter les réglages MIUI par réflexion** (`AppOpsManager`, code
d'opération `OP_AUTO_START`). Non documenté, différent selon les versions de MIUI,
susceptible de casser à la première mise à jour de la surcouche. Rejeté : on ne
bâtit pas la fiabilité d'un voyage d'un an sur de la réflexion non documentée.

**3. Détecter le symptôme plutôt que le réglage**, et guider explicitement.

# Décision

## 3.1 Détection du blocage au démarrage — sans réflexion

Plutôt que d'interroger un réglage propriétaire, on observe le seul fait qui
compte : *est-ce que notre code s'est réveillé après le dernier redémarrage ?*

    à chaque capture et à chaque ouverture de l'application :
        persister  lastSeenUptime     = SystemClock.elapsedRealtime()
                   lastSeenWallClock  = Clock.now()

    le BootReceiver, quand il est appelé, persiste bootHandledAt

    un redémarrage a eu lieu si :
        elapsedRealtime() < (now - lastSeenWallClock)
        c'est-à-dire : le téléphone tourne depuis moins longtemps
        que le temps écoulé depuis notre dernier signe de vie

    si un redémarrage a eu lieu et qu'aucun bootHandledAt ne lui correspond
        → le démarrage automatique est bloqué
        → état « Action nécessaire », avec l'instruction MIUI exacte

Cette détection est indépendante de la version de MIUI, du constructeur et de
toute API privée. Elle fonctionnerait aussi bien sur Samsung ou Oppo.

## 3.2 Résurrection par WorkManager

`arch/01` §8 exige déjà un watchdog. Il prend ici une seconde responsabilité :

    SyncWorker périodique (15 min, WorkManager)
        1. synchroniser les points en attente
        2. si trackingEnabled et que le service de suivi ne tourne pas
           → le redémarrer

Le point 2 n'est possible que grâce à l'exemption d'optimisation de batterie :
elle figure explicitement dans la liste des exemptions autorisant le démarrage
d'un foreground service depuis l'arrière-plan. **L'exemption batterie ne sert donc
pas seulement au Doze : c'est elle qui rend la résurrection possible.** C'est la
permission la plus importante de l'application.

Le service est déclaré `START_STICKY`. MIUI l'ignore souvent ; cela ne coûte rien.

## 3.3 Détection des trous de suivi

Le nombre de captures attendues entre deux points connus est calculable à partir
de l'intervalle configuré. Si le nombre réel est très inférieur, un trou est
enregistré dans le journal de diagnostic, avec sa durée. C'est l'instrument de
mesure du jalon terrain : sans lui, on ne saura pas *si* MIUI nous tue, seulement
que « ça marche à peu près ».

## 3.4 Onboarding spécifique MIUI

L'écran 4 de l'onboarding (`arch/09` §6) devient l'écran le plus important de
l'application. Il liste, dans cet ordre, avec un lien direct vers l'écran système
quand c'est possible et une capture d'écran sinon :

| # | Réglage MIUI | Chemin | Sans lui |
|---|---|---|---|
| 1 | **Démarrage auto en arrière-plan** | Paramètres › Applications › Madhi Tracker › Autorisations › Démarrage auto | pas de reprise après redémarrage |
| 2 | **Économiseur de batterie : aucune restriction** | Paramètres › Applications › Madhi Tracker › Économiseur de batterie › Aucune restriction | suivi tué en veille |
| 3 | **Optimisation de batterie Android : ignorée** | demandée par l'application via intent standard | throttling Doze, pas de résurrection |
| 4 | **Verrouiller dans les applications récentes** | applications récentes › tirer la vignette vers le bas › cadenas | tuée au balayage |
| 5 | **Notifications autorisées** | demandée par l'application (Android 13) | l'état du suivi devient invisible |

Le lien direct vers l'écran de démarrage auto de MIUI utilise le composant
`com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`.
Ce composant n'est pas documenté par Xiaomi : l'appel est **toujours** encapsulé
dans un `try`/`catch` avec repli sur `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
Aucune fonctionnalité ne dépend de sa présence.

En dernier recours seulement, et uniquement si les tests terrain montrent que MIUI
tue quand même l'application : désactiver « Optimisations MIUI » dans les options
pour développeurs. Cette étape n'est pas dans l'onboarding — elle est trop
intrusive pour être proposée par défaut.

## 3.5 Niveaux d'API

    minSdk     29   (Android 10) — première version où ACCESS_BACKGROUND_LOCATION
                                   et foregroundServiceType existent nativement.
                                   Aucune branche de compatibilité héritée.
    targetSdk  34   (Android 14)
    compileSdk  dernière stable

`targetSdk 34` mérite justification, puisque la règle habituelle est de viser la
dernière version. Les restrictions Android sont majoritairement conditionnées au
`targetSdk` **et** à la version de l'OS d'exécution. L'appareil tourne sous
Android 13 : viser 34 ou 36 ne change strictement rien à son comportement. En
revanche, viser 34 évite d'hériter du durcissement des quotas de jobs d'Android 16
si l'appareil devait être remplacé en cours de voyage (`arch/02` §8). L'application
n'est pas distribuée par le Play Store : aucune obligation de `targetSdk` ne
s'applique.

`USE_EXACT_ALARM` est déclarée en plus de `SCHEDULE_EXACT_ALARM`. Sur le Play
Store, cette permission est réservée aux applications de réveil et d'agenda ;
hors store, elle est simplement accordée, et garantit le métronome sans action
utilisateur. Sur Android 13, `SCHEDULE_EXACT_ALARM` est de toute façon
pré-accordée.

# Conséquences

- **Le jalon terrain de la phase 6 devient le point de décision du projet.** Si
  MIUI tue le suivi malgré les cinq réglages, il faudra reconsidérer la stratégie
  d'acquisition avant d'aller plus loin. Le détecteur de trous (§3.3) est ce qui
  rendra cette réponse factuelle plutôt qu'impressionniste.
- Trois des cinq réglages ne peuvent pas être accordés par l'application. Ils
  doivent être **vérifiés physiquement sur le téléphone avant le départ**, et
  refaits après toute mise à jour de MIUI — une montée de version peut les
  réinitialiser. À ajouter à la liste de contrôle du départ.
- L'état « Action nécessaire » de `arch/09` §4 n'est plus un cas limite : c'est le
  mécanisme par lequel la voyageuse apprend, seule et sans nous, que son suivi
  s'est arrêté. Son texte doit dire quoi faire, pas ce qui ne va pas.
- 4 Go de RAM : aucun état critique ne vit en mémoire. Tout ce qui compte est dans
  Room ou DataStore, relu à chaque réveil. C'était déjà le cas par construction.
- La détection §3.1 et le watchdog §3.2 sont internes. Conformément à `arch/01` §8,
  ils n'ajoutent ni écran, ni bouton, ni information permanente sur l'accueil.

# Références

- https://dontkillmyapp.com/xiaomi
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
