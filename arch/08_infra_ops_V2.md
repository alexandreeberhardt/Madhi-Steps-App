**08 — Infrastructure & opérations — V2**

*Surveillance proactive, staging, alertes et plan de reprise*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. Objectif

La V2 rend l’exploitation suffisamment robuste pour un voyage d’un an :
détection des pannes, restauration claire, changements contrôlés et
visibilité à distance.

Elle reste centrée sur le VPS et le domaine personnel. La robustesse se
gagne par des procédures, sauvegardes, alertes et tests de restauration,
pas par une migration automatique vers une plateforme cloud complexe.

# 2. Environnements

- Development.

- Staging proche de la production.

- Production.

# 3. CI/CD

> push → lint/tests → build → staging → smoke tests → production

# 4. Monitoring

- Uptime API et web.

- CPU/mémoire/disque.

- Connexions DB.

- Taux 5xx.

- Latence API.

- Âge de la dernière position reçue.

- Âge du dernier heartbeat.

# 5. Alertes

- API down.

- DB inaccessible.

- Disque presque plein.

- Backup en échec.

- Aucune position pendant une durée configurable.

Les alertes peuvent sortir du VPS, mais elles ne doivent pas contenir de
coordonnées précises. Un message du type “aucune position depuis X
heures” suffit.

# 6. Sauvegarde et reprise

- Backups quotidiens + hebdomadaires + mensuels selon budget.

- Stockage externe chiffré.

- Procédure de restauration écrite.

- Exercice de restauration périodique.

- Objectifs RPO/RTO explicités.

# 7. Sécurité opérationnelle

- SSH par clé.

- Pare-feu minimal.

- Mises à jour de sécurité.

- Rotation des tokens appareil si compromis.

- Accès admin séparé de l’accès public.

- Pas de stockage de secrets dans le dépôt.

- Sauvegardes exportées hors VPS chiffrées.

- Rotation documentée des secrets si une valeur est accidentellement
  publiée dans GitHub.

# 8. Compatibilité POC

La V2 peut reprendre exactement les mêmes conteneurs et services. On
ajoute staging, observabilité, alerting et automatisation autour de
l’architecture POC plutôt que de migrer vers Kubernetes ou des
microservices.

# 9. Limites explicites

Ne pas introduire Kubernetes, data warehouse, service d’analytics, outil
de session replay, CDN pour données privées ou monitoring SaaS contenant
des payloads de localisation sans incident réel qui le justifie.

Ne pas utiliser GitHub public comme stockage d’artefacts privés :
backups, exports GPS, APK release signés, captures contenant des liens
privés et fichiers d’environnement réels restent hors dépôt.
