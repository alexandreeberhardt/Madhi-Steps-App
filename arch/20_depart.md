# 20 — Le départ

*Ce qu'il reste à faire pour que le téléphone parte, et dans quel ordre.*

Ce document ne décrit ni l'architecture ni un choix technique. C'est une
procédure, écrite pour être suivie une fois, à une date précise, par quelqu'un
qui n'aura pas le temps de relire le reste du dossier.

Deux acteurs, deux machines, et ils ne sont pas au même endroit : Madhi
installe l'application sur le Redmi, qu'il a en main ; Alexandre prépare le
serveur, qu'il est le seul à pouvoir joindre. L'ordre compte, parce que
l'activation ne réussit que si le serveur a été préparé **avant**.

# 1. Ce qui n'est pas résolu, et qui ne le sera pas d'ici le départ

Il faut le dire avant la procédure, sinon la procédure donne l'illusion que
tout est prêt.

**Le critère de sortie du projet n'est pas atteint.** `arch/14` §7 exige T1 à
T4 passés sur le Redmi Note 11 réel. Aucun ne l'a été. Ce qui est éprouvé l'a
été sur le OnePlus, qui ne reproduit ni la surcouche MIUI ni la pression
mémoire à 4 Go.

**Le défaut connu est celui qui coûte le plus cher.** L'application ne repart
pas toute seule après un redémarrage du téléphone : observé le 23 août 2026 sur
OxygenOS, vingt-quatre heures de suivi perdues
(`arch/15_journal_tests_terrain.md`, session 5). MIUI est plus hostile encore.
Le réglage « Démarrage auto en arrière-plan » de l'étape 4 de l'onboarding est
la seule chose qui s'y oppose, et **il n'a jamais été vérifié sur le paquet
`com.madhi.tracker`** — la piste ouverte depuis le 26 août est qu'il s'agit
d'une case à cocher plutôt que d'un bug.

Conséquence pratique, à dire à Madhi en clair plutôt qu'à espérer : **après
chaque redémarrage du téléphone, ouvrir l'application une fois.** C'est aussi
vrai après chaque mise à jour de l'APK — Android force-stoppe le paquet et rien
ne le relance.

**Ce que le départ ne change pas.** La consommation écran éteint n'est toujours
pas mesurée, et l'anomalie de cadence à 10 s n'est pas élucidée. Ni l'une ni
l'autre n'empêche de partir ; les deux se liront dans les données du voyage.

# 2. Le serveur, avant que Madhi installe

Tout se passe sur le VPS, dans le répertoire du dépôt. Rien de ce qui suit ne
supprime de position.

## 2.1 Sauvegarder d'abord

Une sauvegarde valide avant de toucher au `.env`, parce que les étapes
suivantes redémarrent l'API :

    sudo systemctl start madhi-backup.service
    sudo journalctl -u madhi-backup.service --no-pager -n 30
    ls -l /var/backups/madhi

## 2.2 Faire tourner le secret des jetons d'appareil

`DEVICE_TOKEN_HASH_SECRET` a été exposé. Sa rotation impose une réactivation du
téléphone — c'est précisément ce qui va arriver de toute façon, donc c'est le
seul moment du projet où elle est gratuite. La faire maintenant, ou ne jamais
la faire.

Dans `server/.env` :

    DEVICE_TOKEN_HASH_SECRET=<openssl rand -hex 32>

Ce que la rotation casse, et c'est voulu : le jeton du OnePlus devient invalide.
S'il tourne encore, il accumulera des positions en attente qui ne partiront
jamais. **Arrêter le suivi sur le OnePlus** avant de redémarrer l'API.

## 2.3 Poser un nouveau code d'activation

L'ancien est consommé — un code est à usage unique. Dans `server/.env` :

    INITIAL_ACTIVATION_CODE=XXXX-XXXX
    ACTIVATION_CODE_TTL_MINUTES=10080

Trois choses à savoir, dont deux se payent en « code invalide » sans autre
explication :

- **Le format est strict** : quatre caractères alphanumériques, un tiret,
  quatre caractères. `^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$`, rien d'autre.
- **Le code est sensible à la casse.** Le serveur hache la chaîne telle quelle,
  sans la normaliser, et le clavier de l'application propose les majuscules par
  défaut. Écrire le code **en majuscules**, et éviter `0`/`O` et `1`/`I`/`L` :
  ils se ressemblent sur un écran de téléphone et le code sera recopié à la
  main, à distance, par quelqu'un d'autre.
- **La durée de vie par défaut est d'une heure.** Elle suffit quand on active
  le téléphone qu'on a en main ; elle ne suffit pas quand on envoie un code à
  quelqu'un qui installera peut-être demain. Sept jours (`10080`) laisse de la
  marge. La minuterie ne repart qu'au démarrage de l'API, et seulement tant que
  le code n'a pas été utilisé.

