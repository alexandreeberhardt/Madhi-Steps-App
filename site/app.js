// Point d'entree du site : il tient l'etat, declenche les chargements, et
// redessine.
//
// Un seul cycle, jamais contourne :
//
//     charger -> etat -> rendre
//
// `rendre` lit l'etat et met le DOM a jour ; elle ne declenche aucun appel
// reseau. Toute action de l'utilisateur modifie l'etat puis rappelle `rendre`.
// C'est ce qui remplace le moteur de rendu d'un framework, et ca ne tient que
// si la regle n'est jamais enfreinte.

import { TITRE_PAR_DEFAUT, TRIP_ID } from "./config.js";
import {
  ErreurApi,
  POINTS_FOND,
  getAdresse,
  getLatestLocation,
  getLocations,
  getTripStatus,
} from "./api-client.js";
import {
  PERIODES,
  PERIODE_PAR_DEFAUT,
  bornesDePeriode,
  libelleDePeriode,
} from "./features/period.js";
import {
  Etat,
  ancienneteDernierePosition,
  calculerEtat,
  doitAfficherPosition,
  historiqueEstVide,
} from "./features/trip-state.js";
import {
  afficherDernierePosition,
  afficherFond,
  afficherTrajet,
  ajusterVue,
  creerCarte,
  fermerBulle,
  montrerBulle,
  rafraichirTaille,
} from "./components/map.js";
import { contenuBulle } from "./components/point-bubble.js";
import { rendreDernierePosition } from "./components/latest-location.js";
import { messagesPour, rendreBandeau, rendreMessageCentral } from "./components/status-banner.js";
import { formaterJourEtHeure } from "./utils/time.js";

/** @typedef {import("./types.js").DonneesVoyage} DonneesVoyage */

// Rafraichir plus vite que le telephone n'envoie fabriquerait une illusion de
// temps reel. Deux minutes, et seulement si l'onglet est visible.
const INTERVALLE_RAFRAICHISSEMENT_MS = 120 * 1000;
// Redessin sans reseau, pour que « il y a 8 min » ne reste pas fige.
const INTERVALLE_REDESSIN_MS = 30 * 1000;

/** @type {DonneesVoyage} */
const donnees = {
  periode: PERIODE_PAR_DEFAUT,
  statut: null,
  dernierePosition: null,
  points: [],
  pointsFond: [],
  fenetre: null,
  historiqueCharge: false,
  resolutionSecondes: 1,
  idPointChoisi: null,
  adresse: null,
  rechercheAdresse: false,
  adresseDesactivee: false,
  chargement: false,
  erreur: null,
  derniereMajReussie: null,
};

const elements = {
  titre: document.getElementById("titre-voyage"),
  carte: document.getElementById("carte"),
  panneau: document.getElementById("panneau"),
  bandeau: document.getElementById("bandeau-etat"),
  messageCentral: document.getElementById("message-central"),
  dernierePosition: document.getElementById("derniere-position"),
  periodes: document.getElementById("periodes"),
  recentrer: document.getElementById("recentrer"),
  couverture: document.getElementById("couverture"),
};

/** @type {import("./components/map.js").Carte | null} */
let carte = null;
// Ce qui est actuellement trace sur la carte. Deux usages : ne pas reconstruire
// la polyline quand rien n'a change — le redessin passe toutes les 30 s pour
// vieillir les horodatages, pas pour retracer 288 points — et ne pas rappeler
// ajusterVue, qui annulerait le deplacement fait a la main par la personne qui
// regarde.
let cleTracee = null;
// Ce qui est actuellement trace en gris, avec sa propre cle. Elle est separee
// de `cleTracee` pour une raison precise : celle-la commande aussi `ajusterVue`,
// et un fond qui change pendant que la periode ne bouge pas recadrerait la carte
// sous les doigts de la personne qui regarde.
let cleFond = null;
// Numero de la demande en cours : une reponse plus lente qu'une demande plus
// recente doit etre ignoree, sinon la periode affichee ne correspond plus au
// bouton actif.
let sequence = 0;
// Le meme garde-fou pour la recherche d'adresse : deux points touches coup sur
// coup, et c'est l'adresse du premier qui pourrait s'afficher sous le second.
let sequenceAdresse = 0;

demarrer();

function demarrer() {
  construireSelecteur();
  elements.recentrer.addEventListener("click", () => {
    if (carte !== null) ajusterVue(carte);
  });

  setInterval(() => {
    if (document.visibilityState === "visible") charger();
  }, INTERVALLE_RAFRAICHISSEMENT_MS);

  setInterval(() => {
    if (document.visibilityState === "visible") rendre();
  }, INTERVALLE_REDESSIN_MS);

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "visible") return;
    if (donneesTropVieilles()) charger();
    else rendre();
  });

  charger();
}

