**16 — Leçons du terrain**

*Ce que l'appareil réel a appris, et que les tests ne pouvaient pas dire*

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# Pourquoi ce document

`arch/15_journal_tests_terrain.md` raconte les sessions dans l'ordre. Celui-ci
en extrait ce qui est réutilisable : les pièges Android, ce que
l'architecture a bien encaissé, et ce qu'on ferait autrement.

Il est écrit après deux jours de développement et deux confrontations à un
appareil réel. Chiffres à cet instant : 6 000 lignes de production, 3 600 de
tests, 216 cas, 33 commits, 8 ADR.

# 1. La leçon principale

**Les 216 tests n'ont trouvé aucun des quatre défauts qui comptaient.**

Ils ont trouvé un bug réel — un `SyncJournal` qui perdait le code d'erreur —
et ils ont empêché des régressions pendant tout le développement. Mais les
défauts qui auraient fait échouer le voyage sont tous venus de l'appareil :

| Défaut | Détectable par un test ? |
|---|---|
| Alarme exacte silencieusement dégradée par le constructeur | non — le système ment à l'application |
| Backoff WorkManager verrouillant le rattrapage après une nuit hors réseau | non — demande une vraie période d'échecs |
| Bouton principal sous la barre de navigation gestuelle | non — géométrie d'un appareil précis |
| État système non relu au retour d'un écran Android | non — dépend du cycle de vie réel |

La conclusion n'est pas que les tests sont inutiles : sans eux, chaque
correction de terrain aurait cassé autre chose. C'est qu'ils **valident la
logique, jamais l'environnement**. Pour une application dont tout l'enjeu est
de survivre à un système d'exploitation hostile, la proportion d'effort doit
en tenir compte.

# 2. Android en arrière-plan

## 2.1 Une alarme exacte peut être accordée puis ignorée

Le piège le plus coûteux du projet.

Sur OnePlus sous OxygenOS 14, avec `USE_EXACT_ALARM` accordée et
`canScheduleExactAlarms()` renvoyant `true`, l'appel à
`setExactAndAllowWhileIdle` était accepté — puis l'alarme reposée avec une
fenêtre de 225 secondes. L'écran de diagnostic affichait fièrement
« Alarmes exactes : oui ».

Résultat : 36 % de couverture sur une nuit. Dérive de 5 à 10 minutes, plus
des trous de 20 à 89 minutes.

**Ce qu'il faut retenir** : sur Android, une permission accordée ne garantit
pas le comportement. Les constructeurs interceptent en aval, et aucune API
ne le signale. La seule façon de le savoir est de comparer ce qu'on a demandé
à ce qui s'est produit.

Corollaire pratique : `dumpsys alarm` expose `windowLength`. Une alarme
réellement exacte a `windowLength 0`. C'est vérifiable, et ça vaut la peine
de le vérifier plutôt que de croire l'API.

## 2.2 Ce qui n'a pas suffi

Au moment de l'échec, tous ces garde-fous étaient en place et ont survécu à
la nuit :

- exemption d'optimisation de batterie accordée ;
- bucket App Standby à 5, exempté ;
- service de premier plan de type `location` toujours vivant au réveil ;
- les trois réglages propriétaires OxygenOS appliqués à la main.

Aucun n'a empêché le regroupement de l'alarme. Empiler les protections ne
remplace pas la mesure.

## 2.3 Confier la cadence au sous-système de localisation

La correction retenue (ADR-008) : ne plus cadencer avec `AlarmManager`, mais
s'abonner en continu au fournisseur de localisation avec un intervalle.

Trois raisons :

1. Le sous-système de localisation est plus privilégié qu'`AlarmManager`
   pour une application portant un service de premier plan de type `location`.
2. Il sait éteindre le récepteur entre deux points, ce qu'une boucle
   applicative ne saurait pas faire — c'est ce qui distingue cette voie d'un
   verrou de réveil permanent, écarté pour son coût en batterie.
3. **Son état est observable** : `dumpsys location` montre la requête active
   et son intervalle réel. On peut donc vérifier au lieu de supposer.

L'alarme subsiste, mais comme filet : elle vérifie qu'une position est bien
arrivée, et n'acquiert que si le flux s'est tu. Un abonnement peut mourir
sans que le service meure — ce serait une panne parfaitement silencieuse.

## 2.4 Le backoff de WorkManager est un piège pour une app hors ligne

Après une nuit d'échecs de synchronisation, le backoff exponentiel avait
atteint plusieurs heures. Au retour du réseau, le jobscheduler affichait :

    Required constraints:    TIMING_DELAY CONNECTIVITY
    Satisfied constraints:   CONNECTIVITY
    Unsatisfied constraints: TIMING_DELAY

Et comme la demande immédiate utilisait `ExistingWorkPolicy.KEEP`, chaque
nouvelle tentative était ignorée au profit de celle qui attendait.

Traduit pour le voyage : trois jours sans réseau, puis vingt minutes de wifi
dans un café, et rien ne part.

