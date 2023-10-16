package io.papermc.restamp.at;

import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.ModifierChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ModifierWidener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModifierWidener.class);

    private static final Set<J.Modifier.Type> KNOWN_VISIBILITY_TYPES = EnumSet.of(
            J.Modifier.Type.Public,
            J.Modifier.Type.Protected,
            J.Modifier.Type.Private
    );

    @NotNull
    public List<J.Modifier> widenModifiers(@NotNull final AccessTransform accessTransform,
                                           @NotNull final List<J.Modifier> modifiers) {
        final List<J.Modifier> result = widenVisibilityModifiers(accessTransform.getAccess(), modifiers);

        // Remove final if needed
        if (accessTransform.getFinal() == ModifierChange.REMOVE) {
            result.removeIf(m -> m.getType() == J.Modifier.Type.Final);
        } else if (accessTransform.getFinal() == ModifierChange.ADD) {
            result.add(new J.Modifier(
                    Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Final, Collections.emptyList()
            ));
        }

        return result;
    }

    @NotNull
    private List<J.Modifier> widenVisibilityModifiers(
            @NotNull final AccessChange accessChange,
            @NotNull final List<J.Modifier> modifiers
    ) {
        if (accessChange == AccessChange.NONE ) return new ArrayList<>(modifiers);

        final List<J.Modifier> modifiersToReturn = new ArrayList<>(modifiers.size());
        @Nullable final J.Modifier.Type typeToKeep = switch (accessChange) {
            case PRIVATE -> J.Modifier.Type.Private;
            case PUBLIC -> J.Modifier.Type.Public;
            case PROTECTED -> J.Modifier.Type.Protected;
            default -> null;
        };

        final ExistingModifierTracker existingModifierTracker = new ExistingModifierTracker();
        for (int i = 0; i < modifiers.size(); i++) {
            final J.Modifier modifier = modifiers.get(i);

            if (!KNOWN_VISIBILITY_TYPES.contains(modifier.getType())) { // If modifier does not concern visibility, keep it.
                modifiersToReturn.add(modifier);
                continue;
            }

            if (modifier.getType() != typeToKeep) { // If it is a visibility modifiers but not our target one, remove it.
                existingModifierTracker.recordRemovedModifier(i, modifier);
                continue;
            }

            existingModifierTracker.recordMatchingModifiers(modifier);  // Record the matching modifier for later.
        }

        if (typeToKeep == null) return modifiersToReturn; // Ne need to add anything, we don't need a resulting modifier, this is package privat.

        // Create the resulting modifier
        final J.Modifier resulting = existingModifierTracker.createResulting(() -> new J.Modifier(
                Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, typeToKeep, Collections.emptyList()
        ));

        // Insert the resulting modifier into the modifier collection at the suggested index
        final int resultingModifierIndex = existingModifierTracker.suggestedResultModifierIndex();
        modifiersToReturn.add(resultingModifierIndex, resulting);

        // Insert a single whitespace prefix into a modifier that potentially previously was at index 0.
        if (resultingModifierIndex == 0 && modifiersToReturn.size() > 1) {
            final J.Modifier followingModifier = modifiersToReturn.get(1);
            if (followingModifier.getPrefix().getWhitespace().isEmpty()) {
                modifiersToReturn.set(1, followingModifier.withPrefix(followingModifier.getPrefix().withWhitespace(" ")));
            }
        }

        return modifiersToReturn;
    }
}
