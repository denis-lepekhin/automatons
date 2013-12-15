package automatons.automatons.test;

import static java.lang.System.out;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import automatons.automatons.AbstractAutomaton;
import automatons.automatons.Automaton;
import automatons.automatons.sched.AutomatonScheduler;

import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public class TestWaitJoin {

	private static final Logger logger = LoggerFactory.getLogger(TestWaitJoin.class);

	private static final ScheduledExecutorService exec = Executors.newScheduledThreadPool(4);

	private static final AutomatonScheduler sched = AutomatonScheduler.fromExecutor(exec);

	protected static class WjAutomaton extends AbstractAutomaton<Integer> {

		private final ListenableFuture<String> someFuture;

		protected WjAutomaton(Builder b, ListenableFuture<String> someFuture) {
			super(b);
			this.someFuture = someFuture;
		}

		int a, b, c, total;

		@Override protected StepResult step(Integer currentState) {
			switch (currentState) {
			case 1: {
				out.println("1");
				return nextWait(someFuture, new Function<String, StepResult>() {
					@Override public StepResult apply(String input) {
						out.println("input" + input);
						a++;
						return nextJoin(2);
					}
				});

			}
			case 2: {
				out.println("2");
				++b;
				return next(3);
			}
			case 3: {
				++c;
				checkAutomaton(a + b + c == 3, "invariant");
				return nextEnd();
			}
			default:
				throw errorStateUndefined(currentState);
			}

		}

		public static class Builder extends BuilderBase<Builder, Integer, WjAutomaton> {
			protected Builder(Integer s1) {
				super(s1);
			};

			public WjAutomaton build(ListenableFuture<String> someFuture) {
				return new WjAutomaton(this, someFuture);
			}
		}

		public static Builder builder(Integer s1) {
			return new Builder(s1);
		}
	}

	@Test(timeout = 100000000) public void test() throws Throwable {
		SettableFuture<String> f = SettableFuture.create();

		WjAutomaton wja = WjAutomaton.builder(1).build(f);

		ListenableFuture<? extends Automaton.StopDescription<Integer>> stopFuture = wja.start(sched);
		
		Thread.sleep(1000);
		f.set("OK");
		Automaton.StopDescription<Integer> desc = stopFuture.get();
		
		assert desc.getReason() == Automaton.StopReason.NATURAL;
	}

}
