package xyz.zcraft.osu.parser.exception;

public class AnalyzeException extends RuntimeException {
    public AnalyzeException(String message, Throwable e) {
        super(message, e);
    }

    public AnalyzeException(String message) {
        super(message);
    }
}
