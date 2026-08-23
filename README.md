# Madhi Tracker

Suivi GPS d'un voyage à vélo de la France au Cap Nord, sur un an.

Un téléphone Android enregistre une position toutes les cinq minutes, la
garde en local, et l'envoie à un serveur privé dès qu'il y a du réseau. La
famille suit le trajet depuis un site web.

Le critère de réussite du projet n'est pas technique :

> Peut-on donner ce téléphone à quelqu'un qui part un an à vélo et avoir
> confiance dans le fait que son trajet continuera d'être enregistré, sans
> intervention permanente ?

## État

| Brique | État |
|---|---|
| Application Android | V1 fonctionnellement complète, en attente de validation terrain |
| Serveur | POC déployé sur `madhi-server.alexeber.fr`, en HTTPS, avec sauvegarde quotidienne |
| Site familial | POC en ligne sur `madhi.alexeber.fr`, en HTTPS, derrière un lien secret et un mot de passe |

Les trois briques sont en place et la chaîne est complète, du téléphone au
site. Cela ne veut pas dire que le projet est prêt : l'application n'a **pas
encore** été validée en conditions réelles. Tant que le protocole de
`arch/14_protocole_test_terrain.md` n'est pas passé sur l'appareil cible — le
Redmi Note 11, pas le OnePlus de pré-validation — elle repose sur une
hypothèse. C'est le seul point bloquant du projet.

## Principes

**Hors ligne d'abord.** Une position est écrite en base locale avant toute
tentative réseau. Aucun chemin de code ne supprime un point non confirmé par
le serveur : ni un timeout, ni une erreur d'authentification, ni une réponse
illisible. Une tâche de vérification échouerait si le domaine se mettait à
dépendre d'Android.

**Idempotence par identifiant.** Chaque position reçoit un UUID à la capture.
Le serveur n'insère que les identifiants inconnus, et renvoie les autres
comme déjà détenus. Un envoi dont la réponse s'est perdue aboutit donc au
rejeu, sans créer de doublon.

**Pas de GAFAM sur le chemin des positions.** Ni Google Play Services, ni
Firebase, ni analytics, ni crash reporting externe. La localisation passe par
l'API système `LocationManager`, pas par Fused Location Provider. Aucune
coordonnée n'atteint les journaux techniques — la signature du port de
journalisation l'interdit par construction.

**Réparable par une seule personne.** Un module Gradle, peu de dépendances,
aucune magie d'infrastructure.

## Architecture

Hexagonale, avec des frontières logiques plutôt que des modules Gradle
séparés tant que la taille du projet ne le justifie pas.

    presentation/     Compose, ViewModels
    adapter/input/    service de premier plan, receveurs, worker
    application/      use cases et ports
    domain/           modèles et règles, sans Android
    adapter/output/   Room, DataStore, OkHttp, LocationManager, AlarmManager
    infrastructure/   racine de composition Hilt, configuration, journalisation

Le domaine et les use cases ne connaissent ni Android, ni Room, ni OkHttp.
La tâche Gradle `checkCoreIsFrameworkFree`, branchée sur `check`, échoue si
un import de framework s'y glisse.

## Construire

Prérequis : JDK 17 ou plus récent, SDK Android.

    ./gradlew assembleDebug        # APK de développement
    ./gradlew assembleRelease      # APK du voyage, signé et minifié par R8
    ./gradlew check                # tests et vérifications d'architecture

Les tests tournent sur la JVM, sans émulateur ni téléphone : Robolectric
couvre Room et DataStore, le reste s'appuie sur des doubles simples.

Le build release **échoue volontairement** si l'URL de l'API n'est pas
configurée, plutôt que de produire un APK pointant vers une valeur factice. Il
échoue aussi sans matériel de signature, pour ne pas livrer un APK signé avec la
clé de debug.

C'est le build release qui part en voyage (`arch/01` §2), donc c'est lui qui
doit passer les tests terrain : R8 supprime du code que le build debug conserve.
Conserver `app/build/outputs/mapping/release/mapping.txt` de chaque version
installée — sans lui, une pile d'appel remontée du voyage est illisible.

## Configuration

Aucune valeur réelle n'est versionnée. Copier les exemples :

    cp local.properties.example local.properties
    cp keystore.properties.example keystore.properties

Les variables d'environnement `MADHI_API_BASE_URL_*` et `ANDROID_SIGNING_*`
ont la priorité sur ces fichiers, ce qui évite d'avoir à les créer en CI.

## Site familial

Le site que regarde la famille est dans `site/` : des fichiers statiques, sans
étape de build, servis par un conteneur nginx de la même stack que le serveur,
derrière un segment d'URL secret et un mot de passe. Il n'embarque aucun
secret — le token de lecture exigé par l'API est posé par le conteneur, et le
répertoire `site/` du dépôt est monté tel quel, sans copie intermédiaire.

    docker compose -f server/docker-compose.yml up -d   # la stack complete
    python3 tools/site-dev/serve.py            # le site seul, donnees fabriquees
    node tools/site-dev/verifier.mjs           # les verifications sans navigateur

Le déploiement sur `madhi.alexeber.fr` et les vérifications à faire ensuite
sont dans `site/README.md`.

## Serveur de simulation

Le serveur réel est déployé (voir `SERVER_DEPLOYMENT.md`) et les builds le
visent désormais. Le serveur de simulation reste utile pour travailler hors
ligne et sert de spécification exécutable de l'idempotence.

    python3 tools/mock-server/server.py

Voir `tools/mock-server/README.md`.

## Documentation

Les documents de `arch/` sont la source de vérité fonctionnelle. Les
décisions techniques prises en cours de route sont dans `arch/adr/`, au
format contexte / options / décision / conséquences.

Les plus utiles pour comprendre le projet :

- `arch/00_architecture_maitre.md` — contrats communs et règles d'évolution
- `arch/13_contrat_api_android_v1.md` — contrat API détaillé
- `arch/adr/002-execution-en-arriere-plan.md` — pourquoi un service de
  premier plan ne suffit pas, et ce qui le complète
- `arch/adr/007-contraintes-miui-redmi-note-11.md` — ce que la surcouche du
  téléphone cible impose
- `arch/14_protocole_test_terrain.md` — comment on prouve que ça marche
- `arch/17_plan_implementation_site_poc.md` — plan d'exécution du site, et les
  pièges du serveur qu'il faut connaître avant de le construire
- `site/README.md` — comment développer, déployer et vérifier le site familial

## Licence

Pas encore déterminée.
