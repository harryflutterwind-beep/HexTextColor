package com.example.examplemod.beams;

public final class RarityTags {
    public static final String KEY  = "rarityKey";        // e.g., "epic"
    public static final String HKEY = "rarityBeamH";      // optional beam height

    // Optional helper tags for overlay rarities/modifiers.
    // The current pseudo-black system mainly stores its marker in LorePages,
    // but these constants give Java-side renderers a stable place to look if
    // the marker is ever mirrored into raw NBT as well.
    public static final String PSEUDO_BLACK      = "pseudoBlack";
    public static final String PSEUDO_BLACK_BASE = "pseudoBlackBase";

    private RarityTags() {}
}
