**18 — Carte embarquée V1**

*Ce qui a été construit le 23 août 2026, et pourquoi de cette façon*

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# 1. Statut de ce document

`arch/09_design_app_V1.md` dit **ce que** l'écran principal doit montrer et
fait foi. `arch/adr/006-carte-embarquee.md` dit **ce qui a été tranché** :
d'abord le report, puis, à la réouverture du 23 août, l'absence de fond
cartographique. Ce document dit **comment c'est fait**, et sert à quelqu'un qui
reprendrait la carte sans avoir suivi la session.

En cas de divergence, `arch/09` et `arch/00` font foi.

# 2. Ce que la carte montre

L'écran d'accueil garde la structure de `arch/09` §2 : la carte occupe la zone
centrale, le bandeau bas reste compact.

| Élément | Comportement |
|---|---|
| Fond de carte | Tuiles raster auto-hébergées : mer, terres, lacs, fleuves, frontières, zones urbaines. Absent si aucune source n'est configurée |
| Tracé | Les positions de la base locale, reliées dans l'ordre du voyage |
| Couleur du tracé | Bleu là où le serveur détient les points, orange là où ils ne sont encore que sur le téléphone |
| Position actuelle | Disque cerclé de blanc sur le point le plus récent |
| Échelle graphique | En bas à gauche, une distance ronde et sa longueur en pixels |
| Légende | En haut à gauche ; la ligne « Sur le téléphone » n'apparaît que s'il y a des points en attente |
| Cadrage | Automatique sur tout le tracé visible, marge comprise |
| Gestes | Glisser pour déplacer, pincer pour zoomer |
| Recentrer | Bouton flottant, visible **uniquement** après un déplacement manuel |
| Base vide | « Aucune position enregistrée pour l'instant. » |

Deux comportements méritent d'être connus avant de les modifier.

**Le cadrage automatique ne reprend pas la main tout seul.** Dès que la
voyageuse déplace ou zoome la carte, le cadrage devient manuel et le reste
jusqu'au bouton « Recentrer ». Sans cela, la carte sauterait sous les doigts à
chaque nouvelle position, c'est-à-dire toutes les cinq minutes.

**La couleur d'un segment est celle de son point d'arrivée.** C'est l'état du
trajet une fois parcouru. Comme la synchronisation avance dans l'ordre du
voyage, on lit une longue portion bleue et une queue orange : exactement ce
qu'on veut savoir d'un coup d'œil après trois jours sans réseau.

# 3. Décisions techniques et leurs raisons

## 3.1 Des tuiles, mais aucune bibliothèque cartographique

Le fond de carte est fait de tuiles raster XYZ peintes dans le même
`Canvas` Compose que le tracé, avec OkHttp qui était déjà là. MapLibre Native
aurait apporté un moteur vectoriel complet, et une dépendance que personne ne
saurait réparer seul en Norvège.

*L'histoire de cette décision.* La V1 est d'abord sortie sans fond du tout, sur
les trois contraintes de l'ADR-006 : pas de SDK Google (`arch/00` §8 règle 7),
la Tile Usage Policy d'OpenStreetMap qui exclut les applications mobiles, et un
fond gris hors ligne. À l'usage, un tracé flottant dans le vide s'est révélé
insuffisant. La sortie a été de lever la deuxième contrainte plutôt que de la
contourner : **le fond est auto-hébergé**, fabriqué par `tools/tiles` depuis des
données du domaine public et servi par le VPS. Aucun compte, aucun quota,
aucune règle d'usage tierce à respecter.

*Le hors-ligne.* Le cache disque est interrogé avant le réseau, toujours (§3.2).
Il vit dans `filesDir` et non `cacheDir` : Android vide le second sous pression
de stockage, et le ferait au pire moment.

## 3.2 Le cache disque est interrogé avant le réseau

Une tuile déjà vue n'est jamais redemandée au serveur.

*Pourquoi.* Ce n'est pas une optimisation. Une tuile ne change pas en un an, et
chaque requête évitée est de la batterie et du forfait données économisés dans
un pays où les deux se comptent. Surtout : une zone consultée une fois à
l'hôtel reste lisible trois jours plus tard au fond d'un fjord. C'est ce
renversement de priorité qui rend le fond utilisable en voyage.

