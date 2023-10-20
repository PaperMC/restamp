package io.papermc.restamp.at;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class ModifierTransformationProgress {

    public record InsertionMarker(int index, @NotNull Space space, boolean useful) {

        InsertionMarker mergeSpace(@NotNull final Space space) {
            return new InsertionMarker(index, mergeSpaceMaxWhitespace(this.space, space), useful);
        }

        InsertionMarker space(@NotNull final Space space) {
            return new InsertionMarker(index, space, useful);
        }

        InsertionMarker useless() {
            return new InsertionMarker(index, space, false);
        }

    }

    public record Result(@NotNull List<J.Modifier> resultingModifiers, @NotNull Space updatedParentSpace) {

    }

    private Space unaccountedSpace = Space.EMPTY;
    private final List<J.Modifier> modifiers;
    @Nullable private InsertionMarker validVisibilitySpot = null;
    private boolean mutatedFromOriginal = false;

    public ModifierTransformationProgress(final List<J.Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    public boolean mutatedFromOriginal() {
        return mutatedFromOriginal;
    }

    public void keepModifier(@NotNull J.Modifier modifier) {
        // If there is a marker at the current index position, consume unaccounted for space into the marker instead of the kept modifier
        // to potentially later use for the new inserted modifier.
        if (validVisibilitySpot != null && validVisibilitySpot.index() == this.modifiers.size() - 1) {
            this.validVisibilitySpot = this.validVisibilitySpot.mergeSpace(unaccountedSpace);
            this.unaccountedSpace = Space.EMPTY;
        }

        // If we have unaccounted for space, consume it by prefixing it to the kept modifiers space.
        if (!this.unaccountedSpace.isEmpty()) {
            modifier = modifier.withPrefix(mergeSpaceMaxWhitespace(this.unaccountedSpace, modifier.getPrefix()));
            this.unaccountedSpace = Space.EMPTY;
        }
        this.modifiers.add(modifier);
    }

    public void dropModifier(@NotNull final J.Modifier modifier) {
        final Space prefix = modifier.getPrefix();

        this.unaccountedSpace = mergeSpaceMaxWhitespace(this.unaccountedSpace, prefix);
        this.mutatedFromOriginal = true;
    }

    public void proposeValidVisibilitySpot() {
        if (validVisibilitySpot != null) return;
        this.validVisibilitySpot = new InsertionMarker(this.modifiers.size(), this.unaccountedSpace, true);
        this.unaccountedSpace = Space.EMPTY;
    }

    public void recordFoundVisibilitySpot() {
        this.validVisibilitySpot = this.validVisibilitySpot != null
            ? this.validVisibilitySpot.useless()
            : new InsertionMarker(0, Space.EMPTY, false);
    }

    @NotNull
    public Result finaliseProgress(
        @NotNull final Supplier<J.Modifier> visibilityModifierCreator,
        @NotNull Space parentSpace
    ) {
        // If we neither found the visibility nor found ones to remove, pretend we found a valid spot at index 0.
        if (this.validVisibilitySpot == null) {
            this.validVisibilitySpot = new InsertionMarker(0, Space.EMPTY, true);

            // Use either the current first modifier's or the parent's space for the inserted one.
            if (!this.modifiers.isEmpty()) {
                final J.Modifier currentFirstModifier = this.modifiers.get(0);
                final Space currentFirstPrefix = currentFirstModifier.getPrefix();
                this.validVisibilitySpot = this.validVisibilitySpot.space(currentFirstPrefix); // set the space.

                this.modifiers.set(0, currentFirstModifier.withPrefix(Space.SINGLE_SPACE));
            } else {
                this.validVisibilitySpot = this.validVisibilitySpot.space(parentSpace);
                parentSpace = Space.SINGLE_SPACE;
            }
        }

        // Insert if the spot is still useful, the visibility was not found.
        if (this.validVisibilitySpot.useful) {
            this.modifiers.add(this.validVisibilitySpot.index, visibilityModifierCreator.get());
            this.mutatedFromOriginal = true;
        }

        // Prefix unaccounted for space into modifier marked as a valid visibility spot.
        // If we have a modifier to mutate, modify its space.
        // Otherwise, prefix it to the unaccounted space left.
        if (this.validVisibilitySpot.index < this.modifiers.size()) {
            final J.Modifier modifierToPrefixSpaceTo = this.modifiers.get(this.validVisibilitySpot.index);
            this.modifiers.set(
                this.validVisibilitySpot.index,
                modifierToPrefixSpaceTo.withPrefix(mergeSpaceMaxWhitespace(this.validVisibilitySpot.space(), modifierToPrefixSpaceTo.getPrefix()))
            );
            if (!this.validVisibilitySpot.space().isEmpty()) this.mutatedFromOriginal = true;
        } else {
            this.unaccountedSpace = mergeSpace(this.validVisibilitySpot.space(), this.unaccountedSpace);
        }

        // Merge the still unaccounted for space into the parent space
        parentSpace = mergeSpaceMaxWhitespace(this.unaccountedSpace, parentSpace);

        return new Result(this.modifiers, parentSpace);
    }

    @NotNull
    public static Space mergeSpace(@NotNull final Space a, @NotNull final Space b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;

        return Space.build(
            Objects.requireNonNull(a.getWhitespace(), "") + Objects.requireNonNull(b.getWhitespace(), ""),
            concat(a.getComments(), b.getComments())
        );
    }

    @NotNull
    public static Space mergeSpaceMaxWhitespace(@NotNull final Space a, @NotNull final Space b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;

        final String aWhiteSpace = Objects.requireNonNull(a.getWhitespace(), "");
        final String bWhitespace = Objects.requireNonNull(b.getWhitespace(), "");
        return Space.build(
            aWhiteSpace.length() > bWhitespace.length() ? aWhiteSpace : bWhitespace,
            concat(a.getComments(), b.getComments())
        );
    }

    private static <T> List<T> concat(final List<T> a, final List<T> b) {
        final List<T> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }

}
