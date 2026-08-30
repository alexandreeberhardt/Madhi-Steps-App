// Formatage des instants, en francais et sans bibliotheque.
//
// L'absolu et le relatif s'affichent toujours ensemble : « il y a 3 jours » ne
// permet pas de recouper avec un message recu, et « 17 aout a 09:12 » seul
// oblige la famille a calculer. Les instants arrivent en UTC et s'affichent
// dans le fuseau du navigateur, sans mention du fuseau (arch/17 §7.4).

const MINUTE_MS = 60 * 1000;
const HEURE_MS = 60 * MINUTE_MS;
const JOUR_MS = 24 * HEURE_MS;

const FORMAT_DATE = new Intl.DateTimeFormat("fr-FR", {
  day: "numeric",
  month: "long",
  year: "numeric",
});
const FORMAT_HEURE = new Intl.DateTimeFormat("fr-FR", {
  hour: "2-digit",
  minute: "2-digit",
});
const FORMAT_JOUR = new Intl.DateTimeFormat("fr-FR", {
  day: "numeric",
  month: "long",
});

/**
 * « 20 aout 2026 a 08:35 »
 * @param {string | Date | null | undefined} instant
 * @returns {string}
 */
export function formaterAbsolu(instant) {
  const date = versDate(instant);
  if (date === null) return "date inconnue";
  return `${FORMAT_DATE.format(date)} à ${FORMAT_HEURE.format(date)}`;
}

/**
 * « 20 aout a 08:35 », pour les bornes de periode ou l'annee est evidente.
 * @param {string | Date | null | undefined} instant
 * @returns {string}
 */
export function formaterJourEtHeure(instant) {
  const date = versDate(instant);
  if (date === null) return "date inconnue";
  return `${FORMAT_JOUR.format(date)} à ${FORMAT_HEURE.format(date)}`;
}

/**
 * « aujourd'hui a 14:32 », « hier a 09:05 », « le 3 janvier a 09:05 ».
 *
 * C'est l'heure d'un point du trace, et non celle du suivi : la question posee
 * est « on etait ou a ce moment-la ? », et elle appelle une date. Le suivi,
 * lui, continue de se juger a son anciennete (arch/09 §2).
 *
 * L'annee n'apparait que si elle differe de l'annee courante : un voyage d'un
 * an finira par en traverser deux, et « 3 janvier » serait alors ambigu.
 *
 * @param {string | Date | null | undefined} instant
 * @param {Date} [maintenant]
 * @returns {string}
 */
export function formaterInstantDuPoint(instant, maintenant = new Date()) {
  const date = versDate(instant);
  if (date === null) return "date inconnue";

  const heure = FORMAT_HEURE.format(date);
  const jour = debutDeJournee(date);
  const aujourdHui = debutDeJournee(maintenant);
  const veille = new Date(aujourdHui.getTime());
  veille.setDate(veille.getDate() - 1);

  if (jour.getTime() === aujourdHui.getTime()) return `aujourd'hui à ${heure}`;
  if (jour.getTime() === veille.getTime()) return `hier à ${heure}`;

  const annee = date.getFullYear() === maintenant.getFullYear() ? "" : ` ${date.getFullYear()}`;
  return `le ${FORMAT_JOUR.format(date)}${annee} à ${heure}`;
}

/**
 * « il y a 8 min »
 * @param {string | Date | null | undefined} instant
 * @param {Date} [maintenant]
 * @returns {string}
 */
export function formaterRelatif(instant, maintenant = new Date()) {
  const ecart = ancienneteMs(instant, maintenant);
  if (ecart === null) return "";
  // Une horloge de navigateur en avance sur le serveur donnerait un ecart
  // negatif, et « il y a -2 min » ferait douter de tout le reste.
  if (ecart < MINUTE_MS) return "à l'instant";
  return `il y a ${formaterDuree(ecart)}`;
}

/**
 * « 8 min », « 6 h », « 3 jours ». Sert aussi a ecrire l'anciennete en toutes
 * lettres, seule facon de distinguer une position ancienne d'un appareil hors
 * ligne sans compter sur la couleur.
 *
 * @param {number} millisecondes
 * @returns {string}
 */
export function formaterDuree(millisecondes) {
  const duree = Math.max(0, millisecondes);
  if (duree < MINUTE_MS) return "moins d'une minute";
  if (duree < HEURE_MS) {
    return `${Math.floor(duree / MINUTE_MS)} min`;
  }
  if (duree < JOUR_MS) {
    const heures = Math.floor(duree / HEURE_MS);
    return `${heures} h`;
  }
  const jours = Math.floor(duree / JOUR_MS);
  if (jours < 60) {
    return jours === 1 ? "1 jour" : `${jours} jours`;
  }
  const mois = Math.floor(jours / 30);
  return `${mois} mois`;
}

/**
 * @param {string | Date | null | undefined} instant
 * @param {Date} [maintenant]
 * @returns {number | null}
 */
export function ancienneteMs(instant, maintenant = new Date()) {
  const date = versDate(instant);
  if (date === null) return null;
  return maintenant.getTime() - date.getTime();
}

/**
 * Minuit du jour de [date], dans le fuseau du navigateur. C'est ce fuseau qui
 * est le bon : ce qui compte est l'heure qu'il faisait sur place.
 *
 * @param {Date} date
 * @returns {Date}
 */
function debutDeJournee(date) {
  const jour = new Date(date.getTime());
  jour.setHours(0, 0, 0, 0);
  return jour;
}

/**
 * @param {string | Date | null | undefined} instant
 * @returns {Date | null}
 */
function versDate(instant) {
  if (instant === null || instant === undefined) return null;
  const date = instant instanceof Date ? instant : new Date(instant);
  return Number.isNaN(date.getTime()) ? null : date;
}
