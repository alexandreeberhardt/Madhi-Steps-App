# Relecture des textes visibles

Tout ce qu'une personne lit dans l'application ou sur le site familial, dans
l'ordre où elle le rencontre. Un bloc par moment vécu, pas un bloc par fichier :
une formulation ne se juge qu'à côté de celles qui l'entourent.

## Comment s'en servir

Sous chaque bloc, une ligne `→`. Y réécrire **seulement les lignes à changer**,
en gardant l'étiquette de gauche :

```
→ Corps  Cette application enregistre ta position …
→ Bouton C'est parti
```

Un bloc sans rien sous son `→` est un bloc validé. Une fois la passe finie, la
réécriture dans le code se fait mécaniquement depuis ce document.

## La voix actuelle

Cinq règles se dégagent de ce qui est déjà écrit. Elles valent d'être tenues ou
changées en connaissance de cause, pas défaites bloc par bloc.

1. **Tutoiement, destinataire féminin.** « Réessaie une fois connectée »,
   « Choisis « Toujours autoriser » ». L'application parle à la voyageuse.
2. **Dire quoi faire, pas ce qui ne va pas.** Aucun message ne nomme une
   permission Android ; chacun donne le geste.
3. **Dire ce qui casse sans le réglage.** Toutes les consignes constructeur
   portent leur conséquence — une consigne dont on ne voit pas l'enjeu se saute.
4. **Rassurer sur les positions.** Trois textes répètent, à trois endroits, que
   rien n'est perdu : hors réseau, à la réactivation, à l'arrêt du suivi.
5. **Jamais de temps réel.** Ancienneté (« il y a 3 min ») partout où il s'agit
   du suivi ; heure exacte seulement pour un point du trajet.

Le diagnostic échappe volontairement à tout cela : il est brut, il sera lu à voix
haute au téléphone.

## Sept incohérences à trancher d'abord

Elles traversent plusieurs blocs. Les régler avant la relecture ligne à ligne
évite de reformuler deux fois la même phrase.

| # | Quoi | Où |
|---|---|---|
| I1 | « Désactiver le **tracking** » alors que tout le reste dit « suivi » | `SettingsScreen.kt:134` |
| I2 | Hors ligne : « trajet sauvegardé **sur le téléphone** » sur l'accueil, « trajet sauvegardé » au diagnostic | `MainScreen.kt:235`, `DiagnosticsScreen.kt:99` |
| I3 | Position inconnue : « aucune » dans l'app, « jamais » au diagnostic — deux fonctions jumelles, deux mots | `RelativeTime.kt:22`, `DiagnosticsScreen.kt:231` |
| I4 | Adresse absente : « Adresse indisponible **hors ligne**. » dans l'app, « Adresse indisponible. » sur le site | `TrackMap.kt:472`, `point-bubble.js:52` |
| I5 | Périodes : le site propose « 30 jours », l'app non | `period.js:19`, `MainScreen.kt:150` |
| I6 | Le diagnostic affiche le nom d'énumération brut : « À corriger : BATTERY_OPTIMIZATION_ENABLED » | `DiagnosticsScreen.kt:117` |
| I7 | « Fréquence de localisation » (réglages) vs « Fréquence » (diagnostic) vs « Autre fréquence » (dialogue) | `SettingsScreen.kt:76`, `DiagnosticsScreen.kt:147`, `CaptureIntervalPicker.kt:96` |

---

# Application — première configuration

Vue une seule fois, à l'installation. Six écrans, une demande par écran.

## A1 · Bienvenue
`OnboardingScreen.kt:92` — premier écran après l'installation.

```
Titre   Suivi du voyage
Corps   Cette application enregistre ta position toutes les cinq minutes et
        l'envoie à ta famille dès qu'il y a du réseau. Sans réseau, tout est
        gardé sur le téléphone, rien n'est perdu.

        La configuration prend deux minutes. Elle ne sera à refaire que si tu
        changes de téléphone.
Bouton  Commencer
```

→

## A2 · Localisation
`OnboardingScreen.kt:111`

```
Titre    Localisation
Corps    L'application a besoin d'accéder à ta position pour enregistrer ton
         trajet. C'est sa seule fonction : rien d'autre n'est collecté.
Statut   Autorisation accordée          (si déjà accordée)
Bouton   Autoriser  /  Continuer
Second.  Vérifier à nouveau
```

