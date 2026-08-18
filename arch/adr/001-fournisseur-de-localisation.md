**ADR-001 — Fournisseur de localisation Android**

*Statut : Accepté — 2026-08-18*

# Contexte

L'application doit acquérir une position environ toutes les 5 minutes pendant un an.

Deux sources se contredisaient :

- `arch/00_architecture_maitre.md` §9 et `arch/01_android_POC.md` §4 imposent
  l'API système `LocationManager`, explicitement **sans** Fused Location Provider
  ni Google Play Services pour le tracking critique.
- La demande initiale de développement mentionnait Fused Location Provider.

La contrainte de fond est produit, pas technique : `arch/00` §2 interdit d'envoyer
les positions précises à des services GAFAM, et interdit de dépendre du Play Store
pour les fonctions critiques. L'APK est distribué à la main, hors store.

# Options

**1. `LocationManager` (AOSP).** Toujours présent, quel que soit l'état de Google
Play Services. Aucune donnée ne transite par un SDK tiers. En contrepartie : ni
fusion multi-capteurs, ni batching matériel, ni `setWaitForAccurateLocation`
géré pour nous. La cadence et l'économie d'énergie sont entièrement à notre charge.

**2. Fused Location Provider (Google Play Services).** Plus économe, fusionne
GPS/WiFi/cellulaire, batching matériel disponible. Mais : dépendance à Play
Services, donc violation directe de `arch/00` §2 ; comportement dégradé ou nul
si Play Services est absent, périmé ou désactivé sur le téléphone cible.

# Décision

**`LocationManager`**, conformément aux documents d'architecture.

L'acquisition passe par le port `LocationSource`, qui expose une acquisition
**ponctuelle** avec délai maximal, et non un flux continu. L'implémentation
concrète (`AndroidLocationSource`) est le seul fichier qui connaît
`LocationManager`.

# Conséquences

- Le domaine et les use cases ignorent totalement l'API de localisation.
  Un changement de fournisseur est un adaptateur à réécrire, sans autre impact.
- L'économie de batterie devient une décision d'architecture explicite plutôt
  qu'un effet de bord du SDK : voir ADR-002.
- Deux implémentations d'acquisition seront comparées en test terrain sur le
  téléphone réel (acquisition ponctuelle vs `requestLocationUpdates` continu),
  derrière le même port. Le doc `arch/01` §4 prévoit déjà cette optimisation
  progressive sur l'appareil réel.
- Aucune dépendance Google Play Services n'entre dans le projet. Cela vaut aussi
  pour les bibliothèques transitives : à vérifier à chaque ajout de dépendance.
