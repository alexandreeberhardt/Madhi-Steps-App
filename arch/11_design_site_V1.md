**Design du site familial — V1**

*POC/POW — une carte, une position, presque rien d’autre*

# 1. Intention

Le site familial doit répondre immédiatement à deux questions : “Où
est-elle ?” et “De quand date cette position ?”. Tout le design découle
de cela.

# 2. Page principale

> ┌──────────────────────────────────────────────┐\
> │ Voyage de \[Prénom\] ⚙ │\
> ├──────────────────────────────────────────────┤\
> │ │\
> │ │\
> │ CARTE │\
> │ trajet ─────── ● │\
> │ │\
> │ │\
> │ │\
> ├──────────────────────────────────────────────┤\
> │ Dernière position : il y a 7 min │\
> │ \[ Recentrer \] │\
> └──────────────────────────────────────────────┘

Sur desktop, la carte peut prendre toute la fenêtre moins un bandeau
supérieur très fin. Sur mobile, la carte reste plein écran avec un petit
panneau flottant en bas.

# 3. Éléments autorisés sur la page d’accueil

- Nom du voyage ou prénom

- Carte

- Trajet

- Dernière position

- Heure/ancienneté de la dernière mise à jour

- Bouton recentrer

- Accès réglages

# 4. Ce qui doit rester hors de la page d’accueil

- Statistiques détaillées

- Liste des jours

- Graphiques

- Informations techniques du téléphone

- État détaillé du serveur

- Compte utilisateur

- Historique des connexions

- Gestion des accès

- Paramètres de confidentialité

- Informations légales

- Scripts analytics ou marketing

- Widgets sociaux

# 5. Réglages / zone secondaire

> Réglages\
> \
> Affichage\
> Fond de carte\
> \
> Accès\
> Se déconnecter\
> \
> Informations\
> Dernière réception serveur\
> Confidentialité\
> \
> Administration (si autorisé)\
> État du téléphone\
> État du serveur

Même dans les réglages, supprimer tout élément qui ne sera pas
réellement utilisé par la famille.

# 6. Responsive

| **Contexte** | **Règle**                                                  |
|--------------|------------------------------------------------------------|
| Téléphone    | Carte plein écran, panneau inférieur compact.              |
| Tablette     | Carte plein écran, contrôles flottants.                    |
| Desktop      | Carte presque plein écran ; aucun sidebar permanent en V1. |

# 7. États spéciaux

| **Situation** | **Message** |
|----|----|
| Position récente | “Dernière position : il y a 4 min”. |
| Position ancienne | Bandeau discret orange “Aucune nouvelle position depuis 6 h”. |
| Aucune donnée | “Aucune position reçue pour le moment.” |
| Erreur serveur | Message simple sans détails techniques. |

# 8. Sobriété et confidentialité visuelle

Le site ne doit pas ressembler à un dashboard SaaS ou à une landing
page. Il doit ressembler à un outil familial privé : carte lisible,
statut clair, très peu de commandes.

Aucun Google Analytics, Tag Manager, pixel publicitaire, widget social
ou carte Google Maps. Les appels réseau visibles depuis le navigateur
doivent être compréhensibles et limités.
