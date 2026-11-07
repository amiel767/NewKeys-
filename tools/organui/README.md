# Organ UI — traduction Kotlin / Compose (minSdk 26)

## Installation
1. `res/drawable/*.xml` (14 fichiers `organ_*`) → `app/src/main/res/drawable/`
2. `kotlin/*.kt` → `app/src/main/java/com/aetherkeys/organ/` (package `com.aetherkeys.organ`)
3. Polices IBM Plex Mono → `res/font/` : `ibm_plex_mono_regular.ttf`, `ibm_plex_mono_medium.ttf`, `ibm_plex_mono_bold.ttf`
4. Dans `OrganTokens.kt` et `OrganControls.kt`, `import com.aetherkeys.R` = le `R` de ton module (adapte si besoin).
5. `OrganFonts.Ui` = police des labels (le HTML utilise system-ui). Mets ta police globale si tu veux.

```kotlin
OrganScreen(listener = object : OrganListener {
    override fun onDrawbar(index: Int, value: Int) { /* moteur */ }
})
```
Dépendances : Compose UI, Foundation, Material3 (juste `Text`), Kotlin 1.9+/2.x.

## Régénérer les XML
`cd gen && node thumbs.js && node widgets.js` (Node 18+). Les textures des thumbs viennent du même PRNG que le HTML.

## Choix de fidélité
- Écran 891×411 mis à l'échelle uniforme (densité redéfinie) : tout reste net.
- Flous (box-shadow / glow) = 10 couches gaussiennes : identiques sur API 26+ (pas de BlurMaskFilter).
- Non repris : bruit feTurbulence à 5 % sur les capuchons, clavier virtuel (hors interface), transitions 0,1–0,2 s des couleurs.
- Comportements du HTML repris tels quels : Rise/Fall affichent 1.5s/2.0s (valeur/20), le double-tap sur le preset n'édite qu'en mode USER, un preset ne bouge pas les knobs, les groupes Vibrato/Perc se réactivent à l'application d'un preset.
