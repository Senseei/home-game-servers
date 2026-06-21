package com.senseei.launcher.application.port;

import java.util.List;

/**
 * Port: persistence of ARK config files — the {@code default/} baseline, each
 * customized map under {@code maps/<Map>/}, and the live server config. These
 * are dumb primitives; the "custom vs default" rule lives in the use-case.
 *
 * <p>{@code source} is either {@code "default"} or a map name; the adapter maps
 * it to {@code default/} or {@code maps/<source>/}.
 */
public interface ConfigStore {

    boolean mapExists(String map);          // maps/<map>/ exists (i.e. customized)

    List<String> customMaps();              // names of the customized maps

    void copyDefaultTo(String map);         // customize: cp default/ -> maps/<map>/

    void deleteMap(String map);             // uncustomize: rm maps/<map>/

    String readGameUserSettings(String source);

    String readGameIni(String source);

    List<String> readModIds(String source);

    void writeModIds(String source, List<String> ids);

    String readLiveGameUserSettings();

    void writeLiveGameUserSettings(String content);

    void writeLiveGameIni(String content);
}
