## Automatons

Statefull concurrent entities which support explicit async state transitions with both sync and async (future-based) steps;

This project was mostly about simplifying the code of random-transitions bots by using some fancy DSL, and NOT consuming a thread per bot.

```java
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
```