→

## A3 · Localisation en arrière-plan
`OnboardingScreen.kt:132`

```
Titre    Localisation en arrière-plan
Corps    Android va te demander de choisir « Toujours autoriser ». C'est ce qui
         permet au suivi de continuer quand l'écran est éteint et quand
         l'application est fermée — c'est-à-dire presque tout le temps.

         Sans cette autorisation, le suivi s'arrête dès que tu ranges le
         téléphone.
Statut   Autorisation accordée
Bouton   Autoriser  /  Continuer
Second.  Vérifier à nouveau
```

→

## A4 · Activation
`OnboardingScreen.kt:154`

```
Titre    Activation
Corps    Saisis le code d'activation qui t'a été communiqué. Il relie ce
         téléphone au voyage et n'est utilisable qu'une fois.
Bouton   Activer
Lien     Je n'ai pas de code pour l'instant
```

→

## A5 · Économie d'énergie — en-tête
`OnboardingScreen.kt:189`

```
Titre    Économie d'énergie
Corps    Android et ton téléphone mettent les applications en veille pour
         économiser la batterie. Il faut faire une exception, sinon le suivi
         s'arrête pendant la nuit sans prévenir.
Carte    Exemption Android
         Déjà accordée.
         À accorder : c'est le réglage le plus important de tous.
Bouton   Ouvrir le réglage
Bouton   Ouvrir les réglages de l'application
Boutons  Vérifier   |   Continuer
```

→

## A6 · Économie d'énergie — consignes Xiaomi
`VendorSetupGuidance.kt:26`

```
1  Démarrage auto en arrière-plan
   Paramètres › Applications › Madhi Tracker › Autorisations › Démarrage auto
   Sans lui, le suivi ne repart pas après un redémarrage du téléphone.

2  Économiseur de batterie : aucune restriction
   Paramètres › Applications › Madhi Tracker › Économiseur de batterie › Aucune restriction
   MIUI ajoute sa propre limite, en plus de celle d'Android. Sans ce réglage,
   le suivi est coupé en veille.

3  Verrouiller dans les applications récentes
   Applications récentes › tirer la vignette vers le bas › cadenas
   Sans le verrou, balayer l'application l'arrête.
```

→

## A7 · Économie d'énergie — consignes OnePlus / Oppo
`VendorSetupGuidance.kt:44`

```
1  Optimisation avancée désactivée
   Paramètres › Batterie › Optimisation de la batterie › ⋮ › Optimisation avancée
   Désactiver « Optimisation poussée » et « Optimisation de la veille ». C'est
   le principal tueur d'applications.

2  Lancement automatique
   Paramètres › Applications › Madhi Tracker › lancement automatique
   Sans lui, le suivi ne repart pas après un redémarrage.

3  Verrouiller dans les applications récentes
   Applications récentes › appui long sur la vignette › cadenas
   Le système réinitialise parfois seul l'exemption de batterie ; le verrou
   l'en empêche.
```

→

## A8 · Économie d'énergie — consignes Samsung et Huawei
`VendorSetupGuidance.kt:62`

```
Samsung 1  Retirer des applications en veille
           Paramètres › Batterie › Limites d'utilisation en arrière-plan
           Vérifier que Madhi Tracker n'est ni en veille, ni en veille profonde.

Samsung 2  Désactiver l'optimisation adaptative
           Paramètres › Batterie › Plus de paramètres › Batterie adaptative
           L'apprentissage d'usage finit par restreindre une application
           ouverte rarement.

Huawei 1   Lancement manuel autorisé
           Paramètres › Applications › Madhi Tracker › Lancement › gérer manuellement
           Activer les trois options : lancement auto, lancement secondaire,
           exécution en arrière-plan.
```

→

## A9 · Vérification finale
`OnboardingScreen.kt:249`

```
Titre    Vérification
Corps    On enregistre une position et on tente un envoi, pour vérifier que
         tout fonctionne réellement sur ce téléphone.
Lignes   Position enregistrée        OK / échec
         Envoi au serveur            OK / échec
Échec    Tu peux continuer quand même : les positions sont gardées sur le
         téléphone et repartiront toutes seules. Le détail reste dans l'écran
         Diagnostic.
Bouton   Lancer le test  /  Refaire le test
Bouton   Démarrer le suivi
```

