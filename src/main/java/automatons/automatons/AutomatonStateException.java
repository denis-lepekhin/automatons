package automatons.automatons;

/**
 * if automaton was ill-designed;
 * error of automaton programmer;
 */
public class AutomatonStateException extends IllegalStateException {
    private static final long serialVersionUID = 7633717617232344698L;

    public AutomatonStateException() {
    }

    public AutomatonStateException(String s) {
        super(s);
    }

    public AutomatonStateException(Throwable cause) {
        super(cause);
    }

    public AutomatonStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
