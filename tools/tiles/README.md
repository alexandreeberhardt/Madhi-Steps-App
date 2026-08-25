# Fond de carte auto-hébergé

Fabrique des tuiles raster que le serveur sert lui-même. Aucun compte, aucune
clé d'API, aucun quota, aucun tiers dont dépendre pendant un an.

**Ce fond n'est plus celui que l'application utilise au quotidien.** Depuis le
26 août 2026 elle affiche Thunderforest, qui donne le détail au niveau de la
rue (ADR-006). Celui-ci reste fabriqué, déployé et documenté pour une raison
précise : c'est la seule source que le projet contrôle de bout en bout, donc la
**seule qu'on ait le droit de pré-charger en masse** — ce qu'un an sans réseau
finira par exiger. Y revenir est une modification de trois lignes.

## Ce que ça produit, et ce que ça ne produit pas

Mer, terres, côtes, lacs, fleuves, frontières, limites régionales, zones
urbaines, grandes routes, et les villes avec leurs noms. Du zoom 0 au zoom 8,
sur l'Europe de l'Ouest et du Nord.

**Il n'y a pas de rues.** Natural Earth s'arrête aux grands axes. Les rues
demanderaient un extrait OpenStreetMap, une base PostGIS et une chaîne de rendu
Mapnik — plusieurs dizaines de gigaoctets, qui ne tenaient pas sur la machine où
ce fond a été fabriqué. Voir « Aller plus loin ».

**Et pas de zoom au-delà de 8**, non par économie mais par honnêteté : la donnée
1:10 m a une résolution de l'ordre du kilomètre, quand le zoom 9 affiche
300 mètres par pixel. Descendre plus bas ne montrerait rien de neuf, seulement
des traits plus lisses, pour quatre fois plus de fichiers. L'application
agrandit le dernier niveau servi, et le tracé reste net par-dessus.

## Provenance des données

