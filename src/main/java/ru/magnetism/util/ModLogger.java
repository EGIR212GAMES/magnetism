package ru.magnetism.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.magnetism.Magnetism;

/** Central logger facade so future subsystems share one consistent logger. */
public final class ModLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(Magnetism.MOD_ID);

    private ModLogger() {
    }

    public static void debug(String message, Object... arguments) {
        LOGGER.debug(message, arguments);
    }

    public static void info(String message, Object... arguments) {
        LOGGER.info(message, arguments);
    }

    public static void warn(String message, Object... arguments) {
        LOGGER.warn(message, arguments);
    }

    public static void error(String message, Object... arguments) {
        LOGGER.error(message, arguments);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
}
