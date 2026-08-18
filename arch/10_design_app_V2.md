**Design de l’application Android — V2**

*Évolution sans rupture — plus utile, mais toujours carte-first et
minimale*

# 1. Principe d’évolution

La V2 enrichit la V1 uniquement avec des fonctions réellement utiles
pendant le voyage. La structure visuelle reste presque identique : carte
dominante, commandes réduites, informations détaillées hors de
l’accueil.

# 2. Écran principal V2

> ┌─────────────────────────────┐\
> │ Voyage ⚙ │\
> ├─────────────────────────────┤\
> │ │\
> │ │\
> │ CARTE │\
> │ trajet du jour + ● │\
> │ │\
> │ │\
> ├─────────────────────────────┤\
> │ Aujourd’hui 67 km │\
> │ ● Suivi actif · il y a 2 min│\
> │ \[ Recentrer \] \[ Journée \] │\
> └─────────────────────────────┘

La V2 autorise une seule statistique synthétique sur l’accueil : la
distance du jour, car elle est directement pertinente pour un voyage à
vélo. Les autres statistiques restent secondaires.

# 3. Actions visibles

| **Action** | **V1** | **V2** |
|----|----|----|
| Recentrer | Oui | Oui |
| Ouvrir le détail de la journée | Non | Oui |
| Corriger un problème | Seulement si nécessaire | Seulement si nécessaire |
| Je vais bien | Non | Optionnel, si réellement utilisé |
| Synchroniser maintenant | Non | Toujours dans Réglages |
| Arrêter le tracking | Réglages | Réglages |

# 4. Détail de la journée

> Journée — 18 août\
> \
> \[ carte du trajet \]\
> \
> 67,4 km\
> Départ 08:21 · Dernier point 18:43\
> \
> \[ Retour \]

Pas de graphiques complexes dans la première V2. Altitude, vitesse et
autres mesures ne sont ajoutées que si elles sont réellement consultées
pendant les tests.

# 5. Historique

Accessible depuis le détail de la journée ou les réglages, pas depuis
une barre de navigation permanente.

> Historique\
> 18 août 67 km\
> 17 août 91 km\
> 16 août Repos\
> 15 août 73 km

# 6. Réglages V2

> Réglages\
> \
> Suivi\
> État et autorisations\
> Mode de suivi : Éco / Normal / Précis\
> Fréquence : 5 min\
> \
> Synchronisation\
> État hors ligne\
> Dernier envoi\
> File locale\
> \
> Diagnostic\
> Santé du suivi\
> Détails techniques\
> \
> Voyage\
> Historique\
> Informations du voyage\
> \
> Partage\
> Confidentialité\
> Lien familial / accès\
> \
> Application\
> Version\
> \
> \[ Désactiver le tracking \]

# 7. Fonctions explicitement exclues

- Réseau social ou commentaires

- Chat intégré

- Badges / gamification

- Météo intégrée

- Musique

- Planification d’itinéraire complète

- Marketplace

- Flux de photos sur l’accueil

- Compte/profil complexe

- Tableau de bord rempli de KPI

- Analytics externe

- Crash reporting externe contenant des données personnelles ou de
  localisation

- Mise à jour imposant le Play Store

# 8. Compatibilité V1 → V2

Le composant carte, le statut de tracking, les réglages de diagnostic et
le moteur de synchronisation sont conservés. La V2 ajoute des vues
autour du noyau existant, elle ne remplace pas le noyau.
