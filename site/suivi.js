import { TRIP_ID } from "./config.js";
import {
  ErreurApi,
  POINTS_VISES,
  getLocations,
  getRecentLocationDiagnostics,
  getTripStatus,
} from "./api-client.js";
import { bornesDePeriode } from "./features/period.js";

const LIMITE_POINTS = 200;
const SEUIL_BON_METRES = 50;
const SEUIL_AFFICHE_METRES = 165;
const RAYON_TERRE_METRES = 6371000;

const elements = {
  titre: document.getElementById("titre"),
  etat: document.getElementById("etat"),
  etatTexte: document.getElementById("etat-texte"),
  rafraichir: document.getElementById("rafraichir"),
  total: document.getElementById("stat-total"),
  analyses: document.getElementById("stat-analyses"),
  mediane: document.getElementById("stat-mediane"),
  bons: document.getElementById("stat-bons"),
  horsCarte: document.getElementById("stat-hors-carte"),
  distance: document.getElementById("stat-distance"),
  jours: document.getElementById("stat-jours"),
  premier: document.getElementById("stat-premier"),
  dernier: document.getElementById("stat-dernier"),
  rythme: document.getElementById("stat-rythme"),
  precisionDetail: document.getElementById("precision-detail"),
  graphPrecision: document.getElementById("graph-precision"),
  graphDelai: document.getElementById("graph-delai"),
  graphBatterie: document.getElementById("graph-batterie"),
  graphVitesse: document.getElementById("graph-vitesse"),
  table: document.getElementById("table-points"),
};

elements.rafraichir.addEventListener("click", charger);
charger();

async function charger() {
  poserEtat("chargement", "Chargement des points...");
  elements.rafraichir.disabled = true;

  try {
    const statut = await getTripStatus(TRIP_ID);
    const fenetre = statut.startedAt === null ? null : bornesDePeriode("TOUT_LE_VOYAGE", statut.startedAt);
    const [points, historique] = await Promise.all([
      getRecentLocationDiagnostics(TRIP_ID, LIMITE_POINTS),
      fenetre === null
        ? Promise.resolve({ points: [], resolutionSecondes: 1 })
        : getLocations(TRIP_ID, fenetre.from, fenetre.to, POINTS_VISES),
    ]);
    rendre(statut, points, historique.points, historique.resolutionSecondes);
    poserEtat("ok", `Derniere lecture : ${formaterDate(new Date())}`);
  } catch (cause) {
    const message = cause instanceof ErreurApi && cause.codeHttp === 403
      ? "Acces refuse par le serveur."
      : "Impossible de charger les donnees de suivi.";
    poserEtat("erreur", message);
  } finally {
    elements.rafraichir.disabled = false;
  }
}

function poserEtat(classe, texte) {
  elements.etat.className = `etat ${classe}`;
  elements.etatTexte.textContent = texte;
}

/**
 * @param {import("./types.js").TripStatusV1} statut
 * @param {import("./types.js").LocationDiagnosticV1[]} points
 * @param {import("./types.js").LocationPointV1[]} historique
 * @param {number} resolutionSecondes
 */
function rendre(statut, points, historique, resolutionSecondes) {
  elements.titre.textContent = `Suivi - ${statut.name}`;
  const stats = calculerStats(points);
  const voyage = calculerStatsVoyage(statut, historique);

  elements.total.textContent = nombre(statut.totalLocations);
  elements.analyses.textContent = nombre(points.length);
  elements.mediane.textContent = metres(stats.mediane);
  elements.bons.textContent = `${nombre(stats.sous50)} / ${nombre(stats.avecPrecision)}`;
  elements.horsCarte.textContent = `${nombre(stats.horsCarte)} / ${nombre(stats.avecPrecision)}`;
  elements.distance.textContent = distance(voyage.distanceMetres);
  elements.jours.textContent = jours(voyage.dureeJours);
  elements.premier.textContent = voyage.premier === null ? "-" : formaterDate(voyage.premier);
  elements.dernier.textContent = voyage.dernier === null ? "-" : formaterDate(voyage.dernier);
  elements.rythme.textContent = rythme(statut.totalLocations, voyage.dureeJours);
  elements.precisionDetail.textContent =
    `${nombre(points.length)} derniers points bruts. Distance calculee sur ${nombre(historique.length)} points affichables${
      resolutionSecondes > 1 ? `, echantillonnes toutes les ${dureeCourte(resolutionSecondes)}` : ""
    }.`;

  const precisions = points.map((point) => point.accuracyMeters);
  const delais = points.map((point) => delaiMinutes(point));
  const batteries = points.map((point) => point.batteryPercent);
  const vitesses = points.map((point) =>
    point.speedMps === null || point.speedMps === undefined ? null : point.speedMps * 3.6,
  );

  rendreGraphique(elements.graphPrecision, precisions, {
    unite: "m",
    maxForce: Math.max(SEUIL_AFFICHE_METRES, maximum(precisions) ?? 0),
    seuils: [
      { valeur: SEUIL_BON_METRES, classe: "seuil-bon", libelle: "50 m" },
      { valeur: SEUIL_AFFICHE_METRES, classe: "seuil-mauvais", libelle: "165 m" },
    ],
  });
  rendreGraphique(elements.graphDelai, delais, { unite: "min" });
  rendreGraphique(elements.graphBatterie, batteries, { unite: "%", maxForce: 100 });
  rendreGraphique(elements.graphVitesse, vitesses, { unite: "km/h" });
  rendreTable(points.slice(-20));
}