**Ce qu'il faut retenir** : quand une exécution périodique existe déjà, elle
*est* le mécanisme de réessai. Un backoff par-dessus n'ajoute rien et peut
tout bloquer. Le worker ne renvoie donc plus jamais `retry`.

## 2.5 Un correctif ne débloque pas un état déjà bloqué

Conséquence directe et contre-intuitive : après avoir corrigé le backoff et
réinstallé l'APK, **le backlog refusait toujours de partir**. L'état de
WorkManager survit à la réinstallation, et `KEEP` protégeait le travail
hérité, coincé.

Il a fallu passer explicitement la demande immédiate en `REPLACE` et
l'exécution périodique en `UPDATE` pour que la version corrigée reprenne la
main.

**Ce qu'il faut retenir** : pour toute planification persistée, se demander
comment une version corrigée reprend la main sur un état hérité. La
voyageuse, elle, ne réinstallera pas.

## 2.6 Le GPS en intérieur ne donne rien

Le système déclarait un dernier fix GPS vieux de sept jours après une nuit de
suivi en intérieur. Un test GPS sur un bureau au fond d'une pièce mesure
surtout l'épaisseur du plafond.

Conséquence sur la conception : s'abonner **aux deux fournisseurs**, GPS et
réseau. Une position réseau à cinq cents mètres reste exploitable pour un
tracé à vélo, et vaut infiniment mieux qu'un trou.

## 2.7 Un reboot non rattrapé coûte plus cher que n'importe quelle panne réseau

Trois jours sans position côté serveur, du 20 au 23 août. La synchronisation
n'y était pour rien : la base locale ne contenait aucun point en attente. Le
téléphone avait redémarré le 22 août à 12:57 UTC, et le processus
`com.madhi.tracker` n'est né que le 23 à 13:25 — au déverrouillage manuel.
L'application n'était pas repartie toute seule (session 5).

C'est le défaut le plus coûteux observé jusqu'ici, et de loin. Une coupure
réseau ne perd rien : les points s'accumulent en base et partent plus tard. Un
reboot non rattrapé, lui, ne capture rien du tout, et le trou dure jusqu'au
prochain déverrouillage. Ici vingt-quatre heures ; en bivouac, potentiellement
plusieurs jours.

Conséquence sur la méthode : **devant un trou côté serveur, comparer d'abord
l'heure de naissance du processus au début du trou.** C'est une minute de
relevé, et elle décide entre deux enquêtes qui n'ont rien à voir. Chercher un
bug de synchronisation avant d'avoir fait cette comparaison, c'est chercher là
où il n'y a rien.

Conséquence sur le protocole : le redémarrage automatique devient le critère
bloquant de T1, à rejouer sur MIUI où il est plus hostile qu'ici.

# 3. Interface

## 3.1 L'état système doit être relu à la reprise de l'écran

Après avoir accordé une permission, l'onboarding affichait toujours
« Autoriser » sans statut : l'état n'était lu qu'à la construction de l'écran.

Les autorisations et les réglages système changent dans une **autre
activité** — dialogue Android, page de paramètres. La reprise de l'écran est
le seul instant où l'application peut en constater le résultat.
`LifecycleEventEffect(ON_RESUME)` sur chaque écran qui affiche un état système.

Le même défaut aurait touché l'écran d'exemption batterie, où il aurait coûté
bien plus cher : c'est le réglage le plus important de l'application.

## 3.2 Les marges système ne sont pas un détail esthétique

Le bouton principal de l'onboarding se trouvait sous la barre de navigation
gestuelle. Un appui restait sans effet ; vingt-cinq pixels plus haut, il
fonctionnait.

Les écrans construits avec `Scaffold` sont couverts ; ceux qui ne le sont pas
demandent `safeDrawingPadding()` explicitement. Un contenu défilant qui passe
sous les barres système rend l'action principale inatteignable, et c'est
invisible depuis un émulateur configuré autrement.

# 4. Ce que l'architecture a bien encaissé

Trois choses ont réellement payé.

**Le port `LocationSource`.** Changer de stratégie d'acquisition — d'une
acquisition ponctuelle cadencée par alarme à un abonnement continu — a
demandé de réécrire **un seul adaptateur**. Le domaine, la persistance, la
synchronisation et l'interface n'ont pas bougé. La décision d'ADR-001 de
placer une frontière là était la bonne, et elle a été rentabilisée en deux
jours au lieu de deux ans.

**L'extraction de `RecordLocation`.** Quand le flux continu est apparu, il
fallait que « une position arrive » emprunte le même chemin que l'acquisition
ponctuelle. Extraire la séquence commune plutôt que la dupliquer a évité deux
versions qui auraient fini par diverger sur un détail coûtant des positions.

**La règle « aucun chemin ne supprime un point ».** Toutes les corrections de
terrain ont été faites sur un appareil qui portait des positions non
synchronisées. À aucun moment il n'a fallu se demander si une manipulation
risquait d'en perdre. Le DAO n'expose pas de `DELETE` : cette absence a valu
mieux qu'une consigne.

