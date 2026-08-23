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

## 3.1 Aucune tuile, aucune bibliothèque cartographique

Le tracé est dessiné sur un fond uni, en Compose `Canvas`.

*Pourquoi.* Les trois contraintes de l'ADR-006 n'ont pas bougé : `arch/00` §8
règle 7 interdit le SDK Google Maps, la Tile Usage Policy d'OpenStreetMap
interdit les applications mobiles sur ses serveurs de tuiles, et un fond non
pré-téléchargé serait gris hors ligne — l'état normal du voyage. Ce qui vient
de Room, lui, s'affiche au fond d'un fjord.

*Conséquence.* Aucune dépendance ajoutée au projet. La carte ne fait aucun
appel réseau et ne peut donc pas devenir un chemin par lequel une donnée
s'échappe.

## 3.2 Projection Web Mercator quand même

Rien n'imposait Web Mercator pour un fond uni : n'importe quelle projection
aurait dessiné un tracé plausible.

*Pourquoi.* C'est la projection de toutes les tuiles. Le jour où le VPS sert un
cache, les tuiles se peindront sous le tracé sans toucher une ligne de
géométrie. Le coût de ce choix aujourd'hui est nul ; le gain plus tard est la
différence entre poser une couche et tout réécrire.

## 3.3 Toute la géométrie dans `domain/`, sans Android

`MapProjection`, `MapViewport` et `MapScaleBar` ne connaissent ni Compose, ni
Android. Le rendu ne fait que peindre des pixels.

*Pourquoi.* Un cadrage faux est invisible à la relecture et évident en test. La
tâche Gradle `checkCoreIsFrameworkFree` garantit que cette séparation ne se
perdra pas, et les 30 tests de géométrie tournent sans émulateur.

## 3.4 La projection est calculée une fois, pas à chaque image

`TrackMap` projette les points en coordonnées normalisées quand la liste
change, puis ne repasse par le viewport que pour une multiplication et une
addition à chaque image.

*Pourquoi.* Mercator coûte un logarithme et une tangente par point. À deux
mille points et soixante images par seconde pendant un glissement, la
différence est celle entre une carte fluide et une carte qui accroche sur un
appareil à 4 Go.

## 3.5 Le tracé est découpé en tronçons de même état, pas en segments

*Pourquoi.* Un appel de dessin par segment ferait deux mille appels par image.
Comme la synchronisation avance dans l'ordre du voyage, il n'y a en pratique
que deux tronçons : ce qui est parti et ce qui reste. Le découpage reste
général — un trou de synchronisation au milieu s'affiche correctement — mais le
cas courant ne coûte rien.

## 3.6 Deux mille points, pas le voyage entier

`ObserveRecentTrack.RECENT_POINT_LIMIT` plafonne à 2 000 points, soit environ
une semaine à la cadence par défaut de cinq minutes.

*Pourquoi.* `arch/09` §2 parle de « polyline du trajet récent ». La question à
laquelle la carte répond est « où suis-je et d'où est-ce que j'arrive », pas
« qu'ai-je fait il y a huit mois » : l'historique complet est l'affaire du site
familial, qui a un écran et un processeur pour ça. Le plafond protège aussi la
mémoire de l'appareil et la lisibilité du tracé.

## 3.7 La requête ne lit que quatre colonnes

Le DAO renvoie un `TrackPointRow` — latitude, longitude, instant, état — et non
des `LocationEntity` complètes.

*Pourquoi.* Lire douze colonnes pour en dessiner trois, sur une table qui
comptera plus de cent mille lignes au bout d'un an, se paierait à chaque
nouvelle position enregistrée.

## 3.8 Le flux s'arrête quand personne ne regarde

`MainViewModel.track` utilise `SharingStarted.WhileSubscribed`.

*Pourquoi.* Écran éteint, la requête cesse et la base n'est plus relue à chaque
capture. La carte est un confort : elle ne doit rien coûter à l'autonomie quand
elle n'est pas affichée.

## 3.9 Couleurs fixes, hors du thème

`TrackColors.synced` (`#1E88E5`) et `TrackColors.pending` (`#F57C00`) ne
changent pas avec le thème clair ou sombre, comme les couleurs d'état du suivi.

*Pourquoi.* Ces deux teintes encodent une information, pas une ambiance. Elles
sont choisies pour rester lisibles sur les deux fonds de carte, et l'orange est
le même que celui de l'état « hors ligne » du bandeau : un seul mot de
vocabulaire visuel pour dire « c'est sur le téléphone ».

## 3.10 L'échelle graphique n'est pas décorative

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

**Vérification visuelle de la géométrie.** Aucun appareil n'était branché en
ADB le 23 août. Le vrai code de `domain/Map*` a donc été exécuté sur un tracé
simulé de 900 points pour produire un rendu SVG : cadrage, code couleur et
échelle ont été contrôlés à l'œil. Ce rendu était un outil jetable et n'a pas
été conservé — il se réécrit en vingt lignes si besoin.

**Ce qui n'est pas vérifié.** Le rendu Compose lui-même, les gestes, et la
fluidité à deux mille points sur un appareil réel. C'est à confirmer sur le
OnePlus, puis sur le Redmi.

# 6. Dette assumée et points ouverts

**L'index manquant.** La requête du tracé trie sur `recorded_at`, que l'index
`(sync_state, recorded_at)` ne sert pas : SQLite balaie la table. À cent mille
lignes le coût reste de quelques dizaines de millisecondes, et seulement quand
l'écran est allumé. L'index dédié demanderait une migration de schéma, qui ne
se fait pas à quelques jours d'un départ (ADR-005). À reprendre en V2, ou plus
tôt si le rendu accroche sur l'appareil.

**Le fond de carte.** Il reste conditionné à un cache de tuiles sur le VPS.
Tant qu'il n'existe pas, la question ne se pose pas côté Android.

**La carte n'est pas un critère de sortie.** Le point bloquant du projet reste
`arch/14_protocole_test_terrain.md` sur le Redmi Note 11, et le redémarrage
automatique en particulier. Une carte qui s'afficherait mal ne coûterait pas
une position ; un reboot non rattrapé coûte tout le temps jusqu'au prochain
déverrouillage.

# 7. Ajouter un fond de tuiles plus tard

La marche à suivre, pour que le prochain n'ait pas à la redécouvrir :

1. **Côté serveur d'abord.** Un cache de tuiles sur le VPS, alimenté depuis une
   source dont la licence autorise l'usage mobile. C'est là qu'est le travail,
   et il n'est pas commencé.
2. **Côté Android ensuite.** `MapViewport` donne déjà le centre, le zoom et la
   taille du monde en pixels au format des tuiles (256 px, zoom entier). Les
   tuiles visibles se déduisent du viewport, se peignent dans le `Canvas`
   **avant** l'appel à `drawTrack`, et rien d'autre ne change.
3. **Le hors-ligne reste la règle.** Une tuile absente ne doit jamais faire
   disparaître le tracé ni bloquer le rendu : le fond est un bonus, la
   géométrie est la fonction.
