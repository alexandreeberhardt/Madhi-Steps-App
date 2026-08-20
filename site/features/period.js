// Les periodes affichables, et elles seules. Ce module produit des bornes de
// dates : il ne touche jamais a la carte, pour que le selecteur reste
// independant du fournisseur cartographique (arch/05 §10).

/** @typedef {import("../types.js").FenetreTemporelle} FenetreTemporelle */

const JOUR_MS = 24 * 60 * 60 * 1000;

// « Tout le voyage » n'est pas servable en un appel : le plafond du serveur est
// de 10 000 points, une annee en vaut environ 105 000, et la troncature porte
// sur les points les plus recents (arch/17 §4.1). La periode la plus longue
// annonce donc ce qu'elle montre vraiment : 30 jours.
export const PERIODES = Object.freeze([
  Object.freeze({ id: "AUJOURDHUI", libelle: "Aujourd'hui", jours: null }),
  Object.freeze({ id: "SEPT_JOURS", libelle: "7 jours", jours: 7 }),
  Object.freeze({ id: "TRENTE_JOURS", libelle: "30 jours", jours: 30 }),
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
    case "SEPT_JOURS":
      from = new Date(maintenant.getTime() - 7 * JOUR_MS);
      break;
    case "TRENTE_JOURS":
      from = new Date(maintenant.getTime() - 30 * JOUR_MS);
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
