// Encapsule le fournisseur de tuiles. C'est le seul fichier du depot qui
// mentionne une URL de tuiles : y ajouter plus tard un proxy heberge sur le VPS
// ne devra toucher a rien d'autre (arch/05 §8).
//
// Leaflet est charge en script classique dans index.html — sa distribution ne
// fournit pas de module ES — et expose la variable globale `L`.

import { indiceDuPointVise } from "../features/track-picking.js";

/** @typedef {import("../types.js").LocationPointV1} LocationPointV1 */

const URL_TUILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const ATTRIBUTION =
  '&copy; les contributeurs <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

const ZOOM_MAX = 18;
// Sur un point unique, fitBounds degenere en zoom maximal sur un immeuble.
const ZOOM_POINT_SEUL = 13;
const MARGE_VUE = [36, 36];

// Les points du trace sont des cibles qu'on vise du doigt, et non plus
// seulement des marques de cadence : ils se dessinent, donc, plutot que de
// rester implicites dans la ligne.
const RAYON_JALON = 3;

// Au-dela, on s'en tient a la ligne. Le rendu canvas encaisse quelques
// milliers de cercles, pas dix mille : « tout le voyage » ferait ramer le
// telephone de la famille pour des points qui se superposent au pixel pres.
// L'appui, lui, continue de viser tous les points, dessines ou non.
const MAXIMUM_JALONS_DESSINES = 2000;

/**
 * @typedef {Object} Carte
 * @property {any} carte
 * @property {any} marqueur
 * @property {any} trace
 * @property {any} jalons
 * @property {any} rendu
 * @property {any} bulle
 * @property {string | null} bulleId
 * @property {((point: LocationPointV1 | null) => void) | null} surClicPoint
 * @property {LocationPointV1[]} points
 * @property {LocationPointV1 | null} dernierePosition
 */

/**
 * @param {HTMLElement} element
 * @param {{surClicPoint?: (point: LocationPointV1 | null) => void}} [ecoutes]
 * @returns {Carte}
 */
export function creerCarte(element, ecoutes = {}) {
  // Les images des marqueurs sont servies depuis le depot, pas depuis un CDN :
  // l'onglet reseau ne doit montrer que le domaine du projet et les tuiles.
  L.Icon.Default.imagePath = "./vendor/images/";

  const carte = L.map(element, {
    // Le zoom part en bas a gauche : la bande du haut appartient au bandeau
    // d'etat, et un « Aucune nouvelle position depuis 6 h » a moitie cache
    // derriere un bouton + serait pire que pas de bandeau du tout. En bas, il
    // est aussi plus facile a atteindre au pouce.
    zoomControl: false,
    attributionControl: true,
  });
  L.control.zoom({ position: "bottomleft" }).addTo(carte);
  L.tileLayer(URL_TUILES, { maxZoom: ZOOM_MAX, attribution: ATTRIBUTION }).addTo(carte);

  /** @type {Carte} */
  const handle = {
    carte,
    marqueur: null,
    trace: null,
    jalons: L.layerGroup(),
    // Les jalons se dessinent sur un seul canvas plutot qu'en un millier
    // d'elements SVG : c'est la difference entre une carte qui glisse et une
    // carte qui saccade sur le telephone de la famille.
    rendu: L.canvas(),
    bulle: null,
    bulleId: null,
    surClicPoint: ecoutes.surClicPoint ?? null,
    points: [],
    dernierePosition: null,
  };
  handle.rendu.addTo(carte);
  handle.jalons.addTo(carte);

  const surClicPoint = handle.surClicPoint;
  if (surClicPoint !== null) {
    carte.on("click", (evenement) => {
      // Les points lus ici, et non captures a l'inscription de l'ecoute : la
      // liste est remplacee a chaque rafraichissement, et viser le trace d'il
      // y a une heure designerait quelqu'un d'autre.
      const indice = indiceDuPointVise(
        handle.points,
        evenement.containerPoint,
        (point) => carte.latLngToContainerPoint([point.latitude, point.longitude]),
      );
      // Cliquer a cote referme la bulle : c'est le geste que tout le monde
      // essaie en premier.
      surClicPoint(indice === null ? null : handle.points[indice]);
    });
  }

  return handle;
}

/**
 * @param {Carte} handle
 * @param {LocationPointV1} point
 * @param {(point: LocationPointV1 | null) => void} [surClic]
 */
export function afficherDernierePosition(handle, point, surClic) {
  handle.dernierePosition = point;
  const coordonnees = [point.latitude, point.longitude];
  if (handle.marqueur === null) {
    handle.marqueur = L.marker(coordonnees, { title: "Dernière position connue" });
    handle.marqueur.addTo(handle.carte);
    // Le marqueur est ce qu'on touche en premier, et il n'appartient pas au
    // trace : sans cette ecoute, le seul point qui interesse vraiment serait
    // le seul a ne rien dire.
    if (surClic !== undefined) {
      handle.marqueur.on("click", () => surClic(handle.dernierePosition));
    }
    return;
  }
  handle.marqueur.setLatLng(coordonnees);
}

