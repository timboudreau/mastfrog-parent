/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.automodule.inject;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Injects a property into each projects in the session which is the combination
 * of the group id and artifact id, converted into a valid Java identifier, to
 * generate an automatic module name for that project.
 * <p>
 * To make use of it, add a &lt;plugin&gt; entry to your pom or parent pom that
 * adds a manifest entry for
 * <code>Automatic-Module-Name: ${automaticModuleName}</code>. This allows an
 * entire tree of projects to get usable automatic module names generated into
 * their manifests without custom configuration in every project.
 * </p><p>
 * If a module-info.java file exists in <code>src/main/java</code>, then the
 * property will remain unset.
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "automodule-inject", threadSafe = false)
public class AutomoduleInjectMojo extends AbstractMojo {

    /**
     * Set the name of the property that is injected into the project.
     */
    @Parameter(property = "auto-auto-module-name", defaultValue = "autoAutoModuleName")
    @SuppressWarnings("FieldMayBeFinal")
    private String autoModuleNameProperty = "autoAutoModuleName";

    /**
     * If true, log the generated name.
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * If true, skip execution.
     */
    @Parameter(property = "automodule.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Optional prefix to prepend to the generated automatic module name.
     */
    @Parameter(property = "automodule.prefix", required = false)
    private String prefix;

    /**
     * Optional suffix to append to the generated automatic module name.
     */
    @Parameter(property = "automodule.suffix", required = false)
    private String suffix;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!skip && !"pom".equals(project.getPackaging())) {
            Path moduleInfo = project.getBasedir().toPath().resolve("src")
                    .resolve("main").resolve("java").resolve("module-info.java");
            if (!Files.exists(moduleInfo)) {
                project.getProperties().setProperty(autoModuleNameProperty,
                        autoModuleName(project.getGroupId(), project.getArtifactId()));
            }
        }
    }

    private String autoModuleName(String gid, String artifactId) {
        // A few special cases from converting mastfrog
        if ("com.mastfrog".equals(gid) && artifactId.startsWith("util-")) {
            if (!"util-function".equals(artifactId)) {
                artifactId = artifactId.substring(5);
            }
        }
        artifactId = artifactId.replace('-', '.');
        String result = splitAndConvert(gid) + '.' + splitAndConvert(artifactId);
        if (prefix != null) {
            result = prefix + result;
        }
        if (suffix != null) {
            result = result + suffix;
        }
        if (verbose) {
            System.out.println("Generated Automatic-Module-Name for " + gid
                    + ":" + artifactId + " is " + result);
        }
        return result;
    }

    private static String splitAndConvert(String what) {
        String[] parts = what.split("[\\._-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String converted = convert(part);
            if (!converted.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(converted);
            }
        }
        return sb.toString();
    }

    private static String convert(String what) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < what.length(); i++) {
            char c = what.charAt(i);
            boolean valid;
            switch (i) {
                case 0:
                    valid = Character.isJavaIdentifierStart(c);
                    break;
                default:
                    valid = Character.isJavaIdentifierPart(c);
                    break;
            }
            if (valid) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
