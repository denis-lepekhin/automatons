package automatons.automatons.random;

import automatons.automatons.utility.LongInterval;

public interface Randomizer {
    /**
     * @return randomNumber beloging to interval [from, to]
     */
    long nextLongBetween(long from, long to);

    long nextLongBetween(LongInterval d);

    /**
     * @return randomNumber beloging to interval[point-gap, point+gap]
     */
    long nextLongAround(long point, long gap);

    double nextDouble();
}
