package automatons.automatons;

import java.util.concurrent.TimeUnit;

import automatons.automatons.sched.AutomatonScheduler;
import automatons.automatons.utility.FunctionWithError;
import automatons.automatons.utility.PartialFunction;

import com.google.common.base.*;
import com.google.common.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static automatons.automatons.utility.NanoTimes.isBefore;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Automaton is a state machine; It consists of states and steps(actions). Each
 * state has one associated step(action) which puts an automaton to a new state; <br>
 * 
 * AbstractAutomaton doesn't support explicit transitions/jumps table, please
 * see {@link JumpAutomaton} <br>
 * Note, that "state" can be considered as a label/identifier(or something like
 * "switch case") for a step; <br>
 * Also note, that "step" of AbstractAutomaton is also very abstract, it's the
 * black box function that returns the next state; <br>
 * AbstractAutomaton supports simple/synch steps {@link Step} and async steps
 * {@link ReactStep}; AsyncSteps are used when automaton live depends on
 * external events (such as network/io) <br>
 * To describe automaton step - you either override step() method [less syntax,
 * more concise] or pass your own statesFunction, default statesFunction
 * supposes that your state type implements Supplier<AbstractStep> or is an
 * AbstractStep itself;
 * 
 * @param <S>
 *            any type you choose, but usually an enum, null state - is a total
 *            stop of automaton;
 * @author denis.lepekhin
 */
public abstract class AbstractAutomaton<S> implements Automaton<S> {
    private static final Logger log = LoggerFactory.getLogger(AbstractAutomaton.class);
    
    public static final long JOIN_PSEUDO_DELAY = -1;
    
    // core automaton state(step) variables {
    
    private @Nullable S currentState;
    private long currentDelay; 
    private TimeUnit currentDelayUnit;
    private @Nullable ListenableFuture<?> stepFuture;
    private @Nullable Function<?, StepResult> stepFutureHandler;
    // }

    private @Nullable final String name;
    private volatile boolean stopFlag;
    private final boolean supportsManualStop;
    private volatile @Nullable SettableFuture<StopDescriptionImpl> stopFuture;
    private long nextCallCount;
    private @Nullable Long maxAge;
    private @Nullable Long maxTime;
    private S initialState;
    private long startTime;
    private @Nullable ListenableFuture<?> deffered;
    private final PartialFunction<S, ? extends AbstractStep<AbstractAutomaton<S>>> statesFunction;
    private AutomatonScheduler currentSched;
    private final Ticker ticker;
    protected final TimeUnit defaulDelaytUnit;
    
    @SuppressWarnings({ "unchecked", "rawtypes" }) protected AbstractAutomaton(
            BuilderBase<? extends BuilderBase<?, ?, ?>, S, ? extends AbstractAutomaton<S>> b) {
        this.maxAge = b.maxAge;
        this.ticker = b.ticker;
        this.defaulDelaytUnit = b.defaultUnit;
        this.supportsManualStop = b.supportsManualStop;
        this.initialState = checkNotNull(b.initialState, "initialState?");
        this.statesFunction = Objects.firstNonNull(b.statesFuntcion, (PartialFunction) defaultStatesFunction);
        this.name = b.name;
    }

    /**
     * first state of Automaton;
     */
    protected final S getInitialState() {
        return this.initialState;
    }

    @Override @SuppressWarnings("unchecked") public void setInitialState(Object s1) {
        this.initialState = (S) checkNotNull(s1);
        checkStateBelongsAutomaton(initialState);
    }

    @Override public void setMaxAge(long maxAge, TimeUnit unit) {
        this.maxAge = unit.toNanos(maxAge);
    }

    @Override public boolean isRestartable() {
        return true;
    }
    
    /**
     * @return ticker value (nano seconds)
     */
    public final long currentClock() {
    	return ticker.read();
    }
   

