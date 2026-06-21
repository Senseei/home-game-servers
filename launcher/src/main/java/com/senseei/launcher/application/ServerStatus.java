package com.senseei.launcher.application;

import com.senseei.launcher.domain.RunState;

/** One row of the status report — data the presentation formats, not text. */
public record ServerStatus(String game, RunState state) {
}
