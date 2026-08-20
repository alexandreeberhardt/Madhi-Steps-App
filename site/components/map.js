// Encapsule le fournisseur de tuiles. C'est le seul fichier du depot qui
// mentionne une URL de tuiles : y ajouter plus tard un proxy heberge sur le VPS
// ne devra toucher a rien d'autre (arch/05 §8).
//
// Leaflet est charge en script classique dans index.html — sa distribution ne
// fournit pas de module ES — et expose la variable globale `L`.

/** @typedef {import("../types.js").LocationPointV1} LocationPointV1 */

const URL_TUILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const ATTRIBUTION =
  '&copy; les contributeurs <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

const ZOOM_MAX = 18;
// Sur un point unique, fitBounds degenere en zoom maximal sur un immeuble.
const ZOOM_POINT_SEUL = 13;
const MARGE_VUE = [36, 36];

/**
 * @typedef {Object} Carte
 * @property {any} carte
 * @property {any} marqueur
 * @property {any} trace
 */

/**
 * @param {HTMLElement} element
 * @returns {Carte}
 */
export function creerCarte(element) {
  // Les images des marqueurs sont servies depuis le depot, pas depuis un CDN :
  // l'onglet reseau ne doit montrer que le domaine du projet et les tuiles.
  L.Icon.Default.imagePath = "./vendor/images/";

  const carte = L.map(element, {
    zoomControl: true,
    attributionControl: true,
  });
  L.tileLayer(URL_TUILES, { maxZoom: ZOOM_MAX, attribution: ATTRIBUTION }).addTo(carte);

  return { carte, marqueur: null, trace: null };
}

/**
 * @param {Carte} handle
 * @param {LocationPointV1} point
 */
export function afficherDernierePosition(handle, point) {
  const coordonnees = [point.latitude, point.longitude];
  if (handle.marqueur === null) {
    handle.marqueur = L.marker(coordonnees, { title: "Dernière position connue" });
    handle.marqueur.addTo(handle.carte);
    return;
  }
  handle.marqueur.setLatLng(coordonnees);
}

/**
 * @param {Carte} handle
 * @param {LocationPointV1[]} points
 */
export function afficherTrajet(handle, points) {
  const coordonnees = points.map((point) => [point.latitude, point.longitude]);

  if (handle.trace !== null) {
    handle.carte.removeLayer(handle.trace);
    handle.trace = null;
  }
  if (coordonnees.length < 2) return;

  handle.trace = L.polyline(coordonnees, {
    color: "#1f4f82",
    weight: 4,
    opacity: 0.85,
  });
  handle.trace.addTo(handle.carte);
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
