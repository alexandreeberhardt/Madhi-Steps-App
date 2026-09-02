**Design du site familial — V1**

*POC/POW — une carte, une position, presque rien d’autre*

# 1. Intention

Le site familial doit répondre immédiatement à deux questions : “Où
est-elle ?” et “De quand date cette position ?”. Tout le design découle
de cela.

# 2. Page principale

> ┌──────────────────────────────────────────────┐\
> │ Voyage de \[Prénom\] │\
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
> │ \[Auj.\]\[24 h\]\[7 j\]\[30 j\]\[Tout\] \[ Recentrer \] │\
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

- Sélecteur de période. *`arch/12` §7 le rangeait en V2. Il est arrivé en V1 :
  une carte sans étendue connue ne répond pas à « où est-elle ? », elle y
  répond à moitié. Cinq valeurs, cinq boutons, aucun panneau.*

- Une ligne de couverture, quand la période affichée n'est pas celle demandée
  — bornée au départ du voyage, ou échantillonnée par le serveur.

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

**Cet écran n'existe pas, et c'est volontaire.** La règle de sobriété
ci-dessous s'est appliquée à lui-même : chacune de ses lignes s'est révélée
inutile ou déplacée, et il ne restait rien à afficher.

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

Ligne par ligne, ce qu'elles sont devenues :

| Ligne prévue | Ce qui s'est passé |
|---|---|
| Fond de carte | Un seul fond, encapsulé dans `site/components/map.js`. Choisir entre deux fournisseurs n'est pas une décision de la famille. |
| Se déconnecter | Il n'y a pas de session à fermer : l'accès tient au segment secret de l'URL et au mot de passe familial posés par nginx. Se déconnecter serait fermer l'onglet. |
| Dernière réception serveur | Remontée sur l'accueil, dans le bloc « dernière position » : c'est l'écart entre capture et réception qui distingue « elle n'enregistre plus » de « elle n'arrive plus à envoyer ». Cachée, elle ne servait à personne. |
| Confidentialité | Rien à régler : aucune requête ne sort du domaine du projet sauf les tuiles. Un écran qui l'aurait dit aurait été un texte, pas un réglage. |
| État du téléphone / du serveur | `arch/12` §5 les range en V2, derrière un accès administrateur. L'accueil dit déjà ce que la famille doit savoir : position ancienne, appareil silencieux, serveur injoignable. |

Si un réglage devait revenir un jour, la règle reste celle-ci : supprimer tout
élément qui ne sera pas réellement utilisé par la famille.

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
