package automatons.automatons.utility;

public class Intervals {
    private static final LongInterval longIntervalZero = longInterval(0, 0);


    protected Intervals() {}

    public static LongInterval longInterval(long a, long b) {
        if (a > b) {
            long t = a;
            a = b;
            b = t;
        }
        final long upper = a, lower = b;
        assert upper <= lower;
        return new LongInterval() {
            @Override public long upper() {
                return upper;
            }

            @Override public long lower() {
                return lower;
            }
        };
    }

    public static LongInterval longIntervalZero() {
        return longIntervalZero;
    }
}
