package com.mastfrog.maven.plugins.revisioninfo;

import static com.mastfrog.maven.plugins.revisioninfo.LibInfo.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.project.MavenProject;

/**
 * We cannot depend on any utils in mastfrog-utils or we have a chicken-and-egg
 * problem - this plugin cannot depend on libraries that will be built using it.
 * So copies of a few things from util-streams, util-collections, util-time and
 * function are here.
 *
 * @author Tim Boudreau
 */
final class Utils {

    // Borrowed from time-util in util-time
    private static final DateTimeFormatter GIT_LOG_FORMAT = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral("-")
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendOffset("+HHMM", "+0000")
            .parseLenient()
            .toFormatter();

    public static final DateTimeFormatter ISO_INSTANT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant()
            .toFormatter(Locale.US);

    static ZonedDateTime fromGitLogFormat(String txt) {
        return ZonedDateTime.parse(txt, GIT_LOG_FORMAT);
    }

    static String toIsoFormat(ZonedDateTime zdt) {
        return zdt.format(ISO_INSTANT);
    }

    // Borrowed or from ArrayUtils in util-collections
    static String[] prepend(String first, String... more) {
        String[] result = new String[more.length + 1];
        System.arraycopy(more, 0, result, 1, more.length);
        result[0] = first;
        return result;
    }

    // From here, borrowed or simulated from CollectionUtils in util-collections
    static <T> Iterable<T> concatenate(Iterable<T> a, Iterable<T> b) {
        Set<T> result = new LinkedHashSet<>();
        for (T obj : a) {
            result.add(obj);
        }
        for (T obj : b) {
            result.add(obj);
        }
        return result;
    }

    static String[] split(char delim, String seq) {
        List<String> result = new ArrayList<>(20);
        int max = seq.length();
        int start = 0;
        for (int i = 0; i < max; i++) {
            char c = seq.charAt(i);
            boolean last = i == max - 1;
            if (c == delim || last) {
                result.add(seq.substring(start, last ? c == delim ? i : i + 1 : i));
                start = i + 1;
            }
        }
        return result.toArray(new String[result.size()]);
    }

