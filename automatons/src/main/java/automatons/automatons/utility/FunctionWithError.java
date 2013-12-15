package automatons.automatons.utility;

import com.google.common.base.Function;

public interface FunctionWithError<A, R> extends Function<A, R> {
    R error(Throwable throwable);
}
