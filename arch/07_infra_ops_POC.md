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

**État réel au 2 septembre 2026.** Cette section est une cible, et deux de ses
quatre lignes ne sont pas atteintes. Le dire ici plutôt que le laisser
découvrir le jour où la machine est perdue.

| Ligne | État |
|---|---|
| Backup quotidien | **Fait.** `tools/backup/madhi-backup.sh`, timer systemd, dump vérifié relisible avant rotation. |
| Rétention | **Fait.** 30 copies, rotation triée par nom, jamais avant qu'une sauvegarde valide soit écrite. |
| Copie hors du serveur principal | **Non fait.** Les dumps ne quittent pas le VPS. Perdre la machine, c'est aujourd'hui perdre la base **et** ses sauvegardes du même coup. Un `rsync` manuel est documenté dans `tools/backup/README.md`, et `arch/20` §7 le porte comme tâche avant le départ. |
| Chiffrement | **Non fait.** Les dumps sont en clair, protégés par les droits du système seuls : répertoire en 0700, fichiers en 0600, posés par le script. Un dump est la trace complète des déplacements d'une personne pendant un an ; ces droits suffisent sur le VPS, ils ne suffiront pas le jour où une copie en sortira. |
| Test de restauration | **Non fait.** La procédure est écrite et vérifiable en une commande (`tools/backup/README.md`, « Restaurer »), mais elle n'a jamais été exécutée. Une sauvegarde non restaurée n'est pas une sauvegarde, c'est une hypothèse. |

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

- Restauration DB testée. *Non atteint : procédure écrite, jamais exécutée
  (§5).*

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
