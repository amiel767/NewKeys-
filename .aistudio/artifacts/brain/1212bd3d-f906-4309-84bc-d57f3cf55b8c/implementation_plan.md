# Plan d'implémentation — Module Sheets (Æther Keys)

## 1. Contexte & Objectif
Intégrer et valider l'ensemble du module Sheets (`com.aetherkeys.sheets`) conformément à la spec `00-SPEC-sheets.md` et aux instructions agent `01-INSTRUCTIONS-AGENT.md`.

---

## 2. Étapes du Plan

### Étape 1 : Polices et Ressources
- Vérifier la présence de toutes les polices dans `app/src/main/res/font/` :
  - `architects_daughter.ttf`
  - `caveat.ttf`
  - `fira_code.ttf`
  - `jetbrains_mono.ttf`
  - `kalam.ttf`
  - `patrick_hand.ttf`
  - `plus_jakarta_sans.ttf`

### Étape 2 : Vérification du package `com.aetherkeys.sheets`
- S'assurer que tous les fichiers du module (Data, Model, Export, UI, VM) sont bien présents sous l'arborescence `com.aetherkeys.sheets`.
- Vérifier les imports de ressources (`R` package).

### Étape 3 : Branchement du Point d'Entrée
- Configurer l'appel à `SheetsRoot` dans `MixerScreen.kt` :
  ```kotlin
  SheetsRoot(
      repo = SheetsRepository(viewModel.fileManager.sheetsDir),
      bottomInset = safeBottomPadding,
      onBackToMixer = { viewModel.navigateBackToMixer() }
  )
  ```

### Étape 4 : Compilation & Validation
- Exécuter `compile_applet` pour garantir un build sans erreur.
