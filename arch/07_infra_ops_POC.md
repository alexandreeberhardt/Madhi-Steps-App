**07 — Infrastructure & opérations — POC / POW**

*Déployer simplement, en HTTPS, avec sauvegarde et possibilité de
restaurer*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. But du POC

L’infrastructure POC doit être simple à comprendre et réparable par une
seule personne. La priorité est la continuité du tracking, pas la
sophistication cloud.

# 2. Topologie

> Internet → Reverse proxy HTTPS → Web + API → PostgreSQL

# 3. Déploiement

- VPS personnel comme cible par défaut.

- Docker Compose possible.

- Conteneurs séparés : reverse-proxy, backend, frontend, postgres si DB
  auto-hébergée.

- Variables d’environnement pour secrets.

- Aucun composant critique ne dépend d’un service GAFAM managé.

- Le dépôt GitHub public contient `.env.example`, mais jamais `.env`,
  `.env.production`, clés SSH, clés de backup, keystores Android,
  certificats privés ou dumps PostgreSQL.

# 4. DNS et HTTPS

- Nom de domaine stable appartenant au propriétaire du projet.

- Certificat TLS automatisé.

- Redirection HTTP vers HTTPS.

# 5. Sauvegarde

- Backup PostgreSQL quotidien.

- Copie hors du serveur principal, idéalement chiffrée.

- Rétention de plusieurs jours/semaines.

- Test de restauration avant le départ.

# 6. Monitoring minimum

- Healthcheck API.

- Disponibilité du site.

- Espace disque.

- Dernier backup réussi.

- Dernière position reçue visible manuellement.

- Monitoring sans fuite de coordonnées précises vers un tiers.

# 7. Environnements

- Local development.

- Production.

- Staging facultatif au POC. À ajouter seulement avant le départ, avant
  une migration risquée, ou si le déploiement production devient trop
  fragile à tester directement.

# 8. Critères d’acceptation

- Re-déploiement reproductible depuis un serveur vierge.

- Secrets absents du dépôt Git.

- `.gitignore` couvre les fichiers d’environnement, backups, dumps,
  keystores, certificats privés et artefacts de build sensibles, sans
  ignorer les migrations SQL versionnées.

- Restauration DB testée.

- HTTPS valide.

- Redémarrage serveur sans perte de données.

- Le site et l’API fonctionnent sur le vrai domaine du projet.

- Inspection des dépendances externes : pas de GAFAM pour analytics,
  crash reporting, cartes ou stockage des positions.

- Déploiement reproductible depuis le dépôt public avec les secrets
  fournis uniquement sur le VPS.

# 9. Préparation V2

- Dockerfiles reproductibles.

- Volumes et backups documentés.

- Configuration par environnement.

- Logs vers stdout/stderr structurés.

- Scripts de migration séparés du démarrage applicatif.

# 10. Ce qui est volontairement évité au POC

- Kubernetes.

- Services managés complexes.

- CDN public pour les données privées.

- Analytics marketing.

- Dépendance à Firebase, Google Cloud, AWS ou Azure pour le chemin
  critique du tracking.

- Commit de fichiers `.env` réels, dumps de production ou APK signés
  contenant des secrets de production.
