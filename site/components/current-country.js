// Le pays du dernier point, affiche dans l'en-tete. Le drapeau est derive du
// code ISO renvoye par le serveur : aucune image ni requete externe n'est
// necessaire.

/**
 * @param {HTMLElement} element
 * @param {{visible: boolean, pays: string | null, codePays: string | null, rechercheEnCours: boolean}} vue
 */
export function rendrePaysActuel(element, vue) {
  element.replaceChildren();
  element.hidden = !vue.visible;
  if (!vue.visible) return;

  const drapeau = document.createElement("span");
  drapeau.className = "pays-drapeau";
  drapeau.setAttribute("aria-hidden", "true");
  drapeau.textContent = drapeauDuPays(vue.codePays) ?? "";

  const texte = document.createElement("span");
  texte.className = "pays-texte";

  const libelle = document.createElement("span");
  libelle.className = "pays-libelle";
  libelle.textContent = "Pays actuel";

  const valeur = document.createElement("strong");
  valeur.className = "pays-valeur";
  valeur.textContent = vue.rechercheEnCours ? "Recherche…" : (vue.pays ?? "Pays inconnu");

  texte.append(libelle, valeur);
  element.append(drapeau, texte);
}

/**
 * Transforme un code ISO 3166-1 alpha-2 en deux indicateurs regionaux Unicode.
 *
 * @param {string | null} codePays
 * @returns {string | null}
 */
export function drapeauDuPays(codePays) {
  if (typeof codePays !== "string" || !/^[a-z]{2}$/i.test(codePays)) return null;
  return [...codePays.toUpperCase()]
    .map((lettre) => String.fromCodePoint(127397 + lettre.charCodeAt(0)))
    .join("");
}
