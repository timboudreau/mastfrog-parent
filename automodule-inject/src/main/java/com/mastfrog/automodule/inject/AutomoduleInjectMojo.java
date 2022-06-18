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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Injects a property into all projects in the session which is the combination
 * of the group id and artifact id, converted into a valid Java identifier.
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

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!"pom".equals(project.getPackaging())) {
            project.getProperties().setProperty(autoModuleNameProperty,
                    autoModuleName(project.getGroupId(), project.getArtifactId()));
        }
    }

    private String autoModuleName(String gid, String artifactId) {
        if ("com.mastfrog".equals(gid) && artifactId.startsWith("util-")) {
            if (!"util-function".equals(artifactId)) {
                artifactId = artifactId.substring(5);
            }
        }
        String result = splitAndConvert(gid) + '.' + splitAndConvert(artifactId);
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
