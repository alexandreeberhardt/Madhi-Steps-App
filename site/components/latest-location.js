// Le bloc « derniere position ». Il n'interprete rien : l'etat lui arrive
// calcule, il ecrit ce qu'on lui donne.

import { formaterAbsolu, formaterDuree, formaterRelatif } from "../utils/time.js";

/** @typedef {import("../types.js").LocationPointV1} LocationPointV1 */

// En dessous, l'ecart entre capture et reception est le delai normal d'un envoi
// groupe ; au-dessus, il raconte quelque chose — le telephone enregistrait sans
// pouvoir envoyer — et merite d'etre ecrit.
const ECART_RECEPTION_NOTABLE_MS = 15 * 60 * 1000;

/**
 * @typedef {Object} VueDernierePosition
 * @property {LocationPointV1 | null} point
 * @property {Date} maintenant
 * @property {boolean} donneesPerimees   vrai quand le dernier rafraichissement a echoue
 * @property {Date | null} derniereMajReussie
 */

/**
 * @param {HTMLElement} element
 * @param {VueDernierePosition} vue
 */
export function rendreDernierePosition(element, vue) {
  element.replaceChildren();

  // Sans position, ce bloc se tait : le message qui occupe la place de la carte
  // dit deja pourquoi, et l'ecrire deux fois ferait douter de la premiere.
  if (vue.point === null) return;

  element.append(
    ligne(
      "position-principale",
      `Dernière position : ${formaterRelatif(vue.point.recordedAt, vue.maintenant)}`,
    ),
  );
  // L'heure exacte accompagne toujours l'anciennete : c'est elle qui permet de
  // recouper avec un message recu.
  element.append(ligne("position-heure", formaterAbsolu(vue.point.recordedAt)));

  const ecartReception = ecartCaptureReception(vue.point);
  if (ecartReception !== null && ecartReception >= ECART_RECEPTION_NOTABLE_MS) {
    element.append(
      ligne(
        "position-detail",
        `Reçue par le serveur ${formaterDuree(ecartReception)} plus tard, ` +
          `le ${formaterAbsolu(vue.point.receivedAt)}.`,
      ),
    );
  }

  if (vue.donneesPerimees && vue.derniereMajReussie !== null) {
    element.append(
      ligne(
        "position-detail",
        `Information non rafraîchie depuis ${formaterRelatif(
          vue.derniereMajReussie,
          vue.maintenant,
        ).replace("il y a ", "")}.`,
      ),
    );
  }
}

/**
 * @param {LocationPointV1} point
 * @returns {number | null}
 */
function ecartCaptureReception(point) {
  const capture = new Date(point.recordedAt).getTime();
  const reception = new Date(point.receivedAt).getTime();
  if (Number.isNaN(capture) || Number.isNaN(reception)) return null;
  return reception - capture;
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
