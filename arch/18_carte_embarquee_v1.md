**18 — Carte embarquée V1**

*Ce qui existe, et pourquoi c'est fait ainsi*

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# 1. Statut de ce document

`arch/09_design_app_V1.md` dit **ce que** l'écran d'accueil doit montrer et fait
foi. `arch/adr/006-carte-embarquee.md` porte les décisions et leur histoire. Ce
document décrit **l'état courant** et sert à quelqu'un qui reprendrait la carte
sans avoir suivi les sessions.

En cas de divergence, `arch/09` et `arch/00` font foi.

# 2. Ce que la carte montre

L'écran d'accueil garde la structure de `arch/09` §2 : la carte occupe la zone
centrale, le bandeau bas reste compact.

| Élément | Comportement |
|---|---|
| Fond de carte | Tuiles raster d'un fournisseur configuré hors du dépôt. Absent si aucune source n'est réglée : la carte reste sur fond uni et n'émet aucune requête |
| Tracé | Les positions de la base locale, reliées dans l'ordre du voyage |
| Couleur du tracé | Bleu là où le serveur détient les points, orange là où ils ne sont encore que sur le téléphone |
| Position actuelle | Disque cerclé sur le point le plus récent |
| Période | Trois boutons sous la carte : aujourd'hui, 7 jours, tout le voyage |
| Échelle graphique | En bas à gauche, une distance ronde et sa longueur en pixels |
| Légende | En haut à gauche ; la ligne « Sur le téléphone » n'apparaît que s'il y a des points en attente |
| Mention légale | En bas à droite, imposée par la licence des tuiles |
| Cadrage | Automatique sur tout le tracé visible, marges des éléments d'interface comprises |
| Gestes | Glisser pour déplacer, pincer pour zoomer |
| Recentrer | Bouton flottant, visible **uniquement** après un déplacement manuel |
| Base vide | « Aucune position enregistrée pour l'instant. » |

Trois comportements méritent d'être connus avant de les modifier.

**Le cadrage automatique ne reprend pas la main tout seul.** Dès que la
voyageuse déplace ou zoome la carte, le cadrage devient manuel et le reste
jusqu'au bouton « Recentrer ». Sans cela, la carte sauterait sous les doigts à
chaque nouvelle position.

**La couleur d'un segment est celle de son point d'arrivée.** C'est l'état du
trajet une fois parcouru. Comme la synchronisation avance dans l'ordre du
voyage, on lit une longue portion bleue et une queue orange : exactement ce
qu'on veut savoir après trois jours sans réseau.

**Changer de période change la finesse, pas seulement l'étendue** (§3.6).

# 3. Décisions techniques et leurs raisons

## 3.1 Des tuiles raster, aucune bibliothèque cartographique

Le fond est fait de tuiles raster XYZ peintes dans le même `Canvas` Compose que
le tracé, avec OkHttp qui était déjà là.

*Pourquoi pas MapLibre.* Le vectoriel donnerait un rendu supérieur et un
hors-ligne complet, mais c'est un moteur cartographique entier — la dépendance
que personne ne répare seul en Norvège. Le critère du projet a tranché.

*Ce que ça coûte.* Le rendu dépend d'un fournisseur de tuiles raster. Le détail
disponible est celui qu'il sert, ni plus ni moins.

## 3.2 La source des tuiles est une configuration, jamais du code

Trois lignes de `local.properties`, jamais versionnées :
gabarit d'URL, mention légale, zoom maximal servi.

*Pourquoi.* Le choix engage une licence, parfois un compte et une clé, et n'a
pas sa place dans un dépôt public. Vide, la carte retombe sur son fond uni sans
qu'une ligne de code change. Changer de fournisseur est une modification de
trois lignes, éprouvée deux fois.

**État courant : Thunderforest, style *Outdoors*, offre gratuite** — courbes de
niveau, sentiers, sommets cotés, hiérarchie des routes, jusqu'au zoom 20.

**Repli conservé : le fond auto-hébergé** de `tools/tiles`, fabriqué depuis des
données Natural Earth du domaine public et servi par le conteneur `site`. Il ne
sert plus au quotidien mais reste déployé : il est la seule source que le
projet contrôle de bout en bout, et la seule qu'on ait le droit de pré-charger
en masse.

