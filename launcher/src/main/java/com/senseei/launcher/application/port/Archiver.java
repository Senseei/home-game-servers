package com.senseei.launcher.application.port;

import java.nio.file.Path;

/** Port: create and extract compressed archives of a directory. */
public interface Archiver {

    void create(Path sourceDir, Path destArchive);

    void extract(Path archive, Path destDir);
}
