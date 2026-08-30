package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EchoConfigAdversarialTest {

    @Test
    void survivesNullAndEmptyArguments() {
        assertEquals(5, EchoConfig.parse(null).threshold());
        assertEquals(5, EchoConfig.parse("").threshold());
        assertEquals(5, EchoConfig.parse("   ").threshold());
    }

    @Test
    void ignoresGarbageOptions() {
        EchoConfig config = EchoConfig.parse("nonsense,,=,=novalue,threshold=7");
        assertEquals(7, config.threshold());
    }

    @Test
    void nonNumericNumbersFallBackToTheDefault() {
        assertEquals(5, EchoConfig.parse("threshold=abc").threshold());
        assertEquals(200, EchoConfig.parse("depth=").stackDepth());
        assertEquals(5000, EchoConfig.parse("templates=9999999999999").templateCacheLimit());
    }

    @Test
    void nonsensicalNumbersAreClampedToSomethingUsable() {
        assertTrue(EchoConfig.parse("threshold=0").threshold() >= 1,
                "a threshold below one would make every statement an echo");
        assertTrue(EchoConfig.parse("threshold=-4").threshold() >= 1);
        assertTrue(EchoConfig.parse("templates=0").templateCacheLimit() >= 1,
                "a zero template cache would silently track nothing");
        assertTrue(EchoConfig.parse("templates=-1").templateCacheLimit() >= 1);
        assertTrue(EchoConfig.parse("depth=0").stackDepth() >= 1,
                "a zero walk depth would never resolve a call site");
        assertTrue(EchoConfig.parse("depth=-9").stackDepth() >= 1);
    }

    @Test
    void booleansAcceptTheObviousSpellings() {
        assertEquals(false, EchoConfig.parse("noise=false").suppressNoise());
        assertEquals(false, EchoConfig.parse("noise=off").suppressNoise());
        assertEquals(true, EchoConfig.parse("noise=true").suppressNoise());
        assertEquals(true, EchoConfig.parse("noise=on").suppressNoise());
        assertEquals(true, EchoConfig.parse("").suppressNoise());
    }

    @Test
    void aValueContainingAnEqualsSignIsKept() {
        assertEquals("/tmp/a=b.log", EchoConfig.parse("out=/tmp/a=b.log").output());
    }

    @Test
    void extraFrameworkPrefixesAreAddedNotSubstituted() {
        EchoConfig config = EchoConfig.parse("framework=com.example.;com.other.");
        assertTrue(config.frameworkPrefixes().contains("com.example."));
        assertTrue(config.frameworkPrefixes().contains("com.other."));
        assertTrue(config.frameworkPrefixes().contains("org.hibernate."),
                "the built in prefixes must survive");
    }

    @Test
    void anEmptyPrefixWouldMatchEverythingAndIsRejected() {
        for (String prefix : EchoConfig.parse("framework=com.example.;").frameworkPrefixes()) {
            assertTrue(!prefix.isBlank(),
                    "an empty framework prefix matches every class and hides all call sites");
        }

        for (String prefix : EchoConfig.parse("framework=;;com.example.").frameworkPrefixes()) {
            assertTrue(!prefix.isBlank(), "an empty framework prefix matches every class");
        }
    }

    @Test
    void anEmptyIgnorePrefixWouldDisableTheAgentEntirely() {
        EchoConfig config = EchoConfig.parse("ignore=com.example.;");
        assertFalse(config.ignoredTypes().matches(
                net.bytebuddy.description.type.TypeDescription.ForLoadedType.of(String.class)),
                "an empty ignore prefix would ignore every class and instrument nothing");
    }

    @Test
    void ignoredTypesMatcherIsAlwaysUsable() {
        assertNotNull(EchoConfig.parse("").ignoredTypes());
        assertNotNull(EchoConfig.parse("ignore=com.example.").ignoredTypes());
    }
}
