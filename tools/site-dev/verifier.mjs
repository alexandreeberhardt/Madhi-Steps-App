// Verifications du site familial, sans navigateur ni dependance.
//
//     node tools/site-dev/verifier.mjs
//
// Elles portent sur ce qui se voit mal a l'oeil nu : les huit etats de
// arch/17 §6, les bornes de periode, et la detection de troncature. Les
// composants DOM sont exerces sur un document minimal, assez pour verifier les
// phrases affichees.

import assert from "node:assert/strict";

const RACINE = new URL("../../site/", import.meta.url);

const { calculerEtat, historiqueEstVide, doitAfficherPosition, Etat } = await import(
  new URL("features/trip-state.js", RACINE)
);
const { bornesDePeriode, PERIODES } = await import(new URL("features/period.js", RACINE));
const { formaterAbsolu, formaterDuree, formaterRelatif } = await import(
  new URL("utils/time.js", RACINE)
);
const { messagesPour } = await import(new URL("components/status-banner.js", RACINE));
const { LIMITE_POINTS, ErreurApi } = await import(new URL("api-client.js", RACINE));

const MAINTENANT = new Date("2026-09-15T12:00:00Z");
const JOUR_MS = 24 * 60 * 60 * 1000;

let reussites = 0;
const echecs = [];

function verifier(intitule, corps) {
  try {
    corps();
    reussites += 1;
  } catch (cause) {
    echecs.push(`${intitule}\n    ${cause.message.split("\n")[0]}`);
  }
}

/**
 * Un etat de site complet, que chaque cas vient deformer.
 */
function donnees(surcharges = {}) {
  return {
    periode: "AUJOURDHUI",
    statut: {
      tripId: "t",
      name: "Madhi 2026",
      startedAt: "2026-08-25T06:00:00Z",
      endedAt: null,
      totalLocations: 10,
      latestRecordedAt: "2026-09-15T11:55:00Z",
      latestReceivedAt: "2026-09-15T11:55:20Z",
    },
    dernierePosition: position("2026-09-15T11:55:00Z"),
    points: [position("2026-09-15T11:55:00Z")],
    fenetre: null,
    historiqueCharge: true,
    tronque: false,
    chargement: false,
    erreur: null,
    derniereMajReussie: MAINTENANT,
    ...surcharges,
  };
}

function position(recordedAt) {
  return {
    id: "p1",
    deviceId: "d1",
    latitude: 48.85,
    longitude: 2.35,
    recordedAt,
    receivedAt: recordedAt,
  };
}

// --- les huit etats ---------------------------------------------------------

verifier("premier chargement : ni panne ni position", () => {
  const etat = calculerEtat(
    donnees({ statut: null, dernierePosition: null, points: [], chargement: true }),
    MAINTENANT,
  );
  assert.equal(etat, Etat.CHARGEMENT);
});

verifier("une erreur passe avant tout le reste", () => {
  const etat = calculerEtat(donnees({ erreur: new ErreurApi("http_500", 500) }), MAINTENANT);
  assert.equal(etat, Etat.SERVEUR_INDISPONIBLE);
});

verifier("startedAt nul : avant le depart, meme si des points existent", () => {
  const avant = donnees({ statut: { ...donnees().statut, startedAt: null } });
  assert.equal(calculerEtat(avant, MAINTENANT), Etat.AVANT_DEPART);
});

verifier("latest-location a null : aucune position, pas une panne", () => {
  const etat = calculerEtat(donnees({ dernierePosition: null }), MAINTENANT);
  assert.equal(etat, Etat.AUCUNE_POSITION);
});

verifier("endedAt non nul : voyage termine", () => {
  const fini = donnees({ statut: { ...donnees().statut, endedAt: "2026-09-14T18:00:00Z" } });
  assert.equal(calculerEtat(fini, MAINTENANT), Etat.VOYAGE_TERMINE);
});

verifier("moins d'une heure : etat nominal", () => {
  assert.equal(calculerEtat(donnees(), MAINTENANT), Etat.RECENT);
});

verifier("entre une heure et douze heures : ancien", () => {
  const vieux = donnees({ dernierePosition: position("2026-09-15T06:00:00Z") });
  assert.equal(calculerEtat(vieux, MAINTENANT), Etat.ANCIEN);
});

verifier("au-dela de douze heures : hors ligne", () => {
  const vieux = donnees({ dernierePosition: position("2026-09-13T12:00:00Z") });
  assert.equal(calculerEtat(vieux, MAINTENANT), Etat.HORS_LIGNE);
});

verifier("les seuils sont exclusifs a la minute pres", () => {
  const juste = donnees({ dernierePosition: position("2026-09-15T11:00:00Z") });
  assert.equal(calculerEtat(juste, MAINTENANT), Etat.ANCIEN);
});

verifier("historique vide n'efface pas la derniere position", () => {
  const vide = donnees({ points: [] });
  assert.equal(historiqueEstVide(vide), true);
  assert.equal(calculerEtat(vide, MAINTENANT), Etat.RECENT);
  assert.equal(doitAfficherPosition(vide), true);
});

verifier("historique non charge n'est pas un historique vide", () => {
  assert.equal(historiqueEstVide(donnees({ points: [], historiqueCharge: false })), false);
});

verifier("serveur muet : la derniere donnee connue reste affichable", () => {
  const coupe = donnees({ erreur: new ErreurApi("reseau_indisponible", null) });
  assert.equal(doitAfficherPosition(coupe), true);
});

// --- periodes ---------------------------------------------------------------

verifier("la periode la plus longue annonce 30 jours, pas le voyage", () => {
  const libelles = PERIODES.map((periode) => periode.libelle).join(" ");
  assert.match(libelles, /30 jours/);
  assert.doesNotMatch(libelles.toLowerCase(), /tout le voyage/);
});

