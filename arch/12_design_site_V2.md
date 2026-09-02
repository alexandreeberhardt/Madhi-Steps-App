**Design du site familial — V2**

*Évolution minimale — historique et contexte sans transformer le site en
dashboard*

# 1. Principe

La V2 garde exactement le même centre de gravité : la carte. Les
nouvelles fonctions apparaissent sous forme de panneaux temporaires ou
dans les réglages, jamais sous forme d’un tableau de bord permanent.

# 2. Page principale V2

> ┌──────────────────────────────────────────────┐\
> │ Voyage de \[Prénom\] Aujourd’hui ▾│\
> ├──────────────────────────────────────────────┤\
> │ │\
> │ CARTE │\
> │ trajet sélectionné + ● │\
> │ │\
> │ │\
> ├──────────────────────────────────────────────┤\
> │ 67 km aujourd’hui · dernière pos. il y a 3 m │\
> │ \[ Recentrer \] \[ Choisir une journée \] │\
> └──────────────────────────────────────────────┘

La nouveauté principale est le sélecteur temporel. Il permet de
consulter un autre jour sans quitter la carte.

# 3. Historique sous forme de panneau

> Choisir une journée\
> \
> Aujourd’hui 67 km\
> 17 août 91 km\
> 16 août Repos\
> 15 août 73 km\
> \
> \[ Date personnalisée \]

Le panneau se ferme dès qu’une journée est sélectionnée. Pas de page
“analytics” séparée si la famille ne l’utilise pas.

# 4. Informations secondaires utiles

- Distance de la journée sélectionnée

- Éventuellement distance totale du voyage

- Dernier check-in “Je vais bien” si cette fonction existe côté
  application

- Alerte si aucune nouvelle position depuis une durée anormalement
  longue

Ces informations doivent tenir dans un panneau compact et ne jamais
concurrencer visuellement la carte.

# 5. Réglages V2

> Réglages\
> \
> Affichage\
> Type de carte\
> Afficher/masquer le trajet\
> \
> Historique\
> Aller à une date\
> \
> Partage & confidentialité\
> Accès famille\
> Retard de position publique (si utilisé)\
> \
> Administration\
> État de l’appareil\
> Dernière synchro\
> Alertes\
> \
> Compte\
> Déconnexion

# 6. Fonctions retirées même en V2

- Dashboard multi-cartes

- Graphiques de vitesse complexes

- Leaderboard

- Commentaires publics

- Chat

- Fil de photos automatique

- Météo détaillée

- Planificateur d’itinéraire

- Notifications marketing

- Personnalisation poussée du thème

- Sections “À découvrir” ou contenu éditorial

- Analytics tiers

- Session replay

- Scripts sociaux

- Carte Google Maps

# 7. Architecture visuelle évolutive

| **Composant** | **V1** | **V2** |
|----|----|----|
| Carte | Plein écran | Plein écran, inchangée |
| Statut dernière position | Oui | Oui |
| Sélecteur de période | **Oui, arrivé en V1** | Oui |
| Sélecteur de date (une journée précise) | Non | Oui, compact |
| Historique | Non | Panneau temporaire |
| Stats | Aucune | 1–2 chiffres max |
| Réglages | Minimal | Un peu enrichis |
| Admin | Caché | Toujours caché des utilisateurs normaux |

# 8. Règle de sobriété

Une fonction n’entre dans la V2 que si elle répond à un usage constaté
pendant la V1. Une idée “sympa” mais non utilisée ne doit pas devenir un
écran, un bouton ou un onglet.

Une intégration externe n’entre dans la V2 que si elle respecte
l’objectif de confidentialité ou si son exception est écrite noir sur
blanc.