## 3.3 Le cache disque est interrogé avant le réseau

Une tuile déjà vue n'est jamais redemandée au serveur.

*Pourquoi.* Ce n'est pas une optimisation. Une tuile ne change pas en un an, et
chaque requête évitée est de la batterie et du forfait données économisés. Une
zone consultée une fois à l'hôtel reste lisible trois jours plus tard au fond
d'un fjord — vérifié (§5).

Le cache vit dans `filesDir` et non `cacheDir` : Android vide le second sous
pression de stockage, et le ferait au pire moment. Le client HTTP des tuiles
est séparé de celui de l'API, et doit le rester : celui de l'API ne doit
**jamais** porter de cache, une réponse de synchronisation servie depuis un
cache serait un bug de correction.

*Conséquence à connaître.* Avec un fond auto-hébergé, l'URL doit porter une
génération — `/tiles/v2/{z}/{x}/{y}.png` — sinon un fond refabriqué ne
parviendrait jamais aux téléphones qui ont déjà l'ancien.

## 3.4 Une tuile fait 256 pixels logiques, pas 256 pixels d'écran

La grille descend d'un niveau de zoom par doublement de densité et peint chaque
tuile d'autant plus grande.

*Pourquoi.* Sur un téléphone à 400 points par pouce, une tuile posée au pixel
d'écran est grande comme un timbre, et ses noms de villes sont dessinés pour
être lus trois fois plus gros. Une partie de ce qui faisait paraître le fond
pauvre était des données rendues trop petites. La grille en demande neuf fois
moins au passage.

## 3.5 Toute la géométrie dans `domain/`, sans Android

`MapProjection`, `MapViewport`, `MapScaleBar`, `TileGrid` et `TrackWindow` ne
connaissent ni Compose ni Android. Le rendu ne fait que peindre des pixels.

*Pourquoi.* Un cadrage faux est invisible à la relecture et évident en test. La
tâche Gradle `checkCoreIsFrameworkFree` garantit que cette séparation ne se
perdra pas, et les tests de géométrie tournent sans émulateur.

La projection est **Web Mercator**, celle de toutes les tuiles. Elle a été
choisie avant qu'un fond existe ; quand il est arrivé, il s'est posé sous le
tracé sans qu'une ligne de géométrie change.

## 3.6 Chaque période porte un pas de temps

Aujourd'hui, sept jours, tout le voyage. Ce ne sont pas trois étendues mais
trois couples étendue/finesse, et SQL ne rend qu'une position par tranche :

| Période | Pas | Plafond |
|---|---|---|
| Aujourd'hui | une minute | 1 440 points |
| 7 jours | cinq minutes | 2 016 points |
| Tout le voyage | une heure | 8 760 points par an |

*Pourquoi.* Un an de voyage fait environ cent mille positions, et les relier
toutes à chaque image de glissement mettrait à genoux un appareil à 4 Go. Un
plafond en nombre de points aurait coupé le passé ; le pas de temps résume sans
amputer, et **la borne tient quelle que soit la cadence de capture réglée**.

*Deux pièges de la requête*, chacun tenu par un test. Le `GROUP BY` doit
qualifier `locations.recorded_at`, sinon le nom désigne l'alias de sortie, qui
est un agrégat. Et les colonnes nues — latitude, longitude, état — sont prises
sur la ligne du `MIN`, comportement documenté de SQLite : sans cela le point
porterait l'heure d'une position et les coordonnées d'une autre.

« Aujourd'hui » est une date, pas une durée : minuit dans le fuseau de la
voyageuse. Au Cap Nord en été l'écart à UTC est de deux heures, et la journée
affichée serait décalée d'autant.

Le vocabulaire est celui du site familial (`site/features/period.js`), et le
serveur applique désormais le même principe d'échantillonnage (`arch/17` §4.1).

## 3.7 La projection est calculée une fois, pas à chaque image

`TrackMap` projette les points en coordonnées normalisées quand la liste
change, puis ne repasse par le viewport que pour une multiplication et une
addition à chaque image.

*Pourquoi.* Mercator coûte un logarithme et une tangente par point. À deux
mille points et soixante images par seconde pendant un glissement, la
différence est celle entre une carte fluide et une carte qui accroche.

## 3.8 Le tracé est découpé en tronçons de même état, pas en segments

