package io.papermc.restamp;

import org.cadixdev.at.AccessTransformSet;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.Java21Parser;

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
@NullMarked
public record RestampInput(
    ExecutionContext executionContext,
    List<SourceFile> sources,
    AccessTransformSet accessTransformers,
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
    public static RestampInput parseFrom(final RestampContextConfiguration contextConfiguration) {
        final Java21Parser parser = Java21Parser.builder().classpath(contextConfiguration.classpath()).build();

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
