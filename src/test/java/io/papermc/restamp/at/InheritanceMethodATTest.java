package io.papermc.restamp.at;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.RestampInput;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.Result;

import java.util.List;

@NullMarked
public class InheritanceMethodATTest {

    @Test
    public void testInheritedATs() {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceMethod(
            MethodSignature.of("test", "(Ljava.lang.Object;)Ljava.lang.String;"), AccessTransform.PUBLIC
        );

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(
            accessTransformSet,
            """
                package io.papermc.test;
                
                public class Test {
                    protected String test(final Object parameter) {
                        return "hi there";
                    }
                }
                """,
            """
                package io.papermc.test;
                
                public class SuperTest extends Test {
                    @Override
                    protected String test(final Object parameter) {
                        return "hi there but better";
                    }
                }
                """
        );

        final List<Result> results = Restamp.run(input).getAllResults();
        Assertions.assertEquals(
            """
                package io.papermc.test;
                
                public class SuperTest extends Test {
                    @Override
                    public String test(final Object parameter) {
                        return "hi there but better";
                    }
                }
                """,
            results.get(1).getAfter().printAll()
        );
    }

}
