package automatons.automatons.dsl;

import automatons.automatons.JumpAction;
import automatons.automatons.JumpAutomaton;
import automatons.automatons.utility.LongFunction;

import com.google.common.base.Predicate;

/**
 * Do not use directly, its a part of JumpAutomaton builder;
 * @author denis.lepekhin
 */
public class DslForJumpAutomaton {
    public interface Jumps<This, State, A extends JumpAutomaton<State>> {

        All<This, State, A> jump(State target, State source);

        /**
         * Alias for {@link Jumps#jump(Object, Object)}
         */
        All<This, State, A> loop(State target);

        This jumpsEnd();
    }


    public interface All<This, State, A extends JumpAutomaton<State>> extends
        Act<This, State, A>, Delay<This, State, A>, Prob<This, State, A>,  When<This, State, A>,
        ActDelay<This, State, A>, ActDelayProb<This, State, A>{}


    public interface Prob<This, State, A extends JumpAutomaton<State>> {
        ActDelay<This, State, A> maybe(double probability);
    }


    public interface When<This, State, A extends JumpAutomaton<State>> {
        ActDelayProb<This, State, A> when(Predicate<A> when);
    }


    public interface Delay<This, State, A extends JumpAutomaton<State>> {
        Jumps<This, State, A> delay(LongFunction<A> interval);
        Jumps<This, State, A> nodelay();
    }

    public interface Act<This, State, A extends JumpAutomaton<State>> {
        Delay<This, State, A> act(JumpAction<A, State> action);
    }


    /**
     * Fix for http://youtrack.jetbrains.com/issue/IDEA-25749 Multiple bounds in generic types not supported
     */
    public interface ActDelay<This, State, A extends JumpAutomaton<State>> extends
            Act<This, State, A>, Delay<This, State, A> {}

    public interface ActDelayProb<This, State, A extends JumpAutomaton<State>> extends
            Act<This, State, A>, Delay<This, State, A>, Prob<This, State, A> {}
}