→

---

# Application — activation

## B1 · Erreurs de code
`ActivationForm.kt:66` — sous le champ, dans l'onboarding comme dans les réglages.

```
Champ        Code d'activation
Invalide     Ce code n'est pas valide. Vérifie la saisie.
Expiré       Ce code a expiré ou a déjà été utilisé. Demande-en un nouveau.
Hors réseau  Pas de réseau. Réessaie une fois connectée.
Trop d'essais Trop de tentatives. Attends quelques minutes.
Serveur      Le serveur ne répond pas correctement. Réessaie plus tard.
Inattendu    Erreur inattendue. Réessaie.
```

→

## B2 · Écran de réactivation
`ActivationScreen.kt:43` — atteint depuis les réglages ou le bouton « Corriger ».

```
Titre     Activation
Retour    Retour
Corps     Saisis le code d'activation qui t'a été communiqué. Il relie ce
          téléphone au voyage et n'est utilisable qu'une fois.
Corps'    Ce téléphone est déjà relié au voyage. Saisir un nouveau code le
          reliera à nouveau — utile si les envois sont refusés.
Bouton    Activer  /  Réactiver
Bas       Les positions déjà enregistrées ne sont jamais supprimées par une
          réactivation.
Succès    Appareil activé
          Les positions en attente vont repartir dès qu'il y a du réseau.
Bouton    Terminé
```

→

---

# Application — accueil

L'écran vu tous les jours. Carte en haut, périodes, état du suivi en bas.

## C1 · Barre du haut et périodes
`MainScreen.kt:71`, `MainScreen.kt:150`

```
Titre     Voyage
Action    Réglages
Périodes  Aujourd'hui | 24 h | 7 jours | Tout le voyage
```

→

## C2 · État du suivi
`MainScreen.kt:203`, `MainScreen.kt:232`

```
Chargement  Lecture de l'état…
Actif       Suivi actif
Hors ligne  Hors ligne — trajet sauvegardé sur le téléphone
Problème    Action nécessaire
Arrêté      Suivi arrêté
Ligne       Dernière position : il y a 3 min
Bouton      Démarrer le suivi        (si arrêté)
Bouton      Corriger                 (si problème)
```

→

## C3 · Les neuf problèmes
`MainScreen.kt:246` — une seule s'affiche, la plus urgente, au-dessus de « Corriger ».

```
Non activé      Ce téléphone n'est pas encore relié au voyage. Saisis le code
                d'activation.
Permission      L'accès à la position a été retiré. Il faut le réautoriser.
Arrière-plan    Le suivi s'arrête dès que l'écran s'éteint. Choisis « Toujours
                autoriser ».
GPS éteint      La localisation du téléphone est désactivée. Rallume-la.
Démarrage auto  Le téléphone a redémarré sans relancer le suivi. Autorise le
                démarrage automatique.
Batterie        L'économie d'énergie interrompt le suivi. Il faut faire une
                exception.
Alarmes         Les rappels précis sont bloqués, le suivi devient irrégulier.
Auth refusée    Les positions ne partent plus : ce téléphone doit être réactivé.
Notifications   Les notifications sont bloquées, l'état du suivi n'est plus
                visible.
```

→

## C4 · La carte
`TrackMap.kt:225`, `TrackMap.kt:352`, `TrackMap.kt:470`, `TrackMap.kt:688`

```
Vide       Aucune position enregistrée pour l'instant.
Bouton     Recentrer
Légende    Envoyé | Sur le téléphone | Reste du voyage
Bulle      aujourd'hui à 14:32 · hier à 09:05 · le 3 janvier 2027 à 09:05
Adresse    Recherche de l'adresse…
           Adresse non configurée.
           Adresse indisponible hors ligne.
Échelle    500 m  ·  2 km
```

→

## C5 · Ancienneté, partout
`RelativeTime.kt:14`

```
Inconnue   aucune
< 1 min    à l'instant
Minutes    il y a 3 min
Heures     il y a 4 h
Jours      il y a 3 j
```

→

---

