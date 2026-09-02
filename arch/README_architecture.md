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
être modifiés à la main. Ils ne couvrent que 00 à 12, et rien ne les
régénère automatiquement : un `.docx` plus ancien que son `.md` est un
export périmé, pas une seconde version.

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

# Documents écrits en cours de réalisation

Les documents 00 à 12 disent ce que le système doit faire, et ils ont été
écrits avant le code. Ceux-ci sont venus après, chacun pour répondre à une
question que la réalisation a posée. Ils ne remplacent pas les cahiers : en cas
de divergence, ce sont 00 à 12 qui font foi.

- 13_contrat_api_android_v1.md — Payloads exacts entre l’application et le
  serveur, que 00 §5-§6 et 03 §4-§10 ne détaillaient pas.

- 14_protocole_test_terrain.md — Les six tests T1 à T6 sur les téléphones
  réels, et le critère de sortie du projet.

- 15_journal_tests_terrain.md — Ce que chaque session de test a réellement
  montré, session par session. Un journal, pas une synthèse.

- 16_lecons_terrain.md — Ce que le terrain a appris et qui vaut au-delà de ce
  projet.

- 17_plan_implementation_site_poc.md — Le site familial tel qu’il est
  implémenté, et les pièges du contrat de lecture.

- 18_carte_embarquee_v1.md — La carte de l’application : état courant,
  décisions techniques et ce qui est vérifié.

- 19_relecture_textes.md — Relecture de tous les textes visibles par la
  voyageuse et par la famille.

- 20_depart.md — La procédure du départ, à suivre une fois, dans l’ordre.

# Décisions d’architecture

`adr/` contient les décisions tranchées en cours de route, quand une question
technique n’était pas déjà écrite ou quand deux sources se contredisaient.
Format : contexte, options, décision, conséquences. `adr/README.md` en tient
l’index et le statut.

Un ADR n’annule jamais un document d’architecture. S’il diverge, c’est le
document qui fait foi et l’ADR doit être corrigé — ou, si la décision de l’ADR
est la bonne, c’est le document qu’il faut mettre à jour. Les deux ont été
faits.
