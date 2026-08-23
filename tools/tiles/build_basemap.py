"""Fabrique un fond de carte raster auto-hébergé, à partir de données publiques.

Pourquoi ce script plutôt qu'une pile Mapnik/PostGIS : le projet doit rester
réparable par une seule personne pendant un an. Deux cents lignes de Python
lisibles et un dossier de PNG se réparent depuis un cybercafé norvégien ; une
base PostGIS de 300 Go et une chaîne de rendu, non.

Ce que ça produit : un fond géographique — mer, terres, lacs, fleuves,
frontières, zones urbaines — du zoom 0 au zoom 8. C'est l'échelle à laquelle on
lit un voyage de 3 000 km. Il n'y a pas de rues : Natural Earth n'en contient
pas, et les servir demanderait la pile lourde. L'application agrandit le
dernier niveau disponible au-delà, et le tracé reste net par-dessus.

    python3 build_basemap.py --out tuiles --zoom 0-8
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys
import time

from PIL import Image, ImageDraw

# Emprise generee. Plus large que le corridor du voyage : au cadrage ou l on voit
# le trajet entier, l ecran deborde largement de la route elle-meme, et une bande
# grise sur le bord se remarque immediatement.
DEFAULT_BBOX = (-12.0, 34.0, 45.0, 72.0)

TILE_SIZE = 256

# Rendu à deux fois la taille finale puis réduit : ImageDraw ne sait pas
# lisser, et une côte en marches d'escalier saute aux yeux.
SUPERSAMPLE = 2

# Palette claire et peu saturée, choisie pour que le tracé bleu et orange de
# l'application reste le seul élément vif de l'écran.
COLOR_OCEAN = (201, 220, 232)
COLOR_LAND = (242, 239, 233)
COLOR_COAST = (176, 195, 207)
COLOR_URBAN = (232, 226, 216)
COLOR_LAKE = (201, 220, 232)
COLOR_RIVER = (168, 197, 216)
COLOR_BOUNDARY = (198, 188, 176)

LAYERS = (
    # fichier, type de géométrie, remplissage, contour, épaisseur
    ("ne_50m_land", "fill", COLOR_LAND, COLOR_COAST, 1.0),
    ("ne_50m_urban_areas", "fill", COLOR_URBAN, None, 0.0),
    ("ne_50m_lakes", "fill", COLOR_LAKE, None, 0.0),
    ("ne_50m_rivers_lake_centerlines", "line", None, COLOR_RIVER, 1.0),
    ("ne_50m_admin_0_boundary_lines_land", "line", None, COLOR_BOUNDARY, 1.0),
)

MAX_LATITUDE = 85.05112878


def normalized(lon: float, lat: float) -> tuple[float, float]:
    """Projette en Web Mercator, ramené sur le carré unité.

    Exactement la projection de `domain/MapProjection.kt`. Les deux doivent
    coïncider au pixel près, sinon le tracé flotterait à côté du fond.
    """
    clamped = max(-MAX_LATITUDE, min(MAX_LATITUDE, lat))
    radians = math.radians(clamped)
    x = (lon + 180.0) / 360.0
    y = (1.0 - math.log(math.tan(radians) + 1.0 / math.cos(radians)) / math.pi) / 2.0
    return x, y


class Shape:
    """Une géométrie déjà projetée, avec sa boîte englobante pour l'élagage."""

    __slots__ = ("rings", "min_x", "min_y", "max_x", "max_y")

    def __init__(self, rings: list[list[tuple[float, float]]]):
        self.rings = rings
        flat = [point for ring in rings for point in ring]
        self.min_x = min(p[0] for p in flat)
        self.max_x = max(p[0] for p in flat)
        self.min_y = min(p[1] for p in flat)
        self.max_y = max(p[1] for p in flat)

    def intersects(self, min_x, min_y, max_x, max_y) -> bool:
        return not (
            self.max_x < min_x
            or self.min_x > max_x
            or self.max_y < min_y
            or self.min_y > max_y
        )


def rings_of(geometry: dict) -> list[list[tuple[float, float]]]:
    """Aplatit une géométrie GeoJSON en une liste d'anneaux projetés."""
    kind = geometry["type"]
    coordinates = geometry["coordinates"]

    if kind == "Polygon":
        parts = coordinates
    elif kind == "MultiPolygon":
        parts = [ring for polygon in coordinates for ring in polygon]
    elif kind == "LineString":
        parts = [coordinates]
    elif kind == "MultiLineString":
        parts = coordinates
    else:
        return []

    return [[normalized(point[0], point[1]) for point in ring] for ring in parts if len(ring) >= 2]