Puis redémarrer, ce qui déclenche le semis :

    docker compose -f server/docker-compose.yml up -d
    curl -s https://madhi-server.alexeber.fr/health

Transmettre le code à Madhi **par un autre canal que l'APK** : c'est le seul
secret de toute la chaîne côté téléphone.

# 3. L'APK

Construit depuis le poste d'Alexandre, avec la clé de signature qui n'est pas
dans le dépôt :

    ./gradlew check assembleRelease

L'APK sort dans `app/build/outputs/apk/release/app-release.apk`. Il porte
`versionCode 3` / `versionName 1.0.1` — la première version dont on sache
qu'elle est celle du voyage.

**Conserver `app/build/outputs/mapping/release/mapping.txt`** hors du répertoire
de build, sous un nom qui porte la version. R8 renomme tout : une pile d'appel
remontée du voyage sans son `mapping.txt` est illisible, et un `./gradlew clean`
l'efface sans prévenir.

Le fichier fait environ 3,4 Mo. N'importe quel canal qui ne recompresse pas
convient. À vérifier avant de l'envoyer, et à faire vérifier par Madhi après
réception :

    shasum -a 256 app/build/outputs/apk/release/app-release.apk

# 4. Madhi installe

La notice qu'il suit est autonome et ne demande aucune connaissance du projet.
L'essentiel du travail est fait par l'application elle-même : l'onboarding
enchaîne six écrans — accueil, localisation, localisation en arrière-plan,
activation, énergie, vérification — et affiche les chemins exacts des réglages
MIUI, appareil détecté (`VendorSetupGuidance`).

Ce qui ne peut pas être automatisé et qu'il faut lui dire :

- Autoriser l'installation depuis une source inconnue, une seule fois.
- Ne pas sauter l'écran « Économie d'énergie ». C'est le seul écran dont
  l'omission ne se voit pas le jour même.
- Terminer sur l'écran « Vérification » : il enregistre une position et tente
  un envoi. Deux lignes vertes, et la chaîne est prouvée de bout en bout.

# 5. Vérifier, côté serveur

Une fois Madhi passé par l'activation, sans attendre le départ :

    curl -H "Authorization: Bearer <PUBLIC_READ_TOKEN>" \
      https://madhi-server.alexeber.fr/api/v1/trips/<INITIAL_TRIP_ID>/status

L'appareil doit y apparaître, avec une dernière position récente. Si rien
n'arrive, le diagnostic ne se fait pas ici : c'est l'écran Diagnostic de
l'application qui dit si la position est prise, et si l'envoi part.

# 6. Le jour du départ

Une seule commande, et elle n'efface rien. `trips.started_at` existe depuis la
première migration et n'est écrit par rien tant qu'on ne l'écrit pas :

    docker compose -f server/docker-compose.yml exec postgres \
      psql -U madhi -d madhi_tracker \
      -c "update trips set started_at = now() where id = '<INITIAL_TRIP_ID>';"

À partir de là, le site familial ne montre plus que le voyage : `from=startedAt`
filtre l'historique, et `latest_location` filtre aussi, donc la « dernière
position » ne peut plus être un point pris à la maison.

Les positions de test — la pré-validation sur le OnePlus, puis ce qui a été
capturé sur le Redmi — restent en base. Ce sont elles qui documentent l'anomalie
de cadence ; les jeter reviendrait à jeter les données qui servent à la
comprendre. Le départ se marque par une date, pas par une purge.

Vérifier que `startedAt` n'est plus `null` sur `/status`, puis ouvrir le site
familial et regarder ce que la famille verra.

# 7. Ce qui attend pendant le voyage

| Échéance | Quoi | Si rien n'est fait |
|---|---|---|
| 17 novembre 2026 | Certificat de `madhi-server.alexeber.fr` | L'application ne synchronise plus. Les positions s'accumulent sur le téléphone, rien n'est perdu, et la panne est invisible côté famille |
| 21 novembre 2026 | Certificat de `madhi.alexeber.fr` | La famille n'accède plus à rien, alors que tout fonctionne par ailleurs |

Le renouvellement automatique est censé être armé. La surveillance ne prévient
pas à l'avance : le jour où un certificat expire, la sonde passe au rouge, et
c'est le jour même. À prouver avant novembre plutôt que pendant :

    sudo certbot renew --dry-run

Et prouver aussi que le canal d'alerte est vivant, parce qu'une chaîne de
surveillance dont le dernier maillon est mort ressemble en tout point à une
chaîne qui fonctionne :

    sudo tools/monitoring/madhi-check.sh --test-alert

Enfin, tirer une copie des sauvegardes hors du VPS avant le départ
(`tools/backup/README.md`) : perdre la machine, c'est aujourd'hui perdre la base
et ses sauvegardes en même temps.