    /**
     * you can redefine step method to use switch-case instead of
     * statesFunction;
     */
    protected StepResult step(S currentState) {
        checkAutomaton(statesFunction.isDefinedAt(currentState), "current state undefined");
        final AbstractStep<AbstractAutomaton<S>> step = statesFunction.apply(currentState);
        if (step instanceof Step) {
            final Step<AbstractAutomaton<S>> theStep = (Step<AbstractAutomaton<S>>) step;
            return theStep.step(this);
        } else if (step instanceof ReactStep) {
            @SuppressWarnings("unchecked") final ReactStep<AbstractAutomaton<S>, Object> theStep = (ReactStep<AbstractAutomaton<S>, Object>) step;
            return nextReact(theStep.future(this), theStep.asHandler(this));
        } else {
            throw errorInCurrentState("no other step kinds should be defined");
        }
    }

    /**
     * think twice if you want recover any errors(!) it's not advised you
     * override this method
     */
    protected void onError(S state, Throwable error) {
        if (log.isTraceEnabled())
            log.trace("automaton error, state = " + state, error);

        notifyStop(StopReason.ERROR, error);
    }

    protected void onStopped(StopReason reason, @Nullable Throwable error) {
        if (log.isTraceEnabled()) {
            log.trace("automaton manual stop at state = {} ", getCurrentState());
        }
    }

    private void notifyStop(StopReason reason, @Nullable Throwable error) {
        stepFuture = null;
        stepFutureHandler = null;
        try {
            onStopped(reason, error);
        } finally {
            stopFuture.set(new StopDescriptionImpl(reason, error, getCurrentState()));
            currentState = null;
        }
    }

    protected final S getCurrentState() {
        return currentState;
    }

    public final String getName() {
        return name;
    }

    // / "NEXT"-methods
    // / These methods should be called in the return-statement of your states
    // step-functions;
    // / return nextXXXX();

    /**
     * main "next" method;
     * navigates to some other automaton state
     */
    protected final StepResult next(@Nullable S nextState, long delay, TimeUnit unit) {
        nextCallCount++;
        currentState = nextState;
        currentDelay = delay;
        currentDelayUnit = unit;
        stepFuture = null;
        stepFutureHandler = null;
        return StepResult.OK;
    }
    
    protected final StepResult next(@Nullable S nextState, long delay) {
       return next(nextState, delay, defaulDelaytUnit);
    }

    /**
     * next state = null, stops the automaton; similar to next(null);
     */
    protected final StepResult nextEnd() {
        return next(null);
    }

    protected final StepResult next(S nextState) {
        return next(nextState, 0);
    }
    
    /**
     * 
     * @param nextState will be run in the same runnable (same thread)
     * @return
     */
    protected final StepResult nextJoin(S nextState) {
        return next(nextState, JOIN_PSEUDO_DELAY);
    }

    
    protected final StepResult nextSameState() {
        return next(currentState);
    }
    
    protected final StepResult nextJoinSameState() {
        return nextJoin(currentState);
    }

    /**
     * This method links automaton to the world of external events;
     * @param handler can also be {@link FunctionWithError} if you want to handle errors
     */
    protected final <T> StepResult nextReact(ListenableFuture<T> future, Function<T, StepResult> handler) {
        nextCallCount++;
        currentDelay = 0;
        this.stepFuture = future;
        this.stepFutureHandler = handler;
        return StepResult.OK;
    }

    final void beforeStep() {
        nextCallCount = 0;
    }

    final void afterStep(StepResult result) {
        checkAutomaton(result == StepResult.OK, "never redefine nextXXXX() methods(!), introduce new if needed.");
        checkAutomaton(nextCallCount == 1,
                "nextXXXX() method should be called ONCE per automaton step, in return statement(!)");
    }

    // optionally override this method to check states from larger domains
    // (integer, strings, ...);
    protected void checkStateBelongsAutomaton(S state) {
        // checkState(state == null || statesFunction.isDefinedAt(state),
        // "error: state doesn't belong this automaton");
    }

    private final Runnable runnableContinuation = new Runnable() {
        @Override public void run() {
            continueExecution(currentSched, true);
        }
    };

