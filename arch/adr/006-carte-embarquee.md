**ADR-006 — Carte embarquée dans l'application**

*Statut : Reportée en fin de V1 — 2026-08-18*

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
