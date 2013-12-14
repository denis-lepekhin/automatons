package automatons.automatons.random;

import java.security.SecureRandom;
import java.util.Random;

public class Randomizers {

    protected Randomizers() {
    }

    public static Randomizer uniform(long seed) {
        final Random random = new Random(seed);
        return new AbstractRandomizer() {
            @Override public final double nextDouble() {
                return random.nextDouble();
            }
        };
    }

    public static Randomizer uniform() {
        return uniform(System.currentTimeMillis());
    }

    public static Randomizer uniformSecure() {
        final SecureRandom random = new SecureRandom();
        return new AbstractRandomizer() {
            @Override public final double nextDouble() {
                return random.nextDouble();
            }
        };
    }

    private static final AbstractRandomizer dummy = new AbstractRandomizer() {
        @Override public final double nextDouble() {
            return 0.5;
        }
    };

    public static Randomizer dummy() {
        return dummy;
    }

}
