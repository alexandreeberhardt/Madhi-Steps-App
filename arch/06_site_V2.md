**06 — Site familial — V2**

*Historique riche, statistiques, partage maîtrisé et meilleure
expérience mobile*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. Objectif

La V2 enrichit l’expérience familiale sans remettre en cause le site
POC. Les composants existants deviennent des blocs réutilisables.

# 2. Fonctionnalités

- Calendrier / sélection de date.

- Aujourd’hui, hier, 7 jours, 30 jours, voyage complet.

- Distance du jour et totale depuis le serveur.

- État du téléphone : dernière communication, batterie, version si
  autorisé.

- Étapes/segments quotidiens.

- Affichage simplifié pour les longues périodes.

- Thème mobile amélioré.

# 3. Confidentialité évoluée

- Vue famille : position précise.

- Vue partage public optionnelle : délai de 30/60 min.

- Possibilité de masquer une zone sensible.

- Liens de partage révocables.

La vue publique optionnelle, si elle existe, ne doit jamais exposer la
position précise en temps réel. Elle utilise un retard, une précision
dégradée ou des zones masquées selon le risque réel.

# 4. États UX

- Tracking récent.

- Position ancienne.

- Appareil hors ligne.

- Voyage terminé.

- Serveur indisponible.

- Historique vide pour la période.

# 5. Performance

- Pagination ou intervalle de dates.

- Polylines simplifiées pour longues périodes.

- Cache HTTP/CDN possible pour les données non sensibles et adaptées.

- Chargement progressif de l’historique.

Le cache ou CDN ne doit pas devenir une fuite de localisation précise.
Par défaut, les données privées restent servies par le domaine du
projet.

# 6. Compatibilité POC

Les composants Map, LatestLocation et TripTimeline du POC sont
conservés. La V2 ajoute des vues et consomme de nouveaux endpoints ;
elle ne nécessite pas de modifier le format LocationPointV1.

# 7. Limites explicites

Pas d’analytics tiers, pas de scripts marketing, pas de carte Google
Maps, pas de dépendance à un compte GAFAM pour consulter le site
familial. Les statistiques affichées viennent du serveur du projet.

Les variables exposées au navigateur sont considérées publiques. Aucun
secret, token d’administration, token famille réel ou clé privée ne doit
être utilisé côté frontend.
