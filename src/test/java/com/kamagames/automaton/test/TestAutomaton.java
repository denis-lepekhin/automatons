package com.kamagames.automaton.test;

import static automatons.automatons.JumpAutomaton.toDelay;
import static com.kamagames.automaton.test.TestAutomaton.RandomAutomaton.State.stateA;
import static com.kamagames.automaton.test.TestAutomaton.RandomAutomaton.State.stateB;
import static com.kamagames.automaton.test.TestAutomaton.RandomAutomaton.State.stateC;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import automatons.automatons.AbstractAutomaton;
import automatons.automatons.Automaton;
import automatons.automatons.JumpAction;
import automatons.automatons.JumpAutomaton;
import automatons.automatons.random.Randomizers;
import automatons.automatons.sched.AutomatonScheduler;
import automatons.automatons.utility.Intervals;
import automatons.automatons.utility.LongFunction;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.kamagames.automaton.test.TestAutomaton.RandomAutomaton.State;


public class TestAutomaton {
    private static final Logger logger = LoggerFactory.getLogger(TestAutomaton.class);

    private static final AutomatonScheduler sched = AutomatonScheduler.fromExecutor(newScheduledThreadPool(4));


    protected static class AbcAutomaton extends AbstractAutomaton<Integer> {

        protected AbcAutomaton(Builder b) {
            super(b);
        }
        int a, b, c, total;


        @Override protected StepResult step(Integer currentState) {
            switch(currentState) {
            case 1: {
                if (total == 0) {
                    return nextEnd();
                }
                --total;
                ++a;
                return next(2);

            }
            case 2: {
                ++b;
                return next(3);
            }
            case 3: {
                ++c;
                return next(1);
            }
            default:
                throw errorStateUndefined(currentState);
            }

        }
        public static class Builder extends BuilderBase<Builder, Integer, AbcAutomaton> {
            protected Builder(Integer s1) { super(s1); };
            public AbcAutomaton build() {
                return new AbcAutomaton(this);
            }
        }
        public static Builder builder(Integer s1) {
            return new Builder(s1);
        }
    }



    protected static class RandomAutomaton extends JumpAutomaton<TestAutomaton.RandomAutomaton.State> {
        int a, b, c, d, e;

        protected RandomAutomaton(Builder b) {
            super(b);
        }

        protected static enum State implements Supplier<AbstractAutomaton.AbstractStep<RandomAutomaton>> {
            stateA (new StepWithJump<RandomAutomaton>()  {
                @Override public void stepWithJump(RandomAutomaton self) {
                    self.a ++;
                }
            }),
            stateB (new StepWithJump<RandomAutomaton>()  {
                @Override public void stepWithJump(RandomAutomaton self) {
                    self.b ++;
                }
            }),
            stateC (new StepWithJump<RandomAutomaton>()  {
                @Override public void stepWithJump(RandomAutomaton self) {
                    self.c ++;
                }
            }),
            stateE(new StepWithJump<RandomAutomaton>()  {
                @Override public void stepWithJump(RandomAutomaton self) {
                    self.d ++;
                }
            }),
            stateD (new StepWithJump<RandomAutomaton>()  {
                @Override public void stepWithJump(RandomAutomaton self) {
                    self.e ++;
                }
            });

            private StepWithJump<RandomAutomaton> step;

            State(StepWithJump<RandomAutomaton> step) {
                this.step = step;
            }

            @Override
            public StepWithJump<RandomAutomaton> get() {
                return step;
            }
        }

        protected static class Builder extends JumpBuilder<Builder, State, RandomAutomaton> {
            protected Builder() {super(State.class);};
            public RandomAutomaton build() {
                return new RandomAutomaton(this);
            }
        }
        public static Builder builder() {
            return new Builder();
        }
    }


    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testAbc() throws Throwable {
        AbcAutomaton a = AbcAutomaton.builder(1).build();
        final int total = 10000;
        a.total = total;
        Automaton.StopDescription<?> d = a.start(sched).get();
        assertEquals(a.a, total);
        assertEquals(a.b, total);
        assertEquals(a.c, total);
        assertEquals(d.getReason(), Automaton.StopReason.NATURAL);
    }


