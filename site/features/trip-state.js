// Le seul endroit ou l'etat du voyage est calcule. Les composants recoivent le
// resultat, ils ne le recalculent jamais : deux definitions du mot « ancien »
// finiraient par diverger, et le site se contredirait a l'ecran.

import { ancienneteMs } from "../utils/time.js";

/** @typedef {import("../types.js").DonneesVoyage} DonneesVoyage */

// Seuils nommes une seule fois. arch/06 §4 les fixe a 1 h et 12 h.
export const SEUIL_RECENT_MS = 60 * 60 * 1000;
export const SEUIL_HORS_LIGNE_MS = 12 * 60 * 60 * 1000;

export const Etat = Object.freeze({
  CHARGEMENT: "CHARGEMENT",
  SERVEUR_INDISPONIBLE: "SERVEUR_INDISPONIBLE",
  AVANT_DEPART: "AVANT_DEPART",
  AUCUNE_POSITION: "AUCUNE_POSITION",
  VOYAGE_TERMINE: "VOYAGE_TERMINE",
  RECENT: "RECENT",
  ANCIEN: "ANCIEN",
  HORS_LIGNE: "HORS_LIGNE",
});

/**
 * L'ordre des tests est celui du tableau de arch/17 §6 : le premier etat dont
 * la condition est vraie l'emporte.
 *
 * @param {DonneesVoyage} donnees
 * @param {Date} [maintenant]
 * @returns {string}
 */
export function calculerEtat(donnees, maintenant = new Date()) {
  // Premier chargement : rien n'est encore connu, et annoncer une panne serait
  // faux. Des qu'une donnee a ete obtenue une fois, elle prime et le
  // chargement redevient invisible.
  if (donnees.chargement && donnees.statut === null && donnees.erreur === null) {
    return Etat.CHARGEMENT;
  }
  // Une erreur l'emporte sur tout le reste : afficher un statut calcule sur des
  // donnees qu'on n'a pas pu rafraichir, sans le dire, est exactement la panne
  // muette que le projet cherche a eliminer.
  if (donnees.erreur !== null || donnees.statut === null) {
    return Etat.SERVEUR_INDISPONIBLE;
  }
  if (donnees.statut.startedAt === null) return Etat.AVANT_DEPART;
  if (donnees.dernierePosition === null) return Etat.AUCUNE_POSITION;
  if (donnees.statut.endedAt !== null) return Etat.VOYAGE_TERMINE;

  const anciennete = ancienneteMs(donnees.dernierePosition.recordedAt, maintenant);
  if (anciennete === null) return Etat.SERVEUR_INDISPONIBLE;
  if (anciennete < SEUIL_RECENT_MS) return Etat.RECENT;
  if (anciennete < SEUIL_HORS_LIGNE_MS) return Etat.ANCIEN;
  return Etat.HORS_LIGNE;
}

/**
 * Etat independant des sept autres : il porte sur la periode choisie, pas sur
 * le voyage. Une derniere position recente et une semaine sans trajet est un
 * cas normal ; le confondre avec « aucune position » ferait dire au site que
 * rien n'a jamais ete recu.
 *
 * @param {DonneesVoyage} donnees
 * @returns {boolean}
 */
export function historiqueEstVide(donnees) {
  return donnees.historiqueCharge && donnees.points.length === 0;
}

/**
 * La derniere donnee obtenue reste affichee quand le serveur ne repond plus,
 * datee, plutot que de laisser un ecran vide.
 *
 * @param {DonneesVoyage} donnees
 * @returns {boolean}
 */
export function doitAfficherPosition(donnees) {
  return donnees.dernierePosition !== null;
}

/**
 * @param {DonneesVoyage} donnees
 * @param {Date} [maintenant]
 * @returns {number | null}
 */
export function ancienneteDernierePosition(donnees, maintenant = new Date()) {
  if (donnees.dernierePosition === null) return null;
  return ancienneteMs(donnees.dernierePosition.recordedAt, maintenant);
}
