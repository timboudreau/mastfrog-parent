package com.mastfrog.maven.plugins.revisioninfo;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public class RevisionInfoMojoTest {

    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
        }

        @Override
        protected void after() {
        }
    };

    @Test
    public void testSomething()
            throws Exception {
        File pom = new File("target/test-classes/project-to-test/");
        assertNotNull(pom);
        assertTrue(pom.exists());

        RevisionInfoMojo mojo = (RevisionInfoMojo) rule.lookupConfiguredMojo(pom, "revision-info");
        assertNotNull(mojo);
        mojo.genClass = "com.foo.VersionInfo";
        mojo.execute();

        File outputDirectory = (File) rule.getVariableValueFromObject(mojo, "outputDirectory");
        assertNotNull(outputDirectory);
        assertTrue(outputDirectory.exists());

        Path propsFile = mojo.propertiesOutputFile();
        assertTrue(Files.exists(propsFile));

        Path sourceFile = mojo.sourceOutputFile();
        assertNotNull(sourceFile);
        assertTrue(Files.exists(sourceFile));

        Properties p = new Properties();
        p.load(Files.newInputStream(propsFile, StandardOpenOption.READ));
        assertFalse(p.isEmpty());
        assertTrue(p.containsKey(LibInfo.REPO_STATUS_PROPERTY));
        assertTrue(p.containsKey(LibInfo.COMMIT_DATE_PROPERTY));
        assertTrue(p.containsKey(LibInfo.LONG_COMMIT_HASH_PROPERTY));
        assertTrue(p.containsKey(LibInfo.SHORT_COMMIT_HASH_PROPERTY));

        String source = Utils.readString(Files.newInputStream(sourceFile, StandardOpenOption.READ), "UTF-8", (int) Files.size(sourceFile));
        assertNotNull(source);
        assertTrue(source.contains("package com.foo;"));
        assertTrue(source.contains("class VersionInfo"));

        for (String s : p.stringPropertyNames()) {
            switch (s) {
                case LibInfo.COMMIT_DATE_ISO_PROPERTY:
                    continue;
                default:
                    String testFor = "public static final String " + Utils.bicapitalizedToConstantName(s);
                    assertTrue("Missing " + testFor, source.contains(testFor));
            }
        }

    }

    @WithoutMojo
    @Test
    public void testBicapitalizeToName() {
        assertEquals("FOO", Utils.bicapitalizedToConstantName("foo"));
        assertEquals("FOO_BAR", Utils.bicapitalizedToConstantName("fooBar"));
        assertEquals("FOO_BAR_BAZ", Utils.bicapitalizedToConstantName("fooBarBaz"));
    }
}