def load_layer(data_directory: pathlib.Path, name: str) -> list[Shape]:
    path = data_directory / f"{name}.geojson"
    if not path.exists():
        sys.exit(f"Donnée absente : {path}. Voir tools/tiles/README.md.")

    with path.open(encoding="utf-8") as handle:
        collection = json.load(handle)

    shapes = []
    for feature in collection["features"]:
        geometry = feature.get("geometry")
        if not geometry:
            continue
        rings = rings_of(geometry)
        if rings:
            shapes.append(Shape(rings))
    return shapes


def tile_range(zoom: int, bbox: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
    lon_min, lat_min, lon_max, lat_max = bbox
    count = 2**zoom
    left, top = normalized(lon_min, lat_max)
    right, bottom = normalized(lon_max, lat_min)
    return (
        max(0, int(left * count)),
        max(0, int(top * count)),
        min(count - 1, int(right * count)),
        min(count - 1, int(bottom * count)),
    )


def render_tile(zoom: int, x: int, y: int, layers: list[tuple], size: int) -> Image.Image:
    count = 2**zoom
    tile_min_x, tile_min_y = x / count, y / count
    tile_max_x, tile_max_y = (x + 1) / count, (y + 1) / count
    scale = size * count

    # Une marge d'élagage : un trait dont le sommet est juste hors tuile a
    # quand même une épaisseur qui déborde à l'intérieur.
    margin = 2.0 / scale

    image = Image.new("RGB", (size, size), COLOR_OCEAN)
    draw = ImageDraw.Draw(image)

    def to_pixels(ring):
        return [((px - tile_min_x) * scale, (py - tile_min_y) * scale) for px, py in ring]

    for shapes, kind, fill, outline, width in layers:
        stroke = max(1, round(width * SUPERSAMPLE)) if outline else 0
        for shape in shapes:
            if not shape.intersects(
                tile_min_x - margin, tile_min_y - margin, tile_max_x + margin, tile_max_y + margin
            ):
                continue
            for ring in shape.rings:
                points = to_pixels(ring)
                if kind == "fill":
                    draw.polygon(points, fill=fill, outline=outline, width=stroke)
                else:
                    draw.line(points, fill=outline, width=stroke, joint="curve")

    return image


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="tuiles", help="dossier de sortie")
    parser.add_argument("--zoom", default="0-8", help="plage de zoom, par exemple 0-8")
    parser.add_argument("--data", default="data", help="dossier des GeoJSON Natural Earth")
    parser.add_argument(
        "--bbox",
        default=",".join(str(v) for v in DEFAULT_BBOX),
        help="lon_min,lat_min,lon_max,lat_max",
    )
    arguments = parser.parse_args()

    here = pathlib.Path(__file__).parent
    data_directory = (here / arguments.data).resolve()
    output = (here / arguments.out).resolve()
    bbox = tuple(float(v) for v in arguments.bbox.split(","))
    first_zoom, last_zoom = (int(v) for v in arguments.zoom.split("-"))

    print("Chargement des données…", flush=True)
    layers = [
        (load_layer(data_directory, name), kind, fill, outline, width)
        for name, kind, fill, outline, width in LAYERS
    ]

    started = time.monotonic()
    written = 0
    for zoom in range(first_zoom, last_zoom + 1):
        min_x, min_y, max_x, max_y = tile_range(zoom, bbox)
        expected = (max_x - min_x + 1) * (max_y - min_y + 1)
        print(f"zoom {zoom} : {expected} tuiles", flush=True)

        for x in range(min_x, max_x + 1):
            directory = output / str(zoom) / str(x)
            directory.mkdir(parents=True, exist_ok=True)
            for y in range(min_y, max_y + 1):
                tile = render_tile(zoom, x, y, layers, TILE_SIZE * SUPERSAMPLE)
                tile = tile.resize((TILE_SIZE, TILE_SIZE), Image.LANCZOS)
                # Palette de 64 couleurs : le fond n'en utilise qu'une
                # poignée, et le PNG passe d'environ 20 Ko à 4 Ko. Sur dix
                # mille tuiles, c'est la différence entre 200 Mo et 40 Mo.
                tile.convert("P", palette=Image.ADAPTIVE, colors=64).save(
                    directory / f"{y}.png", optimize=True
                )
                written += 1

    elapsed = time.monotonic() - started
    total_bytes = sum(p.stat().st_size for p in output.rglob("*.png"))
    print(
        f"{written} tuiles en {elapsed:.0f} s, {total_bytes / 1_048_576:.1f} Mo "
        f"dans {output}"
    )


if __name__ == "__main__":
    main()
