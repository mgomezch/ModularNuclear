package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;

/**
 * A config file that cannot be read or holds a value of the wrong kind. It stops the game at startup: a typo in a
 * config file must not quietly fall back to the defaults and change balance.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Wraps a failure to load one file, naming the file. YAML syntax errors already carry the line and column. */
    public static ConfigException inFile(File file, Throwable cause) {
        return in(file.getPath(), cause);
    }

    /** Wraps a failure to load one document, naming where it came from (a file, or the server's copy of one). */
    public static ConfigException in(String where, Throwable cause) {
        if (cause instanceof ConfigException) {
            return new ConfigException(where + ": " + cause.getMessage(), cause);
        }
        return new ConfigException(where + ": " + cause, cause);
    }
}