Le client HTTP des tuiles est séparé de celui de l'API, et doit le rester :
celui de l'API ne doit **jamais** porter de cache, une réponse de
synchronisation servie depuis un cache serait un bug de correction.

## 3.3 Projection Web Mercator, choisie avant d'en avoir besoin

Le premier jet, sans fond, n'imposait aucune projection particulière.

*Pourquoi celle-là quand même.* C'est la projection de toutes les tuiles.
Quand le fond est arrivé, il s'est posé sous le tracé sans qu'une seule ligne
de géométrie change. Le coût de ce choix était nul, le gain a été total.

## 3.4 Toute la géométrie dans `domain/`, sans Android

`MapProjection`, `MapViewport` et `MapScaleBar` ne connaissent ni Compose, ni
Android. Le rendu ne fait que peindre des pixels.

*Pourquoi.* Un cadrage faux est invisible à la relecture et évident en test. La
tâche Gradle `checkCoreIsFrameworkFree` garantit que cette séparation ne se
perdra pas, et les 30 tests de géométrie tournent sans émulateur.

## 3.5 La projection est calculée une fois, pas à chaque image

`TrackMap` projette les points en coordonnées normalisées quand la liste
change, puis ne repasse par le viewport que pour une multiplication et une
addition à chaque image.

*Pourquoi.* Mercator coûte un logarithme et une tangente par point. À deux
mille points et soixante images par seconde pendant un glissement, la
différence est celle entre une carte fluide et une carte qui accroche sur un
appareil à 4 Go.

## 3.6 Le tracé est découpé en tronçons de même état, pas en segments

*Pourquoi.* Un appel de dessin par segment ferait deux mille appels par image.
Comme la synchronisation avance dans l'ordre du voyage, il n'y a en pratique
que deux tronçons : ce qui est parti et ce qui reste. Le découpage reste
général — un trou de synchronisation au milieu s'affiche correctement — mais le
cas courant ne coûte rien.

## 3.7 Deux mille points, pas le voyage entier

`ObserveRecentTrack.RECENT_POINT_LIMIT` plafonne à 2 000 points, soit environ
une semaine à la cadence par défaut de cinq minutes.

*Pourquoi.* `arch/09` §2 parle de « polyline du trajet récent ». La question à
laquelle la carte répond est « où suis-je et d'où est-ce que j'arrive », pas
« qu'ai-je fait il y a huit mois » : l'historique complet est l'affaire du site
familial, qui a un écran et un processeur pour ça. Le plafond protège aussi la
mémoire de l'appareil et la lisibilité du tracé.

## 3.8 La requête ne lit que quatre colonnes

Le DAO renvoie un `TrackPointRow` — latitude, longitude, instant, état — et non
des `LocationEntity` complètes.

*Pourquoi.* Lire douze colonnes pour en dessiner trois, sur une table qui
comptera plus de cent mille lignes au bout d'un an, se paierait à chaque
nouvelle position enregistrée.

## 3.9 Le flux s'arrête quand personne ne regarde

`MainViewModel.track` utilise `SharingStarted.WhileSubscribed`.

*Pourquoi.* Écran éteint, la requête cesse et la base n'est plus relue à chaque
capture. La carte est un confort : elle ne doit rien coûter à l'autonomie quand
elle n'est pas affichée.

## 3.10 Couleurs fixes, hors du thème

`TrackColors.synced` (`#1E88E5`) et `TrackColors.pending` (`#F57C00`) ne
changent pas avec le thème clair ou sombre, comme les couleurs d'état du suivi.

*Pourquoi.* Ces deux teintes encodent une information, pas une ambiance. Elles
sont choisies pour rester lisibles sur les deux fonds de carte, et l'orange est
le même que celui de l'état « hors ligne » du bandeau : un seul mot de
vocabulaire visuel pour dire « c'est sur le téléphone ».

## 3.11 Les marges du cadrage sont inégales

