package io.papermc.restamp;

import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.io.AccessTransformFormat;
import org.cadixdev.at.io.AccessTransformFormats;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.ExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The restamp input configuration record holds unparsed data that can be parsed into {@link RestampInput} to be then consumed by
 * {@link Restamp#run(RestampInput)}.
 * <p>
 * This type mainly exists to easily construct/parse restamp input without having to call the underlying rewrite logic to do so manually,
 * This is hence the main configuration entry point for third-party consumers of restamp.
 *
 * @param executionContext                        the execution context used for both parsing and running restamp.
 * @param accessTransformSet                      the set of access transformers to apply to the source files.
 * @param sourceRoot                              the path to a common root folder of all source files in {@code sourceFiles}.
 * @param sourceFiles                             the list of paths pointing to the source files restamp should apply access transformers to.
 * @param classpath                               a list of paths pointing to jars that makeup the classpath for the to be parsed source files.
 * @param failWithNotApplicableAccessTransformers whether restamp should fail if not all access transformers defined in {@code accessTransformers}
 *                                                were consumed by restamp.
 */
public record RestampContextConfiguration(
    @NotNull ExecutionContext executionContext,
    @NotNull AccessTransformSet accessTransformSet,
    @NotNull Path sourceRoot,
    @NotNull List<Path> sourceFiles,
    @NotNull List<Path> classpath,
    boolean failWithNotApplicableAccessTransformers
) {

    /**
     * Constructs a new builder for the input configurations.
     *
     * @return the build instance.
     */
    @NotNull
    @Contract(value = "-> new", pure = true)
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for the {@link RestampContextConfiguration} that allows easy construction of the record.
     */
    public static class Builder {

        private @Nullable ExecutionContext executionContext;
        private @Nullable AccessTransformSet accessTransformSet;
        private @Nullable Path sourceRoot;
        private @Nullable List<Path> sourceFiles;
        private boolean sourceFilesFromAT;
        private boolean failWithNotApplicableAccessTransformers = false;

        private @NotNull List<Path> classpath = Collections.emptyList();

        /**
         * Sets the execution context used by restamp for both parsing and running.
         *
         * @param executionContext the execution context.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_ -> this", mutates = "this")
        public Builder executionContext(@NotNull final ExecutionContext executionContext) {
            this.executionContext = executionContext;
            return this;
        }

        /**
         * Sets the path pointing to the file holding the access transformers.
         * This method will assume the access transformers in the provided path are in the forge mod loader format.
         *
         * @param accessTransformerPath the path to the access transformers.
         *
         * @throws IOException when something goes wrong loading the accessTransformSet
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_ -> this", mutates = "this")
        public Builder accessTransformers(@NotNull final Path accessTransformerPath) throws IOException {
            return this.accessTransformers(accessTransformerPath, AccessTransformFormats.FML);
        }

        /**
         * Sets the path pointing to the file holding the access transformers.
         *
         * @param accessTransformerPath   the path to the access transformers.
         * @param accessTransformerFormat the format of the access transformers defined in the file at the provided path.
         *
         * @throws IOException when something goes wrong loading the accessTransformSet
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_,_ -> this", mutates = "this")
        public Builder accessTransformers(@NotNull final Path accessTransformerPath, @NotNull final AccessTransformFormat accessTransformerFormat) throws IOException {
            this.accessTransformSet = accessTransformerFormat.read(accessTransformerPath);
            return this;
        }

        /**
         * Sets the path pointing to the file holding the access transformers.
         *
         * @param accessTransformerPath   the path to the access transformers.
         * @param accessTransformerFormat the format of the access transformers defined in the file at the provided path.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_,_ -> this", mutates = "this")
        public Builder accessTransformSet(@NotNull final AccessTransformSet accessTransformSet) {
            this.accessTransformSet = AccessTransformSet.create();
            this.accessTransformSet.merge(accessTransformSet);
            return this;
        }

        /**
         * Sets the common source root directory holding the {@link #sourceFiles(List)}.
         *
         * @param sourceRoot the path to the common source root directory.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_ -> this", mutates = "this")
        public Builder sourceRoot(@NotNull final Path sourceRoot) {
            this.sourceRoot = sourceRoot;
            return this;
        }

        /**
         * Sets the list of paths to the source files restamp should apply access transformers to.
         *
         * @param sourceFiles the list of source file paths.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_ -> this", mutates = "this")
        public Builder sourceFiles(@NotNull final List<Path> sourceFiles) {
            this.sourceFiles = sourceFiles;
            return this;
        }

        /**
         * Configures this builder to compute the source files needed during the {@link #build()} process
         * based on the {@link #accessTransformers(Path)} and {@link #sourceRoot(Path)} if the {@link #sourceFiles(List)} is empty or null.
         * <p>
         * If {@link #sourceFiles(List)} is called on this builder with a non-empty list, this option is meaningless.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "-> this", mutates = "this")
        public Builder sourceFilesFromAccessTransformers() {
            this.sourceFilesFromAT = true;
            return this;
        }

        /**
         * Sets the list of paths to the jars making up the classpath for the restamp run.
         *
         * @param classpath the list of classpath relevant jars.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "_ -> this", mutates = "this")
        public Builder classpath(@NotNull final List<Path> classpath) {
            this.classpath = classpath;
            return this;
        }

        /**
         * Configures restamp to fail if not all access transformers were applied during the restamp run
         * due to the fields/classes/methods referenced not existing anymore.
         *
         * @return this builder.
         */
        @NotNull
        @Contract(value = "-> this", mutates = "this")
        public Builder failWithNotApplicableAccessTransformers() {
            this.failWithNotApplicableAccessTransformers = true;
            return this;
        }

        /**
         * Builds the {@link RestampContextConfiguration} record from the builder.
         *
         * @return the constructed restamp input configuration.
         *
         * @throws IllegalStateException if the builder is not fully configured and cannot produce the requested input record.
         * @throws IOException           if parsing the access transformer set failed due to an {@link IOException}.
         */
        @Contract(value = "-> new", pure = true)
        @NotNull
        public RestampContextConfiguration build() throws IllegalStateException {
            if (this.executionContext == null) throw new IllegalStateException("Cannot build without an execution context");
            if (this.accessTransformSet == null) throw new IllegalStateException("Cannot build without access transformers!");
            if (this.sourceRoot == null) throw new IllegalStateException("Cannot build without a source root path!");

            List<Path> effectiveSourceFiles = this.sourceFiles;
            final boolean sourceFilesEmpty = effectiveSourceFiles == null || effectiveSourceFiles.isEmpty();

            if (sourceFilesEmpty) {
                if (!this.sourceFilesFromAT) throw new IllegalStateException("Cannot build without source files!");

                effectiveSourceFiles = this.accessTransformSet.getClasses().keySet().stream() // Compute source files from parsed access transformers.
                    .map(s -> s.replace('.', '/'))
                    .map(s -> {
                        final int firstDollarSign = s.indexOf("$");
                        return (firstDollarSign >= 0 ? s.substring(0, firstDollarSign) : s) + ".java";
                    })
                    .distinct()
                    .map(sourceRoot::resolve)
                    .toList();
            }

            // Ensure all source files exist
            final List<Path> sourceFilesThatDoNotExist = effectiveSourceFiles.stream().filter(p -> !Files.exists(p)).toList();
            if (!sourceFilesThatDoNotExist.isEmpty()) throw new IllegalStateException(
                "Cannot build with source file paths that do not exist: " + Arrays.toString(sourceFilesThatDoNotExist.toArray())
            );

            return new RestampContextConfiguration(
                executionContext,
                this.accessTransformSet,
                sourceRoot,
                effectiveSourceFiles,
                classpath,
                failWithNotApplicableAccessTransformers
            );
        }

    }

}
