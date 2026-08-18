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
> │ Voyage ⚙ │\
> ├─────────────────────────────┤\
> │ │\
> │ │\
> │ CARTE │\
> │ trajet + ● │\
> │ │\
> │ │\
> │ │\
> ├─────────────────────────────┤\
> │ ● Suivi actif │\
> │ Dernière position : 3 min │\
> │ │\
> │ \[ Recentrer \] │\
> └─────────────────────────────┘

La carte occupe idéalement 75 à 85 % de la hauteur. Le bandeau inférieur
reste compact et ne montre que l’état du suivi et l’ancienneté de la
dernière position.

| **Élément visible** | **Pourquoi il reste** |
|----|----|
| Carte | But principal : voir où le téléphone se situe et le trajet récent. |
| Marqueur actuel | Indispensable. |
| Polyline du trajet récent | Donne immédiatement le contexte. |
| Statut “Suivi actif / problème” | Indique si la fonction critique fonctionne. |
| Dernière position reçue | Évite de confondre dernière position connue et temps réel. |
| Recentrer | Action cartographique essentielle. |
| Réglages | Accès discret aux fonctions secondaires. |

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

La fréquence de localisation se règle ici, pas sur l’écran principal.
Utiliser une liste courte de valeurs compréhensibles : 2, 5, 10, 15 ou
30 minutes. La valeur par défaut reste 5 minutes.

Le bouton “Désactiver le tracking” doit être explicite : il arrête la
collecte GPS, mais ne supprime pas les positions déjà enregistrées ni les
points en attente de synchronisation.

# 6. Onboarding

- Écran 1 : expliquer le suivi en une phrase.

- Écran 2 : demander la localisation.

- Écran 3 : demander l’autorisation arrière-plan.

- Écran 4 : vérifier les restrictions batterie.

- Écran 5 : test de position + test serveur, puis ouverture de la carte.

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