async function charger() {
  const numero = ++sequence;
  donnees.chargement = true;
  rendre();

  try {
    const resultat = await recupererVoyage();
    if (numero !== sequence) return;
    appliquer(resultat);
    donnees.erreur = null;
    donnees.derniereMajReussie = new Date();
  } catch (cause) {
    if (numero !== sequence) return;
    // La donnee precedente est conservee volontairement : un serveur muet ne
    // doit pas effacer ce que la famille avait sous les yeux.
    donnees.erreur = cause instanceof ErreurApi ? cause : new ErreurApi("erreur_inattendue", null);
  } finally {
    if (numero === sequence) {
      donnees.chargement = false;
      rendre();
    }
  }
}

/**
 * @returns {Promise<{statut: import("./types.js").TripStatusV1, dernierePosition: import("./types.js").LocationPointV1 | null, points: import("./types.js").LocationPointV1[], pointsFond: import("./types.js").LocationPointV1[] | null, fenetre: import("./types.js").FenetreTemporelle | null, resolutionSecondes: number}>}
 */
async function recupererVoyage() {
  // Le statut vient en premier : `startedAt` commande tout le reste.
  const statut = await getTripStatus(TRIP_ID);

  if (statut.startedAt === null) {
    // Avant le depart, ne rien demander de precis. Les positions en base sont
    // celles de la pre-validation et des tests terrain, prises a la maison.
    return {
      statut,
      dernierePosition: null,
      points: [],
      pointsFond: [],
      fenetre: null,
      resolutionSecondes: 1,
    };
  }

  const fenetre = bornesDePeriode(donnees.periode, statut.startedAt);
  const [dernierePosition, historique, pointsFond] = await Promise.all([
    // La derniere position ne vient toujours pas de l'historique. Celui-ci ne
    // coupe plus la fin, mais il l'echantillonne : sur un an, la position la
    // plus recente qu'il rende peut dater d'une demi-heure.
    getLatestLocation(TRIP_ID),
    getLocations(TRIP_ID, fenetre.from, fenetre.to),
    recupererFond(statut.startedAt),
  ]);
  return {
    statut,
    dernierePosition,
    points: historique.points,
    pointsFond,
    fenetre,
    resolutionSecondes: historique.resolutionSecondes,
  };
}

/**
 * Le voyage entier, celui qui se dessine en gris derriere la periode choisie.
 *
 * Rend `[]` sur « tout le voyage » : le fond y serait le trace lui-meme,
 * dessine deux fois et demande au serveur pour rien.
 *
 * Rend `null` — c'est-a-dire « ne change rien » — quand la demande echoue.
 * Aucune erreur ne sort d'ici, et c'est delibere : un trace decoratif n'a pas
 * le droit de faire dire au site que le serveur est en panne alors qu'il vient
 * de repondre pour la periode et la derniere position. Un fond manquant se
 * remarque a peine ; un bandeau rouge pose sur des donnees fraiches serait
 * exactement la panne muette a l'envers.
 *
 * @param {string} debutVoyage
 * @returns {Promise<import("./types.js").LocationPointV1[] | null>}
 */
async function recupererFond(debutVoyage) {
  if (donnees.periode === "TOUT_LE_VOYAGE") return [];

  const voyage = bornesDePeriode("TOUT_LE_VOYAGE", debutVoyage);
  try {
    const reponse = await getLocations(TRIP_ID, voyage.from, voyage.to, POINTS_FOND);
    return reponse.points;
  } catch (cause) {
    return null;
  }
}

/**
 * @param {{statut: import("./types.js").TripStatusV1, dernierePosition: import("./types.js").LocationPointV1 | null, points: import("./types.js").LocationPointV1[], pointsFond: import("./types.js").LocationPointV1[] | null, fenetre: import("./types.js").FenetreTemporelle | null, resolutionSecondes: number}} resultat
 */
function appliquer(resultat) {
  donnees.statut = resultat.statut;
  donnees.dernierePosition = resultat.dernierePosition;
  donnees.points = resultat.points;
  // `null` veut dire que le fond n'a pas pu etre rafraichi. Celui d'il y a deux
  // minutes reste juste : c'est le voyage passe, il ne change pas.
  if (resultat.pointsFond !== null) donnees.pointsFond = resultat.pointsFond;
  donnees.fenetre = resultat.fenetre;
  donnees.historiqueCharge = resultat.fenetre !== null;
  // Le serveur ne tronque plus : il echantillonne et annonce son pas. Le site
  // n'a donc plus a deviner si le trajet est ampute, seulement a dire s'il est
  // resume.
  donnees.resolutionSecondes = resultat.resolutionSecondes;

  // Le trace a change sous la bulle : un point qui n'est plus dessine ne doit
  // pas garder la sienne ouverte. On retient un identifiant et non un indice,
  // justement pour pouvoir poser cette question.
  if (donnees.idPointChoisi !== null && pointChoisi() === null) oublierPointChoisi();
}

