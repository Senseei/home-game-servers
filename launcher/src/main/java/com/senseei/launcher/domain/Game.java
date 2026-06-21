package com.senseei.launcher.domain;

/**
 * A server this tool manages. A record = immutable data carrier (Java 16+):
 * the compiler generates the constructor, accessors {@code name()} etc.,
 * {@code equals}/{@code hashCode}/{@code toString}.
 */
public record Game(String name, String container, String composeDir) {
}
