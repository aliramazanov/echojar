package com.aliramazanov.echojar.agent;

import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class Templates {

    private static final SqlTemplate OVERFLOW =
            new SqlTemplate("(too many distinct statements to track)", 0, true);

    private static final SqlTemplate BLANK = new SqlTemplate("", -1, true);

    private final ConcurrentMap<String, SqlTemplate> byRawSql = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SqlTemplate> byTemplate = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final AtomicLong overflowed = new AtomicLong();
    private final int limit;
    private final boolean suppressNoise;

    Templates(int limit, boolean suppressNoise) {
        this.limit = limit;
        this.suppressNoise = suppressNoise;
    }

    SqlTemplate of(String rawSql) {
        SqlTemplate cached = byRawSql.get(rawSql);
        if (cached != null) {
            return cached;
        }
        SqlTemplate template = resolve(normalize(rawSql));
        if (byRawSql.size() < limit) {
            byRawSql.putIfAbsent(rawSql, template);
        }
        return template;
    }

    private SqlTemplate resolve(String text) {
        if (text.isEmpty()) {
            return BLANK;
        }
        SqlTemplate known = byTemplate.get(text);
        if (known != null) {
            return known;
        }
        if (byTemplate.size() >= limit) {
            overflowed.incrementAndGet();
            return OVERFLOW;
        }
        SqlTemplate created =
                new SqlTemplate(text, ids.incrementAndGet(), suppressNoise && Noise.matches(text));
        SqlTemplate raced = byTemplate.putIfAbsent(text, created);
        return raced != null ? raced : created;
    }

    int cached() {
        return byTemplate.size();
    }

    long overflowed() {
        return overflowed.get();
    }

    static String normalize(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int length = sql.length();
        boolean pendingSpace = false;
        while (i < length) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = out.length() > 0;
                i++;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            if (c == '\'') {
                i = skipQuoted(sql, i, '\'');
                out.append('?');
                continue;
            }
            if (c == '"') {
                int end = skipQuoted(sql, i, '"');
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                while (i < length && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                continue;
            }
            if (Character.isDigit(c) && !isIdentifierPart(previous(out))) {
                i = skipNumber(sql, i);
                out.append('?');
                continue;
            }
            out.append(c);
            i++;
        }
        return collapseInLists(out.toString().trim());
    }

    private static char previous(StringBuilder out) {
        return out.length() == 0 ? ' ' : out.charAt(out.length() - 1);
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static int skipNumber(String sql, int start) {
        int length = sql.length();
        if (sql.charAt(start) == '0' && start + 1 < length
                && (sql.charAt(start + 1) == 'x' || sql.charAt(start + 1) == 'X')) {
            int i = start + 2;
            while (i < length && isHexDigit(sql.charAt(i))) {
                i++;
            }
            return i > start + 2 ? i : start + 1;
        }
        int i = start;
        while (i < length) {
            char c = sql.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                i++;
                continue;
            }
            if ((c == 'e' || c == 'E') && i + 1 < length
                    && (Character.isDigit(sql.charAt(i + 1)) || sql.charAt(i + 1) == '-' || sql.charAt(i + 1) == '+')) {
                i += 2;
                continue;
            }
            break;
        }
        return i;
    }

    private static boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String collapseInLists(String sql) {
        StringBuilder out = null;
        int copied = 0;
        int i = 0;
        int length = sql.length();
        while (i < length) {
            if (isInKeyword(sql, i)) {
                int cursor = i + 2;
                while (cursor < length && sql.charAt(cursor) == ' ') {
                    cursor++;
                }
                if (cursor < length && sql.charAt(cursor) == '(') {
                    int end = scanPlaceholders(sql, cursor);
                    if (end > 0) {
                        if (out == null) {
                            out = new StringBuilder(length);
                        }
                        out.append(sql, copied, i).append("IN (?)");
                        copied = end;
                        i = end;
                        continue;
                    }
                }
            }
            i++;
        }
        if (out == null) {
            return sql;
        }
        return out.append(sql, copied, length).toString();
    }

    private static boolean isInKeyword(String sql, int i) {
        char first = sql.charAt(i);
        if (first != 'i' && first != 'I') {
            return false;
        }
        if (i + 1 >= sql.length()) {
            return false;
        }
        char second = sql.charAt(i + 1);
        if (second != 'n' && second != 'N') {
            return false;
        }
        if (i > 0 && isIdentifierPart(sql.charAt(i - 1))) {
            return false;
        }
        return i + 2 >= sql.length() || !isIdentifierPart(sql.charAt(i + 2));
    }

    private static int scanPlaceholders(String sql, int open) {
        int length = sql.length();
        int i = open + 1;
        int placeholders = 0;
        while (i < length) {
            while (i < length && sql.charAt(i) == ' ') {
                i++;
            }
            if (i >= length || sql.charAt(i) != '?') {
                return -1;
            }
            placeholders++;
            i++;
            while (i < length && sql.charAt(i) == ' ') {
                i++;
            }
            if (i >= length) {
                return -1;
            }
            if (sql.charAt(i) == ',') {
                i++;
                continue;
            }
            if (sql.charAt(i) == ')') {
                return placeholders >= 2 ? i + 1 : -1;
            }
            return -1;
        }
        return -1;
    }
}