/**
 * Le point dont la bulle est ouverte, tel qu'il existe dans les donnees du
 * moment. Chaque rafraichissement remplace les objets recus : les comparer par
 * identifiant est la seule facon de reconnaitre le meme point d'un appel a
 * l'autre.
 *
 * @returns {import("./types.js").LocationPointV1 | null}
 */
function pointChoisi() {
  if (donnees.idPointChoisi === null) return null;
  const duTrace = donnees.points.find((point) => point.id === donnees.idPointChoisi);
  if (duTrace !== undefined) return duTrace;
  const derniere = donnees.dernierePosition;
  return derniere !== null && derniere.id === donnees.idPointChoisi ? derniere : null;
}

/**
 * Toucher un point du trace, ou la carte a cote de tout.
 *
 * @param {import("./types.js").LocationPointV1 | null} point
 */
function choisirPoint(point) {
  const identifiant = point === null ? null : point.id;
  if (donnees.idPointChoisi === identifiant) return;

  oublierPointChoisi();
  donnees.idPointChoisi = identifiant;
  donnees.rechercheAdresse = point !== null;

  rendre();
  if (point !== null) chargerAdresse(point, sequenceAdresse);
}

/**
 * Referme la bulle et abandonne ce qui s'y rapportait, sans redessiner :
 * l'appelant sait mieux quand le faire.
 */
function oublierPointChoisi() {
  donnees.idPointChoisi = null;
  donnees.adresse = null;
  donnees.adresseDesactivee = false;
  donnees.rechercheAdresse = false;
  // Une recherche en cours designe le point precedent : son resultat n'a rien
  // a faire dans la bulle du suivant.
  sequenceAdresse += 1;
}

/**
 * @param {import("./types.js").LocationPointV1} point
 * @param {number} numero
 */
async function chargerAdresse(point, numero) {
  const resultat = await getAdresse(point.latitude, point.longitude);
  if (numero !== sequenceAdresse) return;
  donnees.adresse = resultat.adresse;
  donnees.adresseDesactivee = resultat.desactivee;
  donnees.rechercheAdresse = false;
  rendre();
}

function rendre() {
  const maintenant = new Date();
  const etat = calculerEtat(donnees, maintenant);
  const aUnePosition = doitAfficherPosition(donnees);

  elements.titre.textContent =
    donnees.statut === null ? TITRE_PAR_DEFAUT : donnees.statut.name;

  const avis = messagesPour({
    etat,
    erreur: donnees.erreur,
    historiqueVide: historiqueEstVide(donnees),
    resolutionSecondes: donnees.resolutionSecondes,
    anciennete: ancienneteDernierePosition(donnees, maintenant),
    aUnePosition,
    libellePeriode: libelleDePeriode(donnees.periode),
  });

  if (aUnePosition) {
    // La carte n'apparait qu'avec une position a montrer : pas de carte centree
    // au hasard sur l'ocean.
    elements.carte.hidden = false;
    rendreMessageCentral(elements.messageCentral, []);
    rendreBandeau(elements.bandeau, avis);
    majCarte(maintenant);
  } else {
    elements.carte.hidden = true;
    rendreBandeau(elements.bandeau, []);
    rendreMessageCentral(elements.messageCentral, avis);
  }

  elements.panneau.hidden = !aUnePosition;
  rendreDernierePosition(elements.dernierePosition, {
    point: donnees.dernierePosition,
    maintenant,
    donneesPerimees: donnees.erreur !== null,
    derniereMajReussie: donnees.derniereMajReussie,
  });

  majSelecteur(etat);
  majCouverture();
  elements.recentrer.hidden = !aUnePosition;
  document.body.dataset.etat = etat;
}

/**
 * @param {Date} maintenant
 */
function majCarte(maintenant) {
  try {
    if (carte === null) carte = creerCarte(elements.carte, { surClicPoint: choisirPoint });
    // Le conteneur vient peut-etre d'etre demasque : Leaflet aurait mesure une
    // hauteur nulle et n'afficherait que du gris.
    rafraichirTaille(carte);

    // Le fond a sa propre decision : il se redessine quand il change, et lui
    // seul, sans jamais toucher au cadrage.
    const empreinteFond = signature(donnees.pointsFond);
    if (empreinteFond !== cleFond) {
      afficherFond(carte, donnees.pointsFond);
      cleFond = empreinteFond;
    }

    const cle = cleDeTrace();
    if (cle !== cleTracee) {
      afficherDernierePosition(carte, donnees.dernierePosition, choisirPoint);
      afficherTrajet(carte, donnees.points);
      ajusterVue(carte);
      cleTracee = cle;
    }

    // Hors du test de cle : l'adresse arrive apres coup, et l'heure du point
    // vieillit toute seule entre deux redessins.
    majBulle(maintenant);
  } catch (cause) {
    // Une carte cassee ne doit pas emporter le reste : la derniere position en
    // texte vaut mieux qu'une page blanche.
    console.error("carte indisponible", cause);
    elements.carte.hidden = true;
  }
}