*Pourquoi.* Un appel de dessin par segment ferait deux mille appels par image.
Comme la synchronisation avance dans l'ordre du voyage, il n'y a en pratique
que deux tronçons. Le découpage reste général — un trou au milieu s'affiche
correctement — mais le cas courant ne coûte rien.

## 3.9 Un effet de chargement par tuile, confié à Compose

*Pourquoi.* La première version tenait à la main la liste des tuiles « en
cours », pour ne pas les redemander. Une coroutine annulée avant d'avoir
démarré n'exécute pas son `finally` : la tuile restait marquée en cours pour
toujours et n'était jamais redemandée. Aucune erreur nulle part, juste un fond
qui ne venait pas. Confier ce cycle de vie à Compose supprime la classe entière
du problème au lieu de rendre la comptabilité juste.

## 3.10 Le flux s'arrête quand personne ne regarde

`MainViewModel.track` utilise `SharingStarted.WhileSubscribed`. Écran éteint,
la requête cesse et la base n'est plus relue à chaque capture. La carte est un
confort : elle ne doit rien coûter à l'autonomie quand elle n'est pas affichée.

## 3.11 Les éléments d'interface portent leur propre fond, et le cadrage les évite

Légende, échelle et mention légale sont posées sur un fond semi-opaque, et le
cadrage automatique réserve leur place — leurs hauteurs sont **mesurées**, pas
devinées, parce qu'elles changent avec la taille de police du système et avec
le fournisseur de tuiles.

*Pourquoi.* Deux défauts constatés sur l'appareil, aucun visible en test : le
marqueur de position actuelle passait sous la légende, et la mention légale de
Thunderforest — qui tient toute la largeur — recouvrait l'échelle. Les tests de
cadrage vérifiaient que le tracé tient dans la zone de dessin, ce qui était
vrai ; ils ne pouvaient pas savoir qu'un élément était posé par-dessus.

Ces fonds sont aussi ce qui rend l'écran lisible en thème sombre : les tuiles,
elles, sont toujours claires.

## 3.12 L'échelle graphique n'est pas décorative

`arch/09` §3 retire de l'accueil toute statistique décorative. L'échelle y
échappe volontairement : c'est la seule chose qui dise si le tracé visible fait
deux rues ou deux cents kilomètres. Même raisonnement pour la légende, clé du
code couleur, qui se réduit à une ligne quand rien n'est en attente.

# 4. Où vit quoi

| Fichier | Rôle |
|---|---|
| `domain/MapProjection.kt` | Web Mercator : coordonnées ↔ carré unité, taille du monde, mètres par pixel |
| `domain/MapViewport.kt` | Centre et zoom, passage à l'écran, glissement, pincement ancré, cadrage avec marges inégales |
| `domain/MapScaleBar.kt` | Choix de la distance ronde qui tient dans la place disponible |
| `domain/TileGrid.kt` | Quelles tuiles couvrent la vue, où les poser, à quelle densité |
| `domain/TrackWindow.kt` | Une période → depuis quand, et à quelle finesse |
| `domain/model/TrackPoint.kt`, `TrackPeriod.kt` | Ce que la carte dessine, et ce qu'elle peut montrer |
| `application/port/LocationStore.kt` | `observeTrack(since, bucketMillis)` — ordre chronologique contractuel |
| `application/port/TileStore.kt` | D'où viennent les tuiles, et jusqu'à quel zoom |
| `application/usecase/ObserveTrack.kt` | Le tracé de la période demandée |
| `application/usecase/LoadMapTile.kt` | Une tuile, ou rien |
| `adapter/…/room/LocationDao.kt` | La requête, son regroupement et sa projection de lecture |
| `adapter/…/tiles/HttpTileStore.kt` | Cache disque d'abord, réseau ensuite |
| `infrastructure/di/TileModule.kt` | Client HTTP dédié, cache dans `filesDir` |
| `presentation/map/TrackMap.kt` | Rendu, gestes, légende, échelle, mention légale |
| `presentation/map/TileLayer.kt` | Cache mémoire des tuiles décodées, chargement |
| `presentation/map/MainScreen.kt` | Carte, sélecteur de période, bandeau d'état |
| `tools/tiles/` | Fabrication du fond auto-hébergé de repli |

