package com.aliramazanov.echojar.bootstrap.findings;

import org.jetbrains.annotations.NotNull;

public record CallSite(String declaringClass, String methodName, String fileName, int lineNumber) {

    @Override
    public @NotNull String toString() {
        int dot = declaringClass.lastIndexOf('.');

        String simple = dot < 0 ? declaringClass : declaringClass.substring(dot + 1);

        if (fileName == null || lineNumber < 0) {
            return simple + "." + methodName;
        }

        return simple + "." + methodName + "(" + fileName + ":" + lineNumber + ")";
    }
}
