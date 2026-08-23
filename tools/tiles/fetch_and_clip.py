"""Découpe les données Natural Earth mondiales au corridor du voyage.

Les fichiers mondiaux pèsent 137 Mo, dont l'essentiel décrit des continents que
ce voyage ne traverse pas. Ce script n'en garde que ce qui touche l'emprise, et
que les propriétés réellement utilisées par le rendu. Le résultat, lui, est
versionné : le fond doit pouvoir être refabriqué depuis le dépôt seul.

    python3 fetch_and_clip.py            # télécharge puis découpe
    python3 fetch_and_clip.py --hors-ligne   # découpe ce qui est déjà là
"""

from __future__ import annotations

import argparse
import json
import pathlib
import urllib.request

SOURCE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson"

DEFAULT_BBOX = (-12.0, 34.0, 45.0, 72.0)

# Couche -> propriétés conservées. Tout le reste est jeté : les noms en trente
# langues des tables Natural Earth pèsent plus lourd que les géométries.
LAYERS = {
    "ne_10m_land": (),
    "ne_10m_lakes": ("min_zoom",),
    "ne_10m_rivers_lake_centerlines": ("min_zoom",),
    "ne_10m_urban_areas": (),
    "ne_10m_roads": ("min_zoom", "type"),
    "ne_10m_admin_0_boundary_lines_land": ("min_zoom",),
    "ne_10m_admin_1_states_provinces_lines": ("min_zoom",),
    "ne_10m_populated_places": ("name", "min_zoom", "labelrank", "pop_max"),
}


def bounds_of(coordinates) -> tuple[float, float, float, float]:
    """Boîte englobante d'une géométrie GeoJSON, quelle que soit son imbrication."""
    if isinstance(coordinates[0], (int, float)):
        return coordinates[0], coordinates[1], coordinates[0], coordinates[1]

    boxes = [bounds_of(part) for part in coordinates]
    return (
        min(b[0] for b in boxes),
        min(b[1] for b in boxes),
        max(b[2] for b in boxes),
        max(b[3] for b in boxes),
    )


def clip_ring(ring, box):
    """Découpe un anneau sur la boîte, par l'algorithme de Sutherland-Hodgman.

    Sans cette découpe, le polygone de l'Eurasie arrive entier dans le rendu :
    neuf mégaoctets de côtes chinoises reparcourues pour dessiner l'Alsace.
    """
    lon_min, lat_min, lon_max, lat_max = box
    edges = (
        (0, lon_min, True),   # garder x >= lon_min
        (0, lon_max, False),  # garder x <= lon_max
        (1, lat_min, True),
        (1, lat_max, False),
    )

    output = ring
    for axis, limit, keep_greater in edges:
        if not output:
            return []

        def inside(point):
            return point[axis] >= limit if keep_greater else point[axis] <= limit

        clipped = []
        previous = output[-1]
        for current in output:
            if inside(current):
                if not inside(previous):
                    clipped.append(crossing(previous, current, axis, limit))
                clipped.append(current)
            elif inside(previous):
                clipped.append(crossing(previous, current, axis, limit))
            previous = current
        output = clipped
    return output


def crossing(start, end, axis, limit):
    """Point où le segment traverse la droite `axis = limit`."""
    other = 1 - axis
    span = end[axis] - start[axis]
    ratio = 0.0 if span == 0 else (limit - start[axis]) / span
    crossed = [0.0, 0.0]
    crossed[axis] = limit
    crossed[other] = start[other] + (end[other] - start[other]) * ratio
    return (crossed[0], crossed[1])


