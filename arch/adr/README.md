**ADR — Décisions d'architecture**

*Projet : suivi d'un voyage à vélo pendant 1 an — Android → serveur → site familial*

# Rôle de ce dossier

Les documents `arch/00` à `arch/12` décrivent **ce que le système doit faire**. Les
ADR décrivent **comment nous avons tranché** lorsqu'une décision technique n'était
pas déjà écrite, ou lorsque deux sources se contredisaient.

Un ADR n'annule jamais un document d'architecture. S'il diverge, c'est le document
qui fait foi et l'ADR doit être corrigé.

# Format

    Contexte
    Options
    Décision
    Conséquences

# Index

| ADR | Sujet | Statut |
|-----|-------|--------|
| [001](001-fournisseur-de-localisation.md) | Fournisseur de localisation Android | Accepté |
| [002](002-execution-en-arriere-plan.md) | Stratégie d'exécution en arrière-plan | Accepté |
| [003](003-synchronisation-et-idempotence.md) | Synchronisation, batch, idempotence, erreurs | Accepté |
| [004](004-activation-et-token-appareil.md) | Activation appareil et stockage du token | Accepté |
| [005](005-retention-locale-et-migrations.md) | Rétention locale et migrations Room | Accepté |
| [006](006-carte-embarquee.md) | Carte embarquée dans l'application | Accepté — rouvert deux fois le 23/08 ; fond de carte auto-hébergé |
| [007](007-contraintes-miui-redmi-note-11.md) | Contraintes MIUI sur l'appareil cible | Accepté |
| [008](008-cadence-par-le-flux-de-localisation.md) | Cadence confiée au fournisseur de localisation | Accepté — remplace le métronome de 002 |
