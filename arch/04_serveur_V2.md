**04 — Serveur — V2**

*Sécurité familiale, agrégations, observabilité et outils
d’exploitation*

*Projet : suivi d’un voyage à vélo pendant 1 an — Android → serveur →
site familial*

# 1. Objectif

La V2 enrichit le serveur sans modifier le contrat de tracking. Les
nouvelles capacités concernent surtout l’accès familial, les
statistiques, les alertes et l’exploitation à distance.

Elle durcit le déploiement sur VPS et domaine personnel sans migrer par
défaut vers une plateforme GAFAM managée.

# 2. Nouveaux domaines

> auth-family\
> trip-sharing\
> location-ingest\
> location-query\
> trip-stats\
> device-health\
> alerts\
> admin

# 3. Comptes et partage

- Compte propriétaire/admin.

- Comptes famille optionnels.

- Lien privé révocable optionnel.

- Rôles : owner / viewer.

- Possibilité future de publier une version retardée ou moins précise du
  trajet.

# 4. Device health

- lastSeenAt

- lastLocationAt

- batteryPercent

- pendingCount

- appVersion

- lastSyncAt

# 5. Agrégations

- Distance du jour.

- Distance totale.

- Points par jour.

- Durée estimée de déplacement.

- Altitude cumulée seulement après validation d’une méthode fiable.

# 6. Données cartographiques optimisées

Conserver tous les points bruts, mais exposer éventuellement des routes
simplifiées pour les grandes périodes. Les endpoints de détail restent
disponibles pour un jour précis.

# 7. Alertes

- Aucun contact appareil depuis X heures.

- Aucune position depuis X heures.

- Batterie faible si l’information est disponible.

- Backlog important signalé par heartbeat.

# 8. Observabilité

- Logs JSON structurés.

- Métriques : taux d’erreur, latency, points/min, dernier point reçu.

- Healthcheck API et DB.

- Alerting externe pour indisponibilité.

Les outils d’observabilité doivent être auto-hébergés ou choisis pour ne
pas recevoir de coordonnées précises. Les métriques autorisées par
défaut sont techniques et agrégées : erreurs, latence, âge du dernier
point, état des backups.

La configuration d’observabilité, alertes, sessions, comptes famille et
backups reste injectée par environnement. Le dépôt public ne stocke que
des exemples.

# 9. Évolution de base

> users\
> trip_members\
> share_links\
> device_status\
> alerts\
> (optional) daily_trip_stats

# 10. Compatibilité

L’endpoint POST /api/v1/locations/batch reste inchangé. Les nouveaux
services sont additifs. L’application Android POC peut donc continuer à
fonctionner contre le serveur V2.

# 11. Limites explicites

Ne pas ajouter de pipeline data, entrepôt analytique, service de BI,
Firebase, Google Cloud, AWS managé, Azure managé ou monitoring SaaS tant
que le besoin réel n’a pas été observé. La V2 reste un monolithe
modulaire exploité sur VPS.

Ne pas publier de dump de production, export GPS, fichier de backup,
token famille ou configuration réelle dans GitHub, même temporairement.
