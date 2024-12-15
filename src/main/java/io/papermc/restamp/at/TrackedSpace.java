package io.papermc.restamp.at;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@NullMarked
public record TrackedSpace(@Nullable Space space) {

    /**
     * A constant null tracked space.
     */
    public static final TrackedSpace NULL = new TrackedSpace(null);

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
     * Merges this tracked space by contacting their comments and choosing the more applicable whitespace based on
     * {@link TrackedSpace#WHITESPACE_COMPARATOR}. See the comparator for more documentation on the selection algorithm.
     *
     * @param other the other space to merge.
     *
     * @return the merged space.
     */
    public TrackedSpace mergeSpace(final TrackedSpace other) {
        return this.mergeSpace(other.space());
    }

    /**
     * Merges this tracked space by contacting their comments and choosing the more applicable whitespace based on
     * {@link TrackedSpace#WHITESPACE_COMPARATOR}. See the comparator for more documentation on the selection algorithm.
     *
     * @param other the other space to merge.
     *
     * @return the merged space.
     */
    public TrackedSpace mergeSpace(final @Nullable Space other) {
        if (other == null || other.isEmpty()) return this;
        if (this.space == null || this.space.isEmpty()) return new TrackedSpace(other);

        final String aWhitespace = this.space.getWhitespace();
        final String bWhitespace = other.getWhitespace();
        return new TrackedSpace(Space.build(
            WHITESPACE_COMPARATOR.compare(aWhitespace, bWhitespace) >= 0 ? aWhitespace : bWhitespace,
            concat(this.space.getComments(), other.getComments())
        ));
    }

    /**
     * Merges the passed space only if this tracking space does not track anything yet.
     *
     * @param space the space to track.
     *
     * @return the tracked space.
     */
    public TrackedSpace mergeIfEmpty(Space space) {
        if (!this.tracksSomething()) return new TrackedSpace(space);
        return new TrackedSpace(this.space.withComments(concat(this.space.getComments(), space.getComments())));
    }

    /**
     * Yields if this tracked space tracks an actual space.
     *
     * @return {@code true} if null.
     */
    public boolean tracksSomething() {
        return this.space != null;
    }

    /**
     * Converts this tracked space into an openrewrite space.
     *
     * @return the space.
     */
    public Space into() {
        return space == null ? Space.EMPTY : space;
    }

    /**
     * Counts the occurrences of a substring in a larger parent string.
     *
     * @param string the string to count the occurrences of {@code match} in.
     * @param match  the string to count the occurrences of.
     *
     * @return the amount of times {@code match} was found in {@code string}.
     */
    private static int countOccurrences(final String string, final String match) {
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
