// Miroir des modeles du serveur (server/app/models.py). Un seul fichier decrit
// les formes echangees, comme le demande arch/05 §5 : si le contrat bouge, il
// bouge ici, et l'editeur signale les usages devenus faux.
//
// Verification en dur, optionnelle et jamais requise pour deployer :
//     npx tsc --noEmit --checkJs site/*.js site/**/*.js

/**
 * Une position, telle que renvoyee par le serveur.
 *
 * `recordedAt` est l'instant de la capture par le telephone, `receivedAt`
 * celui de son arrivee au serveur. L'ecart entre les deux distingue « le
 * telephone n'enregistre plus » de « le telephone enregistre mais n'arrive pas
 * a envoyer », deux pannes que la famille ne doit pas confondre.
 *
 * @typedef {Object} LocationPointV1
 * @property {string} id
 * @property {string} deviceId
 * @property {number} latitude
 * @property {number} longitude
 * @property {string} recordedAt          ISO-8601 UTC, suffixe Z
 * @property {string} receivedAt          ISO-8601 UTC, suffixe Z
 * @property {number | null} [accuracyMeters]
 * @property {number | null} [altitudeMeters]
 * @property {number | null} [speedMps]
 * @property {number | null} [batteryPercent]  0 a 100
 */

/**
 * L'etat d'un voyage. C'est l'appel a faire en premier : `startedAt` commande
 * tout le reste, y compris la borne basse de l'historique.
 *
 * @typedef {Object} TripStatusV1
 * @property {string} tripId
 * @property {string} name
 * @property {string | null} startedAt
 * @property {string | null} endedAt
 * @property {number} totalLocations
 * @property {string | null} latestRecordedAt
 * @property {string | null} latestReceivedAt
 */

/**
 * Bornes d'une periode d'affichage, en heure absolue.
 *
 * @typedef {Object} FenetreTemporelle
 * @property {Date} from
 * @property {Date} to
 * @property {boolean} borneAuDepart  vrai quand `from` a ete ramene au depart
 */

/**
 * L'etat complet du site, tel que `app.js` le tient et que `rendre` le lit.
 *
 * @typedef {Object} DonneesVoyage
 * @property {string} periode
 * @property {TripStatusV1 | null} statut
 * @property {LocationPointV1 | null} dernierePosition
 * @property {LocationPointV1[]} points
 * @property {LocationPointV1[]} pointsFond  le voyage entier, trace en bleu clair derriere la periode
 * @property {FenetreTemporelle | null} fenetre
 * @property {boolean} historiqueCharge
 * @property {number} resolutionSecondes  pas d'echantillonnage annonce par le serveur
 * @property {string | null} idPointChoisi  le point dont la bulle est ouverte
 * @property {string | null} adresse        adresse de ce point, quand le serveur la donne
 * @property {boolean} rechercheAdresse
 * @property {boolean} adresseDesactivee    vrai quand le serveur a l'option eteinte
 * @property {boolean} chargement
 * @property {import("./api-client.js").ErreurApi | null} erreur
 * @property {Date | null} derniereMajReussie
 */

export {};
