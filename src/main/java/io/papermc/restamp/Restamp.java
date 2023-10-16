package io.papermc.restamp;

import io.papermc.restamp.at.AccessTransformerTypeConverter;
import io.papermc.restamp.at.ModifierWidener;
import io.papermc.restamp.recipe.ClassATMutator;
import io.papermc.restamp.recipe.FieldATMutator;
import io.papermc.restamp.recipe.MethodATMutator;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.io.AccessTransformFormats;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.Java17Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Restamp {

    private static final Path ATS = Path.of("/Users/lynx/workspace/paper/paper/.gradle/caches/paperweight/taskCache/mergeAdditionalAts.at");
    private static final Path SOURCE_FILES = Path.of("/Users/lynx/workspace/paper/mache/versions/23w40a/src/main/java");

    public static void main(final String[] args) throws IOException {
        final List<Path> classpath = Files.readAllLines(Path.of("classpath.txt")).stream().map(Path::of).toList();

        final InMemoryExecutionContext executionContext = new InMemoryExecutionContext(Throwable::printStackTrace);

        // Construct access transformers and recipe helpers
        final AccessTransformSet accessTransformSet = AccessTransformFormats.FML.read(ATS);
        final ModifierWidener modifierWidener = new ModifierWidener();
        final AccessTransformerTypeConverter accessTransformerTypeConverter = new AccessTransformerTypeConverter();

        final List<Path> sourceFiles = accessTransformSet.getClasses().keySet().stream()
                .map(s -> s.replace('.', '/'))
                .map(s -> s.split("\\$", 2)[0] + ".java")
                .distinct()
                .map(SOURCE_FILES::resolve)
                .filter(Files::exists) // includes CB files right now, which mache does not include.
                .toList();

        final Java17Parser java17Parser = Java17Parser.builder().classpath(classpath).build();
        final List<SourceFile> parseResult = java17Parser.parse(sourceFiles, SOURCE_FILES, executionContext).toList();
        final InMemoryLargeSourceSet sourceSet = new InMemoryLargeSourceSet(parseResult);

        final List<Recipe> recipesToRun = List.of(
                new FieldATMutator(accessTransformSet, modifierWidener),
                new ClassATMutator(accessTransformSet, modifierWidener),
                new MethodATMutator(accessTransformSet, modifierWidener, accessTransformerTypeConverter)
        );
        recipesToRun.stream()
                .map(r -> r.run(sourceSet, executionContext))
                .flatMap(r -> r.getChangeset().getAllResults().stream())
                .forEach(r -> System.out.println(r.diff()));
    }

}
