package automatons.automatons.utility;

import com.google.common.base.Function;

/**
 * Scala-esque partial function;
 * 
 * @author denis.lepekhin
 */
public interface PartialFunction<TArg, TResult> extends Function<TArg, TResult> {

    TResult apply(TArg input);

    boolean isDefinedAt(TArg input);
}