`MapInsets` donne quatre marges au cadrage automatique, et le tracé est centré
dans la zone libre, pas dans la zone de dessin.

*Pourquoi.* Avec une marge uniforme, le marqueur de position actuelle passait
sous la légende dès que le point le plus récent tombait en haut à gauche — ce
qui est arrivé à la première installation sur le OnePlus. La hauteur de la
légende est **mesurée** et non devinée : elle change avec la taille de police
du système.

*Ce que ça dit sur la méthode.* Les seize tests de cadrage passaient tous. Ils
vérifiaient que le tracé tient dans la zone de dessin, ce qui était vrai ; ils
ne pouvaient pas savoir qu'un élément d'interface était posé par-dessus. Un
test de plus couvre maintenant ce cas, mais c'est l'appareil qui l'a trouvé.

## 3.12 L'échelle graphique n'est pas décorative

`arch/09` §3 retire de l'accueil toute statistique décorative. L'échelle y
échappe volontairement.

*Pourquoi.* Sans fond de carte, c'est la seule chose qui dise si le tracé
visible fait deux rues ou deux cents kilomètres. Elle remplace une information
que les tuiles auraient donnée gratuitement. Même raisonnement pour la légende,
qui est la clé du code couleur et non un tableau de bord — et qui se réduit à
une ligne quand rien n'est en attente.

# 4. Où vit quoi

| Fichier | Rôle |
|---|---|
| `domain/MapProjection.kt` | Web Mercator : coordonnées ↔ carré unité, taille du monde, mètres par pixel |
| `domain/MapViewport.kt` | Centre et zoom, passage à l'écran, glissement, pincement ancré, cadrage automatique |
| `domain/MapScaleBar.kt` | Choix de la distance ronde qui tient dans la place disponible |
| `domain/TileGrid.kt` | Quelles tuiles couvrent la vue, et où les poser |
| `application/port/TileStore.kt` | D'où viennent les tuiles, et jusqu'à quel zoom |
| `application/usecase/LoadMapTile.kt` | Une tuile, ou rien |
| `adapter/…/tiles/HttpTileStore.kt` | Cache disque d'abord, réseau ensuite |
| `infrastructure/di/TileModule.kt` | Client HTTP dédié, cache dans `filesDir` |
| `presentation/map/TileLayer.kt` | Cache mémoire des tuiles décodées, chargement |
| `tools/tiles/` | Fabrication des tuiles et provenance des données |
| `domain/model/TrackPoint.kt` | Un point réduit à ce que la carte dessine |
| `application/port/LocationStore.kt` | `observeRecentTrack(limit)` — ordre chronologique contractuel |
| `application/usecase/ObserveRecentTrack.kt` | Le tracé de l'accueil et son plafond |
| `adapter/…/room/LocationDao.kt` | La requête et sa projection de lecture `TrackPointRow` |
| `adapter/…/room/RoomLocationStore.kt` | Remise en ordre du voyage et conversion vers le domaine |
| `presentation/map/TrackMap.kt` | Rendu Compose, gestes, légende, bouton Recentrer |
| `presentation/map/MainViewModel.kt` | Expose `track` en flux |
| `presentation/map/MainScreen.kt` | Assemble carte et bandeau d'état |
| `presentation/common/Theme.kt` | `TrackColors` |

Le domaine et les use cases ne connaissent toujours aucun framework ; la
présentation reste un adaptateur, comme l'annonçait l'ADR-006.

# 5. Ce qui est vérifié, et comment

**Tests unitaires, sans émulateur.**

- `domain/MapProjectionTest.kt` — bords du monde, réversibilité sur toute la
  plage France–Cap Nord, comportement au-delà de la limite de Mercator, et le
  fait que l'échelle se resserre vers le nord (sans le cosinus de latitude,
  l'échelle affichée au Cap Nord serait fausse d'un facteur trois).
- `domain/MapViewportTest.kt` — sens des axes, glissement réversible, **zoom
  qui garde immobile le point sous les doigts**, bornes de zoom, cadrage qui
  fait effectivement tenir tout le tracé dans la marge, tracé réduit à un point,
  zone de dessin pas encore mesurée, marge plus grande que l'écran.
