package com.senseei.launcher.application.port;

import java.util.List;
import java.util.Map;

/** Port: resolves Steam Workshop mod ids to their titles (the Steam Web API). */
public interface WorkshopClient {

    /** Returns id → title for the resolvable ids (unknown ids are omitted). */
    Map<String, String> titles(List<String> ids);
}
