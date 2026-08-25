// Le seul module du site qui connait des URLs, et le seul qui fasse un fetch.
//
// Les chemins sont relatifs, jamais absolus : le token de lecture exige par
// l'API est pose par nginx en relayant l'appel (arch/17 §2.3). Ecrire un
// domaine en dur ici ferait sortir la requete du chemin protege, et le token
// n'y serait pas.

/** @typedef {import("./types.js").LocationPointV1} LocationPointV1 */
/** @typedef {import("./types.js").TripStatusV1} TripStatusV1 */

const RACINE_API = "./api";

// Nombre de positions demande au serveur. Ce n'est plus un plafond qui coupe :
// le serveur echantillonne pour couvrir toute la periode, et annonce le pas
// qu'il a retenu. Une reponse de cette taille n'est donc plus le signe d'un
// trajet ampute, seulement d'un trajet resume.
export const POINTS_VISES = 10000;

// Sans delai maximum, un serveur injoignable laisse le site en chargement
// indefini, ce qui se lit comme un site casse.
const DELAI_MAX_MS = 10000;

export class ErreurApi extends Error {
  /**
   * @param {string} message
   * @param {number | null} codeHttp  null quand la requete n'a pas abouti.
   */
  constructor(message, codeHttp) {
    super(message);
    this.name = "ErreurApi";
    this.codeHttp = codeHttp;
  }
}

/**
 * @param {string} tripId
 * @returns {Promise<TripStatusV1>}
 */
export async function getTripStatus(tripId) {
  return demanderJson(`${RACINE_API}/trips/${encodeURIComponent(tripId)}/status`);
}

/**
 * Renvoie `null` sans erreur quand le voyage n'a aucune position : le serveur
 * repond alors un corps `null` avec un code 200. Ce n'est pas une panne, c'est
 * l'etat « aucune position recue ».
 *
 * @param {string} tripId
 * @returns {Promise<LocationPointV1 | null>}
 */
export async function getLatestLocation(tripId) {
  const corps = await demanderJson(
    `${RACINE_API}/trips/${encodeURIComponent(tripId)}/latest-location`,
  );
  return corps === null || corps === undefined ? null : corps;
}

/**
 * @param {string} tripId
 * @param {Date} from
 * @param {Date} to
 * @returns {Promise<LocationPointV1[]>}
 */
export async function getLocations(tripId, from, to) {
  // toISOString produit exactement le format attendu : ISO-8601 UTC avec
  // suffixe Z. Le serveur rejette tout le reste par un 400.
  const parametres = new URLSearchParams({
    from: from.toISOString(),
    to: to.toISOString(),
    limit: String(POINTS_VISES),
  });
  const { corps, enTetes } = await demander(
    `${RACINE_API}/trips/${encodeURIComponent(tripId)}/locations?${parametres}`,
  );
  const annonce = Number(enTetes.get("X-Madhi-Resolution-Seconds"));
  return {
    points: Array.isArray(corps) ? corps : [],
    // Un serveur anterieur a l'echantillonnage ne pose pas l'en-tete. Une
    // seconde signifie « rien n'a ete regroupe », ce qui etait vrai de lui.
    resolutionSecondes: Number.isFinite(annonce) && annonce > 0 ? annonce : 1,
  };
}

/**
 * @param {string} chemin
 * @returns {Promise<any>}
 */
async function demanderJson(chemin) {
  const { corps } = await demander(chemin);
  return corps;
}

/**
 * Comme [demanderJson], mais rend aussi les en-tetes : le serveur y annonce
 * le pas d'echantillonnage de l'historique, et le site doit pouvoir dire ce
 * qu'il montre plutot que de le deviner.
 *
 * @param {string} chemin
 * @returns {Promise<{corps: any, enTetes: Headers}>}
 */
async function demander(chemin) {
  const controleur = new AbortController();
  const minuterie = setTimeout(() => controleur.abort(), DELAI_MAX_MS);
  let reponse;
  try {
    reponse = await fetch(chemin, {
      signal: controleur.signal,
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
  } catch (cause) {
    // Reseau coupe, DNS, TLS ou delai depasse : indistinguables ici, et le site
    // les traite pareil — le serveur ne repond pas.
    throw new ErreurApi("reseau_indisponible", null);
  } finally {
    clearTimeout(minuterie);
  }

  if (!reponse.ok) {
    // Le code voyage avec l'erreur : le bandeau distingue un acces perime d'un
    // voyage inconnu d'un serveur en panne, trois causes aux suites opposees.
    throw new ErreurApi(`http_${reponse.status}`, reponse.status);
  }

  try {
    return { corps: await reponse.json(), enTetes: reponse.headers };
  } catch (cause) {
    throw new ErreurApi("reponse_illisible", reponse.status);
  }
}
