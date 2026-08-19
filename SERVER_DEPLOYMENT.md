# Deploiement serveur actuel

## Etat

Le serveur accessible publiquement est disponible ici :

    https://madhi-server.alexeber.fr

L'API Android utilise cette base :

    https://madhi-server.alexeber.fr/api/v1

Le service systemd utilisateur qui tourne sur le VPS est :

    madhi-mock-server.service

Il expose le serveur localement sur :

    http://127.0.0.1:8110

Nginx sert de reverse proxy HTTPS entre `madhi-server.alexeber.fr` et
`127.0.0.1:8110`.

## Verification

Verifier que le service local tourne :

    systemctl --user is-active madhi-mock-server.service

Verifier l'etat public du serveur :

    curl https://madhi-server.alexeber.fr/_control/state

Reponse attendue quand le mock est vierge :

    {
      "activated": false,
      "devices": 0,
      "locations": 0,
      "batches": 0,
      "firstRecordedAt": null,
      "lastRecordedAt": null,
      "pendingForcedFailures": []
    }

Lire les logs :

    journalctl --user -u madhi-mock-server.service -n 50 --no-pager

Redemarrer le service :

    systemctl --user restart madhi-mock-server.service

## Activation

Code d'activation configure actuellement :

    MADHI-2026

L'activation est a usage unique dans le mock. Si le code est consomme pendant
un test, redemarrer `madhi-mock-server.service` remet le mock dans son etat
initial avec le meme code.

## Limite importante

Le serveur actuellement deploye est le serveur de simulation du depot, pas le
serveur reel persistant.

Il permet de tester l'application Android via le vrai domaine HTTPS, avec le
contrat API attendu, mais ses donnees sont stockees en memoire et disparaissent
au redemarrage du service.

Il ne faut pas le considerer comme le serveur de production final.
