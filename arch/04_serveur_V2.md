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

# 1 bis. Ce qui est déjà là, et ce qui ne l’est pas

*Ajouté le 2 septembre 2026. Rien du découpage en domaines du §2 n’existe : le
serveur est resté le monolithe du POC. Mais trois besoins de ce document ont
été couverts ailleurs, et les chercher ici ferait conclure à tort qu’ils
manquent.*

| Besoin | État |
|---|---|
| §3 Comptes et partage | **Non fait, et remplacé.** L’accès familial tient à un segment secret dans l’URL et à un mot de passe partagé, posés par nginx (`arch/17` §2.4). Ni comptes, ni rôles, ni sessions. Révocable en changeant les deux valeurs. |
| §4 Device health | **Non fait.** `devices.last_seen_at` et `devices.app_version` sont écrits — le premier à chaque requête authentifiée, le second à l’activation — mais aucun endpoint ne les expose et il n’y a pas de heartbeat (`arch/02` §2). |
| §5 Agrégations | **Non fait.** Aucune distance n’est calculée nulle part, ni serveur ni client. |
| §6 Routes simplifiées pour les grandes périodes | **Fait, en avance.** C’est l’échantillonnage par pas de temps de `GET /locations`, ajouté le 26 août 2026 pour corriger une troncature silencieuse (`arch/17` §4.1). Les points bruts restent tous en base, comme ce paragraphe l’exigeait. |
| §7 Alertes | **Fait hors du serveur.** `tools/monitoring/madhi-check.sh` couvre l’absence de position, l’API et le site injoignables, le disque et l’âge du dernier backup. La batterie faible manque, faute de heartbeat. Aucune alerte ne contient de coordonnée : la sonde interroge `/status`, jamais `/latest-location`. |
| §8 Observabilité | **En partie.** Logs JSON structurés avec `request_id`, `device_id` et `trip_id` : fait. Healthcheck API et base : fait, `/health` touche la base. Alerting externe d’indisponibilité : fait par sonde externe (`MADHI_HEARTBEAT_URL`), parce qu’un VPS éteint n’alerte pas lui-même. Métriques de taux d’erreur, de latence et de points par minute : non faites. |
| §9 Évolution de base | **Non fait**, à une table près : `activation_codes`, qui appartient au POC (`arch/03` §4). |

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
