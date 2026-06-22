package com.senseei.launcher.lifecycle.app;

import com.senseei.launcher.lifecycle.domain.RunState;

/** One row of the status report — data the presentation formats, not text. */
public record ServerStatus(String game, RunState state) {
}
