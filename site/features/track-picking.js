// Quel point du trace un doigt vise. Miroir de `domain/TrackPicking.kt` de
// l'application, et pour la meme raison : une selection fausse se voit mal a
// l'oeil et tres bien en test. La geometrie vit donc ici, hors de Leaflet.

/** @typedef {import("../types.js").LocationPointV1} LocationPointV1 */

/**
 * De quel ecart un appui peut manquer un point sans le manquer vraiment.
 *
 * Un point dessine fait six pixels de large, un doigt en couvre une
 * cinquantaine. Viser au pixel serait injouable, sur un telephone comme au
 * bout d'une souris.
 */
export const TOLERANCE_PIXELS = 22;

/**
 * L'indice du point le plus proche de [clic], ou `null` si aucun n'est assez
 * pres pour qu'on puisse dire qu'il etait vise.
 *
 * A distance egale, le plus recent gagne : c'est la fin du trace qu'on
 * regarde, et c'est elle qui est dessinee par-dessus.
 *
 * `projeter` rend la position a l'ecran d'un point, en pixels du conteneur de
 * la carte. La passer en argument garde ce module libre de Leaflet.
 *
 * @param {LocationPointV1[]} points
 * @param {{x: number, y: number}} clic
 * @param {(point: LocationPointV1) => {x: number, y: number}} projeter
 * @param {number} [tolerancePixels]
 * @returns {number | null}
 */
export function indiceDuPointVise(points, clic, projeter, tolerancePixels = TOLERANCE_PIXELS) {
  if (points.length === 0 || tolerancePixels <= 0) return null;

  const maximumCarre = tolerancePixels * tolerancePixels;
  let meilleurIndice = null;
  let meilleurCarre = Number.POSITIVE_INFINITY;

  points.forEach((point, indice) => {
    const ecran = projeter(point);
    const dx = ecran.x - clic.x;
    const dy = ecran.y - clic.y;
    const carre = dx * dx + dy * dy;
    // `<=` et non `<` : a egalite, le dernier vu l'emporte, donc le plus
    // recent.
    if (carre <= maximumCarre && carre <= meilleurCarre) {
      meilleurCarre = carre;
      meilleurIndice = indice;
    }
  });

  return meilleurIndice;
}
