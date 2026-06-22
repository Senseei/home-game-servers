package com.senseei.launcher.ark.port;

/** Port: the live server config the running ARK reads (GameUserSettings.ini + Game.ini). */
public interface LiveConfig {

    String gameUserSettings();

    void write(String gameUserSettings, String gameIni);
}
