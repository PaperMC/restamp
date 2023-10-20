package io.papermc.restamp;

import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransformSet;
import org.jetbrains.annotations.NotNull;
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
        final InMemoryExecutionContext executionContext = new InMemoryExecutionContext(t -> {
            t.printStackTrace();
            Assertions.fail("Failed to parse inputs", t);
        });
        final List<SourceFile> sourceFiles = java17Parser.parseInputs(
            List.of(Parser.Input.fromString(javaClassSource)),
            null,
            executionContext
        ).toList();

        return new RestampInput(executionContext, sourceFiles, accessTransformSet);
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
     * Returns the respective modifier string (for usage in java source code) the access change represents.
     *
     * @param accessChange the access change.
     *
     * @return the stringified modifier.
     */
    @NotNull
    public static String accessChangeToModifierString(@NotNull final AccessChange accessChange) {
        return switch (accessChange) {
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case PUBLIC -> "public";
            default -> "";
        };
    }

    /**
     * An argument provider that provides all combinations of known visibility modifiers (private, public, protected and nothing (package private)).
     */
    public static final class CartesianVisibilityArgumentProvider implements ArgumentsProvider {

        private static final List<AccessChange> MODIFIERS = List.of(
            AccessChange.PRIVATE, AccessChange.PUBLIC, AccessChange.PROTECTED, AccessChange.PACKAGE_PRIVATE
        );

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return MODIFIERS.stream()
                .flatMap(s -> MODIFIERS.stream().map(other -> Arguments.of(s, other)));
        }

    }

}