[Natural Earth](https://www.naturalearthdata.com/), échelle **1:10 m**,
**domaine public** — aucune attribution juridiquement exigée, celle affichée par
l'application est une politesse.

Récupérées le 23 août 2026 depuis le dépôt officiel des versions GeoJSON,
`github.com/nvkelso/natural-earth-vector`, branche `master`, dossier
`geojson/` :

    ne_10m_land                             ne_10m_roads
    ne_10m_lakes                            ne_10m_admin_0_boundary_lines_land
    ne_10m_rivers_lake_centerlines          ne_10m_admin_1_states_provinces_lines
    ne_10m_urban_areas                      ne_10m_populated_places

Les fichiers mondiaux pèsent 137 Mo et **ne sont pas versionnés** ; ils vivent
dans `monde/`, que `.gitignore` exclut. `fetch_and_clip.py` les télécharge, les
découpe géométriquement sur l'emprise et jette les propriétés inutilisées :
137 Mo deviennent 20 Mo, et c'est **ce découpage-là qui est versionné**, dans
`data/`. Comme `site/vendor/` pour Leaflet, et pour la même raison : le fond
doit pouvoir être refabriqué depuis le dépôt seul, sans réseau et sans qu'une
URL amont ait bougé.

Le découpage n'est pas qu'une question de poids. Sans lui, le polygone de
l'Eurasie arrive entier dans le rendu, et chaque tuile d'Alsace reparcourt les
côtes chinoises.

**Police** : [Noto Sans](https://fonts.google.com/noto), sous licence SIL Open
Font 1.1, versionnée dans `fonts/` avec sa licence. Prendre une police du
système rendrait la fabrication dépendante de la machine, et celle livrée avec
Pillow ne connaît ni Tromsø, ni Växjö, ni Genève — chacun de ces caractères
sortait en carré.

## Refabriquer les tuiles

    python3 -m venv tools/tiles/.venv
    tools/tiles/.venv/bin/pip install Pillow
    tools/tiles/.venv/bin/python tools/tiles/build_basemap.py --zoom 0-8

Environ 30 secondes, 2 951 tuiles, 15 Mo. Les données découpées suffisent ; il
n'y a rien à télécharger. Pour repartir des sources mondiales — changer
d'emprise, ajouter une couche :

    tools/tiles/.venv/bin/python tools/tiles/fetch_and_clip.py

L'emprise et la plage de zoom se règlent en ligne de commande :

    --bbox -12,34,45,72     lon_min,lat_min,lon_max,lat_max
    --zoom 0-8

L'emprise par défaut déborde largement du corridor du voyage. Au cadrage où
l'on voit le trajet entier, l'écran montre l'Islande et la Méditerranée : une
bande grise sur le bord se remarquerait immédiatement.

## Comment c'est servi

Le dossier `tuiles/` est monté en lecture seule dans le conteneur `site` de
`server/docker-compose.yml`, et servi par
`tools/nginx/site.conf.template` :

    https://madhi.alexeber.fr/tiles/v2/{z}/{x}/{y}.png

**Le numéro de génération dans le chemin n'est pas décoratif.** L'application
lit son cache disque avant le réseau, et ces tuiles sont annoncées immuables
pour un an : un fond refabriqué ne parviendrait jamais aux téléphones qui ont
déjà l'ancien. Toutes les générations servent le même dossier ; bumper le
numéro, ici et dans `local.properties`, est la seule façon de pousser un
nouveau fond. La génération 1 était la donnée 1:50 m, sans routes ni villes.

**Public, sans mot de passe, et volontairement.** Ce sont des données du
domaine public qui ne disent rien du voyage. Les mettre derrière le lien secret
familial obligerait à embarquer ce secret dans l'APK, où il n'a rien à faire.

Contrepartie assumée : ce domaine répondait 404 partout, il sert maintenant
quelque chose. Un visiteur de passage apprend qu'il y a une carte, rien de plus.

Rien à changer sur le nginx de l'hôte, que certbot réécrit — c'est la raison
d'avoir choisi le conteneur `site` plutôt qu'un `location` sur l'hôte.

## Côté application

Trois lignes dans `local.properties`, jamais versionnées :

    madhi.tiles.urlTemplate=https://madhi.alexeber.fr/tiles/v2/{z}/{x}/{y}.png
    madhi.tiles.attribution=Fond : Natural Earth
    madhi.tiles.maxZoom=8

`maxZoom` doit valoir le dernier niveau réellement fabriqué. Plus haut,
l'application demanderait des tuiles inexistantes et perdrait son fond en
zoomant ; plus bas, elle agrandirait sans raison.

Sans elles, la carte reste sur fond uni et n'émet aucune requête. C'est le
comportement par défaut du dépôt : aucun serveur de tuiles n'y est figé.

Le cache disque de l'application est interrogé **avant** le réseau. Une zone
consultée une fois reste lisible hors ligne, ce qui est le cas normal du
voyage. C'est aussi pourquoi nginx annonce ces tuiles `immutable` pour un an.

## Aller plus loin : le détail OpenStreetMap

Le jour où les rues deviennent nécessaires, la marche à suivre :

1. Extraits Geofabrik du corridor — France, Benelux, Allemagne, Danemark,
   Norvège, Suède, Finlande. Compter une douzaine de gigaoctets de `.osm.pbf`.
2. Import `osm2pgsql` dans PostGIS, puis rendu Mapnik avec le style
   `openstreetmap-carto`. Compter plusieurs centaines de gigaoctets et des
   heures de rendu ; ni le VPS ni le portable ne l'encaissaient en août 2026 —
   le portable n'avait que 6,4 Go de libre.
3. Ne rendre que le corridor, aux zooms 9 à 14, et poser le résultat à côté des
   tuiles actuelles. Rien à changer dans l'application : il suffira de monter
   `madhi.tiles.maxZoom`.

Le nombre de tuiles quadruple à chaque niveau : compter environ 12 000 tuiles au
zoom 9 pour cette emprise, 48 000 au zoom 10. À ce régime, un dossier de PNG
versionné n'est plus le bon support et il faudra passer à une archive.

Le point important : **l'auto-hébergement est le seul montage qui autorise à
pré-charger en masse pour l'hors-ligne.** Les offres gratuites des fournisseurs
de tuiles l'interdisent presque toutes, et c'est exactement ce dont un voyage
sans réseau a besoin.
