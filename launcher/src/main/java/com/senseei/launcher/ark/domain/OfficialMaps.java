package com.senseei.launcher.ark.domain;

import java.util.List;

/** The official ARK:SE maps (all shipped in the server install) — always selectable. */
public final class OfficialMaps {

    public static final List<String> ALL = List.of(
            "TheIsland", "TheCenter", "ScorchedEarth_P", "Aberration_P", "Extinction",
            "Ragnarok", "Valguero_P", "CrystalIsles", "Genesis", "Gen2", "LostIsland", "Fjordur");

    private OfficialMaps() {
    }
}
