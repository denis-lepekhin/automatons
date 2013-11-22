package automatons.automatons;

import automatons.automatons.sched.AutomatonScheduler;

import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;


/**
 * Automaton external control.
 * 
 * @see AbstractAutomaton
 */
public interface Automaton<S> {

    interface StopDescription<S> {
        StopReason getReason();

        /**
         * will return {@link AutomatonStateException} if automaton was ill-designed;
         */
        @Nullable Throwable getError();

        /**
         * how old was automaton when it has stopped;
         */
        long getAge();

        @Nullable S getLastState();
    }

    enum StopReason {
        NATURAL, AGE, ERROR, MANUAL
    }


    /**
     * should be called once or multiple times, depending on isRestartable()
     */
    ListenableFuture<? extends StopDescription<S>> start(AutomatonScheduler sched);

    /**
     * manual stop; if automaton isStopped()==true this method has no effect;
     * call it AFTER start() never before!;
     */
    ListenableFuture<? extends StopDescription<S>> stop();

    boolean isStopped();

    /**
     * effective ONLY before start()
     */
    void setInitialState(S s1);

    /**
     * effective ONLY before start()
     * 
     * @param maxAge null means age is unbounded (usually milliseconds, but depends on a ticker);
     */
    void setMaxAge(@Nullable Long maxAge);

    /**
     * can automaton be started again after it has stopped;
     */
    boolean isRestartable();
}
