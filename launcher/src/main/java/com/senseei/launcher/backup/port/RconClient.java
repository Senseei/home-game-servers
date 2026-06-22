package com.senseei.launcher.backup.port;

/** Port: a Source RCON client (replaces the old rcon.py). */
public interface RconClient {

    /** Connects to 127.0.0.1:{@code port}, authenticates, runs the command, returns the reply. */
    String execute(int port, String password, String command);
}
