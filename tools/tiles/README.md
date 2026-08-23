# Fond de carte auto-hébergé

Fabrique les tuiles raster que l'application affiche sous le tracé, et que le
serveur sert lui-même. Aucun compte, aucune clé d'API, aucun quota, aucun tiers
dont dépendre pendant un an de voyage.

## Ce que ça produit, et ce que ça ne produit pas

Un fond géographique : mer, terres, côtes, lacs, fleuves, frontières, zones
urbaines. Du zoom 0 au zoom 8, sur l'Europe de l'Ouest et du Nord.

**Il n'y a pas de rues.** Natural Earth n'en contient pas. Les servir
demanderait un extrait OpenStreetMap, une base PostGIS et une chaîne de rendu
Mapnik — plusieurs dizaines de gigaoctets et plusieurs heures, qui ne tenaient
pas sur la machine où ce fond a été fabriqué. Voir « Aller plus loin ».

Ce n'est pas un pis-aller pour autant : le zoom 8 est l'échelle à laquelle on
lit un voyage de 3 000 km, et c'est celle où le tracé a besoin d'un contexte.
Au-delà, l'application agrandit le dernier niveau disponible — le fond devient
une teinte unie, ce qui ne gêne pas, et le tracé reste net par-dessus.

## Provenance des données

[Natural Earth](https://www.naturalearthdata.com/), échelle 1:50 m, **domaine
public** — aucune attribution juridiquement exigée, celle affichée par
l'application est une politesse.

Récupérées le 23 août 2026 depuis le dépôt officiel des versions GeoJSON,
`github.com/nvkelso/natural-earth-vector`, branche `master`, dossier
`geojson/` :

    ne_50m_land
    ne_50m_lakes
    ne_50m_rivers_lake_centerlines
    ne_50m_admin_0_boundary_lines_land
    ne_50m_urban_areas

Les fichiers sont **versionnés dans `data/`**, comme `site/vendor/` l'est pour
Leaflet et pour la même raison : le fond doit pouvoir être refabriqué depuis le
dépôt seul, sans réseau et sans qu'une URL amont ait bougé.

## Refabriquer les tuiles

    python3 -m venv tools/tiles/.venv
    tools/tiles/.venv/bin/pip install Pillow
    tools/tiles/.venv/bin/python tools/tiles/build_basemap.py --zoom 0-8

Environ 30 secondes, 2 951 tuiles, 4,4 Mo. L'emprise et la plage de zoom se
règlent en ligne de commande :

    --bbox -12,34,45,72     lon_min,lat_min,lon_max,lat_max
    --zoom 0-8

L'emprise par défaut déborde largement du corridor du voyage. Au cadrage où
l'on voit le trajet entier, l'écran montre l'Islande et la Méditerranée : une
bande grise sur le bord se remarquerait immédiatement.

## Comment c'est servi

Le dossier `tuiles/` est monté en lecture seule dans le conteneur `site` de
`server/docker-compose.yml`, et servi par
`tools/nginx/site.conf.template` :

    https://madhi.alexeber.fr/tiles/{z}/{x}/{y}.png

**Public, sans mot de passe, et volontairement.** Ce sont des données du
domaine public qui ne disent rien du voyage. Les mettre derrière le lien secret
familial obligerait à embarquer ce secret dans l'APK, où il n'a rien à faire.

Contrepartie assumée : ce domaine répondait 404 partout, il sert maintenant
quelque chose. Un visiteur de passage apprend qu'il y a une carte, rien de plus.

Rien à changer sur le nginx de l'hôte, que certbot réécrit — c'est la raison
d'avoir choisi le conteneur `site` plutôt qu'un `location` sur l'hôte.

## Côté application

Trois lignes dans `local.properties`, jamais versionnées :

    madhi.tiles.urlTemplate=https://madhi.alexeber.fr/tiles/{z}/{x}/{y}.png
    madhi.tiles.attribution=Fond : Natural Earth
    madhi.tiles.maxZoom=8

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
   heures de rendu ; ni le VPS ni le portable ne l'encaissaient en août 2026.
3. Ne rendre que le corridor, aux zooms 9 à 14, et poser le résultat à côté des
   tuiles actuelles. Rien à changer dans l'application : il suffira de monter
   `madhi.tiles.maxZoom`.

Le point important : **l'auto-hébergement est le seul montage qui autorise à
pré-charger en masse pour l'hors-ligne.** Les offres gratuites des fournisseurs
de tuiles l'interdisent presque toutes, et c'est exactement ce dont un voyage
sans réseau a besoin.
