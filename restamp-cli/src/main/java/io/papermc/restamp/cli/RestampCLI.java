package io.papermc.restamp.cli;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampContextConfiguration;
import io.papermc.restamp.RestampInput;
import org.openrewrite.Changeset;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@CommandLine.Command(
    name = "restamp",
    versionProvider = RestampCLIVersionProvider.class,
    mixinStandardHelpOptions = true,
    descriptionHeading = "%n",
    parameterListHeading = "%n",
    optionListHeading = "%nOptions:%n",
    showAtFileInUsageHelp = true,
    description = "Applies access transformers to java source files.",
    sortOptions = false,
    usageHelpAutoWidth = true)
public class RestampCLI implements Callable<Integer> {

    public static void main(final String[] args) {
        System.exit(new CommandLine(new RestampCLI()).execute(args));
    }

    @CommandLine.Option(names = {"-cp", "--classpath"}, split = ";", description = "The classpath needed to fully parse the input sources.")
    List<Path> classpath;

    @CommandLine.Parameters(paramLabel = "<inputs>", description = "The list of source files to transform")
    List<Path> inputs;

    @CommandLine.Option(names = {"--source-path"}, description = "The root path of the inputs.", required = true)
    Path sourcePath;

    @CommandLine.Option(names = {"-at"}, description = "The path to the access transformers", required = true)
    Path accessTransforms;

    @Override
    public Integer call() throws Exception {
        final List<Throwable> exceptions = new ArrayList<>();
        final ReentrantLock lock = new ReentrantLock();

        final RestampContextConfiguration configuration = RestampContextConfiguration.builder()
            .accessTransformers(accessTransforms)
            .executionContext(new InMemoryExecutionContext(t -> {
                lock.lock();
                exceptions.add(t);
                lock.unlock();
            }))
            .sourceRoot(sourcePath)
            .sourceFiles(inputs)
            .classpath(classpath)
            .sourceFilesFromAccessTransformers()
            .build();

        final RestampInput inputs = RestampInput.parseFrom(configuration);
        final Changeset outputs = Restamp.run(inputs);

        for (final Throwable exception : exceptions) {
            throw new RuntimeException("Failed to run restamp", exception);
        }

        for (final Result result : outputs.getAllResults()) {
            System.out.println(result.diff());
        }
        return 0;
    }

}
