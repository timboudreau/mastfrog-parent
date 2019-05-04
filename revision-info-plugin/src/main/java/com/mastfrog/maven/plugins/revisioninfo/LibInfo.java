package com.mastfrog.maven.plugins.revisioninfo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public class LibInfo {

    private static final String[] LOG_ARGS = {"--no-pager", "log", "-1",
        "--format=format:%h %H %cd", "--date=iso", "--no-color", "--encoding=utf8"};
    private static final String[] STATUS_ARGS = {"status", "--porcelain"};
    private static final String GIT_BINARY_NAME = "git";

    private static final Pattern SHORT_HASH_PATTERN = Pattern.compile("^([0-9a-f]+) .*$");
    private static final Pattern LAST_COMMIT_PATTERN = Pattern.compile("^[0-9a-f]+ ([0-9a-f]{40}) .*$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9a-f]+ [0-9a-f]{40} (.*)$");

    public static final String COMMIT_DATE_ISO_PROPERTY = "commitDateISO";
    public static final String COMMIT_DATE_PROPERTY = "commitDate";
    public static final String LONG_COMMIT_HASH_PROPERTY = "longCommitHash";
    public static final String SHORT_COMMIT_HASH_PROPERTY = "shortCommitHash";
    public static final String REPO_STATUS_PROPERTY = "repoStatus";
    private static final String UTC_TIME_ZONE = "UTC";
    private static final String TIME_ZONE_ENV_VAR = "TZ";
    public static final String STATUS_CLEAN = "clean";
    private static final String STATUS_DIRTY = "dirty";
    private static final String STATUS_UNKNOWN = "unknown";

    public static void main(String[] args) throws Exception {
        String path = args.length == 0 ? "/tmp/libinfo.properties" : args[0];
        Path workingDir = Paths.get(".").toFile().getAbsoluteFile().toPath();
        String errors = new LibInfo(Arrays.asList(Paths.get("/usr/bin"), Paths.get("/usr/local/bin"), Paths.get("/opt/local/bin")))
                .writeInfoTo(workingDir, Paths.get(path), LibInfo.class.getName(), null);
        if (!errors.isEmpty()) {
            System.err.println(errors);
            System.exit(1);
        }
    }

    private static ProcessBuilder process(String binary, String... command) {
        String[] args = Utils.prepend(binary, command);
        return new ProcessBuilder(args);
    }
    private final List<Path> gitBinaryPaths;

    LibInfo(List<Path> gitBinaryPaths) {
        this.gitBinaryPaths = gitBinaryPaths;
    }

    public String writeInfoTo(Path gitPath, Path file, String generatorName, ThrowingConsumer<Properties> propConsumer) throws Exception {
        StringBuilder errors = new StringBuilder(120);
        Properties props = getInfo(gitPath, errors);
        if (props != null) {
            OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            Utils.savePropertiesFile(props, out, "Generated by " + generatorName, true);
            if (propConsumer != null) {
                propConsumer.accept(props);
            }
            return "";
        }
        return errors.toString();
    }

    public Properties getInfo(Path path, StringBuilder errors) throws IOException, InterruptedException, ExecutionException {
        Path gitMetadataParent = findGitRoot(path);
        if (gitMetadataParent == null) {
            return null;
        }
        Path binary = findGitBinary();
        if (binary == null) {
            errors.append("Could not find git binary in ").append(Utils.join(',', searchPath()));
            return null;
        }
        String output = runGitForLogInfo(binary, gitMetadataParent, errors);
        String repoStatus = runGitForRepoStatus(binary, gitMetadataParent, errors);
        if (output != null && !output.trim().isEmpty()) {
            return gitOutputToProperties(output, repoStatus, errors);
        }
        return null;
    }

    private Properties gitOutputToProperties(String output, String status, StringBuilder errors) {
        Properties props = new Properties();
        props.setProperty(REPO_STATUS_PROPERTY, status);
        Matcher m = SHORT_HASH_PATTERN.matcher(output);
        if (m.find()) {
            props.setProperty(SHORT_COMMIT_HASH_PROPERTY, m.group(1));
        }
        m = LAST_COMMIT_PATTERN.matcher(output);
        if (m.find()) {
            props.setProperty(LONG_COMMIT_HASH_PROPERTY, m.group(1));
        }
        m = DATE_PATTERN.matcher(output);
        if (m.find()) {
            String gitDate = m.group(1);
            props.setProperty(COMMIT_DATE_PROPERTY, gitDate);
            try {
                ZonedDateTime zdt = Utils.fromGitLogFormat(gitDate);
                props.setProperty(COMMIT_DATE_ISO_PROPERTY, Utils.toIsoFormat(zdt));
            } catch (DateTimeParseException ex) {
                Logger.getLogger(LibInfo.class.getName()).log(Level.WARNING, "Exception parsing date stamp '" + gitDate + "'", ex);;
            }
        }
        if (props.isEmpty()) {
            errors.append("Could not match git output '").append(output).append("'");
            return null;
        }
        return props;
    }

    private Iterable<Path> searchPath() {
        if (gitBinaryPaths != null) {
            Iterable<Path> system = systemPath();
            if (system.iterator().hasNext()) {
                return Utils.concatenate(gitBinaryPaths, system);
            }
        }
        return systemPath();
    }

    private Path findGitBinary() {
        return searchGitBinary(searchPath());
    }

    private Iterable<Path> systemPath() {
        String pth = System.getenv("PATH");
        Set<Path> result = new LinkedHashSet<>();
        if (pth != null) {
            for (String p : Utils.split(File.pathSeparatorChar, pth)) {
                Path path = Paths.get(p);
                if (Files.exists(path) && Files.isDirectory(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private Path searchGitBinary(Iterable<Path> paths) {
        for (Path p : paths) {
            if (Files.exists(p) && Files.isDirectory(p)) {
                Path gitBinary = p.resolve(GIT_BINARY_NAME);
                if (!Files.exists(gitBinary)) {
                    gitBinary = p.resolve(GIT_BINARY_NAME + ".exe");
                }
                if (Files.exists(gitBinary)) {
                    if (gitBinary.toFile().canExecute()) {
                        return gitBinary;
                    }
                }
            }
        }
        return null;
    }

    private Path findGitRoot(Path path) {
        if (!Files.isDirectory(path)) {
            path = path.getParent();
        }
        do {
            Path gitDir = path.resolve(".git");
            if (Files.exists(gitDir)) {
                return path;
            }
        } while ((path = path.getParent()) != null);
        return null;
    }

    private String runGitForLogInfo(Path binary, Path gitMetadataParent, StringBuilder errors) throws IOException, InterruptedException, ExecutionException {
        ProcessBuilder pb = process(binary.toString(),
                LOG_ARGS);
        pb.environment().put(TIME_ZONE_ENV_VAR, UTC_TIME_ZONE);
        pb.directory(gitMetadataParent.toFile());
        Process proc = pb.start();
        waitForProcess(proc);
        if (proc.exitValue() != 0) {
            errors.append("Process '").append(Utils.join(' ', pb.command())).append("' exited with code ").append(proc.exitValue()).append(". Error output:\n");
            errors.append(Utils.readString(proc.getErrorStream(), Charset.defaultCharset().name(), 1536));
        }
        // For log encoding, we explicitly request UTF-8, so this is correct
        return Utils.readString(proc.getInputStream(), StandardCharsets.UTF_8.name(), 768);
    }

    private String runGitForRepoStatus(Path binary, Path gitMetadataParent, StringBuilder errors) throws IOException, InterruptedException, ExecutionException {
        ProcessBuilder pb = process(binary.toString(), STATUS_ARGS);
        pb.directory(gitMetadataParent.toFile());
        Process proc = pb.start();
        waitForProcess(proc);
        if (proc.exitValue() != 0) {
            errors.append("Process '").append(Utils.join(' ', pb.command())).append("' exited with code ").append(proc.exitValue()).append(". Error output:\n");
            errors.append(Utils.readString(proc.getErrorStream(), Charset.defaultCharset().name(), 1536));
            return STATUS_UNKNOWN;
        }
        if (Utils.copy(proc.getInputStream(), Utils.nullOutputStream()) > 0) {
            return STATUS_DIRTY;
        }
        return STATUS_CLEAN;
    }

    private int waitForProcess(Process proc) throws InterruptedException, ExecutionException {
        // JDK 9
        // proc.onExit().get();
        long then = System.currentTimeMillis();
        for (;;) {
            if (proc.isAlive()) {
                Thread.sleep(10);
            }
            try {
                return proc.exitValue();
            } catch (IllegalThreadStateException ex) {
                // keep going, not exited
            }
            if (System.currentTimeMillis() - then > 30 * 1000) {
                throw new IllegalStateException("Timed out after 30 seconds waiting for git" + proc);
            }
        }
    }
}
