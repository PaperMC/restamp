package io.papermc.restamp.at;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The modifier transformation progress is a stateful and mutable type that is iterated over an incoming set of
 * {@link org.openrewrite.java.tree.J.Modifier} by the {@link ModifierTransformer}.
 * <p>
 * Its algorithm is straight forward in that it records both kept and dropped modifiers.
 * <p>
 * The first time a visibility modifier is dropped, e.g. {@code private} the progress places a {@link InsertionMarker} at the index of the now
 * dropped
 * modifier to potentially later insert a wanted new modifier.
 * This way, transforming a list of modifiers to have a {@code public} modifier can find the first occurrence of a visibility modifier and insert
 * the wanted {@code public} modifier at said index instead of always prefixing/suffixing it.
 * This way, diffs like
 * <pre>
 * -static private int number = 10;
 * +static public int number = 10;
 * </pre>
 * are possible, leading to a minimal change introduced by restamp.
 * <p>
 * While recording dropped modifiers, the progress merges their {@link Space}s into either an available {@link InsertionMarker} or a general
 * {@code unaccountedSpace} variable. Anytime the progress records a modifier to be kept, all {@code unaccountedSpace} is merged into its space,
 * preserving potential comments or non-normal whitespaces by shifting them from dropped modifiers to the next kept one.
 * {@link Space} tracked on the {@link InsertionMarker} is later applied to the inserted new modifier (like the {@code public} modifier from aboves)
 * example.
 * <p>
 * Finally, after all modifiers have been recorded to be either dropped or kept, the progress finalises itself via
 * {@link #finaliseProgress(Supplier, Space)} by inserting the requested modifier into the previously inserted {@link InsertionMarker} index or,
 * if no suitable space was found, by prefixing it to the modifier list. All {@link Space} of dropped modifiers that were merged into the
 * {@link InsertionMarker} is applied to the inserted modifier and the transformation is finalised.
 *
 * @see #mergeSpace(Space, Space)
 * @see #finaliseProgress(Supplier, Space)
 */
public class ModifierTransformationProgress {

    /**
     * A comparator that compares whitespaces when merging {@link Space} types.
     * We are generally not interested in merging spaces by just appending their whitespaces as, e.g. the following line
     * <pre>{@code
     * static private final int a = 0;
     * }</pre>
     * has a single space of whitespace in-front of both the {@code private} and {@code final} modifier.
     * Simply appending these two would lead to a double space if both are removed.
     * <p>
     * Instead, we only use the longer whitespace or the one that has more new lines in them.
     * This way, we preserve potential un-expected newlines while also not creating unnecessary white spaces.
     */
    private static final Comparator<String> WHITESPACE_COMPARATOR = Comparator
        .<String>comparingInt(s -> countOccurrences(s, System.lineSeparator()))
        .thenComparingInt(s -> s.length() - Math.max(0, s.lastIndexOf(System.lineSeparator())));

    /**
     * The insertion marker used by the progress to mark the suitable location for a later insertion of the modifier wanted.
     *
     * @param index  the index at which to insert the modifier.
     * @param space  the space of the modifier when inserted, build up by the space of the dropped modifiers after the marker.
     * @param useful if the marker is still useful or if e.g. the wanted modifier was already found in the list of modifiers.
     */
    public record InsertionMarker(int index, @NotNull Space space, boolean useful) {

        @NotNull
        InsertionMarker mergeSpace(@NotNull final Space space) {
            return new InsertionMarker(index, ModifierTransformationProgress.mergeSpace(this.space, space), useful);
        }

        @NotNull
        InsertionMarker space(@NotNull final Space space) {
            return new InsertionMarker(index, space, useful);
        }

        @NotNull
        InsertionMarker useless() {
            return new InsertionMarker(index, space, false);
        }

    }

    /**
     * The result type of the progress, containing the modifiers and updated space that should be applied to the parent.
     * The parent in this case could be the field type, method return type or class kind.
     *
     * @param resultingModifiers the list of modifiers.
     * @param updatedParentSpace the space of the parent after the transformation.
     */
    public record Result(@NotNull List<J.Modifier> resultingModifiers, @NotNull Space updatedParentSpace) {

    }

    private Space unaccountedSpace = Space.EMPTY;
    private final List<J.Modifier> modifiers;
    @Nullable private InsertionMarker validVisibilitySpot = null;
    private boolean mutatedFromOriginal = false;

    public ModifierTransformationProgress(final List<J.Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * Indicates if the progress recorded a mutation from the originally passed list.
     *
     * @return the boolean value.
     */
    public boolean mutatedFromOriginal() {
        return mutatedFromOriginal;
    }

    /**
     * Records that a modifier should be kept and appends it into the modifier list.
     * This method also respects a potentially placed marker at the index the new modifier is inserted into,
     * while also merging all unaccounted for space into the passed modifier.
     *
     * @param modifier the modifier to keep.
     */
    public void keepModifier(@NotNull J.Modifier modifier) {
        // If there is a marker at the current index position, consume unaccounted for space into the marker instead of the kept modifier
        // to potentially later use for the new inserted modifier.
        if (validVisibilitySpot != null && validVisibilitySpot.useful && validVisibilitySpot.index() == this.modifiers.size() - 1) {
            this.validVisibilitySpot = this.validVisibilitySpot.mergeSpace(unaccountedSpace);
            this.unaccountedSpace = Space.EMPTY;
        }

        // If we have unaccounted for space, consume it by prefixing it to the kept modifiers space.
        if (!this.unaccountedSpace.isEmpty()) {
            modifier = modifier.withPrefix(mergeSpace(this.unaccountedSpace, modifier.getPrefix()));
            this.unaccountedSpace = Space.EMPTY;
        }
        this.modifiers.add(modifier);
    }

    /**
     * Records that the modifier should be dropped. This method does not add the modifier to the result list but
     * records its {@link Space} into the unaccounted for space.
     *
     * @param modifier the modifier to drop.
     */
    public void dropModifier(@NotNull final J.Modifier modifier) {
        final Space prefix = modifier.getPrefix();

        this.unaccountedSpace = mergeSpace(this.unaccountedSpace, prefix);
        this.mutatedFromOriginal = true;
    }

    /**
     * Proposes a valid spot for the later inserted visibility modifier.
     * If the progress already has marked a spot for said modifier via an {@link InsertionMarker}, this method is a NOOP.
     */
    public void proposeValidVisibilitySpot() {
        if (validVisibilitySpot != null) return;
        this.validVisibilitySpot = new InsertionMarker(this.modifiers.size(), this.unaccountedSpace, true);
        this.unaccountedSpace = Space.EMPTY;
    }

    /**
     * Records that, while iterating the original modifiers, the wanted visibility modifier was found.
     * This will mark any potential marker that already exists or may be placed later as useless.
     */
    public void recordFoundVisibilitySpot() {
        this.validVisibilitySpot = this.validVisibilitySpot != null
            ? this.validVisibilitySpot.useless()
            : new InsertionMarker(0, Space.EMPTY, false);
    }

    /**
     * Finalises this progress by building the {@link Result} of the progress.
     * For the specific algorithm, see {@link ModifierTransformationProgress}.
     *
     * @param visibilityModifierCreator a supplier that may be called to create the wanted visibility modifier and insert it at the computed location.
     * @param parentSpace               the current space of the parent that owns these modifiers.
     *
     * @return the result instance.
     */
    @NotNull
    public Result finaliseProgress(
        @NotNull final Supplier<J.Modifier> visibilityModifierCreator,
        @NotNull Space parentSpace
    ) {
        // If we neither found the visibility nor found ones to remove, pretend we found a valid spot at index 0.
        if (this.validVisibilitySpot == null) {
            this.validVisibilitySpot = new InsertionMarker(0, Space.EMPTY, true);

            // Use either the current first modifier's or the parent's space for the inserted one.
            // If we have no other modifier, we insert the only modifier here.
            // In that case, we also consume all unaccounted for space.
            if (!this.modifiers.isEmpty()) {
                final J.Modifier currentFirstModifier = this.modifiers.get(0);
                final Space currentFirstPrefix = currentFirstModifier.getPrefix();
                this.validVisibilitySpot = this.validVisibilitySpot.space(currentFirstPrefix); // set the space.

                this.modifiers.set(0, currentFirstModifier.withPrefix(Space.SINGLE_SPACE));
            } else {
                this.validVisibilitySpot = this.validVisibilitySpot.space(mergeSpace(this.unaccountedSpace, parentSpace));
                this.unaccountedSpace = Space.EMPTY;
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
                modifierToPrefixSpaceTo.withPrefix(mergeSpace(this.validVisibilitySpot.space(), modifierToPrefixSpaceTo.getPrefix()))
            );
            if (!this.validVisibilitySpot.space().isEmpty()) this.mutatedFromOriginal = true;
        } else {
            this.unaccountedSpace = mergeSpace(this.validVisibilitySpot.space(), this.unaccountedSpace);
        }

        // Merge the still unaccounted for space into the parent space
        parentSpace = mergeSpace(this.unaccountedSpace, parentSpace);

        return new Result(this.modifiers, parentSpace);
    }

    /**
     * Merges two spaces by contacting their comments and choosing the more applicable whitespace based on
     * {@link ModifierTransformationProgress#WHITESPACE_COMPARATOR}. See the comparator for more documentation on the selection algorithm.
     *
     * @param a the first space to merge.
     * @param b the second space to merge.
     *
     * @return the merged space.
     */
    @NotNull
    public static Space mergeSpace(@NotNull final Space a, @NotNull final Space b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;

        final String aWhitespace = a.getWhitespace();
        final String bWhitespace = b.getWhitespace();
        return Space.build(
            WHITESPACE_COMPARATOR.compare(aWhitespace, bWhitespace) >= 0 ? aWhitespace : bWhitespace,
            concat(a.getComments(), b.getComments())
        );
    }

    /**
     * Counts the occurrences of a substring in a larger parent string.
     *
     * @param string the string to count the occurrences of {@code match} in.
     * @param match  the string to count the occurrences of.
     *
     * @return the amount of times {@code match} was found in {@code string}.
     */
    public static int countOccurrences(@NotNull final String string, @NotNull final String match) {
        int counts = 0;
        int currentMatchIndex = 0;
        while ((currentMatchIndex = string.indexOf(match, currentMatchIndex)) >= 0) {
            counts++;
            currentMatchIndex += match.length();
        }
        return counts;
    }

    /**
     * Concat two lists of type T.
     *
     * @param a   the first list to concat.
     * @param b   the second list to concat.
     * @param <T> the generic type of the content of the list.
     *
     * @return the concatted list.
     */
    private static <T> List<T> concat(final List<T> a, final List<T> b) {
        final List<T> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }

}
