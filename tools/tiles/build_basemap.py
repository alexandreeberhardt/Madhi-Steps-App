"""Fabrique un fond de carte raster auto-hébergé, à partir de données publiques.

Pourquoi ce script plutôt qu'une pile Mapnik/PostGIS : le projet doit rester
réparable par une seule personne pendant un an. Trois cents lignes de Python
lisibles et un dossier de PNG se réparent depuis un cybercafé norvégien ; une
base PostGIS de 300 Go et une chaîne de rendu, non.

Ce que ça produit : mer, terres, lacs, fleuves, frontières, limites régionales,
zones urbaines, grandes routes et noms de villes, du zoom 0 au zoom 8. Il n'y a
pas de rues : Natural Earth n'en contient pas, et les servir demanderait la
pile lourde. L'application agrandit le dernier niveau disponible au-delà.

    python3 build_basemap.py --out tuiles --zoom 0-8
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys
import time

from PIL import Image, ImageDraw, ImageFont

from fetch_and_clip import clip_line, clip_ring

DEFAULT_BBOX = (-12.0, 34.0, 45.0, 72.0)

TILE_SIZE = 256

# Rendu à deux fois la taille finale puis réduit : ImageDraw ne sait pas
# lisser, et une côte en marches d'escalier saute aux yeux.
SUPERSAMPLE = 2

# Marge de géométrie rendue autour de la tuile puis coupée : un trait épais
# dont le sommet est juste dehors doit quand même mordre à l'intérieur.
BUFFER = 8

# Les noms, eux, sont écrits après la découpe, directement sur la tuile finale,
# avec des coordonnées qui peuvent tomber hors du cadre — Pillow les rogne. Un
# nom dont la ville est dans la tuile voisine entre donc dans celle-ci, et les
# deux tuiles écrivent exactement le même pixel de part et d'autre de la
# limite. Cette marge dit jusqu'où aller chercher ces villes-là.
LABEL_REACH = 180

MAX_LATITUDE = 85.05112878

# Palette claire et peu saturée, choisie pour que le tracé bleu et orange de
# l'application reste le seul élément vif de l'écran.
OCEAN = (201, 220, 232)
LAND = (243, 240, 234)
COAST = (168, 188, 201)
URBAN = (232, 226, 214)
LAKE = (198, 218, 231)
RIVER = (163, 194, 214)
BOUNDARY = (176, 164, 148)
REGION = (214, 205, 191)
MOTORWAY = (223, 176, 110)
PRIMARY = (231, 202, 152)
MINOR = (222, 214, 200)
PLACE_DOT = (110, 99, 86)
PLACE_TEXT = (66, 59, 48)
PLACE_HALO = (247, 245, 241)

MAJOR_ROADS = ("Major Highway", "Beltway")

# Police versionnée dans fonts/ plutôt que prise au système : les noms du
# voyage s'écrivent Tromsø, Växjö, Genève, et la police livrée avec Pillow
# rendait chacun de ces caractères par un carré.
FONT_PATH = pathlib.Path(__file__).parent / "fonts" / "NotoSans-Regular.ttf"
LABEL_SIZE = 11


def normalized(lon: float, lat: float) -> tuple[float, float]:
    """Projette en Web Mercator, ramené sur le carré unité.

    Exactement la projection de `domain/MapProjection.kt`. Les deux doivent
    coïncider au pixel près, sinon le tracé flotterait à côté du fond.
    """
    clamped = max(-MAX_LATITUDE, min(MAX_LATITUDE, lat))
    radians = math.radians(clamped)
    return (
        (lon + 180.0) / 360.0,
        (1.0 - math.log(math.tan(radians) + 1.0 / math.cos(radians)) / math.pi) / 2.0,
    )


class Shape:
    """Une géométrie projetée, avec ce dont le style a besoin."""

    __slots__ = ("parts", "min_zoom", "kind", "label", "rank", "population", "key")

    def __init__(self, parts, min_zoom=0.0, kind=None, label=None, rank=0, population=0, key=0):
        self.parts = parts
        self.min_zoom = min_zoom
        self.kind = kind
        self.label = label
        self.rank = rank
        self.population = population
        self.key = key


def parts_of(geometry) -> list[list[tuple[float, float]]]:
    kind = geometry["type"]
    coordinates = geometry["coordinates"]
    if kind == "Polygon":
        rings = coordinates
    elif kind == "MultiPolygon":
        rings = [ring for polygon in coordinates for ring in polygon]
    elif kind == "LineString":
        rings = [coordinates]
    elif kind == "MultiLineString":
        rings = coordinates
    elif kind == "Point":
        rings = [[coordinates]]
    else:
        return []
    return [[normalized(p[0], p[1]) for p in ring] for ring in rings if ring]


def load(directory: pathlib.Path, name: str) -> list[Shape]:
    path = directory / f"{name}.geojson"
    if not path.exists():
        sys.exit(f"Donnée absente : {path}. Lancer fetch_and_clip.py d'abord.")

    with path.open(encoding="utf-8") as handle:
        collection = json.load(handle)

    shapes = []
    for index, feature in enumerate(collection["features"]):
        parts = parts_of(feature["geometry"])
        if not parts:
            continue
        properties = feature.get("properties") or {}
        shapes.append(
            Shape(
                parts=parts,
                min_zoom=float(properties.get("min_zoom") or 0.0),
                kind=properties.get("type"),
                label=properties.get("name"),
                rank=int(properties.get("labelrank") or 0),
                population=int(properties.get("pop_max") or 0),
                key=index,
            )
        )
    return shapes


def clip_shapes(shapes: list[Shape], box, closed: bool) -> list[Shape]:
    """Réduit un jeu de formes à la boîte, en gardant leurs attributs.

    Appelé à chaque niveau de la descente : chaque tuile ne travaille donc que
    sur ce que son parent lui a déjà réduit, et le coût total suit la
    profondeur au lieu du nombre de tuiles.
    """
    kept = []
    for shape in shapes:
        if closed:
            parts = [clip_ring(part, box) for part in shape.parts]
            parts = [part for part in parts if len(part) >= 3]
        else:
            parts = [run for part in shape.parts for run in clip_line(part, box)]
        if parts:
            kept.append(
                Shape(
                    parts, shape.min_zoom, shape.kind, shape.label,
                    shape.rank, shape.population, shape.key,
                )
            )
    return kept


def clip_points(shapes: list[Shape], box) -> list[Shape]:
    lon_min, lat_min, lon_max, lat_max = box
    return [
        shape
        for shape in shapes
        if lon_min <= shape.parts[0][0][0] <= lon_max
        and lat_min <= shape.parts[0][0][1] <= lat_max
    ]


def choose_labels(places, zoom, font) -> set[int]:
    """Décide, pour tout le niveau de zoom d'un coup, quels noms sont écrits.

    La décision doit être prise ici et pas dans le rendu d'une tuile : deux
    tuiles voisines qui trancheraient chacune de leur côté écriraient un nom
    d'un côté de la limite et pas de l'autre, coupant le mot en deux.

    Choix glouton : les villes les plus importantes d'abord, et on refuse celle
    dont le nom mordrait sur un nom déjà posé.
    """
    world = TILE_SIZE * 2**zoom
    candidates = sorted(
        (place for place in places if place.min_zoom <= zoom),
        key=lambda place: (place.rank, -place.population),
    )

    accepted: set[int] = set()
    boxes: list[tuple[float, float, float, float]] = []
    for place in candidates:
        if not place.label:
            continue
        x, y = place.parts[0][0]
        left = x * world + LABEL_SIZE * 0.5
        top = y * world - LABEL_SIZE
        width = font.getlength(place.label)
        box = (left - 2, top - 2, left + width + 2, top + LABEL_SIZE + 2)

        if any(
            box[0] < other[2] and other[0] < box[2] and box[1] < other[3] and other[1] < box[3]
            for other in boxes
        ):
            continue
        boxes.append(box)
        accepted.add(place.key)
    return accepted


def road_style(kind: str | None, zoom: int) -> tuple[tuple[int, int, int], float] | None:
    if kind in MAJOR_ROADS:
        return MOTORWAY, 2.2
    if kind == "Secondary Highway":
        return (PRIMARY, 1.4) if zoom >= 7 else None
    return (MINOR, 1.0) if zoom >= 8 else None


def render_tile(zoom, x, y, layers, font, labelled: set[int]) -> Image.Image:
    count = 2**zoom
    scale = TILE_SIZE * SUPERSAMPLE * count
    margin = BUFFER * SUPERSAMPLE
    edge = TILE_SIZE * SUPERSAMPLE + 2 * margin
    origin_x = x / count - margin / scale
    origin_y = y / count - margin / scale

    image = Image.new("RGB", (edge, edge), OCEAN)
    draw = ImageDraw.Draw(image)

    def pixels(part):
        return [((px - origin_x) * scale, (py - origin_y) * scale) for px, py in part]

    def fill(shapes, colour, outline=None, width=1):
        for shape in shapes:
            for part in shape.parts:
                draw.polygon(pixels(part), fill=colour, outline=outline, width=width)

    def stroke(shapes, colour, width):
        thickness = max(1, round(width * SUPERSAMPLE))
        for shape in shapes:
            for part in shape.parts:
                draw.line(pixels(part), fill=colour, width=thickness, joint="curve")

    visible = lambda shapes, offset=0.0: [s for s in shapes if s.min_zoom <= zoom + offset]

    fill(layers["land"], LAND, outline=COAST, width=SUPERSAMPLE)
    if zoom >= 6:
        fill(layers["urban"], URBAN)
    fill(visible(layers["lakes"], 1.0), LAKE)
    stroke(visible(layers["rivers"], 1.0), RIVER, 0.9)

    if zoom >= 7:
        stroke(visible(layers["regions"], 1.0), REGION, 0.8)
    stroke(visible(layers["borders"], 2.0), BOUNDARY, 1.1)

    if zoom >= 5:
        for shape in visible(layers["roads"], 1.0):
            style = road_style(shape.kind, zoom)
            if style is None:
                continue
            colour, width = style
            for part in shape.parts:
                draw.line(pixels(part), fill=colour, width=max(1, round(width * SUPERSAMPLE)))

    cropped = image.crop((margin, margin, edge - margin, edge - margin))
    tile = cropped.resize((TILE_SIZE, TILE_SIZE), Image.LANCZOS)

    if zoom >= 5:
        draw_places(tile, zoom, x, y, layers["places"], font, labelled)
    return tile


def draw_places(tile, zoom, x, y, places, font, labelled: set[int]) -> None:
    """Écrit les villes sur la tuile finale, en taille réelle.

    Deux raisons de ne pas les dessiner avec le reste. Le texte rendu à sa
    taille définitive est plus net que le même texte agrandi puis réduit. Et
    surtout, les coordonnées peuvent sortir du cadre sans précaution : une
    ville de la tuile d'à côté voit son nom déborder ici, Pillow le rogne, et
    les deux tuiles peignent le même pixel de chaque côté de la limite.
    """
    world = TILE_SIZE * 2**zoom
    draw = ImageDraw.Draw(tile)

    for place in sorted(places, key=lambda place: place.rank):
        if place.min_zoom > zoom:
            continue
        px, py = place.parts[0][0]
        left = px * world - x * TILE_SIZE
        top = py * world - y * TILE_SIZE

        draw.ellipse([left - 2, top - 2, left + 2, top + 2], fill=PLACE_DOT)
        if place.label and place.key in labelled:
            draw.text(
                (left + LABEL_SIZE * 0.5, top - LABEL_SIZE),
                place.label,
                font=font,
                fill=PLACE_TEXT,
                stroke_width=2,
                stroke_fill=PLACE_HALO,
            )


def walk(zoom, x, y, layers, target, first_zoom, last_zoom, font, labelled, output, counter):
    """Descend le quadtree en réduisant les données à chaque étage."""
    count = 2**zoom
    world = TILE_SIZE * count
    margin = (BUFFER + 4) / world
    box = (
        x / count - margin,
        y / count - margin,
        (x + 1) / count + margin,
        (y + 1) / count + margin,
    )
    # Les villes se cherchent bien plus loin que la géométrie : leur nom entre
    # dans la tuile alors qu'elles-mêmes n'y sont pas.
    reach = LABEL_REACH / world
    places_box = (
        x / count - reach,
        y / count - reach,
        (x + 1) / count + reach,
        (y + 1) / count + reach,
    )

    reduced = {
        "land": clip_shapes(layers["land"], box, closed=True),
        "urban": clip_shapes(layers["urban"], box, closed=True),
        "lakes": clip_shapes(layers["lakes"], box, closed=True),
        "rivers": clip_shapes(layers["rivers"], box, closed=False),
        "borders": clip_shapes(layers["borders"], box, closed=False),
        "regions": clip_shapes(layers["regions"], box, closed=False),
        "roads": clip_shapes(layers["roads"], box, closed=False),
        "places": clip_points(layers["places"], places_box),
    }

    if zoom >= first_zoom:
        directory = output / str(zoom) / str(x)
        directory.mkdir(parents=True, exist_ok=True)
        tile = render_tile(zoom, x, y, reduced, font, labelled[zoom])
        # Palette de 128 couleurs : le fond n'en utilise qu'une poignée, et le
        # PNG y perd les trois quarts de son poids.
        tile.convert("P", palette=Image.ADAPTIVE, colors=128).save(
            directory / f"{y}.png", optimize=True
        )
        counter[0] += 1
        if counter[0] % 250 == 0:
            print(f"  {counter[0]} tuiles…", flush=True)

    if zoom >= last_zoom:
        return

    for child_x in (2 * x, 2 * x + 1):
        for child_y in (2 * y, 2 * y + 1):
            if intersects(zoom + 1, child_x, child_y, target):
                walk(
                    zoom + 1, child_x, child_y, reduced, target,
                    first_zoom, last_zoom, font, labelled, output, counter,
                )


def intersects(zoom, x, y, target) -> bool:
    count = 2**zoom
    lon_min, lat_min, lon_max, lat_max = target
    left, top = normalized(lon_min, lat_max)
    right, bottom = normalized(lon_max, lat_min)
    return not (
        (x + 1) / count < left
        or x / count > right
        or (y + 1) / count < top
        or y / count > bottom
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="tuiles")
    parser.add_argument("--zoom", default="0-8")
    parser.add_argument("--data", default="data")
    parser.add_argument("--bbox", default=",".join(str(v) for v in DEFAULT_BBOX))
    arguments = parser.parse_args()

    here = pathlib.Path(__file__).parent
    data = (here / arguments.data).resolve()
    output = (here / arguments.out).resolve()
    target = tuple(float(v) for v in arguments.bbox.split(","))
    first_zoom, last_zoom = (int(v) for v in arguments.zoom.split("-"))

    print("Chargement des données…", flush=True)
    layers = {
        "land": load(data, "ne_10m_land"),
        "urban": load(data, "ne_10m_urban_areas"),
        "lakes": load(data, "ne_10m_lakes"),
        "rivers": load(data, "ne_10m_rivers_lake_centerlines"),
        "borders": load(data, "ne_10m_admin_0_boundary_lines_land"),
        "regions": load(data, "ne_10m_admin_1_states_provinces_lines"),
        "roads": load(data, "ne_10m_roads"),
        "places": load(data, "ne_10m_populated_places"),
    }

    font = ImageFont.truetype(str(FONT_PATH), LABEL_SIZE)
    labelled = {
        zoom: choose_labels(layers["places"], zoom, font)
        for zoom in range(first_zoom, last_zoom + 1)
    }

    started = time.monotonic()
    counter = [0]
    walk(0, 0, 0, layers, target, first_zoom, last_zoom, font, labelled, output, counter)

    elapsed = time.monotonic() - started
    total = sum(p.stat().st_size for p in output.rglob("*.png"))
    print(f"{counter[0]} tuiles en {elapsed:.0f} s, {total / 1_048_576:.1f} Mo dans {output}")


if __name__ == "__main__":
    main()
