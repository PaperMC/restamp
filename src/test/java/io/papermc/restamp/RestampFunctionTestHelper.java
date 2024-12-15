package io.papermc.restamp;

import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.ModifierChange;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.Java21Parser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A simple helper utility for function tests on restamp.
 */
@NullMarked
public class RestampFunctionTestHelper {

    /**
     * Constructs a new restamp input object from a single java class' source in a string.
     *
     * @param accessTransformSet the access transformers to apply.
     * @param javaClassesSource    the source code of a java class.
     *
     * @return the constructed restamp input.
     */
    public static RestampInput inputFromSourceString(final AccessTransformSet accessTransformSet,
                                                     final String... javaClassesSource) {
        final Java21Parser javaParser = Java21Parser.builder().build();
        final InMemoryExecutionContext executionContext = new InMemoryExecutionContext(t -> Assertions.fail("Failed to parse inputs", t));
        final List<SourceFile> sourceFiles = javaParser.parseInputs(
            Arrays.stream(javaClassesSource).map(Parser.Input::fromString).toList(),
            null,
            executionContext
        ).toList();

        return new RestampInput(executionContext, sourceFiles, accessTransformSet, false);
    }

    /**
     * Constructs a new modifier for testing purposes.
     *
     * @param space the space of the modifier.
     * @param type  the type of the modifier.
     *
     * @return the modifier that was created.
     */
    public static J.Modifier modifierFrom(final Space space, final J.Modifier.Type type) {
        return new J.Modifier(Tree.randomId(), space, Markers.EMPTY, null, type, Collections.emptyList());
    }

    /**
     * Returns the respective modifier string (for usage in java source code) the access change represents suffixed by the following modifiers.
     *
     * @param accessChange       the access change.
     * @param followingModifiers the following modifiers to be printed after the access change. If an element null, it is ignored.
     *
     * @return the stringify modifier.
     */
    public static String accessChangeToModifierString(final AccessChange accessChange,
                                                      @Nullable final String... followingModifiers) {
        final String accessChangeAsModifier = switch (accessChange) {
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case PUBLIC -> "public";
            default -> "";
        };

        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(accessChangeAsModifier);

        for (final String followingModifier : followingModifiers) {
            if (followingModifier == null) continue;

            if (!stringBuilder.isEmpty()) stringBuilder.append(" ");
            stringBuilder.append(followingModifier);
        }

        return stringBuilder.toString();
    }

    public record TestCodeStyle(boolean includesLeadingAnnotation, boolean leadingSpace) {

    }

    /**
     * An argument provider that provides all combinations of known visibility modifiers (private, public, protected and nothing (package private)).
     */
    public static final class CartesianVisibilityArgumentProvider implements ArgumentsProvider {

        /**
         * This list does not include {@link AccessChange#PROTECTED}. Switching from private <-> public already
         * checks if switching between two modifiers works. No need to blow up the tests even more.
         */
        private static final List<AccessTransform> MODIFIERS = List.of(
            AccessTransform.of(AccessChange.PRIVATE, ModifierChange.ADD),
            AccessTransform.of(AccessChange.PRIVATE, ModifierChange.REMOVE),
            AccessTransform.of(AccessChange.PUBLIC, ModifierChange.ADD),
            AccessTransform.of(AccessChange.PUBLIC, ModifierChange.REMOVE),
            AccessTransform.of(AccessChange.PACKAGE_PRIVATE, ModifierChange.ADD),
            AccessTransform.of(AccessChange.PACKAGE_PRIVATE, ModifierChange.REMOVE)
        );

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return provideArguments();
        }

        static Stream<? extends Arguments> provideArguments() {
            return MODIFIERS.stream().flatMap(given ->
                MODIFIERS.stream().flatMap(target ->
                    Stream.of("static", null).map(staticModifier ->
                        Arguments.of(given, target, staticModifier)
                    )
                )
            );
        }

    }

    /**
     * An argument provider that provides all combinations of known visibility modifiers (private, public, protected and nothing (package private))
     * and code styles as defined in {@link TestCodeStyle}.
     */
    public static final class CartesianVisibilityArgumentAndStyleProvider implements ArgumentsProvider {

        private static Object[] concat(final Object[] first, final Object... other) {
            final Object[] result = new Object[first.length + other.length];
            System.arraycopy(first, 0, result, 0, first.length);
            System.arraycopy(other, 0, result, first.length, other.length);
            return result;
        }

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return CartesianVisibilityArgumentProvider.provideArguments().flatMap(arguments ->
                Stream.of(true, false).flatMap(includeAnnotation ->
                    Stream.of(true, false).map(leadingSpace ->
                        Arguments.arguments(concat(arguments.get(), new TestCodeStyle(includeAnnotation, leadingSpace)))
                    )
                )
            );
        }

    }

}
