package io.papermc.restamp.at;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class ExistingModifierTracker {

    @Nullable
    private J.Modifier foundMatchingModifier;
    private Space deletedSpace = Space.EMPTY;
    private final List<J.Annotation> annotations = new ArrayList<>();
    private int firstDeletedModifierIndex = -1;

    public void recordRemovedModifier(final int modifierIndex, @NotNull final J.Modifier removed) {
        this.deletedSpace = mergeSpaces(this.deletedSpace, removed.getPrefix());
        this.annotations.addAll(removed.getAnnotations());
        if (this.firstDeletedModifierIndex == -1) this.firstDeletedModifierIndex = modifierIndex;
    }

    public int suggestedResultModifierIndex() {
        return firstDeletedModifierIndex == -1 ? 0 : firstDeletedModifierIndex;
    }

    public void recordMatchingModifiers(@NotNull final J.Modifier matching) {
        foundMatchingModifier = matching;
    }

    public J.Modifier createResulting(@NotNull final Supplier<J.Modifier> empty) {
        final J.Modifier result = this.foundMatchingModifier != null ? this.foundMatchingModifier : empty.get();
        return result
                .withAnnotations(concat(result.getAnnotations(), this.annotations))
                .withPrefix(mergeSpaces(result.getPrefix(), deletedSpace));
    }

    private Space mergeSpaces(@NotNull final Space a, @NotNull final Space b) {
        return Space.build(
                Objects.requireNonNull(a.getWhitespace(), "") + Objects.requireNonNull(b.getWhitespace(), ""),
                concat(a.getComments(), b.getComments())
        );
    }

    private <T> List<T> concat(final List<T> a, final List<T> b) {
        final List<T> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }
}
