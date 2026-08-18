**README — Dossier d’architecture**

*Ordre de lecture et stratégie de réalisation*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# Documents

- 00_architecture_maitre.md — Contrats communs et règles de
  compatibilité.

- 01_android_POC.md — Minimum Android à prouver.

- 02_android_V2.md — Durcissement Android pour le voyage réel.

- 03_serveur_POC.md — API et stockage minimum.

- 04_serveur_V2.md — Sécurité, stats, alertes et admin.

- 05_site_POC.md — Carte familiale minimum.

- 06_site_V2.md — Historique, statistiques et partage.

- 07_infra_ops_POC.md — Déploiement minimal sûr.

- 08_infra_ops_V2.md — Exploitation robuste sur un an.

Les fichiers Markdown sont la source de vérité. Les `.docx`, s’ils sont
conservés, sont des exports générés depuis les `.md` et ne doivent pas
être modifiés à la main.

# Ordre de mise en œuvre

1\. Lire 00_architecture_maitre.

2\. Construire 03_serveur_POC.

3\. Construire 01_android_POC.

4\. Construire 05_site_POC.

5\. Faire tests terrain et corriger le tracking.

6\. Finaliser 07_infra_ops_POC.

7\. Passer progressivement aux V2, en priorité Android puis serveur,
site et ops.

# Règle de décision

Une fonctionnalité V2 n’est introduite que lorsqu’elle répond à un
risque observé, un besoin réel de la famille ou une exigence
d’exploitation. Le POC doit rester volontairement petit et testable.

# Contraintes de contexte

- L’application Android sera installée par APK par la voyageuse, hors
  Play Store. Le POC ne doit donc pas supposer de distribution Google
  Play, de mise à jour automatique via store, ni de service Firebase
  obligatoire.

- Le serveur et le site seront hébergés sur un VPS contrôlé par le
  propriétaire du projet, derrière un nom de domaine qui lui appartient.
  L’architecture cible par défaut est donc un déploiement simple sur
  VPS, pas une plateforme managée complexe.

- Ne pas partager les données de localisation avec les GAFAM est un
  objectif produit et technique. Les choix de cartes, logs, analytics,
  crash reporting, monitoring et notifications doivent être évalués avec
  cette contrainte.

- Toute exception à cette règle doit être explicite : quelle donnée
  sort, vers quel fournisseur, pourquoi c’est nécessaire, et quelle
  alternative plus sobre a été envisagée.

- Le dépôt GitHub sera public. Aucun secret, token appareil, clé de
  signature APK, mot de passe PostgreSQL, URL privée d’administration ou
  identifiant personnel ne doit être commité. Les valeurs réelles vivent
  dans des fichiers `.env` locaux ou sur le VPS ; seuls des exemples
  sans secret sont versionnés.

# Documents de design UI/UX

Ces quatre documents complètent les cahiers techniques avec une règle
commune : carte dominante, actions minimales visibles, fonctions
secondaires dans les réglages, suppression de tout élément non utilisé.

- 09_design_app_V1.md — Application Android V1 — carte-first, tracking
  et résolution des problèmes.

- 10_design_app_V2.md — Application Android V2 — évolution minimale
  avec journée/historique sans surcharge.

- 11_design_site_V1.md — Site familial V1 — carte presque plein écran
  et dernière position.

- 12_design_site_V2.md — Site familial V2 — historique et contexte via
  panneaux temporaires.
