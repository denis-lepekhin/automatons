package automatons.automatons;

import javax.annotation.Nullable;

/**
 * Action associated to JumpAutomaton's jump(transition), see
 * {@link JumpAutomaton.Jump}
 * 
 * @author denis.lepekhin
 */
public interface JumpAction<A extends JumpAutomaton<S>, S> {
    void action(A self, S sourceState, @Nullable S targetState);
}