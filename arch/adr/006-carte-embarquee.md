**ADR-006 — Carte embarquée dans l'application**

*Statut : Accepté — décision courante arrêtée le 26 août 2026*

# Contexte

Deux documents divergeaient. `arch/01_android_POC.md` §11 place la carte hors du
périmètre du POC et décrit un écran de statut textuel ; `arch/09_design_app_V1.md`
§1-2 en fait le contenu principal, occupant 75 à 85 % de la hauteur.

Trois contraintes pèsent sur un fond de carte embarqué :

1. `arch/00` §8 règle 7 interdit le SDK Google Maps.
2. La Tile Usage Policy d'OpenStreetMap exclut les applications mobiles de ses
   serveurs de tuiles.
3. Hors ligne — le cas normal du voyage — un fond non pré-téléchargé est gris.
   Le tracé, lui, vient de Room et s'affiche toujours.

# Décision

**La carte est le contenu principal de l'écran d'accueil. Le tracé est dessiné
sur `Canvas`, en Web Mercator, à partir de la base locale. Le fond est fait de
tuiles raster dont la source est une configuration hors du dépôt.**

Aucune bibliothèque cartographique n'entre au projet. MapLibre Native est
écarté : c'est un moteur complet, et le critère du projet est qu'une seule
personne puisse le réparer pendant un an.

Aucun fournisseur de tuiles n'est figé dans le dépôt : le choix engage une
licence, parfois un compte, et n'a pas sa place dans un dépôt public. Sans
configuration, la carte reste sur fond uni et n'émet aucune requête.

# Options écartées

**SDK Google Maps** — interdit par `arch/00` §8 règle 7.

**MapLibre Native + tuiles vectorielles.** Techniquement le meilleur choix :
rendu supérieur, et hors-ligne complet par un extrait `.pmtiles` du corridor —
2,5 Go jusqu'au zoom 14 pour un couloir de 100 km, mesuré le 26 août 2026. Rien
ne bloque côté infrastructure. C'est la dépendance qui bloque, et cette
décision-là appartient au critère « réparable par une seule personne ».
**À rouvrir si le détail hors ligne partout devient nécessaire.**

**Tuiles OpenStreetMap directes** — hors des règles d'usage pour une
application mobile, avec un risque de blocage en cours de voyage.

**Pile PostGIS/Mapnik auto-hébergée** — 150 à 250 Go pour l'import du corridor.
Ni le VPS (27 Go libres) ni le portable (6,7 Go) ne l'encaissent.

# Conséquences

- **La présentation reste un adaptateur.** Ni le domaine, ni les use cases, ni
  la persistance ne savent qu'une carte existe.
- **Le détail affiché est celui du fournisseur**, ni plus ni moins. Changer de
  source est une modification de trois lignes, éprouvée deux fois.
- **Le hors-ligne dépend de la licence de la source.** Les offres gratuites
  autorisent en général le cache des tuiles consultées et interdisent le
  pré-chargement d'une zone. Le fond auto-hébergé de `tools/tiles` reste
  déployé pour cette raison : c'est la seule source que le projet contrôle de
  bout en bout, donc la seule qu'on ait le droit de pré-charger.
- **Chaque tuile dit au fournisseur où l'on regarde.** Ce n'est pas le chemin
  des positions, mais c'en est une fuite, et elle disparaîtrait avec un fond
  auto-hébergé.
- L'état courant et les détails d'implémentation sont dans `arch/18`.

# Historique

Cette décision a été prise en trois temps, et l'ordre a compté.

**18 août 2026 — reportée.** Le risque du projet était la perte de points, pas
l'absence de carte. L'écran d'accueil est resté un écran de statut le temps que
le noyau soit prouvé en conditions réelles.

**23 août 2026 — carte livrée, puis fond auto-hébergé.** Le noyau éprouvé, la
carte a été écrite. Livrée d'abord sans fond, sur les trois contraintes
ci-dessus ; un tracé flottant dans le vide s'est révélé insuffisant à l'usage.
La sortie a été de contourner la deuxième contrainte par le haut plutôt que de
l'enfreindre : un fond fabriqué depuis des données Natural Earth du domaine
public, servi par le VPS.

**26 août 2026 — fournisseur externe.** Natural Earth s'arrête aux grands axes ;
le détail au niveau de la rue demandait des données OpenStreetMap, que le projet
n'a pas les moyens de rendre lui-même. Thunderforest, offre gratuite, style
*Outdoors*. Le fond auto-hébergé est conservé en repli.

Ce que cette histoire enseigne, et qui vaut au-delà de la carte : **le report a
été bon**. Il a évité d'écrire trois fois une carte pendant que le vrai risque
n'était pas fermé. Et le choix de Web Mercator, fait quand aucun fond n'existait,
a rendu chacune des deux additions indolores.