Un quatrième point, plus discret : **la tâche Gradle `checkCoreIsFrameworkFree`**
a tenu pendant tout le remaniement. Le cœur métier n'a jamais commencé à
importer Android par facilité.

# 5. Instrumentation : ce qu'il a fallu ajouter pour voir

Au premier incident, l'application était aveugle. Trois ajouts ont changé la
donne, et tous méritent d'exister en permanence :

- **`CAPTURE_SCHEDULED dans Xs`** — journaliser le délai *demandé*. Sans lui,
  impossible de distinguer « on demande un mauvais délai » de « l'alarme se
  déclenche plus souvent que demandé ». Deux causes très différentes.
- **`STREAM_STARTED` / `STREAM_STOPPED` / `STREAM_SILENT`** — un abonnement
  qui meurt en silence est la pire panne possible.
- **`TrackingCoverage`** — comparer les positions enregistrées au nombre
  attendu. Sans ce ratio, la question « est-ce que la surcouche nous tue ? »
  n'a pour réponse qu'une impression. Avec, elle a un pourcentage.

Le port `EventLog` a bien tenu son rôle : sa signature n'accepte aucune
coordonnée, donc aucune des instrumentations ajoutées dans l'urgence n'a pu
faire fuiter une position dans les journaux.

# 6. Pièges d'outillage rencontrés

Ils ont chacun coûté du temps, et aucun n'est deviné à l'avance.

| Piège | Contournement |
|---|---|
| `adb logcat -s MonTag` ne rend rien sur OxygenOS | `adb logcat -d \| grep -i montag` |
| Le tampon de logs par défaut (256 Kio) ne couvre pas une nuit | `adb logcat -G 8M` avant le test |
| Copier la base d'un appareil sans le fichier `-wal` donne une table vide | copier `.db` **et** `.db-wal` |
| `system_profiler SPUSBDataType` ne rend rien dans cet environnement | ne pas en tirer de conclusion ; se fier à `adb devices` |
| Un câble USB peut ne transporter que du courant | passer au débogage sans fil, plus rapide à diagnostiquer |
| `dumpsys jobscheduler` dit pourquoi un travail n'est pas exécuté | lire `Unsatisfied constraints` avant de supposer |

# 7. Méthode : ce qui a marché

**Un test câblé de quinze minutes avant le test de nuit.** Il a validé la
chaîne complète — activation, capture, envoi, idempotence — et trouvé trois
défauts en quinze minutes. Sans lui, on aurait attendu quatre heures pour
découvrir qu'un bouton ne répondait pas.

**Mesurer avant de conclure.** L'hypothèse d'une avance de l'horodatage GPS
sur l'horloge du téléphone était séduisante et fausse : l'écart mesuré était
de +1,8 s. Deux minutes de vérification ont évité une correction inutile.

**Consigner les hypothèses écartées.** Le journal note ce qui a été testé et
invalidé, pas seulement ce qui a été trouvé. C'est ce qui évite d'y revenir.

**Ce qui a moins bien marché** : j'ai conclu une fois qu'une anomalie s'était
« arrêtée d'elle-même » sur un échantillon de deux mesures. Elle durait encore
treize minutes plus tard. Sur un phénomène intermittent, deux points ne font
pas une tendance.

# 8. Ce qui reste ouvert

- **Le coût en batterie de l'abonnement continu.** C'est la mesure qui manque,
  et la raison même d'avoir écarté le verrou de réveil permanent. Un suivi
  parfait qui vide la batterie en une nuit ne sert à rien.
- **Le redémarrage automatique**, désormais le point bloquant du projet : il a
  échoué en conditions réelles sur OxygenOS (§2.7), et MIUI est plus hostile.
- **Le comportement sur MIUI.** Tout ce document décrit OxygenOS. Le Redmi
  Note 11 arrive la semaine du 24 août ; MIUI bloque en plus le démarrage
  automatique, et n'a que 4 Go de RAM.
- **La rafale de captures à dix secondes** observée le premier soir n'a jamais
  été expliquée. L'instrumentation ajoutée la caractérisera si elle revient.
- **La ligne « Alarmes exactes » du diagnostic est trompeuse** : elle affiche
  ce que l'API déclare, pas ce que le système fait. À reformuler.
- **La carte embarquée** a été livrée le 23 août, sans fond cartographique
  (ADR-006, réouverture, et `arch/18`). Le report avait tenu tant que le terrain
  portait sur le noyau ; il n'avait plus de raison d'être une fois la chaîne
  éprouvée de bout en bout. Vue sur le OnePlus le soir même ; les gestes, le
  thème sombre et la fluidité à deux mille points restent à éprouver.
- **Un segment de 440 km sans point intermédiaire** est apparu sur la carte du
  OnePlus. Déplacement réel non capturé, ou mesure grossière acceptée : la
  validation ne filtre pas sur la précision, par choix. À trancher avec les
  `accuracy_m` du serveur.