    @Test(timeout=100000000)
    public void testRandom() throws Throwable {
        LongFunction<RandomAutomaton> delay = toDelay(Intervals.longInterval(0, 20));
        double P = 0.5;
        RandomAutomaton a = RandomAutomaton
                .builder()
                .randomizer(Randomizers.uniform())
                .jumpsBegin(stateA)
                .jump(stateA, stateB).maybe(P).delay(delay)
                .loop(stateA).nodelay()
                .jump(stateB, stateC).maybe(P).delay(delay)
                .loop(stateB).nodelay()
                .jump(stateC, null).delay(delay)
                .jumpsEnd()
                .build();

      Automaton.StopDescription<?> d = a.start(sched).get();
      assertEquals(Automaton.StopReason.NATURAL, d.getReason());
      assertTrue(a.a >= 1);
      assertTrue(a.b >= 1);
      assertTrue(a.c >= 1);
      assertTrue(a.isStopped());
    }

    @Test(timeout=100000000)
    public void testProbability() throws Throwable {
        LongFunction<RandomAutomaton> delay = toDelay(Intervals.longInterval(0, 20));
        double Pab = 0.7;
        double Pac = 0.1;

        JumpAction<RandomAutomaton, RandomAutomaton.State> print = new JumpAction<RandomAutomaton, RandomAutomaton.State>() {
            @Override
            public void action(RandomAutomaton self, State sourceState,
                    State targetState) {

            }
        };

        RandomAutomaton a = RandomAutomaton.builder()
                .maxAge(20000L) // 20 sec
                .randomizer(Randomizers.uniform())
                .jumpsBegin(stateA)
                .jump(stateA, stateB).maybe(Pab).act(print).delay(delay)
                .jump(stateA, stateC).maybe(Pac).act(print).delay(delay)
                .loop(stateA).act(print).delay(delay)
                .jump(stateB, stateA).delay(delay)
                .jump(stateC, stateA).delay(delay)
                .jumpsEnd()
                .build();
      Automaton.StopDescription<?> death = a.start(sched).get();
      assertEquals(death.getReason(), Automaton.StopReason.AGE);
      assertTrue(a.a > a.b && a.b > a.c && a.c >= 1);
      logger.debug("rand {} {} {}", a.b /(double) (a.c+1), a.c, a.b);
      assertTrue(a.isStopped());
    }


    @Test(timeout=100000000)
    public void testConditions() throws Throwable {
        LongFunction<RandomAutomaton> delay = toDelay(Intervals.longInterval(0, 20));
        final double Paa = 0.1; // the rest; i.e. 1 - Pab - Pac
        RandomAutomaton a = RandomAutomaton
                .builder()
                .maxAge(20000L) // 20 sec
                .randomizer(Randomizers.uniform())
                .jumpsBegin(stateA)
                .jump(stateA, stateB).when(new Predicate<RandomAutomaton>() {
                    @Override
                    public boolean apply(RandomAutomaton a) {
                        return a.getRandomizer().nextDouble() < 0.9;
                    }
                }).delay(delay)
                .jump(stateA, stateC).when(new Predicate<RandomAutomaton>() {
                    @Override
                    public boolean apply(RandomAutomaton a) {
                        return a.getRandomizer().nextDouble() > 0.9;
                    }
                }).delay(delay)
                .loop(stateA).nodelay()
                .jump(stateB, stateA).delay(delay)
                .jump(stateC, stateA).delay(delay)
                .jumpsEnd()
                .build();

      Automaton.StopDescription d = a.start(sched).get();
      assertEquals(d.getReason(), Automaton.StopReason.AGE);
      logger.debug("cond {} {} {}", a.b /(double) (a.c+1), a.c, a.b);
      assertTrue(a.a > a.b && a.b > a.c && a.c >= 1);
      assertTrue(a.isStopped());
    }
}
