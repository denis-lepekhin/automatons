package automatons.automatons;

import automatons.automatons.dsl.DslForJumpAutomaton;
import automatons.automatons.dsl.DslForJumpAutomaton.*;
import automatons.automatons.random.Randomizer;
import automatons.automatons.sched.AutomatonScheduler;
import automatons.automatons.utility.*;

import com.google.common.base.*;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Automaton with configurable jumps/transitions;<br>
 * 
 * Each state may have MANY associated jumps/transition(s) to it; Only 1 jump
 * can be fired at each step; Jumps may have associated conditions(predicates)
 * and/or probability(stochastic/fuzzy jumps) <br>
 * 
 * Formally, jump is a tuple of (StateA, StateB, Priority/Order,
 * Condition/Predicate, Probability, Action, Delay) Loop - is a jump to the same
 * state. Priority/Order - determines the order of jump predicate checking;
 * Delay, Action, Predicates - are automaton current state dependent (may vary);
 * 
 * <br>
 * If a state A has multiple jumps, only one jump will be chosen, based on the
 * following procedure:
 * 
 * 1. find first condition (predicate) C of state A witch evaluates to true or
 * if no such predicate exists take default(always true). 2. if predicate C has
 * ONE jump - just fire it; 3. if predicate C has MANY jumps - choose jump
 * randomly based on jump probability; 4. if no jump exists error will be thrown
 * IllegalStateException "no jumps".
 * 
 * for more details {@link Jumps#fire(JumpAutomaton)} <br>
 * 
 * @author denis.lepekhin
 */

public class JumpAutomaton<S> extends AbstractAutomaton<S> {

    private final Map<S, ? extends Jumps<S, ? extends JumpAutomaton<S>>> jumps;
    private final @Nullable Randomizer randomizer;
    private @Nullable Object assoc;
    private final Supplier<Object> assocSupplier;

    @SuppressWarnings("unchecked")
    public JumpAutomaton(JumpBuilder<? extends JumpBuilder<?, ?, ?>, S, ? extends JumpAutomaton<S>> b) {
        super(b);
        checkArgument(!b.jumps.isEmpty());
        this.jumps = b.jumps;
        this.randomizer = b.randomizer;
        this.assocSupplier = (Supplier<Object>)b.assocSupplier;
    }

    public @Nullable Randomizer getRandomizer() {
        return randomizer;
    }

    // convenience class;
    public static abstract class StepWithJump<A extends JumpAutomaton<?>> extends Step<A> {
        @Override public final AbstractAutomaton.StepResult step(A self) {
            stepWithJump(self);
            return self.nextJump();
        }

        public abstract void stepWithJump(A self);
    }

    public static abstract class AsyncStepWithJump<A extends JumpAutomaton<?>, V> extends AsyncStep<A, V> {
        @Override public final AbstractAutomaton.StepResult step(A self, V result) {
            stepWithJump(self, result);
            return self.nextJump();
        }

        public abstract void stepWithJump(A self, V result);
    }

    protected final StepResult nextJump() {
        @SuppressWarnings("unchecked") final Jumps<S, JumpAutomaton<S>> tt = (Jumps<S, JumpAutomaton<S>>) jumps
                .get(getCurrentState());
        if (tt == null) {
            throw errorInCurrentState("no state found in jumps table");
        }
        final StepResult result = tt.fire(this);
        if (result == null) {
            throw errorInCurrentState("no jumps found");
        }
        return result;
    }
    
    @Override
    public ListenableFuture<? extends Automaton.StopDescription<S>> start(AutomatonScheduler sched) {
        if (assocSupplier != null) {
            this.assoc = assocSupplier.get();
        }
        return super.start(sched);
    }
    
    @Override protected void onStopped(StopReason reason, Throwable error) {
        this.assoc = null; // let gc remove assoc;
        super.onStopped(reason, error);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAssoc() {
        return (T) assoc;
    }

    public static final class Jump<TState, A extends JumpAutomaton<TState>> {
        public final TState source;
        public final @Nullable TState target; // null-state is automaton total
                                              // stop;
        public final @Nullable Double probability;
        public final @Nullable Predicate<A> when; // condition of the jump
        public final @Nullable LongFunction<A> delay;
        public final @Nullable JumpAction<A, TState> action; // associated
                                                             // action

        public Jump(TState source, TState target, @Nullable Predicate<A> when, Double probability,
                    @Nullable LongFunction<A> delay, @Nullable JumpAction<A, TState> action) {
            this.source = source;
            this.target = target;
            this.probability = probability;
            this.when = when;
            this.delay = delay;
            this.action = action;
        }

        final @Nullable StepResult fire(A automaton) {
            if (action != null) {
                action.action(automaton, source, target);
            }
            if (delay != null) {
                return automaton.next(target, TimeUnit.MILLISECONDS, delay.apply(automaton));
            } else {
                return automaton.next(target);
            }
        }
    }

    // predicate associated one jump or many jumps with different probability;
    private static class PredicateAssoc<TState, A extends JumpAutomaton<TState>> {
        protected final Predicate<A> when;
        protected final @Nullable TreeMap<Double, Jump<TState, A>> probable;
        protected final @Nullable Jump<TState, A> jump;
        private double psum; // probability sum;

        PredicateAssoc(Predicate<A> when, @Nullable Jump<TState, A> jump) {
            this.when = when;
            this.jump = jump;
            this.probable = jump == null ? new TreeMap<Double, Jump<TState, A>>() : null;
        }

        final @Nullable StepResult fire(A automaton) {
            if (jump != null) {
                return jump.fire(automaton);
            } else {
                Entry<Double, Jump<TState, A>> e = probable.ceilingEntry(checkNotNull(automaton.getRandomizer(),
                        "randomizer?").nextDouble());
                if (e != null) {
                    final Jump<TState, A> t = e.getValue();
                    final StepResult result = t.fire(automaton);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        void add(Jump<TState, A> tr) {
            psum = (tr.probability == null ? 1 : psum + tr.probability);
            checkArgument(psum > 0 && psum <= 1, "probability sum error");
            probable.put(psum, tr);
        }
    }

    private static class Jumps<State, A extends JumpAutomaton<State>> {
        private List<PredicateAssoc<State, A>> theSwitch = new LinkedList<>();
        private PredicateAssoc<State, A> theDefault;
        private HashMap<Predicate<A>, PredicateAssoc<State, A>> predicate2Assoc = new HashMap<>();

        void add(Jump<State, A> jump) {
            final Predicate<A> when = jump.when;
            PredicateAssoc<State, A> assoc = predicate2Assoc.get(when);

            if (assoc == null) {
                predicate2Assoc.put(when, (assoc = new PredicateAssoc<>(when, jump.probability == null ? jump : null)));
            } else {
                checkState(assoc.probable != null, "multiple jumps with no prob-ty");
            }

            if (assoc.probable != null) {
                assoc.add(jump);
            }

            if (jump.when != null) {
                theSwitch.add(assoc);
            } else {
                theDefault = assoc;
            }
        }

        void clear() {
            predicate2Assoc = null;
        }

        final StepResult fire(A automaton) {
            for (PredicateAssoc<State, A> p : theSwitch) {
                assert p.when != null;
                if (p.when.apply(automaton)) {
                    final StepResult result = p.fire(automaton);
                    if (result != null) {
                        return result;
                    }
                    break;
                }
            }
            if (theDefault != null) {
                final StepResult result = theDefault.fire(automaton);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    protected abstract static class JumpBuilder<This extends JumpBuilder<?, ?, ?>, S, A extends JumpAutomaton<S>> extends BuilderBase<This, S, A> {
        private @Nullable Randomizer randomizer;
        private @Nullable Supplier<?> assocSupplier;
        private @Nullable DslJumps jumpsDsl;
        protected final Map<S, Jumps<S, A>> jumps;

        @SuppressWarnings({ "unchecked", "rawtypes" })
        protected JumpBuilder(Class<? extends Enum<?>> enumClass) {
            jumps = new EnumMap(enumClass);
        }

        protected JumpBuilder() {
            jumps = new HashMap<>();
        }

        public final This randomizer(Randomizer randomizer) {
            this.randomizer = randomizer;
            return getThis();
        }

        private Jumps<S, A> getJumps(S source) {
            Jumps<S, A> tt = jumps.get(source);
            if (tt == null) {
                jumps.put(source, tt = new Jumps<>());
            }
            return tt;
        }
        
        public This assocSupplier(Supplier<?> assocSupplier) {
            this.assocSupplier = assocSupplier;
            return getThis();
        }

        public DslForJumpAutomaton.Jumps<This, S, A> jumpsBegin(S initialState) {
            checkState(jumpsDsl == null, "once");
            jumpsDsl = new DslJumps();
            initialState(initialState);
            return jumpsDsl;
        }

        final class DslJumps implements DslForJumpAutomaton.Jumps<This, S, A> {
            private DslJumps() {
            }

            DslJumps addJump(Jump<S, A> jump) {
                getJumps(jump.source).add(jump);
                return this;
            }

            @Override
            public DslJump jump(S source, @Nullable S target) {
                return new DslJump(source, target);
            }

            @Override
            public DslJump loop(S source) {
                return jump(source, source);
            }

            @Override
            public This jumpsEnd() {
                for (Jumps<S, A> jj : jumps.values()) {
                    jj.clear();
                }
                return getThis();
            }

            final class DslJump implements All<This, S, A> {
                private final S source;
                private final @Nullable S target;
                private @Nullable Predicate<A> when;
                private @Nullable Double probability;
                private @Nullable LongFunction<A> delay;
                private @Nullable JumpAction<A, S> action;

                private DslJump(S source, S target) {
                    this.source = source;
                    this.target = target;
                }

                @Override
                public Delay<This, S, A> act(JumpAction<A, S> action) {
                    checkState(this.action == null);
                    this.action = checkNotNull(action);
                    return this;
                }

                @Override
                public DslForJumpAutomaton.Jumps<This, S, A> delay(LongFunction<A> delay) {
                    checkState(this.delay == null);
                    this.delay = checkNotNull(delay);
                    addJump(convert());
                    return DslJumps.this;
                }

                @Override
                public DslForJumpAutomaton.Jumps<This, S, A> nodelay() {
                    checkState(this.delay == null);
                    addJump(convert());
                    return DslJumps.this;
                }

                @Override
                public ActDelay<This, S, A> maybe(double probability) {
                    checkState(this.probability == null);
                    this.probability = probability;
                    return this;
                }

                @Override
                public ActDelayProb<This, S, A> when(Predicate<A> when) {
                    checkState(this.when == null);
                    this.when = checkNotNull(when);
                    return this;
                }

                Jump<S, A> convert() {
                    return new Jump<>(source, target, when, probability, delay, action);
                }
            }
        }
    }

    // delay factory methods;

    public static <A extends JumpAutomaton<?>> LongFunction<A> toDelay(final LongInterval interval) {
        return new LongFunction<A>() {
            @Override public long apply(A automaton) {
                return automaton.getRandomizer().nextLongBetween(interval);
            }
        };
    }

    public static <A extends JumpAutomaton<?>> LongFunction<A> toDelay(long lower, long upper) {
        return toDelay(Intervals.longInterval(lower, upper));
    }

    public static <A extends JumpAutomaton<?>> LongFunction<A> toDelay(long delay) {
        return toDelay(delay, delay);
    }
}
