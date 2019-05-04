package com.mastfrog.maven.plugins.revisioninfo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;

/**
 * Runs git and stores the commit hash and commit date and a few other things in
 * <code>META-INF/${groupId}.${artifactId}.properties</code>, and optionally
 * generates a class with the same information.
 */
@Mojo(name = "revision-info", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class RevisionInfoMojo extends AbstractMojo {

    /**
     * The project build directory.
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    File outputDirectory;

    /**
     * The source encoding, for reading git log messages.
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}", readonly = true)
    String encoding;

    /**
     * The fully qualified name of the class to generate, if any.
     */
    @Parameter(property = "class", defaultValue = "none", alias = "revisionClass")
    String genClass;

    /**
     * The dest dir for generated classes.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations", readonly = true)
    File genSourcesDir;

    /**
     * If true, scan the source roots for source files, find the lowest package
     * which has contains a source file, and treat that as the target package
     * for a class with a standard name "RevisionInfo". If the file name is
     * specified, this is ignored.
     */
    @Parameter(property = "auto", defaultValue = "true", alias = "autoGenerate")
    boolean auto;

    @Component
    MavenProject project;

    /**
     * The source directories containing the sources to be processed.
     *
     * @parameter expression="${project.compileSourceRoots}"
     * @required
     * @readonly
     */
    @Parameter(defaultValue = "${project.compileSourceRoots}", readonly = true)
    private List<String> compileSourceRoots;

    Path propertiesOutputFile() {
        return outputDirectory.toPath().resolve("classes/META-INF/" + project.getGroupId() + "." 
                + project.getArtifactId() + ".versions.properties");
    }

    Path sourceOutputFile() throws IOException {
        String genClass = generatedClassFqn();
        if (genClass != null) {
            String sourceRelativePath = Utils.fqnToSourcePath(genClass);
            return genSourcesDir.toPath().resolve(sourceRelativePath);
        }
        return null;
    }

    String generatedClassFqn() throws IOException {
        String genClass = this.genClass;
        if (genClass == null || "none".equals(genClass)) {
            genClass = project.getProperties().getProperty("revisionClass");
        }
        String result = "none".equals(genClass) ? null : genClass;
        if (result == null && auto) {
            result = scanForLeastPackageWithSourceFile();
            if (result != null) {
                return result + ".RevisionInfo";
            }
        }
        return result;
    }

    private static String fileExt(Path pth) {
        String nm = pth.getFileName().toString();
        int ix = nm.lastIndexOf('.');
        if (ix > 0 && ix < nm.length() - 1) {
            return nm.substring(ix + 1);
        }
        return null;
    }

    private void scan(Path dir, Set<Path> dirs) throws IOException {
        Files.list(dir).forEach(child -> {
            if (Files.isDirectory(child)) {
                try {
                    scan(child, dirs);
                } catch (IOException ex) {
                    Logger.getLogger(RevisionInfoMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                String ext = fileExt(child);
                if (ext != null) {
                    switch (ext) {
                        case "java":
                        case "groovy":
                            dirs.add(dir);
                    }
                }
            }
        });
    }

    private int dotCount(String s) {
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            result += s.charAt(i) == '.' ? 1 : 0;
        }
        return result;
    }

    private String scanForLeastPackageWithSourceFile() throws IOException {
        if (compileSourceRoots != null) {
            Set<String> allPackages = new HashSet<>();
            for (String s : compileSourceRoots) {
                Path dir = Paths.get(s);
                if (Files.exists(dir) && Files.isDirectory(dir)) {
                    Set<Path> dirs = new HashSet<>();
                    scan(dir, dirs);
                    for (Path p : dirs) {
                        p = dir.relativize(p);
                        allPackages.add(p.toString().replace(File.separatorChar, '.'));
                    }
                }
            }
            List<String> pkgs = new ArrayList<>(allPackages);
            Collections.sort(pkgs, (a, b) -> {
                Integer dca = dotCount(a);
                Integer dcb = dotCount(b);
                int result = dca.compareTo(dcb);
                if (result == 0) {
                    dca = a.length();
                    dcb = b.length();
                    result = dca.compareTo(dcb);
                }
                return result;
            });
            if (!pkgs.isEmpty()) {
                return pkgs.get(0);
            }
        }
        return null;
    }

    @Override
    public void execute() throws MojoExecutionException {
        if ("pom".equals(project.getPackaging())) {
            // we ignore pom projects - no classes, nothing to do
            getLog().debug("revision-info-plugin ignoring POM project");
            return;
        }
        StringBuilder errors = new StringBuilder();
        try {
            if (genClass != null && !"none".equals(genClass)) {
                checkGenClass(genClass);
            }
            if (encoding != null) {
                try {
                    Charset.forName(encoding);
                } catch (Exception ex) {
                    throw new MojoExecutionException("Could not find encoding '" + encoding + "'", ex);
                }
            }

            Properties props = new LibInfo(Collections.<Path>emptyList()).getInfo(outputDirectory.toPath(), errors);
            if (props == null) {
                if (errors.length() > 0) {
                    getLog().warn(errors);
                    return;
                } else {
                    getLog().warn("Failed to get git revision info and did not "
                            + "write properties file");
                }
            }
            File f = outputDirectory;
            if (!f.exists()) {
                f.mkdirs();
            }

            Path outputFile = propertiesOutputFile();

            Files.createDirectories(outputFile.getParent());
            try (OutputStream out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                Utils.savePropertiesFile(props, out, "Generated by com.mastfrog:revision-info-plugin", true);
                getLog().info("Generated revision info to " + project.getBasedir().toPath().relativize(outputFile));
            }
            Path sourceFilePath = sourceOutputFile();
            String fqn = generatedClassFqn();
            if (sourceFilePath != null && fqn != null) {
                String source = Utils.javaSourceFromProperties(fqn, props, project);
                getLog().info("Generating class " + fqn + " in " + project.getBasedir().toPath().relativize(sourceFilePath));
                Path sourceFilePackage = sourceFilePath.getParent();
                Files.createDirectories(sourceFilePackage);
                try (OutputStream out = Files.newOutputStream(sourceFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    out.write(source.getBytes(encoding == null ? "UTF-8" : encoding));
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error creating file: " + errors, e);
        }
    }

    private static void checkGenClass(String genClass) throws MojoExecutionException {
        for (String s : Utils.split('.', genClass)) {
            checkNamePart(s);
        }
    }

    private static void checkNamePart(String genClass) throws MojoExecutionException {
        int max = genClass.length();
        for (int i = 0; i < max; i++) {
            char c = genClass.charAt(i);
            if (i == 0) {
                if (!Character.isJavaIdentifierStart(c)) {
                    throw new MojoExecutionException("Class name contains "
                            + "invalid first character '" + c + "', which cannot "
                            + "begin a Java identifier: '" + genClass + "'");
                }
            } else {
                if (!Character.isJavaIdentifierPart(c)) {
                    throw new MojoExecutionException("Class name contains "
                            + "invalid character '" + c + "' at index " + i + ", which cannot "
                            + "be part of Java identifier: '" + genClass + "'");
                }
            }
        }
    }
}