    /**
     * call this method to start automaton;
     * 
     * @param sched
     */
    @Override public ListenableFuture<? extends StopDescription<S>> start(AutomatonScheduler sched) {
        checkArgument(stopFuture == null || stopFuture.isDone());
        currentSched = checkNotNull(sched);
        stopFlag = false;
        currentState = getInitialState();
        checkNotNull(currentState, "degenerated automaton which stops in its initial state(null) is strange!");
        stopFuture = SettableFuture.create(); // mem visibility(!)
        startTime = ticker.read();
        maxTime = null;
        if (maxAge != null) {
            maxTime = startTime + maxAge;
        }
        continueExecution(sched, true);
        return stopFuture;
    }

    @Override public final ListenableFuture<? extends StopDescription<S>> stop() {
    	checkState(supportsManualStop, "manual stop must be enabled");
        checkAutomaton(stopFuture != null, "not started");
        stopFlag = true;
        return stopFuture;
    }

    @Override public final boolean isStopped() {
        return stopFuture != null || stopFuture.isDone();
    }

    protected final IllegalStateException errorStateUndefined(S state) {
        return new AutomatonStateException("[Automaton error: " + this + "] state undefined:" + state);
    }

    protected final IllegalStateException errorInCurrentState(String errorText) {
        return new AutomatonStateException("[Automaton error: " + this + "] in state: " + getCurrentState() + ": "
                + errorText);
    }

    protected final void checkAutomaton(boolean cond, String msg) {
        if (!cond) {
            throw new AutomatonStateException("[Automaton check failure:" + this + "]: " + msg);
        }
    }

    /**
     * Core automaton logic;
     */
    final void continueExecution(final AutomatonScheduler sched, final boolean doStep) {
        if (currentState != null) {
            checkStateBelongsAutomaton(currentState);
        }
        try {
            if (supportsManualStop && stopFlag) {
                notifyStop(StopReason.MANUAL, null);
                return;
            }

            if (currentState == null) {
                notifyStop(StopReason.NATURAL, null);
                return;
            }
            
            if (maxTime != null && isBefore(maxTime, currentClock())) {
                notifyStop(StopReason.AGE, null);
                return;
            }
            if (doStep) {
            	beforeStep();
            	afterStep(step(currentState));
            }
            
            while (currentDelay < 0) {
            	assert currentDelay == JOIN_PSEUDO_DELAY;
            	// join steps;
            	beforeStep();
            	afterStep(step(currentState));
            }
            
            if (stepFuture == null) {
                // normal step
                sched.submit(runnableContinuation, currentDelay, currentDelayUnit);
            } else {
                // wait step
                final ListenableFuture<?> future = this.stepFuture;
                @SuppressWarnings("unchecked") final Function<Object, StepResult> handler = (Function<Object, StepResult>) stepFutureHandler;
                this.stepFuture = null;
                this.stepFutureHandler = null;
                Futures.addCallback(future, new FutureCallback<Object>() {

                    @Override public void onSuccess(Object result) {
                    	beforeStep();
                        afterStep(handler.apply(result));
                        continueExecution(sched, false);
                    }

                    @Override public void onFailure(Throwable t) {
                        if (handler instanceof FunctionWithError) {
                            beforeStep();
                            afterStep(((FunctionWithError<Object, StepResult>)handler).error(t));
                            continueExecution(sched, false);
                        } else {
                            onError(currentState, t);
                        }
                    }
                }, sched.futuresExecutor());
            }
        } catch (AutomatonStateException ase) {
            // non-recoverable
            log.error("Automaton inner logic disaster", ase);
            notifyStop(StopReason.ERROR, ase);
        } catch (Throwable error) {
            onError(currentState, error);
            if (getCurrentState() != null) {
                // if error was recovered;
                continueExecution(sched, true);
            }
        }

    }

    public class StopDescriptionImpl implements StopDescription<S> {
        private final @Nullable Throwable error;
        private final StopReason reason;
        private final long age;
        private final S lastState;

