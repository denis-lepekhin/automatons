package automatons.automatons.sched;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * @author denis.lepekhin@gmail.com
 */
public abstract class AutomatonScheduler {
    /**
     * delay == 0 => immediate;
     * 
     * @param delay
     *            units of delay - are scheduler-dependent (though usually
     *            milliseconds)
     */
    public abstract void submit(final Runnable runnable, final long delay);
    
    public static AutomatonScheduler fromExecutor(final Executor exec) {
        return fromExecutor(exec, null);
    }

    public static AutomatonScheduler fromExecutor(final Executor exec, @Nullable TimeUnit _timeUnits) {
        final ScheduledExecutorService sched = (exec instanceof ScheduledExecutorService) ? (ScheduledExecutorService) exec
                : null;
        final TimeUnit timeUnits = _timeUnits == null ? TimeUnit.MILLISECONDS : _timeUnits;
        return new AutomatonScheduler() {
            @Override public void submit(Runnable runnable, long delay) {
                assert delay >= 0;
                if (delay == 0) {
                    exec.execute(runnable);
                } else {
                    if (sched == null) {
                        throw new IllegalStateException("scheduling is not supported by this executor type: " + 
                                exec.getClass());
                    }
                    sched.schedule(runnable, delay, timeUnits);
                }
            }
        };
        
    }
}