- `domain/MapScaleBarTest.kt` — la barre ne déborde jamais, prend la plus
  grande distance ronde disponible, et se tait plutôt que de mentir.
- `persistence/RoomLocationStoreTest.kt` — ordre du voyage, plafond qui garde
  les points **les plus récents**, distinction envoyé / en attente, base vide.

**Vérification visuelle de la géométrie, hors appareil.** Avant tout
branchement, le vrai code de `domain/Map*` a été exécuté sur un tracé simulé de
900 points pour produire un rendu SVG : cadrage, code couleur et échelle
contrôlés à l'œil. Ce rendu était un outil jetable et n'a pas été conservé — il
se réécrit en vingt lignes.

**Vérification sur appareil, 23 août au soir.** Build release signé installé
sur le OnePlus 8T par-dessus la version du 19 août, données conservées. La
carte affiche le tracé réel, le bandeau annonce « Suivi actif » et l'âge de la
dernière position, l'échelle indique 100 km, et la légende se réduit à
« Envoyé » — cohérent avec une file d'attente vide. **Un défaut trouvé et
corrigé** : la légende masquait le marqueur de position actuelle (§3.11).

**Vérification du fond, hors appareil.** Les vraies tuiles fabriquées par
`tools/tiles` ont été composées avec le vrai `TileGrid` et le vrai
`MapViewport`, sur un tracé France–Cap Nord : les tuiles se posent exactement
sous le tracé. C'est la preuve que les deux projections coïncident — celle du
générateur Python et celle de `domain/MapProjection.kt` — ce qui est la seule
chose qui pouvait rater en silence.

**Vérification du fond sur appareil, 23 août au soir.** Fond auto-hébergé
déployé, application reconstruite et installée sur le OnePlus : les tuiles se
posent sous le tracé, à la bonne échelle. **Deux défauts trouvés, tous les deux
invisibles en test :**

- *Aucune tuile ne s'affichait.* Le chargeur tenait à la main la liste des
  tuiles « en cours », pour ne pas les redemander. Une coroutine annulée avant
  d'avoir démarré n'exécute pas son `finally` : la tuile restait marquée en
  cours pour toujours. Aucune erreur nulle part, juste un fond qui ne venait
  pas. Corrigé en confiant ce cycle de vie à Compose — un effet par tuile,
  indexé sur son identifiant — ce qui supprime la comptabilité au lieu de la
  rendre juste.
- *Les tuiles débordaient sous le bandeau d'état.* `drawBehind` ne borne pas au
  cadre du composant, et une tuile déborde toujours des bords par construction.
  Corrigé par `clipToBounds`.

Le réseau avait été écarté d'abord, en interrogeant le serveur depuis le
téléphone : `curl` répondait 200. Sans cette vérification, la piste évidente
aurait été le réseau, et elle aurait coûté une heure.

**Gestes et thème, vérifiés le même soir.** Sur le OnePlus, en thème sombre :
le glissement déplace la carte et les tuiles de la zone découverte se chargent
à la volée ; « Recentrer » n'apparaît qu'après un déplacement manuel et rend
bien le cadrage automatique, tracé dégagé de la légende et de l'échelle.

**Fond 1:10 m vérifié sur appareil, 23 août 22 h 54.** Villes nommées, routes,
limites régionales et zones urbaines s'affichent sur le OnePlus, cadrage et
échelle justes, premier chargement complet depuis la génération `v2` de l'URL.

**Ce qui n'est toujours pas vérifié.** Le pincement — `adb` ne sait pas simuler
deux doigts, il faudra le faire à la main. Le rendu au-delà du zoom 8, là où
les tuiles sont agrandies. Le comportement hors ligne du cache, qui est
pourtant le mode normal du voyage. La fluidité à deux mille points : le tracé
de l'appareil de pré-validation en compte bien moins. Et rien de tout cela n'a
été éprouvé sur le Redmi Note 11, qui est l'appareil qui fait foi.

# 6. Dette assumée et points ouverts

