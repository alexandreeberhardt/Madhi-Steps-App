// Bandeau d'etat et messages d'erreur.
//
// Ce module traduit un etat deja calcule en phrases ; il ne decide jamais de
// l'etat lui-meme. Aucune formulation ne doit suggerer du temps reel : le
// telephone envoie toutes les cinq minutes au mieux, et peut rester des jours
// hors reseau.

import { formaterDuree } from "../utils/time.js";
import { Etat } from "../features/trip-state.js";

/**
 * @typedef {Object} Avis
 * @property {"info" | "attention" | "alerte"} ton
 * @property {string} titre
 * @property {string} [detail]
 */

/**
 * @typedef {Object} VueBandeau
 * @property {string} etat
 * @property {import("../api-client.js").ErreurApi | null} erreur
 * @property {boolean} historiqueVide
 * @property {boolean} tronque
 * @property {number | null} anciennete
 * @property {boolean} aUnePosition
 * @property {string} libellePeriode
 */

/**
 * @param {VueBandeau} vue
 * @returns {Avis[]}
 */
export function messagesPour(vue) {
  const avis = [];
  const principal = avisPrincipal(vue);
  if (principal !== null) avis.push(principal);

  // Ces deux-la portent sur la periode affichee, pas sur le voyage : ils se
  // combinent avec n'importe quel etat.
  if (vue.tronque) {
    avis.push({
      ton: "attention",
      titre: "Le trajet affiché est peut-être incomplet.",
      detail:
        "Le serveur a renvoyé le nombre maximum de points pour cette période. " +
        "Les plus récents peuvent manquer ; la dernière position, elle, reste exacte.",
    });
  }
  // Sans aucune position connue, « aucun deplacement sur cette periode » ne
  // ferait que repeter autrement « aucune position recue », et laisserait
  // croire a deux problemes distincts.
  if (vue.historiqueVide && vue.aUnePosition) {
    avis.push({
      ton: "info",
      titre: `Aucun déplacement enregistré sur cette période (${vue.libellePeriode}).`,
      detail: "La dernière position connue reste affichée : elle est plus ancienne que cette période.",
    });
  }
  return avis;
}

/**
 * @param {VueBandeau} vue
 * @returns {Avis | null}
 */
function avisPrincipal(vue) {
  switch (vue.etat) {
    case Etat.CHARGEMENT:
      return { ton: "info", titre: "Chargement…" };

    case Etat.SERVEUR_INDISPONIBLE:
      return avisErreur(vue);

    case Etat.AVANT_DEPART:
      return {
        ton: "info",
        titre: "Le voyage n'a pas encore commencé.",
        detail: "Le trajet s'affichera à partir du départ.",
      };

    case Etat.AUCUNE_POSITION:
      return {
        ton: "info",
        titre: "Aucune position reçue pour l'instant.",
        detail: "Rien n'est encore arrivé au serveur depuis le départ.",
      };

    case Etat.VOYAGE_TERMINE:
      return { ton: "info", titre: "Le voyage est terminé.", detail: "Voici le trajet complet." };

    case Etat.ANCIEN:
      return {
        ton: "attention",
        titre: `Aucune nouvelle position depuis ${dureeLisible(vue.anciennete)}.`,
      };

    case Etat.HORS_LIGNE:
      return {
        ton: "alerte",
        titre: `Aucune nouvelle position depuis ${dureeLisible(vue.anciennete)}.`,
        detail: "La position affichée est la dernière connue, pas la position actuelle.",
      };

    default:
      return null;
  }
}

/**
 * Le code HTTP distingue trois causes aux suites opposees : un acces perime se
 * regle en demandant un nouveau lien, un voyage inconnu est une erreur de
 * configuration, une panne serveur ne demande rien a la famille.
 *
 * @param {VueBandeau} vue
 * @returns {Avis}
 */
function avisErreur(vue) {
  const code = vue.erreur === null ? null : vue.erreur.codeHttp;
  const rappel = vue.aUnePosition
    ? " La dernière information connue reste affichée ci-dessous."
    : "";

  if (code === null) {
    return {
      ton: "alerte",
      titre: "Le serveur ne répond pas.",
      detail: `Le site réessaiera tout seul.${rappel}`,
    };
  }
  if (code === 401 || code === 403) {
    return {
      ton: "alerte",
      titre: "Cet accès n'est plus valide.",
      detail: "Le lien ou le mot de passe familial a changé. Demande le nouveau.",
    };
  }
  if (code === 404) {
    return {
      ton: "alerte",
      titre: "Ce voyage est introuvable.",
      detail: "Le serveur ne connaît pas l'identifiant de voyage configuré pour ce site.",
    };
  }
  if (code >= 500) {
    return {
      ton: "alerte",
      titre: "Le serveur a un problème.",
      detail: `Ce n'est pas le téléphone : les positions continuent d'être enregistrées.${rappel}`,
    };
  }
  return {
    ton: "alerte",
    titre: "Le serveur a refusé la demande.",
    detail: `Code ${code}.${rappel}`,
  };
}

/**
 * @param {HTMLElement} element
 * @param {Avis[]} avis
 */
export function rendreBandeau(element, avis) {
  element.replaceChildren();
  element.hidden = avis.length === 0;
  for (const message of avis) {
    element.append(bloc(message, "avis"));
  }
}

/**
 * Utilise quand il n'y a rien a montrer sur une carte : le message occupe alors
 * la place de la carte, plutot que de laisser une carte centree au hasard.
 *
 * @param {HTMLElement} element
 * @param {Avis[]} avis
 */
export function rendreMessageCentral(element, avis) {
  element.replaceChildren();
  element.hidden = avis.length === 0;
  for (const message of avis) {
    element.append(bloc(message, "avis avis-central"));
  }
}

/**
 * @param {Avis} message
 * @param {string} classeDeBase
 * @returns {HTMLElement}
 */
function bloc(message, classeDeBase) {
  const conteneur = document.createElement("div");
  conteneur.className = `${classeDeBase} avis-${message.ton}`;

  const titre = document.createElement("p");
  titre.className = "avis-titre";
  titre.textContent = message.titre;
  conteneur.append(titre);

  if (message.detail !== undefined) {
    const detail = document.createElement("p");
    detail.className = "avis-detail";
    detail.textContent = message.detail;
    conteneur.append(detail);
  }
  return conteneur;
}

/**
 * @param {number | null} anciennete
 * @returns {string}
 */
function dureeLisible(anciennete) {
  return anciennete === null ? "un moment" : formaterDuree(anciennete);
}
