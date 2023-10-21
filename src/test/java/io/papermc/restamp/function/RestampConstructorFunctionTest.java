package io.papermc.restamp.function;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.RestampInput;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;

import java.util.List;

@Tag("function")
public class RestampConstructorFunctionTest {

    // The constructor is pretty much fully covered by the RestampMethodFunctionTest.
    // A single test case is fine to ensure AT conversion is handled.
    @ParameterizedTest()
    @ValueSource(strings = {"private ", ""})
    public void testAccessTransformerOnConstructor(@NotNull final String initialModifier) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceMethod(
            MethodSignature.of("<init>", "(Ljava.lang.String;F)V"), AccessTransform.PUBLIC
        );

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet, constructConstructorTest(initialModifier));
        final List<Result> results = Restamp.run(input).getAllResults();

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.get(0).getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructConstructorTest("public "), fileAfterRestamp.printAll());
    }

    @NotNull
    private String constructConstructorTest(@NotNull final String modifier) {
        return """
            package io.papermc.test;
                        
            public class Test {
               %sTest(String name, float money) {
               }
            }
            """.formatted(modifier);
    }

}
