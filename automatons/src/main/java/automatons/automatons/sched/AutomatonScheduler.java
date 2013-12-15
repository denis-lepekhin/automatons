package automatons.automatons.sched;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
    public abstract void submit(final Runnable runnable, final long delay, TimeUnit unit);
    
    
    public abstract @Nullable Executor futuresExecutor();
    
    
    
    
    public static AutomatonScheduler make(final Executor instantExec, @Nullable final Executor futuresExec,
    		@Nullable final ScheduledExecutorService sched) {
    	
    	return new AutomatonScheduler() {
			@Override public void submit(Runnable runnable, long delay, TimeUnit unit) {
				if (delay == 0) {
					instantExec.execute(runnable);
				} else {
					checkArgument(delay > 0);
					checkNotNull(sched, "sched?").schedule(runnable, delay, unit);
				}
				
			}
			@Override public @Nullable Executor futuresExecutor() {
				return futuresExec;
			}
		};
    	
    }
    
    
    public static AutomatonScheduler fromExecutor(final Executor exec) {
        final ScheduledExecutorService sched = (exec instanceof ScheduledExecutorService) ? (ScheduledExecutorService) exec
                : null;
        return make(exec, exec, sched);
    }
}
