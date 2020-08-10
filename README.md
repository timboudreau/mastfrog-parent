Mastfrog Parent
---------------

Master build for a bunch of projects with the groupid com.mastfrog, including giulius, util, giulius-web, scopes and acteur. Also
contains the parent pom for all of the above, which defines global defaults. Subprojects are linked in as Git submodules.

All projects aggregated here are available from Maven Central [under the groupId com.mastfrog](https://search.maven.org/search?q=g:com.mastfrog%20a:subscription).

The following projects are built here (and can be found via [this Maven repository](http://timboudreau.com/builds/):

  * [Acteur](https://github.com/timboudreau/acteur) - A Netty + Guice framework using constructors as function objects to easily build scalable, highly concurrent HTTP servers from reusable, testable chunks of logic
  * [Bunyan-Java](https://github.com/timboudreau/bunyan-java-v2) - A format-compatible port of NodeJS's JSON-based [Bunyan](https://github.com/trentm/node-bunyan) - logging in Java meets the modern world
  * [Blather](https://github.com/timboudreau/blather) - An asynchrnous websocket test harness
  * [Giulius](https://github.com/timboudreau/giulius) - Mini-frameworks for binding loading and merging local and remote configuration, parsing command-line arguments, and automagically binding it all with Guice, and general Guice binding utilities, including
    * Configuring and binding the MongoDB and Postgres _asynchronous_ drivers, so using these is as easy as providing a few configuration parameters
    * The `maven-merge-configuration` plugin, which can generate "fat-jar" applications while correctly coalescing configuration and META-INF/services files allowing Giulius and Acteur based applications to be simply run using `java -jar`
    * Regular JDBC and MongoDB guice bindings
    * Reloading giulius configuration on Unix signals
  * [Giulius-Tests](https://github.com/timboudreau/giulius-tests) - A JUnit-4 test runner that allows unit tests methods to be injected by Guice, so you can write zero-setup Guice tests, including parameterizing tests with multiple modules that bind different implementations and run the same tests against each one
  * [Giulius-Selenium](https://github.com/timboudreau/giulius-selenium-tests) - An extension to Giulius-Tests which makes it easy to write Selenium tests with injected test fixtures and run them via JUnit, so your Selenium tests can take advantage of all of the reporting options for JUnit; and supports writing Selenium tests in Groovy.  So you define complex test fixtures that may drive a web site to a particular state, and have them injected into your test.
  * [Annotation Tools](https://github.com/timboudreau/annotation-tools) - A toolkit for writing annotation processors, and an excellent, modern builder-based Java code generator
  * [Giulius-Web](https://github.com/timboudreau/giulius-web) - Guice bindings for using Giulius with servlet containers;  Freemarker-templated email generation
  * [Netty-Http-Client](https://github.com/timboudreau/netty-http-client) - An asynchronous, Netty-based HTTP client
  * [Mastfrog-Utils](https://github.com/timboudreau/util) - Now broken out into a number of libraries, includes
    * Fast, tiny-memory-footprint BitSet based directed and undirected graphs
    * The missing functional interfaces you wish the JDK had - throwing variants of JDK consumers, predicates and functions, and derivations with 3-8 arguments - so if you need a 5-argument consumer that throws an `IOException`, then `IOPetaConsumer` is the lambda you're looking for
    * Tools for writing annotation processors cleanly and easily with extensive validation
    * High-performance binary-search based collection implementations over arrays of Java primitives
    * Utilities on top of `java.time` including
       * Replacements for Joda Time's very useful (for time-series data) interval classes that `java.time` didn't replicate (even though they have the same author)
       * Flexible string serialization and deserialization / formatting of Durations
    * Log structured storage - the persistence building block for any sort of reliable message queue, store-and-forward cache, etc.
    * File channel pooling - allows multiple threads to access a file channel safely without interfering with each other - useful for creating append-only data structures such as message queues
    * String and stream utilities, including
       * Easy to use, extensible character escaping
       * Levenshtein distance
       * Random string and unique identifier generation with tunable characteristics
       * Eight-bit deduplicated and interned string pools for handling huge parses of ASCII data in finite memory
       * Replacing `System.out` and `System.err` on a per-thread basis
       * Platform-independent implementation of `java.nio.Path` - `UnixPath` - useful for implementing in-memory filesystems
       * File tailing utilities
       * Wrapper streams which compute hashes as bytes are pulled through them
       * Memory-based NIO channel implementations - equivalents to `ByteArray*Stream` for use with NIO
    * Tools for building complex (yet non-lambda, _loggable!_) predicates

There is very little code in this project - it mainly hosts the parent POM files and integrates other projects
as Git submodules.  All of those projects are independently buildable.

The following structure is used to allow child projects to remain independently buildable:

  * ``CHILD_PROJECT``
     * ``CHILD_PROJECT_MODULE_LIST``
         * ``GIT SUBMODULE``

``parent/pom.xml`` is the master POM file which all of the mentioned projects and their subprojects inherit from.

The master POM file lists each sub-project as a module, and lists ``parent/pom.xml`` as its parent; each sub-project lists modules
in a Git submodule.  Thus each sub-project can be built as long as the parent POM can be downloaded, but the whole group can be built
as a group using the module-POM files that are part of this project.

To build all of the above, clone this repo and the run the ``init`` script in the root of the repository, to populate the Git submodules and do an initial build