# Application — réglages

## D1 · Écran replié
`SettingsScreen.kt:58`

```
Titre     Réglages
Retour    Retour
Section   Suivi
          Fréquence de localisation
Section   Synchronisation
          Points en attente          12
          Plus ancien en attente     il y a 4 h
          Dernier envoi réussi       il y a 20 min
Bouton    Autres réglages  /  Masquer les autres réglages
```

→

## D2 · Fréquence de capture
`CaptureIntervalPicker.kt:60`, `CaptureIntervalPicker.kt:96`

```
Puces     5 min | 15 min | 1 h | Autre
Puce'     Autre : 1 h 30
Dialogue  Autre fréquence
Champ     Minutes
Aide      Une position toutes les 15 min.
Aide'     Entre 1 et 1440 minutes, soit 24 h au plus.
Boutons   Valider  |  Annuler
```

→

## D3 · Réglages dépliés
`SettingsScreen.kt:98`

```
Section   Appareil
          Relié au voyage            oui / non
Bouton    Activer l'appareil  /  Réactiver l'appareil
Section   Diagnostic
          État détaillé du GPS, du réseau, des autorisations et du serveur.
Bouton    Ouvrir le diagnostic
Section   Application
          Version                    1.0.3
          Les positions sont envoyées uniquement au serveur du voyage. Aucun
          service tiers ne les reçoit, et aucune coordonnée n'est écrite dans
          les journaux techniques.
Bouton    Activer le tracking  /  Désactiver le tracking
Bas       Désactiver arrête la collecte de nouvelles positions. Les positions
          déjà enregistrées sont conservées et continuent d'être envoyées.
```

→

---

# Application — diagnostic

Volontairement brut : il sera lu à voix haute au téléphone.

## E1 · En-tête et contrôles
`DiagnosticsScreen.kt:73`

```
Titre       Diagnostic
Chargement  Lecture de l'état…
Bannière    Suivi actif | Hors ligne — trajet sauvegardé | Action nécessaire |
            Suivi arrêté
Ligne       À corriger : BATTERY_OPTIMIZATION_ENABLED
Ligne       Dernière position : il y a 3 min
Boutons     Démarrer  |  Arrêter
Titre       Fréquence
Bouton      Rafraîchir
```

→

## E2 · Les quatre cartes
`DiagnosticsScreen.kt:158`

```
Couverture (dernière heure)
  Points attendus            12
  Points enregistrés         11
  Taux                       92 %   /  —
  Service en cours           oui / NON

Système
  Localisation (précise)     oui / NON
  Localisation (arrière-plan)
  Notifications
  GPS activé
  Exemption batterie
  Alarmes exactes
  Réseau
  Batterie                   64 %  /  —
  [bouton] Vérifier les autorisations

Synchronisation
  Appareil activé            oui / NON
  Points en attente          12
  Plus ancien en attente     il y a 4 h
  Dernier essai              il y a 2 min
  Dernier succès             il y a 20 min
  Dernière erreur            —
  Échecs consécutifs         0

Ancienneté ici : jamais · à l'instant · il y a 3 min · il y a 4 h · il y a 3 j
```

→

---

# Application — notification et système

## F1 · Notification permanente
`res/values/strings.xml`

```
Nom app     Madhi Tracker
Canal       Suivi du voyage
Descr.      Indique que l'enregistrement du trajet est en cours.
Titre       Suivi actif
Texte       Le trajet est enregistré.
```

→

---

# Site familial

Ouvert par la famille depuis un lien secret. Une seule page.

## G1 · Structure de la page
`index.html:14`

```
Onglet      Suivi du voyage
Titre       Voyage                    (remplacé par le nom du voyage)
Carte       Carte du trajet           (aria-label)
Panneau     Dernière position et période affichée
Groupe      Période affichée
Bouton      Recentrer
Marqueur    Dernière position connue  (infobulle)
Sans JS     Ce site a besoin de JavaScript pour afficher la carte et la
            dernière position.
```

→

## G2 · Périodes
`features/period.js:19`

```
Aujourd'hui | 24 h | 7 jours | 30 jours | Tout le voyage
```

→

## G3 · Bloc « dernière position »
`components/latest-location.js:32`