verifier("les bornes ne remontent jamais avant le depart", () => {
  const depart = "2026-09-10T06:00:00Z";
  const fenetre = bornesDePeriode("TRENTE_JOURS", depart, MAINTENANT);
  assert.equal(fenetre.from.toISOString(), new Date(depart).toISOString());
  assert.equal(fenetre.borneAuDepart, true);
});

verifier("sans depart connu, la periode garde sa duree", () => {
  const fenetre = bornesDePeriode("SEPT_JOURS", null, MAINTENANT);
  assert.equal(fenetre.to.getTime() - fenetre.from.getTime(), 7 * JOUR_MS);
  assert.equal(fenetre.borneAuDepart, false);
});

verifier("aujourd'hui part de minuit, pas de vingt-quatre heures en arriere", () => {
  const fenetre = bornesDePeriode("AUJOURDHUI", null, MAINTENANT);
  assert.equal(fenetre.from.getHours(), 0);
  assert.equal(fenetre.from.getMinutes(), 0);
  assert.ok(fenetre.to.getTime() - fenetre.from.getTime() < JOUR_MS);
});

verifier("le format des bornes est celui qu'exige le serveur", () => {
  const fenetre = bornesDePeriode("SEPT_JOURS", null, MAINTENANT);
  assert.match(fenetre.from.toISOString(), /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
});

verifier("trente jours de captures restent sous le plafond du serveur", () => {
  const pointsParJour = (24 * 60) / 5;
  assert.ok(30 * pointsParJour < LIMITE_POINTS);
});

// --- messages ---------------------------------------------------------------

function vueBandeau(surcharges = {}) {
  return {
    etat: Etat.RECENT,
    erreur: null,
    historiqueVide: false,
    tronque: false,
    anciennete: 5 * 60 * 1000,
    aUnePosition: true,
    libellePeriode: "7 jours",
    ...surcharges,
  };
}

verifier("l'etat nominal n'affiche aucun bandeau", () => {
  assert.deepEqual(messagesPour(vueBandeau()), []);
});

verifier("aucun libelle ne suggere du temps reel", () => {
  const interdits = /en direct|temps r|live|en ligne maintenant/i;
  for (const etat of Object.values(Etat)) {
    for (const message of messagesPour(vueBandeau({ etat, anciennete: 3 * JOUR_MS }))) {
      assert.doesNotMatch(message.titre, interdits);
      assert.doesNotMatch(message.detail ?? "", interdits);
    }
  }
});

verifier("l'anciennete est ecrite en toutes lettres", () => {
  const avis = messagesPour(vueBandeau({ etat: Etat.HORS_LIGNE, anciennete: 3 * JOUR_MS }));
  assert.match(avis[0].titre, /3 jours/);
});

verifier("les erreurs se distinguent par leur cause", () => {
  const acces = messagesPour(vueBandeau({ etat: Etat.SERVEUR_INDISPONIBLE, erreur: new ErreurApi("http_403", 403) }));
  const inconnu = messagesPour(vueBandeau({ etat: Etat.SERVEUR_INDISPONIBLE, erreur: new ErreurApi("http_404", 404) }));
  const panne = messagesPour(vueBandeau({ etat: Etat.SERVEUR_INDISPONIBLE, erreur: new ErreurApi("http_502", 502) }));
  const muet = messagesPour(vueBandeau({ etat: Etat.SERVEUR_INDISPONIBLE, erreur: new ErreurApi("reseau", null) }));
  const titres = [acces, inconnu, panne, muet].map((avis) => avis[0].titre);
  assert.equal(new Set(titres).size, 4);
});

verifier("un historique vide ne dit pas que rien n'a jamais ete recu", () => {
  const avis = messagesPour(vueBandeau({ historiqueVide: true }));
  assert.equal(avis.length, 1);
  assert.match(avis[0].titre, /Aucun déplacement enregistré sur cette période/);
  assert.match(avis[0].detail, /dernière position connue reste affichée/);
});

verifier("une reponse de la taille du plafond est signalee", () => {
  const avis = messagesPour(vueBandeau({ tronque: true }));
  assert.match(avis[0].titre, /incomplet/);
});

verifier("le bandeau de troncature accompagne l'etat, il ne le remplace pas", () => {
  const avis = messagesPour(vueBandeau({ etat: Etat.HORS_LIGNE, anciennete: JOUR_MS, tronque: true }));
  assert.equal(avis.length, 2);
});

// --- formatage --------------------------------------------------------------

verifier("l'heure absolue est lisible par la famille", () => {
  assert.match(formaterAbsolu("2026-08-20T08:35:00Z"), /^20 août 2026 à \d{2}:\d{2}$/);
});

verifier("le relatif est court et en francais", () => {
  assert.equal(formaterRelatif("2026-09-15T11:52:00Z", MAINTENANT), "il y a 8 min");
  assert.equal(formaterRelatif("2026-09-15T11:59:50Z", MAINTENANT), "à l'instant");
});

verifier("une horloge de navigateur en avance ne produit pas de duree negative", () => {
  assert.equal(formaterRelatif("2026-09-15T12:05:00Z", MAINTENANT), "à l'instant");
});

verifier("les durees se lisent sans calcul", () => {
  assert.equal(formaterDuree(6 * 60 * 60 * 1000), "6 h");
  assert.equal(formaterDuree(JOUR_MS), "1 jour");
  assert.equal(formaterDuree(3 * JOUR_MS), "3 jours");
});

// --- resultat ---------------------------------------------------------------

console.log(`${reussites} verifications passees`);
for (const echec of echecs) {
  console.log(`ECHEC ${echec}`);
}
if (echecs.length > 0) {
  console.log(`${echecs.length} echec(s)`);
  process.exit(1);
}
