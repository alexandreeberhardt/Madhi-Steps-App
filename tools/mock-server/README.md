# Serveur de simulation

Outil de développement. Il implémente `arch/13_contrat_api_android_v1.md`
pour permettre de valider l'application Android avant que le serveur réel
existe.

**Ne jamais l'exposer sur Internet.** Aucun chiffrement, aucune persistance
durable, aucun contrôle d'accès sérieux. Les données disparaissent à l'arrêt.

Il sert aussi de **spécification exécutable** : ce qu'il fait est ce que le
serveur POC devra reproduire, en particulier l'idempotence.

## Lancer

    python3 tools/mock-server/server.py

Aucune dépendance, bibliothèque standard Python uniquement. Le code
d'activation est affiché au démarrage.

Options : `--port` (8080), `--code` (aléatoire), `--max-batch` (200, au-delà
il répond 413).

## Connecter un téléphone réel

Le plus simple passe par USB, sans toucher à la configuration réseau :

    adb reverse tcp:8080 tcp:8080

Le téléphone voit alors le serveur sur `http://localhost:8080`, qui est déjà
autorisé en clair par la configuration réseau du build debug. Aucune adresse
IP à renseigner, rien à changer quand on change de réseau wifi.

Dans `local.properties` :

    madhi.api.baseUrl.debug=http://localhost:8080/api/v1

Puis reconstruire et réinstaller l'APK debug.

> Pour une connexion sans fil, il faudrait ajouter l'adresse IP locale de la
> machine dans `app/src/debug/res/xml/network_security_config.xml`. `adb
> reverse` évite ce détour et fonctionne quel que soit le réseau.

## Endpoints

Conformes au contrat :

    POST /api/v1/devices/activate
    POST /api/v1/locations/batch

Deux endpoints de contrôle, absents du contrat et réservés aux tests :

    GET  /_control/state                      état courant du serveur
    POST /_control/fail?code=503&times=2      injecte des erreurs

L'injection d'erreurs sert à éprouver la gestion d'erreur du client sans
avoir à débrancher quoi que ce soit :

    # vérifier que les points ne sont pas perdus sur erreur serveur
    curl -X POST "http://localhost:8080/_control/fail?code=503&times=3"

    # vérifier que le client réduit la taille de ses lots
    curl -X POST "http://localhost:8080/_control/fail?code=413&times=1"

    # vérifier que le client arrête les tentatives agressives
    curl -X POST "http://localhost:8080/_control/fail?code=401&times=1"

## Idempotence

C'est le comportement le plus important à reproduire côté serveur.

Un identifiant déjà connu revient dans `duplicates`, jamais dans `rejected`,
et n'est pas réécrit. Un lot rejoué après une réponse perdue aboutit donc
sans créer de doublon :

    premier envoi  → {"accepted": ["aaa","bbb"], "duplicates": [],           "rejected": []}
    même lot rejoué → {"accepted": [],           "duplicates": ["aaa","bbb"], "rejected": []}

Le client traite `accepted` et `duplicates` de la même façon : dans les deux
cas le serveur détient le point.
