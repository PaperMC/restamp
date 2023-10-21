package io.papermc.restamp;

import org.cadixdev.at.AccessTransformSet;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.Java17Parser;

import java.util.List;

/**
 * The restamp input record type holds all values needed to run restamp.
 *
 * @param executionContext                        the execution context used for running restamp.
 * @param sources                                 a list of source files that restamp should iterate over.
 * @param accessTransformers                      the set of access transformers that restamp should apply to the provided source files.
 * @param failWithNotApplicableAccessTransformers whether restamp should fail if not all access transformers defined in {@code accessTransformers}
 *                                                were consumed by restamp.
 */
public record RestampInput(
    @NotNull ExecutionContext executionContext,
    @NotNull List<SourceFile> sources,
    @NotNull AccessTransformSet accessTransformers,
    boolean failWithNotApplicableAccessTransformers
) {

    /**
     * Parses a ready-to-use restamp input type from the passed context configuration.
     * This process is not cheap as the entire source set is parsed.
     *
     * @param contextConfiguration the context configuration of restamp, used to parse the inputs.
     *
     * @return the parsed restamp input, ready for consumption via {@link Restamp#run(RestampInput)}.
     */
    @NotNull
    public static RestampInput parseFrom(@NotNull final RestampContextConfiguration contextConfiguration) {
        final Java17Parser parser = Java17Parser.builder().classpath(contextConfiguration.classpath()).build();

        final List<SourceFile> sourceFiles = parser.parse(
            contextConfiguration.sourceFiles(),
            contextConfiguration.sourceRoot(),
            contextConfiguration.executionContext()
        ).toList();

        return new RestampInput(
            contextConfiguration.executionContext(),
            sourceFiles,
            contextConfiguration.accessTransformSet(),
            contextConfiguration.failWithNotApplicableAccessTransformers()
        );
    }

}
