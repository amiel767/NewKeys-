# Démos audio Æther (piano & sax)

Échantillons rendus par le **moteur réel** (`aetherfx`, header-only), hors Android,
avec les correctifs courants (boucle piano aplatiefondue, bite sax 5 ms).

| Fichier | Contenu |
|---|---|
| `audio/_MEDLEY_pianos.mp3` | les 12 pianos/électriques à la suite |
| `audio/_MEDLEY_sax.mp3` | les 4 sax à la suite |
| `audio/piano_NN_<modele>.mp3` | un par modèle (Felt, Studio, … , DX, E.Piano) |
| `audio/sax_<type>.mp3` | soprano, alto, ténor, baryton |

## Régénérer

```bash
g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx tools/aetherfx/demo_pianos_all.cpp -o /tmp/dpa
g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx tools/aetherfx/demo_sax.cpp        -o /tmp/dsax
/tmp/dpa  app/src/main/assets /tmp/demos        # 12 x .f32
/tmp/dsax app/src/main/assets /tmp/demos        # 4 x .f32
```

Les `.f32` sont du PCM stéréo 44,1 kHz entrelacé (pas d'en-tête). Conversion :

```bash
python3 tools/demos/f32_to_mp3.py /tmp/demos tools/demos/audio
```

## Licence des instruments

MTG Solo Sax (CC-BY 4.0) et les packs piano/EP du dépôt. **Bulldog Sax V2** :
l'utilisateur a déclaré détenir la licence et l'accord du propriétaire
(2026-11-04) — réintégrable.
