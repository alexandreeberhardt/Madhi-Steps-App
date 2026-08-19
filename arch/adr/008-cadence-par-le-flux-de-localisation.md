**ADR-008 — Confier la cadence au fournisseur de localisation**

*Statut : Accepté — 2026-08-19. Remplace le métronome de l'ADR-002.*

# Contexte

L'ADR-002 confiait la cadence à `AlarmManager`, via
`setExactAndAllowWhileIdle`, au motif qu'une alarme exacte traverse le Doze.
Le test T1 a démontré que ce raisonnement ne tient pas sur les surcouches
constructeur.

Sur OnePlus 8T sous OxygenOS 14, après une nuit de sept heures :

- **36 % de couverture** — 31 positions au lieu de 86 ;
- l'alarme demandée exacte revenait avec `windowLength 224998`, soit une
  fenêtre de 225 secondes ;
- des trous francs de 20 à 89 minutes s'y ajoutaient.

Le plus gênant est que **l'application ne pouvait pas le détecter** :
`USE_EXACT_ALARM` était accordée, `canScheduleExactAlarms()` renvoyait `true`,
l'écran de diagnostic affichait « Alarmes exactes : oui », et le code appelait
bien `setExactAndAllowWhileIdle`. Le système acceptait l'appel et l'ignorait.

La valeur de 225 secondes est celle qu'utilise `com.oplus.nhs`, le service de
gestion d'énergie du constructeur, alors que trente-trois autres alarmes du
système conservaient `windowLength 0` au même instant. Nous étions donc
regroupés dans un lot d'économie d'énergie.

Ni l'exemption d'optimisation de batterie, ni le bucket App Standby exempté,
ni le service de premier plan toujours vivant n'ont suffi.

# Options

**1. Verrou de réveil permanent et minuteur interne.** Cadence exacte,
totalement indépendante du système. Écartée : maintenir le processeur éveillé
en permanence sur un téléphone qui doit tenir une journée de vélo est
exactement le compromis que le projet refuse.

**2. Confier la cadence au fournisseur de localisation.** S'abonner en continu
avec un intervalle, et laisser le sous-système livrer les positions. Il est
plus privilégié qu'`AlarmManager` pour une application portant un service de
premier plan de type `location`, et il sait éteindre le récepteur entre deux
points — ce qu'une boucle applicative ne saurait pas faire.

**3. Accepter la dérive.** Écartée : un trou de 89 minutes représente une
trentaine de kilomètres manquants sur le tracé.

# Décision

**Option 2.**

    trackingEnabled = true
            │
            ▼
    TrackingForegroundService
            │
       souscrit
            ▼
    LocationSource.stream(interval)      ← LocationRequestCompat
            │                              QUALITY_BALANCED_POWER_ACCURACY
       chaque position
            ▼
    RecordLocation → validation → Room → demande de synchronisation

L'abonnement porte sur **les deux fournisseurs**, GPS et réseau. T1 a passé
une nuit entière sans qu'un fix GPS aboutisse en intérieur ; une position
réseau approximative vaut mieux qu'un trou.

`AlarmManager` **reste**, mais change de rôle : il ne capture plus
systématiquement. Il vérifie, à trois fois l'intervalle, qu'une position est
bien arrivée récemment, et n'acquiert que si le flux s'est tu au-delà de deux
intervalles. Un abonnement peut mourir sans que le service meure : ce serait
une panne parfaitement silencieuse.

`RecordLocation` a été extrait de `CaptureLocation` pour que le flux continu
et l'acquisition ponctuelle empruntent exactement le même chemin. Deux
versions de cette séquence finiraient par diverger sur un détail qui coûterait
des positions.

# Conséquences

- Le suivi ne dépend plus d'un mécanisme que le constructeur peut brider en
  silence. Il reste soumis aux restrictions de localisation, mais celles-ci
  sont observables : `dumpsys location` montre la requête active et son
  intervalle.
- **Le coût en batterie n'est pas connu.** C'est le point à mesurer : le
  système est censé éteindre le récepteur entre deux points, mais le
  comportement dépend du matériel. Un test de nuit avec relevé de la
  consommation est nécessaire avant de conclure.
- L'écran de diagnostic ne peut toujours pas dire si l'alarme est réellement
  exacte. Cette ligne devient secondaire puisque l'alarme n'est plus critique,
  mais elle reste trompeuse et devra être reformulée.
- La décision est à revalider sur le Redmi Note 11 : MIUI n'applique pas les
  mêmes politiques qu'OxygenOS.
