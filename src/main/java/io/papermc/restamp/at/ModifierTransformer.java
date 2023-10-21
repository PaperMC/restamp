package io.papermc.restamp.at;

import io.papermc.restamp.utils.RecipeHelper;
import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.ModifierChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The modifier transform is responsible for transforming a list of modifiers to match a passed {@link AccessTransform}.
 */
public class ModifierTransformer {

    private static final Set<J.Modifier.Type> KNOWN_MUTABLE_TYPES = EnumSet.of(
        J.Modifier.Type.Public,
        J.Modifier.Type.Protected,
        J.Modifier.Type.Private,
        J.Modifier.Type.Final
    );

    /**
     * Transforms the modifiers passed in as the list to match the access transform goal passed to the method.
     * <p>
     * The method follows a specific algorithm that preserves the initial order of modifiers to minimize the diff
     * of restamp's output.
     * <p>
     * For specifics on the algorithm, see {@link ModifierTransformationProgress}'s javadocs.
     *
     * @param accessTransform the access transform to apply to the modifier list.
     * @param modifiers       the list of modifiers that should be transformed to fit the access transform.
     * @param parentSpace     the current {@link Space} of the parent that owns the modifiers.
     *
     * @return the result of the modification.
     */
    @NotNull
    public ModifierTransformationResult transformModifiers(@NotNull final AccessTransform accessTransform,
                                                           @NotNull final List<J.Modifier> modifiers,
                                                           @NotNull final Space parentSpace) {
        final ModifierTransformationProgress transformationProgress = new ModifierTransformationProgress(new ArrayList<>(modifiers.size()));
        final AccessChange accessChange = accessTransform.getAccess();

        // Compute the access modifier type to keep
        @Nullable final J.Modifier.Type accessTypeToKeep = RecipeHelper.typeFromAccessChange(accessChange);

        // Package private is always found, it is simply *not a modifier*
        if (accessChange == AccessChange.PACKAGE_PRIVATE) {
            transformationProgress.recordFoundVisibilitySpot();
        }

        boolean foundFinal = false;
        for (final J.Modifier modifier : modifiers) {
            // This is not a removable modifier, we keep it in its original relative position.
            if (!KNOWN_MUTABLE_TYPES.contains(modifier.getType())) {
                transformationProgress.keepModifier(modifier);
                continue;
            }

            // Drop final modifier if unwanted/found already or keep it if add/none.
            if (modifier.getType() == J.Modifier.Type.Final) {
                if (accessTransform.getFinal() == ModifierChange.REMOVE) {
                    transformationProgress.dropModifier(modifier);
                } else {
                    transformationProgress.keepModifier(modifier);
                }
                foundFinal = true;
                continue;
            }

            // Drop unwanted visibility modifiers and record potential position for new modifier.
            if (modifier.getType() != accessTypeToKeep) {
                transformationProgress.dropModifier(modifier);
                transformationProgress.proposeValidVisibilitySpot();
                continue;
            }

            transformationProgress.recordFoundVisibilitySpot();
            transformationProgress.keepModifier(modifier);
        }

        // Finalise the progress tracker
        final ModifierTransformationProgress.Result result = transformationProgress.finaliseProgress(
            () -> new J.Modifier(
                Tree.randomId(), Space.EMPTY, Markers.EMPTY, null,
                Objects.requireNonNull(accessTypeToKeep, "package private caused insertion"), Collections.emptyList()
            ),
            parentSpace
        );
        final List<J.Modifier> resultingModifiers = result.resultingModifiers();
        Space updatedParentSpace = result.updatedParentSpace();

        // Potentially suffix a final modifier if needed.
        // Because we insert at the end, we do not have to care about the potential space of a previous modifier.
        // However, we do have to handle the edge case of no modifier existing in the result set, in which case final is the only
        // modifier added, meaning we have to inherit/replace the pareent space.
        boolean mutatedFromOriginal = transformationProgress.mutatedFromOriginal();
        if (!foundFinal && accessTransform.getFinal() == ModifierChange.ADD) {
            Space finalModifierSpace = Space.SINGLE_SPACE;
            if (resultingModifiers.isEmpty()) { // Inherit parent space if needed.
                finalModifierSpace = updatedParentSpace;
                updatedParentSpace = Space.SINGLE_SPACE;
            }

            resultingModifiers.add(new J.Modifier(
                Tree.randomId(), finalModifierSpace, Markers.EMPTY, null, J.Modifier.Type.Final, Collections.emptyList()
            ));
            mutatedFromOriginal = true;
        }

        // Yield back the result, yielding the original list instance if possible.
        return new ModifierTransformationResult(
            mutatedFromOriginal ? resultingModifiers : modifiers,
            updatedParentSpace
        );
    }

}