```
Ligne 1   Dernière position : il y a 8 min
Ligne 2   20 août 2026 à 08:35
Retard    Reçue par le serveur 4 h plus tard, le 20 août 2026 à 12:35.
Périmé    Information non rafraîchie depuis 6 min.
```

→

## G4 · Ligne de couverture
`app.js:493`

```
Trajet affiché du 20 août à 08:35 au 20 août à 19:02 — 128 positions.
Trajet affiché depuis le départ, le 12 août à 06:00 au … — 1 position.
Suffixe    Le reste du voyage est tracé en gris.
```

→

## G5 · Bandeau — états du voyage
`components/status-banner.js:84`

```
Chargement   Chargement…
Avant départ Le voyage n'a pas encore commencé.
             Le trajet s'affichera à partir du départ.
Rien reçu    Aucune position reçue pour l'instant.
             Rien n'est encore arrivé au serveur depuis le départ.
Terminé      Le voyage est terminé.
             Voici le trajet complet.
Ancien       Aucune nouvelle position depuis 6 h.
Hors ligne   Aucune nouvelle position depuis 2 jours.
             La position affichée est la dernière connue, pas la position
             actuelle.
```

→

## G6 · Bandeau — précisions sur la période
`components/status-banner.js:56`

```
Résumé   Trajet résumé : environ une position toutes les 20 minutes.
         (aussi : par heure · toutes les 3 heures)
         La période est couverte en entier, mais les positions sont espacées
         pour rester affichables. Choisir une période plus courte les montre
         toutes.
Vide     Aucun déplacement enregistré sur cette période (7 jours).
         La dernière position connue reste affichée : elle est plus ancienne
         que cette période.
```

→

## G7 · Bandeau — erreurs serveur
`components/status-banner.js:126`

```
Injoignable  Le serveur ne répond pas.
             Le site réessaiera tout seul.
401/403      Cet accès n'est plus valide.
             Le lien ou le mot de passe familial a changé. Demande le nouveau.
404          Ce voyage est introuvable.
             Le serveur ne connaît pas l'identifiant de voyage configuré pour
             ce site.
5xx          Le serveur a un problème.
             Ce n'est pas le téléphone : les positions continuent d'être
             enregistrées.
Autre        Le serveur a refusé la demande.
             Code 429.
Rappel       « La dernière information connue reste affichée ci-dessous. »
             ajouté aux deux messages ci-dessus quand une position est connue.
```

→

## G8 · Bulle d'un point du trajet
`components/point-bubble.js:29`

```
Heure       aujourd'hui à 14:32 · hier à 09:05 · le 3 janvier 2027 à 09:05
Adresse     Recherche de l'adresse…
            Adresse non configurée.
            Adresse indisponible.
Coordonnées 48.85660, 2.35220
```

→

## G9 · Durées et dates
`utils/time.js:32`

```
Absolu      20 août 2026 à 08:35
Jour+heure  20 août à 08:35
Inconnu     date inconnue
Relatif     à l'instant · il y a 8 min
Durées      moins d'une minute · 8 min · 6 h · 1 jour · 3 jours · 4 mois
Manquant    un moment            (ancienneté inconnue dans le bandeau)
```

→

---

## Ce qui casse si on change

Quelques formulations sont vérifiées par les tests. Les changer demande de
toucher aussi ces fichiers — c'est mécanique, mais ça ne doit pas être oublié.

| Texte | Test |
|---|---|
| `aucune`, `à l'instant`, `il y a … min/h/j` | `RelativeTimeTest.kt` |
| `aujourd'hui à`, `hier à`, `le … à` | `PointTimeTest.kt` |
| `5 min`, `1 h`, `1 h 30` | `CaptureIntervalLabelTest.kt` |
| `Recentrer` | `TrackMapCameraTest.kt` |
| Bulle du trajet | `TrackMapBubbleTest.kt` |
| Consignes constructeur (non vides) | `VendorSetupGuidanceTest.kt` |
| Bandeau du site | `tools/site-dev/verifier.mjs` |

Une contrainte est dure et non négociable côté site : aucun libellé du bandeau
ne peut contenir « en direct », « temps réel », « live » ni « en ligne
maintenant ». `verifier.mjs` refuse la suite si l'un d'eux apparaît.
