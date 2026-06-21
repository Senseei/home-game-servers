package com.senseei.launcher.application.port;

/** Port: the live server config the running ARK reads (GameUserSettings.ini + Game.ini). */
public interface LiveConfig {

    String gameUserSettings();

    void write(String gameUserSettings, String gameIni);
}