/**
 * @param {import("./types.js").TripStatusV1} statut
 * @param {import("./types.js").LocationPointV1[]} historique
 */
function calculerStatsVoyage(statut, historique) {
  const debut = statut.startedAt === null ? null : new Date(statut.startedAt);
  const finTexte = statut.endedAt ?? statut.latestRecordedAt;
  const fin = finTexte === null ? null : new Date(finTexte);
  const premier = historique.length === 0 ? debut : new Date(historique[0].recordedAt);
  const dernier = historique.length === 0 ? fin : new Date(historique[historique.length - 1].recordedAt);
  const dureeMs =
    debut !== null && fin !== null && Number.isFinite(debut.getTime()) && Number.isFinite(fin.getTime())
      ? Math.max(0, fin.getTime() - debut.getTime())
      : null;
  return {
    distanceMetres: distanceTotale(historique),
    dureeJours: dureeMs === null ? null : dureeMs / 86400000,
    premier,
    dernier,
  };
}

/**
 * @param {import("./types.js").LocationPointV1[]} points
 */
function distanceTotale(points) {
  let total = 0;
  for (let index = 1; index < points.length; index += 1) {
    total += distanceEntre(points[index - 1], points[index]);
  }
  return total;
}

function distanceEntre(a, b) {
  const lat1 = radians(a.latitude);
  const lat2 = radians(b.latitude);
  const deltaLat = radians(b.latitude - a.latitude);
  const deltaLon = radians(b.longitude - a.longitude);
  const haversine =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;
  return 2 * RAYON_TERRE_METRES * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
}

function radians(degres) {
  return degres * Math.PI / 180;
}

/**
 * @param {import("./types.js").LocationDiagnosticV1[]} points
 */
function calculerStats(points) {
  const valeurs = points
    .map((point) => point.accuracyMeters)
    .filter((valeur) => typeof valeur === "number" && Number.isFinite(valeur))
    .sort((a, b) => a - b);
  return {
    avecPrecision: valeurs.length,
    mediane: percentile(valeurs, 0.5),
    sous50: valeurs.filter((valeur) => valeur <= SEUIL_BON_METRES).length,
    horsCarte: valeurs.filter((valeur) => valeur > SEUIL_AFFICHE_METRES).length,
  };
}

/**
 * @param {HTMLElement} conteneur
 * @param {(number | null | undefined)[]} valeurs
 * @param {{unite: string, maxForce?: number, seuils?: {valeur: number, classe: string, libelle: string}[]}} options
 */
function rendreGraphique(conteneur, valeurs, options) {
  const points = valeurs
    .map((valeur, index) => ({ valeur, index }))
    .filter((point) => typeof point.valeur === "number" && Number.isFinite(point.valeur));

  if (points.length === 0) {
    conteneur.innerHTML = `<div class="vide">Aucune donnee exploitable.</div>`;
    return;
  }

  const largeur = 900;
  const hauteur = conteneur.classList.contains("compact") ? 230 : 360;
  const marge = { haut: 22, droite: 20, bas: 34, gauche: 54 };
  const max = Math.max(options.maxForce ?? 0, maximum(points.map((point) => point.valeur)) ?? 1);
  const min = Math.min(0, minimum(points.map((point) => point.valeur)) ?? 0);
  const plageX = Math.max(1, valeurs.length - 1);
  const plageY = Math.max(1, max - min);

  const x = (index) => marge.gauche + (index / plageX) * (largeur - marge.gauche - marge.droite);
  const y = (valeur) =>
    hauteur - marge.bas - ((valeur - min) / plageY) * (hauteur - marge.haut - marge.bas);

  const chemin = points
    .map((point, index) => `${index === 0 ? "M" : "L"} ${x(point.index).toFixed(2)} ${y(point.valeur).toFixed(2)}`)
    .join(" ");
  const seuils = (options.seuils ?? [])
    .filter((seuil) => seuil.valeur <= max)
    .map(
      (seuil) => `
        <line class="${seuil.classe}" x1="${marge.gauche}" x2="${largeur - marge.droite}" y1="${y(seuil.valeur)}" y2="${y(seuil.valeur)}"></line>
        <text class="label" x="${largeur - marge.droite - 38}" y="${y(seuil.valeur) - 6}">${seuil.libelle}</text>
      `,
    )
    .join("");
  const cercles = points
    .map((point) => {
      const classe = classeQualite(point.valeur);
      return `<circle class="point ${classe}" cx="${x(point.index)}" cy="${y(point.valeur)}" r="3.3">
        <title>${metresOuNombre(point.valeur, options.unite)} - ${point.index + 1}</title>
      </circle>`;
    })
    .join("");

  conteneur.innerHTML = `
    <svg viewBox="0 0 ${largeur} ${hauteur}" role="img" aria-label="Graphique ${options.unite}">
      <line class="axis" x1="${marge.gauche}" y1="${hauteur - marge.bas}" x2="${largeur - marge.droite}" y2="${hauteur - marge.bas}"></line>
      <line class="axis" x1="${marge.gauche}" y1="${marge.haut}" x2="${marge.gauche}" y2="${hauteur - marge.bas}"></line>
      <line class="grid" x1="${marge.gauche}" y1="${y(max)}" x2="${largeur - marge.droite}" y2="${y(max)}"></line>
      <text class="label" x="8" y="${y(max) + 4}">${metresOuNombre(max, options.unite)}</text>
      <text class="label" x="8" y="${hauteur - marge.bas + 4}">${metresOuNombre(min, options.unite)}</text>
      ${seuils}
      <path class="serie" d="${chemin}"></path>
      ${cercles}
    </svg>
  `;
}