        public StopDescriptionImpl(StopReason reason, @Nullable Throwable error, @Nullable S lastState) {
            this.error = error;
            this.reason = reason;
            this.age = ticker.read() - startTime;
            this.lastState = lastState;
        }

        @Override public @Nullable Throwable getError() {
            return error;
        }

        @Override public StopReason getReason() {
            return reason;
        }

        @Override public long getAge(TimeUnit unit) {
            return unit.convert(age, TimeUnit.NANOSECONDS);
        }

        @Override public @Nullable S getLastState() {
            return lastState;
        }
        @Override public String toString() {
            return "StopDescriptionImpl [error=" + error + ", reason=" + reason + ", age=" + age + ", lastState="
                    + lastState + "]";
        }
        
        
    }

    // ensure that client will never be able to write his/her own nextXXXX()
    // method;
    protected static class StepResult {
        final static StepResult OK = new StepResult();
    }

    protected static abstract class BuilderBase<This extends BuilderBase<?, ?, ?>, S, A extends AbstractAutomaton<S>> {

        @Nullable Long maxAge; // null = forever;
        S initialState;
        PartialFunction<S, ? extends AbstractStep<A>> statesFuntcion;
        String name;
        Ticker ticker = Ticker.systemTicker();
        TimeUnit defaultUnit = TimeUnit.MILLISECONDS;
        boolean supportsManualStop;

        protected BuilderBase(S initialState) {
            this.initialState = initialState;
        }

        protected BuilderBase() {
        }

        public This initialState(S initialState) {
            this.initialState = initialState;
            return getThis();
        }

        public final This maxAge(long duration, TimeUnit unit) {
            this.maxAge = unit.toNanos(duration);
            return getThis();
        }

        public final This name(String name) {
            this.name = name;
            return getThis();
        }

        public final This ticker(Ticker ticker) {
            this.ticker = ticker;
            return getThis();
        }
        
        public final This defaultDelayUnit(TimeUnit unit) {
        	this.defaultUnit = unit;
        	return getThis();
        }
        
        public final This enableManualStop() {
        	this.supportsManualStop = true;
        	return getThis();
        }
        
        // unstable api - may change in fututure. too abstract...
        public final This statesFunction(PartialFunction<S, ? extends AbstractStep<A>> pf) {
            Preconditions.checkState(statesFuntcion == null, "partial function already defined");
            this.statesFuntcion = checkNotNull(pf);
            return getThis();
        }

        /**
         * to eat unchecked warnings in one place;
         */
        @SuppressWarnings("unchecked") protected final This getThis() {
            return (This) this;
        }
    }

    public static abstract class AbstractStep<A extends AbstractAutomaton<?>> {
    }

    public static abstract class Step<A extends AbstractAutomaton<?>> extends AbstractStep<A> {
        public abstract StepResult step(A self);
    }

    public abstract static class ReactStep<A extends AbstractAutomaton<?>, V> extends AbstractStep<A> {
        public abstract ListenableFuture<V> future(A self);

        public abstract StepResult step(A self, V result);

        Function<V, StepResult> asHandler(final A self) {
            return new Function<V, StepResult>() {
                @Override public StepResult apply(V input) {
                    return step(self, input);
                }
            };
        }
    }

    protected static final PartialFunction<Object, AbstractStep<AbstractAutomaton<Object>>> defaultStatesFunction = new PartialFunction<Object, AbstractStep<AbstractAutomaton<Object>>>() {
        @Override public boolean isDefinedAt(Object state) {
            return true;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" }) @Override public AbstractStep<AbstractAutomaton<Object>> apply(
                Object state) {
            if (state instanceof Supplier) {
                return ((Supplier<AbstractStep>) state).get();
            }
            if (state instanceof AbstractStep) {
                return ((AbstractStep) state);
            }
            return null;
        }
    };

    public String toString() {
        return "[ " + super.toString() + " name: " + name + "]";
    }
    
    public static void main(String[] args) {
		System.out.println("NANOTIME " + System.nanoTime() + "=" + System.currentTimeMillis());
	}
}
