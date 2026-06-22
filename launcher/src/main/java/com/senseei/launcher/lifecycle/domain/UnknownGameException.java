package com.senseei.launcher.lifecycle.domain;

import java.util.List;

/** Thrown when a requested game name isn't in the catalog. */
public class UnknownGameException extends RuntimeException {

    public UnknownGameException(String name, List<String> valid) {
        super("unknown game: '" + name + "' (valid: " + valid + ")");
    }
}