/**
 * @param {import("./types.js").LocationDiagnosticV1[]} points
 */
function rendreTable(points) {
  elements.table.replaceChildren(
    ...points.map((point) => {
      const tr = document.createElement("tr");
      const precision = point.accuracyMeters;
      tr.innerHTML = `
        <td>${formaterDate(new Date(point.recordedAt))}</td>
        <td class="qualite-${classeTexte(precision)}">${metres(precision)}</td>
        <td>${minutes(delaiMinutes(point))}</td>
        <td>${pourcentage(point.batteryPercent)}</td>
      `;
      return tr;
    }),
  );
}

function delaiMinutes(point) {
  const debut = new Date(point.recordedAt).getTime();
  const fin = new Date(point.receivedAt).getTime();
  if (!Number.isFinite(debut) || !Number.isFinite(fin)) return null;
  return Math.max(0, (fin - debut) / 60000);
}

function percentile(valeurs, rang) {
  if (valeurs.length === 0) return null;
  const index = (valeurs.length - 1) * rang;
  const bas = Math.floor(index);
  const haut = Math.ceil(index);
  if (bas === haut) return valeurs[bas];
  return valeurs[bas] + (valeurs[haut] - valeurs[bas]) * (index - bas);
}

function maximum(valeurs) {
  const filtrees = valeurs.filter((valeur) => typeof valeur === "number" && Number.isFinite(valeur));
  return filtrees.length === 0 ? null : Math.max(...filtrees);
}

function minimum(valeurs) {
  const filtrees = valeurs.filter((valeur) => typeof valeur === "number" && Number.isFinite(valeur));
  return filtrees.length === 0 ? null : Math.min(...filtrees);
}

function classeQualite(valeur) {
  if (valeur > SEUIL_AFFICHE_METRES) return "mauvais";
  if (valeur > SEUIL_BON_METRES) return "moyen";
  return "";
}

function classeTexte(valeur) {
  if (typeof valeur !== "number" || !Number.isFinite(valeur)) return "moyenne";
  if (valeur > SEUIL_AFFICHE_METRES) return "mauvaise";
  if (valeur > SEUIL_BON_METRES) return "moyenne";
  return "bonne";
}

function nombre(valeur) {
  return valeur.toLocaleString("fr-FR");
}

function metres(valeur) {
  if (typeof valeur !== "number" || !Number.isFinite(valeur)) return "-";
  return `${valeur.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} m`;
}

function minutes(valeur) {
  if (typeof valeur !== "number" || !Number.isFinite(valeur)) return "-";
  if (valeur < 1) return "< 1 min";
  return `${valeur.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} min`;
}

function pourcentage(valeur) {
  if (typeof valeur !== "number" || !Number.isFinite(valeur)) return "-";
  return `${Math.round(valeur)} %`;
}

function distance(valeur) {
  if (typeof valeur !== "number" || !Number.isFinite(valeur)) return "-";
  if (valeur < 1000) return `${Math.round(valeur).toLocaleString("fr-FR")} m`;
  return `${(valeur / 1000).toLocaleString("fr-FR", { maximumFractionDigits: 1 })} km`;
}

function jours(valeur) {
  if (typeof valeur !== "number" || !Number.isFinite(valeur)) return "-";
  if (valeur < 1) return "< 1 j";
  return `${valeur.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} j`;
}

function rythme(total, dureeJours) {
  if (
    typeof total !== "number" ||
    !Number.isFinite(total) ||
    typeof dureeJours !== "number" ||
    !Number.isFinite(dureeJours) ||
    dureeJours <= 0
  ) {
    return "-";
  }
  return `${Math.round(total / dureeJours).toLocaleString("fr-FR")} / j`;
}

function dureeCourte(secondes) {
  if (secondes < 60) return `${secondes} s`;
  if (secondes < 3600) return `${Math.round(secondes / 60)} min`;
  return `${(secondes / 3600).toLocaleString("fr-FR", { maximumFractionDigits: 1 })} h`;
}

function metresOuNombre(valeur, unite) {
  if (unite === "m") return metres(valeur);
  return `${valeur.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} ${unite}`;
}

function formaterDate(date) {
  return new Intl.DateTimeFormat("fr-FR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
