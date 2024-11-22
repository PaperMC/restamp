package io.papermc.restamp.function;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.RestampInput;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.ModifierChange;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;

import java.util.List;

import static io.papermc.restamp.RestampFunctionTestHelper.accessChangeToModifierString;

@Tag("function")
@NullMarked
public class RestampFieldFunctionTest {

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentAndStyleProvider.class)
    public void testAccessTransformerOnField(final AccessTransform given,
                                             final AccessTransform target,
                                             @Nullable final String staticModifier,
                                             final RestampFunctionTestHelper.TestCodeStyle testCodeStyle) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceField("passphrase", target);

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet, constructFieldTest(
            accessChangeToModifierString(given.getAccess(), staticModifier, given.getFinal() == ModifierChange.ADD ? "final" : null), testCodeStyle
        ));

        final List<Result> results = Restamp.run(input).getAllResults();
        if (given.equals(target)) {
            Assertions.assertTrue(results.isEmpty());
            return;
        }

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.getFirst().getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructFieldTest(
            accessChangeToModifierString(target.getAccess(), staticModifier, target.getFinal() == ModifierChange.ADD ? "final" : null), testCodeStyle
        ), fileAfterRestamp.printAll());
    }

    private String constructFieldTest(String modifier, final RestampFunctionTestHelper.TestCodeStyle testCodeStyle) {
        if (!modifier.isEmpty()) modifier = modifier + " ";
        final StringBuilder builder = new StringBuilder();
        builder.append("""
            package io.papermc.test;
            
            public class Test {
                /* Comment above */
            """);
        if (testCodeStyle.includesLeadingAnnotation()) builder.append("    @Experimental\n");
        if (testCodeStyle.leadingSpace()) builder.append(" /* leading space */ ");
        builder.append("""
                %sString passphrase = "Hello World";
            }
            """.formatted(modifier));
        return builder.toString();
    }

}