/**
 * @param {Date} maintenant
 */
function majBulle(maintenant) {
  const point = pointChoisi();
  if (point === null) {
    fermerBulle(carte);
    return;
  }
  montrerBulle(
    carte,
    point,
    contenuBulle({
      point,
      maintenant,
      adresse: donnees.adresse,
      rechercheEnCours: donnees.rechercheAdresse,
      adresseDesactivee: donnees.adresseDesactivee,
    }),
  );
}

/**
 * Identifie ce que la carte montre de la periode choisie : le trace, la
 * derniere position, et la periode elle-meme.
 *
 * @returns {string}
 */
function cleDeTrace() {
  return `${donnees.periode}|${donnees.dernierePosition.id}|${signature(donnees.points)}`;
}

/**
 * Ce qui distingue deux listes de points sans les comparer une par une.
 *
 * Les identifiants des extremes comptent autant que l'effectif : sur sept
 * jours, un point qui sort par le debut pendant qu'un autre entre par la fin
 * laisse le compte inchange.
 *
 * @param {import("./types.js").LocationPointV1[]} points
 * @returns {string}
 */
function signature(points) {
  const premier = points.length > 0 ? points[0].id : "-";
  const dernier = points.length > 0 ? points[points.length - 1].id : "-";
  return `${points.length}|${premier}|${dernier}`;
}

function construireSelecteur() {
  for (const periode of PERIODES) {
    const bouton = document.createElement("button");
    bouton.type = "button";
    bouton.className = "bouton-periode";
    bouton.dataset.periode = periode.id;
    bouton.textContent = periode.libelle;
    bouton.addEventListener("click", () => choisirPeriode(periode.id));
    elements.periodes.append(bouton);
  }
}

/**
 * @param {string} idPeriode
 */
function choisirPeriode(idPeriode) {
  if (donnees.periode === idPeriode) return;
  donnees.periode = idPeriode;
  donnees.points = [];
  // Le fond ne depend pas de la periode : on le garde, sinon le gris
  // disparaitrait puis reviendrait a chaque changement de bouton. Sauf sur
  // « tout le voyage », ou le trace prend sa place et ou le laisser le
  // doublerait.
  if (idPeriode === "TOUT_LE_VOYAGE") donnees.pointsFond = [];
  donnees.historiqueCharge = false;
  donnees.resolutionSecondes = 1;
  // La bulle parlait d'un point de l'ancienne periode. La refermer plutot que
  // de la laisser flotter au-dessus d'un trace qui n'est plus le sien.
  oublierPointChoisi();
  rendre();
  charger();
}

/**
 * @param {string} etat
 */
function majSelecteur(etat) {
  const utilisable = etat !== Etat.AVANT_DEPART;
  for (const bouton of elements.periodes.children) {
    const actif = bouton.dataset.periode === donnees.periode;
    bouton.setAttribute("aria-pressed", String(actif));
    bouton.disabled = !utilisable;
  }
  elements.periodes.hidden = !utilisable;
}

function majCouverture() {
  if (donnees.fenetre === null || !donnees.historiqueCharge) {
    elements.couverture.hidden = true;
    elements.couverture.textContent = "";
    return;
  }

  const nombre = donnees.points.length;
  const positions = nombre === 1 ? "1 position" : `${nombre.toLocaleString("fr-FR")} positions`;
  const debut = donnees.fenetre.borneAuDepart
    ? `depuis le départ, le ${formaterJourEtHeure(donnees.fenetre.from)}`
    : `du ${formaterJourEtHeure(donnees.fenetre.from)}`;

  // Sans cette phrase, un trait gris apparaitrait sur la carte sans que rien
  // ne dise ce qu'il est.
  const fond = donnees.pointsFond.length > 0 ? " Le reste du voyage est tracé en gris." : "";

  elements.couverture.hidden = false;
  elements.couverture.textContent =
    `Trajet affiché ${debut} au ${formaterJourEtHeure(donnees.fenetre.to)} — ${positions}.${fond}`;
}

/**
 * @returns {boolean}
 */
function donneesTropVieilles() {
  if (donnees.derniereMajReussie === null) return true;
  return Date.now() - donnees.derniereMajReussie.getTime() >= INTERVALLE_RAFRAICHISSEMENT_MS;
}
