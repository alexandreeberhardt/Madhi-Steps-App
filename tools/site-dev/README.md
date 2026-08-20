# Outils de développement du site

Deux outils, tous deux en bibliothèque standard, sans dépendance.

## `serve.py` — tenir le rôle de nginx

    python3 tools/site-dev/serve.py

Sert `site/` sur `http://127.0.0.1:8090/f/dev/` et répond aux appels API
relatifs, exactement là où nginx répondra en production.

**Ne jamais l'exposer sur Internet** : ni chiffrement, ni mot de passe.

### Scénarios

Sans `--proxy`, les réponses sont fabriquées. Chaque scénario produit un des
états de `arch/17_plan_implementation_site_poc.md` §6, sans avoir à vider une
base ni à arrêter un serveur :

| Scénario | Ce qu'il montre |
|---|---|
| `nominal` | une journée de trajet, position récente |
| `ancien` | dernière position vieille de 6 h |
| `hors-ligne` | dernière position vieille de 3 jours, reçue avec 2 h de retard |
| `avant-depart` | `startedAt` nul : aucune position précise ne doit s'afficher |
| `aucune-position` | `latest-location` renvoie `null` avec un code 200 |
| `termine` | `endedAt` non nul |
| `historique-vide` | une position récente, aucun point sur la période |
| `tronque` | une réponse de la taille exacte du plafond |
| `panne` | 502 |
| `interdit` | 403, comme un token périmé |
| `voyage-inconnu` | 404 |
| `muet` | ne répond pas : éprouve le délai maximum du client |

### Contre le vrai serveur

    PUBLIC_READ_TOKEN=... python3 tools/site-dev/serve.py \
      --proxy https://madhi-server.alexeber.fr

Le token est posé par l'outil, comme nginx le fera. Si le site fonctionne dans
ce mode, c'est qu'il n'a besoin d'aucun secret.

## `verifier.mjs` — les vérifications sans navigateur

    node tools/site-dev/verifier.mjs

Porte sur ce qui se voit mal à l'œil nu : l'ordre d'évaluation des huit états,
les bornes de période et leur écrêtage au départ, la détection de troncature,
et l'absence de toute formulation suggérant du temps réel.
