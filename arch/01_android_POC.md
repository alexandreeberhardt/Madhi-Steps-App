**01 — Application Android — POC / POW**

*Prouver que le tracking peut fonctionner pendant plusieurs jours sans
perte de données*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. But du POC

Le POC Android ne cherche pas une application riche. Il doit prouver
quatre propriétés : acquisition périodique, fonctionnement en
arrière-plan, conservation hors ligne et synchronisation fiable.

# 2. Périmètre obligatoire

- Kotlin natif Android.

- Écran unique de statut. *Dépassé : l’application en compte six —
  onboarding, activation, carte, réglages, diagnostic, et l’écran de
  vérification qui clôt l’onboarding. La carte est devenue l’accueil
  (ADR-006), et l’activation a imposé son propre écran (ADR-004). Le principe
  qui portait cette ligne n’a pas bougé : un seul écran est vu pendant le
  voyage, les autres se traversent une fois ou se consultent en panne.*

- APK release signé, installable hors Play Store sur le téléphone de la
  voyageuse.

- Permission de localisation nécessaire au scénario retenu.

- Acquisition d’une position selon une fréquence configurable, avec 5
  minutes comme valeur par défaut.

- Sauvegarde Room/SQLite avant envoi.

- File locale des points pending.

- Envoi par batch HTTPS.

- Retry en cas d’échec.

- Identifiant UUID par point.

- Reprise du suivi après redémarrage si le mode de tracking le permet et
  si l’utilisateur l’a activé.

- Réglage permettant de désactiver le tracking GPS. La désactivation
  arrête l’acquisition de nouvelles positions, sans supprimer les points
  locaux pending ni empêcher leur synchronisation.

- Réglage permettant de modifier la fréquence de localisation dans des
  bornes raisonnables. *Livré autrement : trois paliers d’un geste — 5, 30 et
  60 minutes — et une saisie libre bornée à 1–1440 minutes, ouverte à la
  demande. Le garde-fou n’a pas disparu, il a changé de nature : ce sont les
  bornes qui le portent, plus la liste fermée. Le défaut reste 5 minutes.*

- Fonctionnement critique sans Firebase, Crashlytics, Google Analytics
  ni service Play Store obligatoire.

- Aucune URL de production, token appareil, clé de signature ou mot de
  passe ne doit être codé en dur dans le dépôt public.

# 3. Distribution APK

- Générer un APK signé avec un numéro de version lisible.

- Documenter la procédure d’installation hors Play Store sur le
  téléphone cible, y compris l’autorisation Android “installer des
  applications inconnues”.

- Tester l’installation, la mise à jour par nouvel APK, et la
  conservation de la base locale après mise à jour.

- Prévoir un moyen simple de transmettre l’APK à la voyageuse sans
  rendre le fichier public.

- Ne pas supposer que la voyageuse saura diagnostiquer une restriction
  batterie ou permission arrière-plan : l’app doit afficher l’action
  concrète à faire.

- Stocker la configuration de signature Android hors dépôt :
  keystore, alias, mots de passe et chemin local viennent de variables
  d’environnement ou d’un fichier local non versionné.

# 4. Confidentialité mobile

- Le tracking doit fonctionner sans envoyer les positions à Google,
  Firebase, Meta, Apple ou un service d’analytics.

- Le fournisseur de localisation Android doit être choisi explicitement.
  Choix POC : utiliser l’API Android système `LocationManager`, sans
  dépendance Fused Location Provider / Google Play Services pour le
  tracking critique.

- Optimiser la batterie avec une stratégie simple : fréquence par défaut
  de 5 minutes, précision suffisante plutôt que maximale, arrêt des
  demandes GPS inutiles, envoi réseau par batch, backoff en cas d’échec,
  et adaptation prudente si batterie faible ou appareil immobile après
  tests terrain.

- Les logs techniques restent locaux ou sont envoyés uniquement au
  serveur du projet.

- Aucun identifiant publicitaire, analytics produit ou crash reporting
  externe n’est intégré au POC.

# 5. Configuration publique / privée

- Le dépôt public peut contenir un exemple de configuration Android avec
  des valeurs factices.

