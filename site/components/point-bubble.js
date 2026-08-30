// Ce qu'un point du trace a a dire : quand, et ou.
//
// L'heure et les coordonnees viennent de la reponse du serveur et s'affichent
// toujours. L'adresse, elle, demande un appel de plus, et son absence est le
// cas normal : l'option est eteinte par defaut sur le VPS (arch/13 §6). Elle
// se dit sans dramatiser, et une bulle sans adresse reste une bulle utile.

import { formaterInstantDuPoint } from "../utils/time.js";

/** @typedef {import("../types.js").LocationPointV1} LocationPointV1 */

/**
 * @typedef {Object} VueBulle
 * @property {LocationPointV1} point
 * @property {Date} maintenant
 * @property {string | null} adresse
 * @property {boolean} rechercheEnCours
 * @property {boolean} adresseDesactivee   vrai quand le serveur a l'option eteinte
 */

/**
 * @param {VueBulle} vue
 * @returns {HTMLElement}
 */
export function contenuBulle(vue) {
  const bloc = document.createElement("div");
  bloc.className = "bulle-point";

  bloc.append(
    ligne("bulle-heure", formaterInstantDuPoint(vue.point.recordedAt, vue.maintenant)),
    ligne(
      vue.adresse === null ? "bulle-adresse bulle-adresse-absente" : "bulle-adresse",
      ligneAdresse(vue),
    ),
    ligne("bulle-coordonnees", formaterCoordonnees(vue.point)),
  );

  return bloc;
}

/**
 * Dit ce qui se passe, jamais « erreur ». Une adresse est un agrement, pas
 * une donnee du voyage : la perdre n'est pas une panne.
 *
 * @param {VueBulle} vue
 * @returns {string}
 */
export function ligneAdresse(vue) {
  if (vue.adresse !== null) return vue.adresse;
  if (vue.rechercheEnCours) return "Recherche de l'adresse…";
  if (vue.adresseDesactivee) return "Adresse non configurée.";
  return "Adresse indisponible.";
}

/**
 * Cinq decimales, soit environ un metre : au-dela, le GPS n'en sait rien.
 *
 * Point decimal et non virgule, meme en francais : une virgule decimale et une
 * virgule separatrice dans la meme ligne donneraient « 48,85660, 2,35220 »,
 * que personne ne sait relire.
 *
 * @param {LocationPointV1} point
 * @returns {string}
 */
export function formaterCoordonnees(point) {
  return `${point.latitude.toFixed(5)}, ${point.longitude.toFixed(5)}`;
}

/**
 * @param {string} classe
 * @param {string} texte
 * @returns {HTMLParagraphElement}
 */
function ligne(classe, texte) {
  const paragraphe = document.createElement("p");
  paragraphe.className = classe;
  paragraphe.textContent = texte;
  return paragraphe;
}
