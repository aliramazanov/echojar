package com.aliramazanov.echojar.agent;

import java.util.List;

import com.aliramazanov.echojar.bootstrap.findings.CallSite;

final class CallSites {

    private static final StackWalker WALKER = StackWalker.getInstance();

    private static final List<String> GENERATED_MARKERS = List.of("$$", "$HibernateProxy", "$Proxy", "$JaxbAccessor",
            "GeneratedMethodAccessor");

    private final List<String> frameworkPrefixes;
    private final int depth;

    CallSites(List<String> frameworkPrefixes, int depth) {
        this.frameworkPrefixes = frameworkPrefixes;
        this.depth = depth;
    }

    CallSite resolve() {
        return WALKER.walk(frames -> frames
                .limit(depth)
                .filter(frame -> isApplication(frame.getClassName()))
                .findFirst()
                .map(frame -> new CallSite(
                        frame.getClassName(),
                        frame.getMethodName(),
                        frame.getFileName(),
                        frame.getLineNumber()))
                .orElse(null));
    }

    boolean isApplication(String className) {
        for (String prefix : frameworkPrefixes) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }

        for (String marker : GENERATED_MARKERS) {
            if (className.contains(marker)) {
                return false;
            }
        }

        return true;
    }
}