- L’URL API réelle est injectée au build via variable d’environnement,
  `local.properties` non versionné ou mécanisme équivalent.

- Les tokens appareil sont provisionnés hors dépôt et stockés de manière
  raisonnablement protégée sur le téléphone.

- Le token appareil ne doit pas être codé dans l’APK release. Choix POC :
  le serveur génère un code d’activation, la voyageuse le saisit dans
  l’app au premier lancement, puis l’app échange ce code contre un token
  appareil long stocké localement.

- Les builds debug peuvent pointer vers une URL locale ou staging, mais
  cette valeur reste configurable.

- La fréquence choisie et l’état activé/désactivé du tracking sont
  persistés localement et restaurés après redémarrage.

# 6. Modules

> app-ui\
> tracking-core\
> location-provider\
> local-storage\
> sync-client\
> device-state

Même si le projet reste dans un seul module Gradle au début, ces
responsabilités doivent rester séparées dans les packages et interfaces.

# 7. Flux critique

> Location provider → LocationPoint → Room transaction → Sync queue →
> API batch → ACK serveur → mark synced

# 8. Résilience d’envoi sans changement d’interface

Ce bloc couvre le cas où la localisation fonctionne et où le téléphone a
de la connexion, mais où les positions ne sont plus envoyées.

- La synchronisation doit être indépendante de l’acquisition GPS. Si des
  points sont `pending`, l’app doit pouvoir tenter l’envoi même si aucun
  nouveau point GPS n’est collecté.

- Après redémarrage du téléphone, l’app restaure `trackingEnabled`, la
  fréquence choisie et planifie de nouveau le worker de synchronisation.

- Après mise à jour APK, l’app vérifie que le worker de synchronisation
  est toujours planifié et le recrée si nécessaire.

- Au lancement de l’app, vérifier systématiquement l’état local :
  tracking activé/désactivé, nombre de points pending, dernier essai
  d’envoi, dernier envoi réussi, dernier code HTTP reçu.

- Si `pending > 0` et que `lastSyncAttemptAt` est trop ancien, relancer
  une tentative de synchronisation avec backoff raisonnable.

- Une erreur `401/403` arrête les retries agressifs et marque un état
  d’authentification à corriger ; les points pending ne sont jamais
  supprimés.

- Une erreur serveur, timeout, `413` ou `429` ne supprime aucun point.
  L’app réduit la taille des batches ou applique un backoff selon le cas.

- Un arrêt forcé Android de l’application est une limite connue : les
  tâches peuvent ne repartir qu’après réouverture manuelle de l’app. Ce
  cas doit être documenté dans le diagnostic, sans ajouter d’écran.

- Ces garde-fous sont internes. Ils ne doivent pas ajouter de bouton,
  d’écran, de panneau ou d’information permanente sur l’accueil.

# 9. Base locale

Table telle qu’elle existe. Les noms sont ceux des colonnes SQLite ; le schéma
qui fait foi est `app/schemas/…/1.json`, commité (ADR-005).

| **Champ**       | **Type**  | **Rôle**                              |
|-----------------|-----------|---------------------------------------|
| id              | TEXT      | UUID généré à la capture, idempotence |
| latitude        | REAL      | GPS                                   |
| longitude       | REAL      | GPS                                   |
| recorded_at     | INTEGER   | Heure réelle du point, epoch millis   |
| accuracy_m      | REAL?     | Qualité GPS                           |
| altitude_m      | REAL?     | Contrat `LocationPointV1`             |
| speed_mps       | REAL?     | Contrat `LocationPointV1`             |
| battery_percent | INTEGER?  | Contrat `LocationPointV1`             |
| sync_state      | TEXT      | PENDING / SYNCED                      |
| attempt_count   | INTEGER   | Diagnostic et backoff                 |
| last_attempt_at | INTEGER?  | Diagnostic                            |
| last_error_code | TEXT?     | Diagnostic                            |

Deux écarts avec la première rédaction, tous deux tranchés par ADR-003.

**Il n’y a pas d’état `ERROR`.** Un point non confirmé reste `PENDING`, sans
exception et sans limite de temps. Un troisième état est un état dont il faut
penser à sortir, et un point oublié dedans est un point perdu — ce que le
projet interdit.

