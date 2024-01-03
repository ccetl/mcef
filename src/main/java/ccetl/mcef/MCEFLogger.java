package ccetl.mcef;

public class MCEFLogger {
    private static Logger logger;

    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        MCEFLogger.logger = logger;
    }

    public interface Logger {
        void info(String message);
        void warn(String message);
        void error(String message, Exception exception);
    }
}
