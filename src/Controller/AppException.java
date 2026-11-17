package Controller;

/** Only deliberately safe messages may be shown to the user. */
public final class AppException extends RuntimeException {
    public AppException(String message) { super(message); }
}
