package automatons.automatons.utility;

/**
 * effective & shorter version of Function<A,Long>
 * 
 * @author denis.lepekhin
 */
public interface LongFunction<A> {
    long apply(A input);
}
