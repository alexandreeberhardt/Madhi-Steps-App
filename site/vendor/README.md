# Leaflet, versionné

Leaflet **1.9.4**, copié depuis `https://unpkg.com/leaflet@1.9.4/dist/`.

Versionné plutôt que chargé depuis un CDN, pour trois raisons : le site doit
fonctionner sans dépendre d'un tiers qui peut disparaître ou changer, l'onglet
réseau ne doit montrer que le domaine du projet et le serveur de tuiles, et la
`Content-Security-Policy` n'autorise aucun script hors du domaine.

Les fichiers ont été recoupés octet à octet avec cdnjs et jsDelivr : les trois
origines donnent les mêmes empreintes, ce qui écarte une altération sur un seul
chemin de distribution.

## Empreintes SHA-256

Recalculer après toute mise à jour, et comparer avec au moins deux origines
indépendantes :

    shasum -a 256 site/vendor/leaflet.js site/vendor/leaflet.css site/vendor/images/*

    db49d009c841f5ca34a888c96511ae936fd9f5533e90d8b2c4d57596f4e5641a      leaflet.js
    a7837102824184820dfa198d1ebcd109ff6d0ff9a2672a074b9a1b4d147d04c6      leaflet.css
    066daca850d8ffbef007af00b06eac0015728dee279c51f3cb6c716df7c42edf      images/layers-2x.png
    1dbbe9d028e292f36fcba8f8b3a28d5e8932754fc2215b9ac69e4cdecf5107c6      images/layers.png
    00179c4c1ee830d3a108412ae0d294f55776cfeb085c60129a39aa6fc4ae2528      images/marker-icon-2x.png
    574c3a5cca85f4114085b6841596d62f00d7c892c7b03f28cbfa301deb1dc437      images/marker-icon.png
    264f5c640339f042dd729062cfc04c17f8ea0f29882b538e3848ed8f10edb4da      images/marker-shadow.png

## Mettre à jour

Leaflet n'a pas besoin d'être mis à jour pour lui-même. Ne le faire que pour
une faille annoncée, et jamais pendant le voyage sans pouvoir vérifier le
résultat dans un navigateur : c'est la seule dépendance du site.

Les images de `images/` sont référencées par `leaflet.css` en relatif, et par
`components/map.js` via `L.Icon.Default.imagePath`. Les trois doivent rester
cohérentes.
