**ADR-006 — Carte embarquée dans l'application**

*Statut : Reportée en fin de V1 — 2026-08-18*\
*Complétée : carte livrée sans fond cartographique — 2026-08-23*

# Contexte

Deux documents divergeaient :

- `arch/01_android_POC.md` §11 place explicitement la « carte embarquée » hors du
  périmètre du POC, et décrit en §10 un écran de statut textuel.
- `arch/09_design_app_V1.md` §1-2 fait de la carte le contenu principal, occupant
  75 à 85 % de la hauteur de l'écran.

Trois contraintes techniques pèsent sur la carte embarquée :

1. `arch/00` §8 règle 7 interdit le SDK Google Maps. Le choix conforme est
   MapLibre Native Android, fork open source de Mapbox sans télémétrie.
2. La Tile Usage Policy d'OpenStreetMap interdit les applications mobiles sur
   `tile.openstreetmap.org`. Il faut donc un proxy/cache de tuiles sur le VPS —
   du travail serveur qui n'existe pas encore.
3. Hors ligne, ce qui est le cas normal du voyage, les tuiles non pré-téléchargées
   sont absentes : la carte serait grise. Le tracé, lui, vient de Room et
   s'affiche toujours.

# Options

**1. Carte MapLibre + tuiles proxifiées par le VPS + cache ambiant**, dès la V1.
**2. Écran de statut d'abord, carte ajoutée en fin de V1**, une fois le noyau
prouvé en conditions réelles.
**3. Tracé seul dessiné sur Canvas**, sans tuiles ni dépendance cartographique.

# Décision

**Option 2.** L'écran principal de la phase POC est un écran de statut conforme à
`arch/01` §10. La carte est ajoutée en fin de V1, après validation terrain du
noyau tracking et synchronisation, et l'écran principal prend alors la forme
décrite par `arch/09` §2.

La lecture retenue des deux documents est chronologique et non contradictoire :
`arch/01` décrit le noyau technique à prouver, `arch/09` décrit l'application
finie qui part en voyage. Les deux s'appliquent, dans cet ordre.

# Conséquences

- Le risque principal du projet est la perte de points, pas l'absence de carte sur
  le téléphone. L'ordre de travail suit ce risque.
- La carte utile à la famille est celle du site web, qui la sert déjà
  (`arch/05`, `arch/11`). La carte embarquée est un confort, pas une fonction
  critique.
- La présentation étant un adaptateur, remplacer l'écran de statut par un écran
  carte ne touche ni le domaine, ni les use cases, ni la persistance. Le
  `TrackingStatus` exposé par `ObserveTrackingStatus` est le même dans les deux cas.
- Décision à rouvrir à l'ouverture de la phase 11, avec le choix du fournisseur de
  tuiles et la stratégie hors ligne. Cet ADR sera alors complété ou remplacé.

# Réouverture du 23 août 2026

La décision prévoyait d'être rouverte au moment d'écrire la carte. Elle l'est,
et l'arbitrage porte cette fois sur le **fond de carte**, pas sur la carte.

## Ce qui a changé

Rien sur le terrain, et c'est l'argument : le relevé du 23 août montre que le
risque restant est le redémarrage non rattrapé, pas la synchronisation. Le
noyau tenait la condition posée par l'option 2 — « une fois le noyau prouvé en
conditions réelles ». La carte pouvait donc être écrite.

Les trois contraintes du fond de carte, elles, sont intactes : pas de SDK
Google, pas de tuiles OSM pour une application mobile sans cache sur le VPS,
et un fond gris hors ligne — c'est-à-dire la plupart du temps.

## Décision

**Option 3 pour le fond, option 2 pour le calendrier.** L'écran principal
montre désormais la carte, et cette carte dessine le tracé sur un fond uni.
Aucune tuile, aucune dépendance cartographique, aucun appel réseau : ce qui
vient de Room s'affiche partout, y compris au fond d'un fjord sans réseau.

Ce que les tuiles auraient apporté et qui manquerait sans elles — savoir à
quelle distance on regarde — est rendu par une échelle graphique dessinée sur
la carte. C'est la raison pour laquelle cette échelle n'est pas décorative.

## Conséquences

- La géométrie vit dans `domain/MapProjection.kt`, `domain/MapViewport.kt` et
  `domain/MapScaleBar.kt`, sans dépendance Android, et se teste sans émulateur.
  Le rendu Compose n'a plus qu'à peindre.
- La projection retenue est **Web Mercator**, celle des tuiles, alors que rien
  ne l'imposait pour un fond uni. C'est le choix qui rend l'ajout ultérieur
  d'un cache de tuiles indolore : les tuiles se peindraient sous le tracé sans
  toucher une ligne de géométrie.
- La carte encode l'état de synchronisation par la couleur : bleu pour ce que
  le serveur détient, orange pour ce qui n'est encore que sur le téléphone.
  C'est la seule information de synchronisation sur l'accueil ; le compteur de
  file reste au diagnostic (`arch/09` §3).
- Le tracé est plafonné à 2 000 points, environ une semaine à la cadence par
  défaut. `arch/09` §2 parle de « trajet récent », et l'historique complet est
  l'affaire du site familial.
- **Dette assumée** : la requête du tracé trie sur `recorded_at`, que l'index
  `(sync_state, recorded_at)` ne sert pas. SQLite balaie donc la table. À
  cent mille lignes le coût reste de quelques dizaines de millisecondes, et
  seulement quand l'écran est allumé — le flux s'arrête sinon. L'index dédié
  demanderait une migration de schéma, ce qui ne se fait pas à quelques jours
  d'un départ (ADR-005). À reprendre en V2, ou plus tôt si le rendu accroche.
