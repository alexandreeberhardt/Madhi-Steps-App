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

// Nombre de positions demande pour le trace de fond, celui du voyage entier
// dessine en bleu clair derriere la periode choisie. Bien moins que le trace
// principal : c'est un repere qu'on regarde de loin, jamais un trajet qu'on
// examine, et il se redemande a chaque rafraichissement.
export const POINTS_FOND = 1500;

// Sans delai maximum, un serveur injoignable laisse le site en chargement
// indefini, ce qui se lit comme un site casse.
const DELAI_MAX_MS = 10000;

// Revenir trois fois sur le meme point ne doit pas faire trois requetes, et
// surtout pas trois appels sortants du VPS vers un tiers. Le cache vit le
// temps de l'onglet : la carte ne merite pas un stockage de plus.
/** @type {Map<string, {adresse: string | null, desactivee: boolean}>} */
const cacheAdresses = new Map();

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
 * @param {number} [limite]  positions visees ; le serveur espace pour couvrir
 *   toute la periode, il ne coupe pas la fin.
 * @returns {Promise<{points: LocationPointV1[], resolutionSecondes: number}>}
 */
export async function getLocations(tripId, from, to, limite = POINTS_VISES) {
  // toISOString produit exactement le format attendu : ISO-8601 UTC avec
  // suffixe Z. Le serveur rejette tout le reste par un 400.
  const parametres = new URLSearchParams({
    from: from.toISOString(),
    to: to.toISOString(),
    limit: String(limite),
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
 * @param {string} tripId
 * @param {number} [limite]
 * @returns {Promise<import("./types.js").LocationDiagnosticV1[]>}
 */
export async function getRecentLocationDiagnostics(tripId, limite = 200) {
  const parametres = new URLSearchParams({ limit: String(limite) });
  const corps = await demanderJson(
    `${RACINE_API}/trips/${encodeURIComponent(tripId)}/diagnostics/recent-locations?${parametres}`,
  );
  return Array.isArray(corps) ? corps : [];
}

/**
 * L'adresse d'une position, relayee par le serveur du voyage et jamais
 * demandee au geocodeur depuis le navigateur (`arch/13` §6). Un tiers ne voit
 * ainsi que l'adresse IP fixe du VPS, deja publique, et non celle de chaque
 * personne de la famille qui regarde la carte.
 *
 * Aucune erreur ne sort d'ici. Une adresse introuvable, un serveur qui a
 * l'option eteinte, un reseau coupe : trois fois le meme affichage, l'heure et
 * les coordonnees, qui n'ont besoin de rien.
 *
 * @param {number} latitude
 * @param {number} longitude
 * @returns {Promise<{adresse: string | null, desactivee: boolean}>}
 */
export async function getAdresse(latitude, longitude) {
  const cle = `${format(latitude)},${format(longitude)}`;
  const connu = cacheAdresses.get(cle);
  if (connu !== undefined) return connu;

  try {
    const corps = await demanderJson(
      `${RACINE_API}/reverse-geocode?lat=${format(latitude)}&lon=${format(longitude)}`,
    );
    const trouvee = typeof corps?.address === "string" && corps.address !== "";
    const adresse = trouvee ? corps.address : null;
    // Seules les reussites sont retenues. D'ici, un reseau coupe et un point
    // en pleine mer rendent le meme `null` : mettre le second en cache
    // mettrait le premier avec lui, et la bulle resterait vide jusqu'a la fin
    // du voyage. C'est le serveur, lui, qui sait faire la difference et qui
    // garde les reponses vides.
    if (adresse !== null) cacheAdresses.set(cle, { adresse, desactivee: false });
    return { adresse, desactivee: false };
  } catch (cause) {
    // 503 est le seul cas qui ne se resoudra pas tout seul : l'option est
    // eteinte sur le VPS, et la bulle a le droit de le dire autrement qu'une
    // coupure de reseau.
    const desactivee = cause instanceof ErreurApi && cause.codeHttp === 503;
    return { adresse: null, desactivee };
  }
}

/**
 * Toujours le point decimal, quelle que soit la langue du navigateur : le
 * serveur lirait « 48,8566 » comme deux parametres. Cinq decimales, soit
 * environ un metre, ce qui fait aussi la cle de cache.
 *
 * @param {number} valeur
 * @returns {string}
 */
function format(valeur) {
  return valeur.toFixed(5);
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
