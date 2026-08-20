// Configuration du site familial.
//
// Ce fichier ne contient aucun secret : le token de lecture est pose par nginx
// (voir tools/nginx/madhi.alexeber.fr) et le segment secret vit dans l'URL, pas
// dans les fichiers servis.

// Doit correspondre a INITIAL_TRIP_ID du fichier server/.env du VPS. Si les
// deux divergent, le serveur repond 404 et le site le dit explicitement.
export const TRIP_ID = "8f14e45f-ceea-467a-9f4e-2b1c9a1a1a1a";

// Affiche tant que le serveur n'a pas donne le nom du voyage.
export const TITRE_PAR_DEFAUT = "Voyage";
