package io.papermc.restamp;

import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.ModifierChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.Java17Parser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A simple helper utility for function tests on restamp.
 */
public class RestampFunctionTestHelper {

    /**
     * Constructs a new restamp input object from a single java class' source in a string.
     *
     * @param accessTransformSet the access transformers to apply.
     * @param javaClassSource    the source code of a java class.
     *
     * @return the constructed restamp input.
     */
    @NotNull
    public static RestampInput inputFromSourceString(@NotNull final AccessTransformSet accessTransformSet,
                                                     @NotNull final String javaClassSource) {
        final Java17Parser java17Parser = Java17Parser.builder().build();
        final InMemoryExecutionContext executionContext = new InMemoryExecutionContext(t -> Assertions.fail("Failed to parse inputs", t));
        final List<SourceFile> sourceFiles = java17Parser.parseInputs(
            List.of(Parser.Input.fromString(javaClassSource)),
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
    @NotNull
    public static J.Modifier modifierFrom(@NotNull final Space space, @NotNull final J.Modifier.Type type) {
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
    @NotNull
    public static String accessChangeToModifierString(@NotNull final AccessChange accessChange,
                                                      @Nullable final String @NotNull ... followingModifiers) {
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
            return MODIFIERS.stream().flatMap(given ->
                MODIFIERS.stream().flatMap(target ->
                    Stream.of("static", null).map(staticModifier -> Arguments.of(
                        given, target, staticModifier
                    ))
                )
            );
        }

    }

}
