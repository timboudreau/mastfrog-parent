#mastfrog-parent

Master build for a bunch of projects with the groupid com.mastfrog, including giulius, util, giulius-web, scopes and acteur. Also
contains the parent pom for all of the above, which defines global defaults. Subprojects are linked in as Git submodules.

The following projects are built here:

  * [Giulius](https://github.com/timboudreau/giulius) - Mini-frameworks for binding local settings and writing JUnit tests using Guice
  * [Scopes](https://github.com/timboudreau/scopes) - Reentrant Guice scopes and related frameworks
  * [Giulius-Web](https://github.com/timboudreau/giulius-web) - Mini-frameworks for doing traditional Java web development with Guice
  * [Utilities](https://github.com/timboudreau/util) - Misc utilities

The following structure is used to allow child projects to remain individually buildable:

  * ``CHILD_PROJECT``
     * ``CHILD_PROJECT_MODULE_LIST``
         * ``GIT SUBMODULE``

``parent/pom.xml`` is the master POM file which all of the mentioned projects and their subprojects inherit from.

The master POM file lists each sub-project as a module, and lists ``parent/pom.xml`` as its parent; each sub-project lists modules
in a Git submodule.  Thus each sub-project can be built as long as the parent POM can be downloaded, but the whole group can be built
as a group using the module-POM files that are part of this project.


