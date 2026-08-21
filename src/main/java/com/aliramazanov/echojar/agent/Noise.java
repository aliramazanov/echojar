package com.aliramazanov.echojar.agent;

import java.util.List;
import java.util.Locale;

final class Noise {

    private static final List<String> WHOLE_STATEMENTS = List.of(
            "select ?",
            "select ? from dual",
            "select ? from rdb$database",
            "values ?",
            "begin",
            "commit",
            "rollback",
            "set autocommit ?",
            "select @@session.transaction_read_only");

    private static final List<String> SEQUENCE_TOKENS = List.of(
            "nextval",
            "currval",
            "hibernate_sequence");

    private static final String SEQUENCE_PHRASE = "next value for ";

    private Noise() {
    }

    static boolean matches(String normalized) {
        String lower = normalized.toLowerCase(Locale.ROOT).trim();
        while (lower.endsWith(";")) {
            lower = lower.substring(0, lower.length() - 1).trim();
        }
        for (String statement : WHOLE_STATEMENTS) {
            if (lower.equals(statement)) {
                return true;
            }
        }
        if (lower.contains(SEQUENCE_PHRASE)) {
            return true;
        }
        for (String token : SEQUENCE_TOKENS) {
            if (containsIdentifier(lower, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentifier(String sql, String identifier) {
        int from = 0;
        while (true) {
            int at = sql.indexOf(identifier, from);
            if (at < 0) {
                return false;
            }
            int before = at - 1;
            int after = at + identifier.length();
            boolean startsCleanly = before < 0 || !isIdentifierPart(sql.charAt(before));
            boolean endsCleanly = after >= sql.length() || !isIdentifierPart(sql.charAt(after));
            if (startsCleanly && endsCleanly) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
