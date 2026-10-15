package guessmarket.client;

/**
 * Entry point.
 *
 * Starting a JavaFX Application subclass directly requires JavaFX to be on the
 * module path. Launching from a plain class that is NOT an Application removes
 * that requirement, so the program also starts when JavaFX is supplied on the
 * classpath. This makes the batch file far less fragile on someone else's machine.
 */
public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) {
        ClientApp.main(args);
    }
}