Le domaine et les use cases ne connaissent aucun framework ; la présentation
reste un adaptateur.

# 5. Ce qui est vérifié, et comment

**Tests unitaires, sans émulateur.** Projection et réversibilité sur toute la
plage France–Cap Nord ; cadrage qui fait tenir le tracé hors des éléments
d'interface ; pincement qui garde immobile le point sous les doigts ; échelle
qui ne déborde jamais et se tait plutôt que de mentir ; grille de tuiles —
repli à l'antiméridien, absence de tuile au-delà des pôles, plafond de sécurité,
sensibilité à la densité ; bornes et pas de chaque période ; requête Room —
ordre, regroupement, colonnes prises sur la bonne ligne.

**Vérifié sur le OnePlus 8T**, en thème clair et sombre :

- le fond s'affiche et se pose exactement sous le tracé ;
- le glissement déplace la carte et charge les tuiles découvertes ;
- « Recentrer » n'apparaît qu'après un déplacement manuel et rend le cadrage ;
- les trois périodes changent bien l'étendue et la finesse ;
- **le hors-ligne tient** : les zones déjà consultées restent lisibles sans
  réseau. Rapporté par Alexandre le 26 août 2026, non observé directement.

**Ce qui n'est pas vérifié.** Le pincement — `adb` ne simule pas deux doigts.
La fluidité à deux mille points : le tracé de l'appareil de pré-validation en
compte bien moins. Et **rien sur le Redmi Note 11**, qui est l'appareil qui
fait foi.

# 6. Dette assumée et points ouverts

**L'index manquant.** La requête du tracé trie sur `recorded_at`, que l'index
`(sync_state, recorded_at)` ne sert pas : SQLite balaie la table. Quelques
dizaines de millisecondes à cent mille lignes, et seulement écran allumé.
L'index dédié demanderait une migration de schéma, qui ne se fait pas à
quelques jours d'un départ (ADR-005). À reprendre en V2.

**Le hors-ligne ne couvre que ce qui a été vu.** La licence de Thunderforest
autorise le cache des tuiles consultées, et interdit le pré-chargement d'une
zone qu'on n'a pas regardée. Une région traversée sans l'avoir ouverte à
l'avance sera sans fond. C'est la limite structurelle de cette option, et la
raison de garder le fond auto-hébergé déployé.

**Chaque tuile dit au fournisseur où l'on regarde.** Ce n'est pas le chemin des
positions, qui reste chez soi, mais c'en est une fuite. Un fond auto-hébergé
n'a pas ce défaut.

**La carte n'est pas un critère de sortie.** Le point bloquant du projet reste
`arch/14_protocole_test_terrain.md` sur le Redmi Note 11, et le redémarrage
automatique en particulier. Une carte qui s'afficherait mal ne coûterait pas
une position ; un reboot non rattrapé coûte tout le temps jusqu'au prochain
déverrouillage.

# 7. Deux pièges d'outillage rencontrés

**`Properties.load(InputStream)` décode en ISO-8859-1.** La mention légale
« Maps © Thunderforest » arrivait sur le téléphone en « Maps Â© Thunderforest ».
Rien n'échoue, aucune exception, le texte est simplement faux. `local.properties`
se lit explicitement en UTF-8.

**`drawBehind` ne borne pas au cadre du composant.** Une tuile déborde toujours
des bords par construction, et le fond se peignait par-dessus le bandeau
d'état. `clipToBounds` suffit, mais rien ne le signale.

# 8. Changer de fond de carte

Trois lignes de `local.properties`, puis reconstruire. Pour revenir au fond
auto-hébergé — provenance, fabrication, montage nginx, et la marche à suivre
pour passer au détail OpenStreetMap complet — tout est dans
`tools/tiles/README.md`.

Le jour où le détail au niveau de la rue doit fonctionner **hors ligne partout**,
la seule voie est le vectoriel : un extrait `.pmtiles` du corridor rendu par
MapLibre Native. Mesuré le 26 août 2026 sur le planet Protomaps : un couloir de
100 km autour de l'itinéraire pèse 2,5 Go jusqu'au zoom 14, ce qui tient sur le
téléphone. Le verrou n'est pas l'infrastructure mais la dépendance MapLibre,
que l'ADR-006 a écartée.
