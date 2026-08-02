package guessmarket.engine.service;

/**
 * Thrown by the engine whenever a request from the UI layer cannot be
 * fulfilled (invalid selection, event closed, bad quantity, etc).
 * The message is meant to be shown directly to the end user.
 */
public class GuessMarketException extends RuntimeException {
    public GuessMarketException(String message) {
        super(message);
    }
}