**L'index manquant.** La requête du tracé trie sur `recorded_at`, que l'index
`(sync_state, recorded_at)` ne sert pas : SQLite balaie la table. À cent mille
lignes le coût reste de quelques dizaines de millisecondes, et seulement quand
l'écran est allumé. L'index dédié demanderait une migration de schéma, qui ne
se fait pas à quelques jours d'un départ (ADR-005). À reprendre en V2, ou plus
tôt si le rendu accroche sur l'appareil.

**Le fond de carte s'arrête au zoom 8.** Il donne le contexte d'un voyage, pas
celui d'un carrefour. Le détail OpenStreetMap est décrit dans
`tools/tiles/README.md` et demande une infrastructure qui n'existe pas encore.

**Le fond au-delà du zoom 8** n'a pas été regardé sur l'appareil : c'est là que
les tuiles sont agrandies, et personne n'a encore vu ce que ça donne en main.

**Le hors-ligne n'a pas été éprouvé.** Tout le pari du cache — consulter une
zone au chaud, la retrouver trois jours plus tard sans réseau — repose sur un
raisonnement, pas sur un essai.

Le test bute sur un détail d'outillage : le débogage se fait **par Wi-Fi**, donc
couper le réseau du téléphone coupe aussi le lien ADB. Deux façons de s'en
sortir, l'une comme l'autre à faire une fois avant le départ :

- brancher un câble USB, puis mode avion ;
- ou arrêter le conteneur `site` une minute, ce qui rend les tuiles
  injoignables sans toucher au téléphone. Le chemin testé est le même : le
  cache doit répondre seul.

**La carte n'est pas un critère de sortie.** Le point bloquant du projet reste
`arch/14_protocole_test_terrain.md` sur le Redmi Note 11, et le redémarrage
automatique en particulier. Une carte qui s'afficherait mal ne coûterait pas
une position ; un reboot non rattrapé coûte tout le temps jusqu'au prochain
déverrouillage.

# 7. Le fond de carte : d'où il vient

Fabriqué par `tools/tiles/build_basemap.py` depuis des données Natural Earth
1:10 m, **domaine public**, découpées au corridor et versionnées dans le dépôt. Servi par le conteneur
`site` du serveur, publiquement et sans mot de passe : ces tuiles ne disent
rien du voyage, et les mettre derrière le lien secret familial obligerait à
embarquer ce secret dans l'APK.

L'URL porte une génération — `/tiles/v2/{z}/{x}/{y}.png` — et c'est une
conséquence directe du §3.2 : le cache est lu avant le réseau et les tuiles sont
annoncées immuables, donc un fond refabriqué ne parviendrait jamais aux
téléphones qui ont déjà l'ancien. Bumper le numéro est la seule façon de pousser
un nouveau fond.

Trois lignes de `local.properties` branchent l'application dessus ; sans elles,
la carte reste sur fond uni et n'émet aucune requête. **Aucun serveur de tuiles
n'est figé dans le dépôt** : le choix engage une licence, parfois un compte, et
n'a pas sa place dans un dépôt public.

Le détail complet — provenance exacte, commande de refabrication, montage
nginx, et la marche à suivre pour passer au détail OpenStreetMap avec les rues
— est dans `tools/tiles/README.md`.

**Ce que ce fond n'a pas : les rues.** Natural Earth s'arrête aux grands axes,
et le rendu OpenStreetMap demande une pile PostGIS/Mapnik qui ne tenait pas sur
la machine disponible. Il donne en revanche mer, terres, lacs, fleuves,
frontières, limites régionales, zones urbaines, autoroutes et villes nommées.

**Et pas de zoom au-delà de 8**, non par économie mais par honnêteté : la donnée
1:10 m a une résolution de l'ordre du kilomètre, quand le zoom 9 affiche
300 mètres par pixel. Descendre plus bas ne montrerait rien de neuf, seulement
des traits plus lisses, pour quatre fois plus de fichiers. Au-delà,
l'application agrandit le dernier niveau servi et le tracé reste net
par-dessus ; monter en détail plus tard ne demandera qu'un `madhi.tiles.maxZoom`
plus haut.
