// Le seul module du site qui connait des URLs, et le seul qui fasse un fetch.
//
// Les chemins sont relatifs, jamais absolus : le token de lecture exige par
// l'API est pose par nginx en relayant l'appel (arch/17 §2.3). Ecrire un
// domaine en dur ici ferait sortir la requete du chemin protege, et le token
// n'y serait pas.

/** @typedef {import("./types.js").LocationPointV1} LocationPointV1 */
/** @typedef {import("./types.js").TripStatusV1} TripStatusV1 */

const RACINE_API = "./api";

// Plafond impose par le serveur. Une reponse de cette taille exacte est
// probablement tronquee : le serveur trie par recorded_at croissant puis coupe,
// donc ce sont les points les plus recents qui manquent.
export const LIMITE_POINTS = 10000;

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
    limit: String(LIMITE_POINTS),
  });
  const corps = await demanderJson(
    `${RACINE_API}/trips/${encodeURIComponent(tripId)}/locations?${parametres}`,
  );
  return Array.isArray(corps) ? corps : [];
}

/**
 * @param {string} chemin
 * @returns {Promise<any>}
 */
async function demanderJson(chemin) {
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
    return await reponse.json();
  } catch (cause) {
    throw new ErreurApi("reponse_illisible", reponse.status);
  }
}
