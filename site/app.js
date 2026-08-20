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
  LIMITE_POINTS,
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
  afficherTrajet,
  ajusterVue,
  creerCarte,
  rafraichirTaille,
} from "./components/map.js";
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
  fenetre: null,
  historiqueCharge: false,
  tronque: false,
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
// Ajuster la vue a chaque redessin annulerait le deplacement fait a la main par
// la personne qui regarde. On ne le fait que lorsque ce qui est trace change.
let cleVueAjustee = null;
// Numero de la demande en cours : une reponse plus lente qu'une demande plus
// recente doit etre ignoree, sinon la periode affichee ne correspond plus au
// bouton actif.
let sequence = 0;

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
 * @returns {Promise<{statut: import("./types.js").TripStatusV1, dernierePosition: import("./types.js").LocationPointV1 | null, points: import("./types.js").LocationPointV1[], fenetre: import("./types.js").FenetreTemporelle | null}>}
 */
async function recupererVoyage() {
  // Le statut vient en premier : `startedAt` commande tout le reste.
  const statut = await getTripStatus(TRIP_ID);

  if (statut.startedAt === null) {
    // Avant le depart, ne rien demander de precis. Les positions en base sont
    // celles de la pre-validation et des tests terrain, prises a la maison.
    return { statut, dernierePosition: null, points: [], fenetre: null };
  }

  const fenetre = bornesDePeriode(donnees.periode, statut.startedAt);
  const [dernierePosition, points] = await Promise.all([
    // La derniere position ne vient jamais de l'historique : l'historique est
    // plafonne a 10 000 points et coupe les plus recents.
    getLatestLocation(TRIP_ID),
    getLocations(TRIP_ID, fenetre.from, fenetre.to),
  ]);
  return { statut, dernierePosition, points, fenetre };
}

/**
 * @param {{statut: import("./types.js").TripStatusV1, dernierePosition: import("./types.js").LocationPointV1 | null, points: import("./types.js").LocationPointV1[], fenetre: import("./types.js").FenetreTemporelle | null}} resultat
 */
function appliquer(resultat) {
  donnees.statut = resultat.statut;
  donnees.dernierePosition = resultat.dernierePosition;
  donnees.points = resultat.points;
  donnees.fenetre = resultat.fenetre;
  donnees.historiqueCharge = resultat.fenetre !== null;
  // Une reponse de la taille exacte du plafond est probablement tronquee : le
  // serveur trie du plus ancien au plus recent, puis coupe.
  donnees.tronque = resultat.points.length >= LIMITE_POINTS;
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
    tronque: donnees.tronque,
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
    majCarte();
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

function majCarte() {
  try {
    if (carte === null) carte = creerCarte(elements.carte);
    // Le conteneur vient peut-etre d'etre demasque : Leaflet aurait mesure une
    // hauteur nulle et n'afficherait que du gris.
    rafraichirTaille(carte);

    afficherDernierePosition(carte, donnees.dernierePosition);
    afficherTrajet(carte, donnees.points);

    const cle = `${donnees.periode}|${donnees.dernierePosition.id}|${donnees.points.length}`;
    if (cle !== cleVueAjustee) {
      ajusterVue(carte);
      cleVueAjustee = cle;
    }
  } catch (cause) {
    // Une carte cassee ne doit pas emporter le reste : la derniere position en
    // texte vaut mieux qu'une page blanche.
    console.error("carte indisponible", cause);
    elements.carte.hidden = true;
  }
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
  donnees.historiqueCharge = false;
  donnees.tronque = false;
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

  elements.couverture.hidden = false;
  elements.couverture.textContent =
    `Trajet affiché ${debut} au ${formaterJourEtHeure(donnees.fenetre.to)} — ${positions}.`;
}

/**
 * @returns {boolean}
 */
function donneesTropVieilles() {
  if (donnees.derniereMajReussie === null) return true;
  return Date.now() - donnees.derniereMajReussie.getTime() >= INTERVALLE_RAFRAICHISSEMENT_MS;
}