def clip_line(points, box):
    """Garde les portions de ligne qui touchent la boîte.

    Une ligne n'est pas refermable : on la coupe en morceaux plutôt que de la
    rogner, en gardant un point de part et d'autre pour que le trait entre et
    sorte du cadre au lieu de s'arrêter net dessus.
    """
    lon_min, lat_min, lon_max, lat_max = box
    inside = [lon_min <= x <= lon_max and lat_min <= y <= lat_max for x, y in points]

    runs = []
    current = []
    for index, point in enumerate(points):
        neighbour_inside = (
            inside[index]
            or (index > 0 and inside[index - 1])
            or (index + 1 < len(points) and inside[index + 1])
        )
        if neighbour_inside:
            current.append(point)
        elif current:
            runs.append(current)
            current = []
    if current:
        runs.append(current)
    return [run for run in runs if len(run) >= 2]


def clip_geometry(geometry, box):
    """Renvoie une géométrie réduite à la boîte, ou `None` si rien n'en reste."""
    kind = geometry["type"]
    coordinates = geometry["coordinates"]

    if kind in ("Polygon", "MultiPolygon"):
        rings = coordinates if kind == "Polygon" else [r for poly in coordinates for r in poly]
        kept = [clip_ring([tuple(p[:2]) for p in ring], box) for ring in rings]
        kept = [ring for ring in kept if len(ring) >= 3]
        return {"type": "MultiPolygon", "coordinates": [[ring] for ring in kept]} if kept else None

    if kind in ("LineString", "MultiLineString"):
        lines = [coordinates] if kind == "LineString" else coordinates
        kept = [run for line in lines for run in clip_line([tuple(p[:2]) for p in line], box)]
        return {"type": "MultiLineString", "coordinates": kept} if kept else None

    return geometry


def slim(properties: dict, kept: tuple[str, ...]) -> dict:
    """Ne garde que les propriétés utiles, sous un nom en minuscules.

    Natural Earth écrit `min_zoom` dans certaines couches et `MIN_ZOOM` dans
    d'autres. Normaliser ici évite de traîner cette incohérence jusqu'au rendu.
    """
    lowered = {key.lower(): value for key, value in properties.items()}
    return {name: lowered[name] for name in kept if lowered.get(name) is not None}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bbox", default=",".join(str(v) for v in DEFAULT_BBOX))
    parser.add_argument(
        "--hors-ligne",
        action="store_true",
        help="ne rien télécharger, réutiliser monde/",
    )
    arguments = parser.parse_args()

    here = pathlib.Path(__file__).parent
    world = here / "monde"
    output = here / "data"
    world.mkdir(exist_ok=True)
    output.mkdir(exist_ok=True)

    lon_min, lat_min, lon_max, lat_max = (float(v) for v in arguments.bbox.split(","))

    for name, kept in LAYERS.items():
        source = world / f"{name}.geojson"
        if not source.exists():
            if arguments.hors_ligne:
                raise SystemExit(f"{source} absent et mode hors ligne demandé.")
            print(f"téléchargement {name}…", flush=True)
            urllib.request.urlretrieve(f"{SOURCE}/{name}.geojson", source)

        with source.open(encoding="utf-8") as handle:
            collection = json.load(handle)

        kept_features = []
        for feature in collection["features"]:
            geometry = feature.get("geometry")
            if not geometry or not geometry.get("coordinates"):
                continue
            west, south, east, north = bounds_of(geometry["coordinates"])
            if east < lon_min or west > lon_max or north < lat_min or south > lat_max:
                continue
            trimmed = clip_geometry(geometry, (lon_min, lat_min, lon_max, lat_max))
            if trimmed is None:
                continue
            kept_features.append(
                {
                    "type": "Feature",
                    "properties": slim(feature.get("properties") or {}, kept),
                    "geometry": trimmed,
                }
            )

        destination = output / f"{name}.geojson"
        with destination.open("w", encoding="utf-8") as handle:
            json.dump(
                {"type": "FeatureCollection", "features": kept_features},
                handle,
                separators=(",", ":"),
                ensure_ascii=False,
            )
        before = source.stat().st_size / 1_048_576
        after = destination.stat().st_size / 1_048_576
        print(
            f"{name:<42} {len(collection['features']):>6} → {len(kept_features):>6} objets"
            f"   {before:>6.1f} → {after:>5.1f} Mo"
        )


if __name__ == "__main__":
    main()
