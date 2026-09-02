**Design de l’application Android — V1**

*POC/POW — interface minimale, lisible et centrée sur la carte*

# 1. Intention de design

La V1 ne doit pas ressembler à une application “riche”. Son rôle est de
confirmer que le suivi fonctionne, montrer la dernière position, et
permettre de corriger rapidement un problème. L’écran principal est donc
presque entièrement occupé par la carte.

- Carte = contenu principal.

- Un seul bouton principal visible si une action est nécessaire.

- Aucune statistique décorative sur l’accueil.

- Aucun flux social, journal, météo, galerie, profil détaillé ou menu
  complexe.

- Le diagnostic et les détails techniques sont déplacés dans Réglages.

# 2. Écran principal

> ┌─────────────────────────────┐\
> │ Voyage ⚙ Réglages │\
> ├─────────────────────────────┤\
> │ légende │\
> │ │\
> │ CARTE │\
> │ trajet + ● │\
> │ \[Recentrer\] │\
> │ échelle mention │\
> ├─────────────────────────────┤\
> │ Aujourd’hui 24 h 7 j Tout │\
> │ ● Suivi actif │\
> │ Dernière position : 3 min │\
> └─────────────────────────────┘

La carte occupe idéalement 75 à 85 % de la hauteur. Le bandeau inférieur
reste compact : le choix de la période, l’état du suivi, l’ancienneté de la
dernière position.

| **Élément visible** | **Pourquoi il reste** |
|----|----|
| Carte | But principal : voir où le téléphone se situe et le trajet récent. |
| Marqueur actuel | Indispensable. |
| Polyline du trajet récent | Donne immédiatement le contexte. |
| Statut “Suivi actif / problème” | Indique si la fonction critique fonctionne. |
| Dernière position reçue | Évite de confondre dernière position connue et temps réel. |
| Sélecteur de période | Un tracé sans étendue connue ne veut rien dire. Quatre valeurs, mêmes libellés que le site familial. |
| Échelle graphique | La seule chose qui dise si le tracé visible fait deux rues ou deux cents kilomètres. |
| Légende du code couleur | Bleu là où le serveur détient les points, orange là où ils ne sont que sur le téléphone. Se réduit à une ligne quand rien n’est en attente. |
| Mention légale des tuiles | Imposée par la licence du fournisseur de fond. |
| Recentrer | Action cartographique essentielle. Flottant sur la carte, et visible **uniquement** après un déplacement manuel. |
| Réglages | Accès discret aux fonctions secondaires. |

Les quatre lignes ajoutées après la première rédaction — période, échelle,
légende, mention légale — sont arrivées avec la carte (ADR-006). Le détail de
leur rendu est dans `arch/18_carte_embarquee_v1.md`.

# 3. Ce qui est retiré de l’accueil

- Distance du jour

- Altitude

- Vitesse

- Batterie en pourcentage

- Nombre de points en attente

- État détaillé GPS/réseau/serveur

- Historique complet

- Bouton de synchronisation forcée

- Version de l’application

- Informations sur l’appareil

Ces informations restent disponibles dans Réglages \> Diagnostic si
elles sont réellement utiles au dépannage.

Trois éléments de la carte échappent volontairement à cette règle, et il faut
dire pourquoi, sinon la prochaine relecture les retirera. L’échelle, la légende
et le sélecteur de période ne décrivent pas le voyage : ils décrivent **ce que
l’image montre**. Sans échelle, un tracé ne dit pas s’il fait deux rues ou deux
cents kilomètres ; sans légende, le code couleur ne se devine pas ; sans
période, on ne sait pas de quelle étendue on parle. Une statistique décorative
s’ajoute à la carte ; ces trois-là la rendent lisible.

# 4. États de l’écran principal

| **État** | **Affichage** |
|----|----|
| Normal | Petit indicateur vert + “Suivi actif”. |
| Pas de réseau | Indicateur orange + “Hors ligne — trajet sauvegardé sur le téléphone”. |
| Action nécessaire | Indicateur rouge + bouton “Corriger”. |
| Tracking arrêté | Message clair + bouton “Démarrer le suivi”. |

# 5. Réglages

> Réglages\
> \
> Suivi\
> État du tracking\
> Autorisations\
> Fréquence : 5 min\
> \
> Synchronisation\
> Dernier envoi\
> Points en attente\
> \
> Diagnostic\
> GPS / réseau / serveur\
> Tester maintenant\
> \
> Application\
> Version\
> Confidentialité\
> \
> \[ Désactiver le tracking \]

Les Réglages sont volontairement utilitaires. Pas de personnalisation
esthétique, pas de préférences qui ne changent rien au fonctionnement
réel.

La fréquence de localisation se règle ici, pas sur l’écran principal. Trois
paliers se choisissent d’un geste — 5, 30 et 60 minutes — et tout le reste
passe par « Autre », c’est-à-dire par une saisie délibérée, bornée à 1–1440
minutes. La valeur par défaut reste 5 minutes.

La liste fermée d’origine — 2, 5, 10, 15 ou 30 — visait à empêcher une erreur
de frappe qu’on ne pourrait pas corriger à distance pendant le voyage. La
saisie a été ouverte à la demande ; le garde-fou n’a pas disparu, ce sont les
bornes qui le portent désormais.

Le bouton “Désactiver le tracking” doit être explicite : il arrête la
collecte GPS, mais ne supprime pas les positions déjà enregistrées ni les
points en attente de synchronisation.

# 6. Onboarding

Six écrans, et non cinq : l’activation de l’appareil s’est intercalée en
quatrième position (ADR-004). Elle ne pouvait pas rester une note technique —
sans elle l’application n’a pas de token et n’envoie rien.

- Écran 1 : expliquer le suivi en une phrase.

- Écran 2 : demander la localisation.

- Écran 3 : demander l’autorisation arrière-plan.

- Écran 4 : saisir le code d’activation, et l’échanger contre le token
  appareil. L’erreur doit distinguer « code invalide » de « pas de réseau ».

- Écran 5 : vérifier les restrictions batterie. C’est l’écran le plus important
  de l’application (ADR-007 §3.4) : il liste les réglages constructeur avec
  leur chemin exact, l’appareil détecté. C’est le seul dont l’omission ne se
  voit pas le jour même.

- Écran 6 : test de position + test serveur, puis ouverture de la carte.

# 7. Installation par APK

L’installation hors Play Store fait partie de l’expérience réelle. Elle
doit être prévue comme un parcours court, pas comme une note technique.

Points à vérifier avec la voyageuse :

- APK installé sur le téléphone réel.

- Autorisations localisation et arrière-plan accordées.

- Restrictions batterie identifiées.

- Mise à jour par nouvel APK testée.

- Message clair si une permission bloque le suivi.

# 8. Règle de décision

Toute nouvelle information doit répondre à la question : “Est-ce que la
voyageuse en a besoin sur l’écran principal pendant son trajet ?”. Si la
réponse est non, elle va dans les réglages. Si elle n’est utile ni à la
voyageuse ni au dépannage, elle est supprimée.

Un écran ou SDK qui existe seulement pour mesurer l’usage, faire joli ou
imiter une app grand public est supprimé.