    static String join(char delim, Iterable<?> iterable) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<?> iter = iterable.iterator(); iter.hasNext();) {
            Object next = iter.next();
            sb.append(next);
            if (iter.hasNext()) {
                sb.append(delim);
            }
        }
        return sb.toString();
    }

    // From here, borrowed from Streams in util-streams
    static String readString(final InputStream in, String charset, int bufferSize) throws IOException {
        try (Reader r = bufferSize == 0 ? new InputStreamReader(in) : new BufferedReader(new InputStreamReader(in), bufferSize)) {
            return readString(r);
        }
    }

    static String readString(final Reader in) throws IOException {
        final StringBuilder buffer = new StringBuilder(2_048);
        int value;
        while ((value = in.read()) != -1) {
            buffer.append((char) value);
        }
        return buffer.toString();
    }

    static int copy(final InputStream in, final OutputStream out)
            throws IOException {
        final byte[] buffer = new byte[4_096];
        int bytesCopied = 0;
        for (;;) {
            int byteCount = in.read(buffer, 0, buffer.length);
            if (byteCount <= 0) {
                break;
            } else {
                out.write(buffer, 0, byteCount);
                bytesCopied += byteCount;
            }
        }
        return bytesCopied;
    }

    static OutputStream nullOutputStream() {
        return new NullOutputStream();
    }

    private static final class NullOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            //do nothing
        }

        @Override
        public void close() throws IOException {
            //do nothing
        }

        @Override
        public void flush() throws IOException {
            //do nothing
        }

        @Override
        public void write(byte[] b) throws IOException {
            //do nothing
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            //do nothing
        }
    }

    // From here, borrowed from utils-fileformat's PropertiesFileUtils -
    // Save a properties file with the same tortured logic as java.util.Properties,
    // minus the leading comment
    private static final char[] ESCAPED_SPACE = "\\ ".toCharArray();
    private static final int[] NIBBLES = new int[]{12, 8, 4, 0};
    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Saves a properties file in standard properties-file format, sans the
     * generated Date comment which makes builds non-repeatable.
     *
     * @param props The properties
     * @param out The output
     * @param comment An optional comment
     * @param close If true, close the stream when done
     * @throws IOException if something goes wrong
     */
    public static final void savePropertiesFile(Properties props, OutputStream out, String comment, boolean close) throws IOException {
        // Stores properties file without date comments, with consistent key ordering and line terminators, for
        // repeatable builds
        List<String> keys = new ArrayList<>(props.stringPropertyNames());
        Collections.sort(keys);
        List<String> lines = new ArrayList<>();
        if (comment != null) {
            lines.add("# " + comment);
        }
        for (String key : keys) {
            String val = props.getProperty(key);
            key = convert(key, true);
            /* No need to escape embedded and trailing spaces for value, hence
                 * pass false to flag.
             */
            val = convert(val, false);
            lines.add(key + "=" + val);
        }
        printLines(lines, out, ISO_8859_1, close);
    }

    private static String convert(String keyVal, boolean escapeSpace) {
        int len = keyVal.length();
        StringBuilder sb = new StringBuilder(len * 2 < 0 ? Integer.MAX_VALUE : len * 2);

        for (int i = 0; i < len; i++) {
            char ch = keyVal.charAt(i);
            if ((ch > 61) && (ch < 127)) {
                if (ch == '\\') {
                    sb.append("\\\\");
                } else {
                    sb.append(ch);
                }
                continue;
            }
            switch (ch) {
                case ' ':
                    sb.append(escapeSpace ? ESCAPED_SPACE : ' ');
                    break;
                case '\n':
                    appendEscaped('n', sb);
                    break;
                case '\r':
                    appendEscaped('r', sb);
                    break;
                case '\t':
                    appendEscaped('t', sb);
                    break;
                case '\f':
                    appendEscaped('f', sb);
                    break;
                case '#':
                case '=':
                case '!':
                case ':':
                    sb.append('\\').append(ch);
                    break;
                default:
                    if (((ch < 0x0020) || (ch > 0x007e))) {
                        appendEscapedHex(ch, sb);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static void appendEscaped(char c, StringBuilder sb) {
        sb.append('\\').append(c);
    }

    private static void appendEscapedHex(char c, StringBuilder sb) {
        sb.append('\\').append('u');
        for (int i : NIBBLES) {
            char hex = HEX[(c >> i) & 0xF];
            sb.append(hex);
        }
    }

    public static int printLines(Iterable<String> lines, OutputStream out, boolean close) throws IOException {
        return printLines(lines, out, UTF_8, close);
    }

    public static int printLines(Iterable<String> lines, OutputStream out, Charset encoding, boolean close) throws IOException {
        // Ensures UTF-8 encoding and avoids non-repeatable builds due to Windows line endings
        int count = 0;
        for (String line : lines) {
            byte[] bytes = line.getBytes(encoding);
            out.write(bytes);
            out.write('\n');
            count++;
        }
        if (close) {
            out.close();
        }
        return count;
    }

    public static String bicapitalizedToConstantName(String prop) {
        StringBuilder sb = new StringBuilder();
        int max = prop.length();
        boolean lastCaps = false;
        for (int i = 0; i < max; i++) {
            char c = prop.charAt(i);
            boolean caps = Character.isUpperCase(c);
            if (i != 0 && caps && !lastCaps && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
            lastCaps = caps;
        }
        return sb.toString();
    }

    static final String fqnToSourcePath(String fqn) {
        return fqn.replace('.', File.separatorChar) + ".java";
    }

    private static final String packageFor(String fqn) {
        int ix = fqn.lastIndexOf('.');
        if (ix < 0) {
            return "";
        }
        return fqn.substring(0, ix);
    }

    private static final String classNameFor(String fqn) {
        int ix = fqn.lastIndexOf('.');
        if (ix < 0) {
            return fqn;
        }
        return fqn.substring(ix + 1);
    }

    private static final String INDENT = "    ";

    private static Instant commitTimestamp(Properties props) {
        String dt = props.getProperty(LibInfo.COMMIT_DATE_ISO_PROPERTY);
        if (dt != null) {
            try {
//                return ZonedDateTime.parse(dt, ISO_INSTANT).toInstant();
                return Instant.parse(dt);
            } catch (DateTimeParseException ex) {

                Logger.getLogger(Utils.class.getName()).log(Level.WARNING, "", ex);
            }
        }
        dt = props.getProperty(LibInfo.COMMIT_DATE_PROPERTY);
        if (dt != null) {
            try {
                return ZonedDateTime.parse(dt, GIT_LOG_FORMAT).toInstant();
            } catch (DateTimeParseException ex) {
                try {
                    // old netty, if we iterate dependencies
                    return Instant.parse(dt);
                } catch (Exception ex1) {
                    ex.addSuppressed(ex1);
                }
                Logger.getLogger(Utils.class.getName()).log(Level.WARNING, "", ex);
            }
        }
        return Instant.ofEpochMilli(0);
    }

    public static final String javaSourceFromProperties(boolean packagePrivate, String fqn, Properties props, MavenProject project) {
        String pkg = packageFor(fqn);
        String className = classNameFor(fqn);
        StringBuilder sb = new StringBuilder("package ").append(pkg).append(';');
        sb.append("\n\nimport java.time.Instant;\n");

        sb.append("\n/**\n * Generated by com.mastfrog:revision-info-plugin\n */\n");
        if (!packagePrivate) {
            sb.append("public ");
        }
        sb.append("final class ").append(className).append(" {\n");

        Set<String> all = new TreeSet<>(props.stringPropertyNames());

        StringBuilder sblock = new StringBuilder(1024);
        sblock.append("\n").append(INDENT).append(INDENT)
                .append("// avoids the compiler inlining values into methods that use these fields");

        Set<String> writtenProperties = new HashSet<>();
        for (String prop : all) {
            switch (prop) {
                case LibInfo.COMMIT_DATE_ISO_PROPERTY:
                    break;
                default:
                    String nm = bicapitalizedToConstantName(prop);
                    writtenProperties.add(nm);
                    sb.append('\n').append(INDENT)
                            .append("public static final String ")
                            .append(nm).append(';');
                    sblock.append("\n").append(INDENT).append(INDENT).append(nm)
                            .append(" = ").append('"').append(props.getProperty(prop)).append("\";");
            }
        }
        Instant commitTimestamp = commitTimestamp(props);
        long ts = commitTimestamp.toEpochMilli() / 1000;

        sb.append('\n').append(INDENT)
                .append("public static final Instant ")
                .append("COMMIT_TIMESTAMP")
                .append(" = ")
                .append("Instant.ofEpochMilli(").append(ts).append("L * 1000L)").append(";");

        sb.append('\n').append(INDENT)
                .append("public static final String ")
                .append("GROUP_ID").append(';');

        sblock.append("\n").append(INDENT).append(INDENT).append("GROUP_ID")
                .append(" = ").append('"').append(project.getGroupId()).append("\";");

        sb.append('\n').append(INDENT)
                .append("public static final String ")
                .append("ARTIFACT_ID").append(';');

        sblock.append("\n").append(INDENT).append(INDENT).append("ARTIFACT_ID")
                .append(" = ").append('"').append(project.getArtifactId()).append("\";");

        if (!writtenProperties.contains("VERSION")) {
            sb.append('\n').append(INDENT)
                    .append("public static final String ")
                    .append("VERSION").append(";");

            sblock.append("\n").append(INDENT).append(INDENT).append("VERSION")
                    .append(" = ").append('"').append(project.getVersion()).append("\";");
        }

        String fullRevisionString = project.getGroupId() + ":" + project.getArtifactId() + ":"
                + project.getVersion() + ";"
                + props.getProperty(LONG_COMMIT_HASH_PROPERTY, "?")
                + '-' + props.getProperty(REPO_STATUS_PROPERTY);

        sb.append('\n').append(INDENT)
                .append("public static final String ")
                .append("REVISION").append(";");

        sblock.append("\n").append(INDENT).append(INDENT).append("REVISION")
                .append(" = ").append('"').append(fullRevisionString).append("\";");

        sb.append('\n').append(INDENT)
                .append("public static final boolean ")
                .append("CLEAN_REPO").append(";");

        sblock.append("\n").append(INDENT).append(INDENT).append("CLEAN_REPO")
                .append(" = ").append(STATUS_CLEAN.equals(props.getProperty(REPO_STATUS_PROPERTY))).append(";");

        sb.append("\n\n").append(INDENT);

        sb.append("static {").append(sblock).append('\n').append(INDENT).append("}\n\n").append(INDENT);

        sb.append("private ").append(className).append("() {\n").append(INDENT).append(INDENT);
        sb.append("throw new AssertionError();\n");
        sb.append(INDENT).append("}\n");
        return sb.append("}\n").toString();
    }

    private Utils() {
        throw new AssertionError();
    }
}
