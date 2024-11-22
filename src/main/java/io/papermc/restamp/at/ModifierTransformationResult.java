package io.papermc.restamp.at;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.List;

/**
 * The {@link ModifierTransformationResult} record holds the new modifiers that were the result of the transformation
 * as well as space that could no longer be accounted for due to the removal of modifiers.
 * <p>
 * The unaccounted for space should usually be prefixed to e.g. the fields own space.
 *
 * @param newModifiers the new modifier list for the target.
 * @param parentSpace  the space of the parent.
 */
@NullMarked
public record ModifierTransformationResult(
    List<J.Modifier> newModifiers,
    Space parentSpace
) {

}