**`retryCount` s’appelle `attempt_count` et n’a aucune conséquence
destructrice.** Il sert au diagnostic et au calcul du backoff, jamais à
abandonner un point.

# 10. Écran POC

Cette liste décrivait l’écran de statut unique du §2. Elle reste juste sur ce
qu’il faut pouvoir lire ; elle a cessé de l’être sur l’endroit où on le lit.
`arch/09_design_app_V1.md` fait foi sur l’accueil, et il tranche autrement : la
carte l’occupe, le reste descend dans les réglages et le diagnostic.

| Information | Où elle est |
|---|---|
| Tracking : actif / arrêté | Accueil, bandeau bas |
| Dernière position locale | Accueil, marqueur et ancienneté |
| Dernière synchronisation réussie | Réglages, et Diagnostic |
| Nombre de points en attente | Réglages, et Diagnostic |
| État permission localisation | Diagnostic |
| État réseau | Diagnostic |
| Fréquence actuelle du tracking | Réglages |
| Bouton démarrer / arrêter | Accueil si le suivi est arrêté, Réglages sinon |
| Bouton synchroniser maintenant | **N’existe pas** |

Le bouton de synchronisation manuelle a été retiré par `arch/09` §3, et rien ne
l’a fait revenir. La synchronisation part après chaque capture, toutes les
quinze minutes par WorkManager, au lancement de l’application, après un
redémarrage et après une mise à jour d’APK (ADR-003). Un bouton n’aurait rien
déclenché que ces quatre entrées ne déclenchent déjà, et aurait laissé croire
qu’appuyer sert à quelque chose les jours où rien ne part.

# 11. Ce qui est volontairement hors POC

- Statistiques avancées

- Journal de voyage

- Photos

- Notifications riches

- ~~Carte embarquée~~ *Réintégrée. Reportée le 18 août 2026 parce que le
  risque du projet était la perte de points, pas l’absence de carte ; écrite le
  23 août une fois le noyau éprouvé. Elle est aujourd’hui l’écran d’accueil.
  L’histoire de cette décision et ses raisons sont dans ADR-006, son état
  courant dans `arch/18_carte_embarquee_v1.md`.*

- Comptes multiples

- Optimisation dynamique sophistiquée de batterie

# 12. Critères d’acceptation

- Mode avion 4 h : aucun point collecté localement n’est perdu.

- Après retour réseau : backlog envoyé et confirmé.

- Double envoi du même batch : aucun doublon côté serveur.

- Redémarrage : état du tracking récupéré selon la stratégie Android
  choisie.

- Redémarrage : fréquence de tracking et état activé/désactivé restaurés
  correctement.

- Redémarrage : les points pending sont envoyés après retour du système,
  sans ouverture obligatoire de l’écran principal lorsque le système le
  permet.

- L’application peut rester fermée visuellement sans perdre le suivi
  prévu.

- Test terrain de 24 h avec journal des trous de tracking.

- Installation par APK sur le téléphone réel de la voyageuse validée.

- Mise à jour par nouvel APK sans perte des points pending validée.

- Mise à jour par nouvel APK : le worker de synchronisation est toujours
  actif ou recréé au lancement suivant.

- Restrictions batterie et autorisations arrière-plan vérifiées sur le
  modèle de téléphone réel.

- Changement de fréquence validé sans perte des points pending.

- Désactivation du tracking validée : plus aucune nouvelle position
  n’est collectée, mais la synchronisation du backlog reste possible.

- Blocage de synchronisation simulé : si `pending > 0` et dernier essai
  trop ancien, une nouvelle tentative est planifiée automatiquement.

- Erreur `401/403` simulée : les points pending restent locaux et l’état
  d’authentification est diagnostiquable.

- Vérification qu’un scan du dépôt public ne révèle aucun token, keystore
  ou URL privée.

# 13. Préparation V2 dès le POC

- Interface LocationProvider abstraite.

- Repository local abstrait.

- API client séparé.

- Configuration fréquence/precision centralisée.

- Schéma Room migrable.

- Version de l’app envoyée dans le status/heartbeat.
