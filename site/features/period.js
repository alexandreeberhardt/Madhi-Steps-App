// Les periodes affichables, et elles seules. Ce module produit des bornes de
// dates : il ne touche jamais a la carte, pour que le selecteur reste
// independant du fournisseur cartographique (arch/05 §10).

/** @typedef {import("../types.js").FenetreTemporelle} FenetreTemporelle */

const JOUR_MS = 24 * 60 * 60 * 1000;

// « Tout le voyage » est servable depuis que le serveur echantillonne au lieu
// de tronquer : il rend une position par tranche de temps et couvre toujours
// toute la periode demandee, quelle que soit sa duree. L'ancienne limite a
// 30 jours venait d'un plafond de 10 000 points qui coupait les positions les
// plus recentes, en silence (arch/17 §4.1, corrige).
export const PERIODES = Object.freeze([
  Object.freeze({ id: "AUJOURDHUI", libelle: "Aujourd'hui", jours: null }),
  Object.freeze({ id: "VINGT_QUATRE_HEURES", libelle: "24 h", jours: 1 }),
  Object.freeze({ id: "SEPT_JOURS", libelle: "7 jours", jours: 7 }),
  Object.freeze({ id: "TRENTE_JOURS", libelle: "30 jours", jours: 30 }),
  Object.freeze({ id: "TOUT_LE_VOYAGE", libelle: "Tout le voyage", jours: null }),
]);

export const PERIODE_PAR_DEFAUT = "AUJOURDHUI";

/**
 * @param {string} idPeriode
 * @param {string | null} debutVoyage  `startedAt` du voyage, ISO-8601 ou null.
 * @param {Date} [maintenant]
 * @returns {FenetreTemporelle}
 */
export function bornesDePeriode(idPeriode, debutVoyage, maintenant = new Date()) {
  const to = new Date(maintenant.getTime());
  let from;

  switch (idPeriode) {
    case "AUJOURDHUI":
      from = new Date(maintenant.getTime());
      from.setHours(0, 0, 0, 0);
      break;
    case "VINGT_QUATRE_HEURES":
      // Une duree, pas une date. A midi les deux se ressemblent ; a une heure
      // du matin, « aujourd'hui » ne montre plus qu'une heure de trajet et il
      // faut passer a sept jours pour revoir l'etape de la veille.
      from = new Date(maintenant.getTime() - JOUR_MS);
      break;
    case "SEPT_JOURS":
      from = new Date(maintenant.getTime() - 7 * JOUR_MS);
      break;
    case "TRENTE_JOURS":
      from = new Date(maintenant.getTime() - 30 * JOUR_MS);
      break;
    case "TOUT_LE_VOYAGE":
      // Remonter au plus loin : c'est le bornage au depart, plus bas, qui
      // ramene la fenetre au debut reel du voyage.
      from = new Date(0);
      break;
    default:
      throw new Error(`periode inconnue : ${idPeriode}`);
  }

  // Le voyage n'existe qu'a partir de son depart. Remonter plus loin
  // ramenerait les positions de pre-validation et des tests terrain, prises a
  // la maison, qui ne sont pas supprimees de la base.
  const depart = debutVoyage === null ? null : new Date(debutVoyage);
  if (depart !== null && !Number.isNaN(depart.getTime()) && depart.getTime() > from.getTime()) {
    return { from: depart, to, borneAuDepart: true };
  }
  return { from, to, borneAuDepart: false };
}

/**
 * @param {string} idPeriode
 * @returns {string}
 */
export function libelleDePeriode(idPeriode) {
  const periode = PERIODES.find((candidate) => candidate.id === idPeriode);
  return periode === undefined ? idPeriode : periode.libelle;
}