/**
 * @param {Carte} handle
 * @param {LocationPointV1[]} points
 */
export function afficherTrajet(handle, points) {
  handle.points = points;
  const coordonnees = points.map((point) => [point.latitude, point.longitude]);

  if (handle.trace !== null) {
    handle.carte.removeLayer(handle.trace);
    handle.trace = null;
  }
  handle.jalons.clearLayers();

  if (coordonnees.length >= 2) {
    handle.trace = L.polyline(coordonnees, {
      color: "#1f4f82",
      weight: 4,
      opacity: 0.85,
      // Non interactif, sinon un clic pose sur la ligne serait avale par elle
      // et n'atteindrait jamais la carte, qui est ce qui designe le point.
      interactive: false,
    });
    handle.trace.addTo(handle.carte);
  }

  if (points.length <= MAXIMUM_JALONS_DESSINES) {
    for (const point of points) {
      L.circleMarker([point.latitude, point.longitude], {
        renderer: handle.rendu,
        radius: RAYON_JALON,
        color: "#1f4f82",
        weight: 1,
        fillColor: "#1f4f82",
        fillOpacity: 0.9,
        interactive: false,
      }).addTo(handle.jalons);
    }
  }
}

/**
 * Ouvre la bulle d'un point, ou met a jour celle qui l'est deja.
 *
 * Le redessin passe toutes les trente secondes pour vieillir les horodatages :
 * rouvrir la bulle a chaque fois la ferait clignoter et redeplacerait la carte.
 * Tant que c'est le meme point, seul le contenu change.
 *
 * @param {Carte} handle
 * @param {LocationPointV1} point
 * @param {HTMLElement} contenu
 */
export function montrerBulle(handle, point, contenu) {
  // Toucher la bulle la referme, comme toucher la carte a cote. Leaflet
  // retient les clics poses sur une popup : sans cette ecoute, le geste
  // n'aurait aucun effet.
  if (handle.surClicPoint !== null) {
    const fermer = handle.surClicPoint;
    contenu.addEventListener("click", () => fermer(null));
  }

  if (handle.bulle !== null && handle.bulleId === point.id) {
    handle.bulle.setContent(contenu);
    return;
  }

  // Une bulle par point : `autoClose` etant eteint, Leaflet laisserait
  // volontiers la precedente ouverte sous la nouvelle.
  fermerBulle(handle);

  handle.bulle = L.popup({
    // Ni croix ni fermeture automatique ni touche d'echappement : c'est notre
    // ecoute de clic qui decide, et elle seule. Deux mecanismes de fermeture
    // concurrents se marcheraient dessus au clic suivant, qui designe deja un
    // autre point.
    closeButton: false,
    closeOnClick: false,
    autoClose: false,
    closeOnEscapeKey: false,
    className: "bulle",
    offset: [0, -6],
    // Pres du bord haut, la carte se deplace pour que la bulle tienne dans le
    // cadre (`autoPan`, defaut de Leaflet). L'application, elle, fait passer
    // la bulle sous le point sans toucher au cadrage. Meme but, deux moyens :
    // ici c'est le geste attendu d'une carte web, et « Recentrer » remet le
    // trajet en place.
    autoPan: true,
  })
    .setLatLng([point.latitude, point.longitude])
    .setContent(contenu)
    .openOn(handle.carte);
  handle.bulleId = point.id;
}

/**
 * @param {Carte} handle
 */
export function fermerBulle(handle) {
  if (handle.bulle === null) return;
  handle.carte.closePopup(handle.bulle);
  handle.bulle = null;
  handle.bulleId = null;
}

/**
 * Fait tenir le trajet et la derniere position dans la fenetre.
 * @param {Carte} handle
 */
export function ajusterVue(handle) {
  const limites = L.latLngBounds([]);
  if (handle.trace !== null) limites.extend(handle.trace.getBounds());
  if (handle.marqueur !== null) limites.extend(handle.marqueur.getLatLng());

  if (!limites.isValid()) return;

  if (limites.getNorthEast().equals(limites.getSouthWest())) {
    handle.carte.setView(limites.getCenter(), ZOOM_POINT_SEUL);
    return;
  }
  handle.carte.fitBounds(limites, { padding: MARGE_VUE, maxZoom: ZOOM_MAX });
}

/**
 * A appeler quand le conteneur passe de masque a visible : Leaflet a mesure
 * une hauteur nulle et n'afficherait que des tuiles grises.
 *
 * @param {Carte} handle
 */
export function rafraichirTaille(handle) {
  handle.carte.invalidateSize();
}
