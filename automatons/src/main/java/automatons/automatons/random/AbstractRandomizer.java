package automatons.automatons.random;

import automatons.automatons.utility.LongInterval;

/**
 * implementation in terms of nextRandom();
 * @author denis.lepekhin
 */
public abstract class AbstractRandomizer implements Randomizer {
    public final long nextLongBetween(long from, long to) {
        if (from == to) {
            return from;
        }
        if (from > to) {
            final long t = from; 
            from = to; to = t;
        }
        return from +  (long) (((double)(to - from)) * nextDouble());
    }
    
    @Override
    public final long nextLongBetween(LongInterval d) {
        return nextLongBetween(d.lower(), d.upper());
    }
    
    public final long nextLongAround(long point, long gap) {
        assert gap >= 0;
        return nextLongBetween(point - gap, point + gap);
    }
}
