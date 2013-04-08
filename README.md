#mastfrog-parent

Master build for a bunch of projects with the groupid com.mastfrog, including giulius, util, giulius-web, scopes and acteur. Also
contains the parent pom for all of the above, which defines global defaults. Subprojects are linked in as Git submodules.

The following projects are built here (and can be found via [this Maven repository](http://timboudreau.com/builds/):

  * [Giulius](https://github.com/timboudreau/giulius) - Mini-frameworks for binding local settings from properties files
  * [Giulius-Tests](https://github.com/timboudreau/giulius-tests) - A JUnit test runner that allows test methods to be injected with arguments, so you can write zero-setup Guice tests
  * [Scopes](https://github.com/timboudreau/scopes) - Reentrant Guice scopes and related mini-frameworks
  * [Giulius-Web](https://github.com/timboudreau/giulius-web) - Mini-frameworks for doing traditional Java web development with Guice
  * [Utilities](https://github.com/timboudreau/util) - Misc utilities
  * [Giulius-Selenium](https://github.com/timboudreau/giulius-selenium-tests) - An extension to Giulius-Tests which makes it easy to write Selenium tests with injected test fixtures and run them via JUnit, so your Selenium tests can take advantage of all of the reporting options for JUnit
  * [Acteur](https://github.com/timboudreau/acteur) - A Netty+Guice framework using constructors as function objects to easily build scalable HTTP servers
  * [Acteur-Timetracker](https://github.com/timboudreau/acteur-timetracker) - A simple JSON web API for time tracking, abcked by Acteur and MongoDB

The following structure is used to allow child projects to remain independently buildable:

  * ``CHILD_PROJECT``
     * ``CHILD_PROJECT_MODULE_LIST``
         * ``GIT SUBMODULE``

``parent/pom.xml`` is the master POM file which all of the mentioned projects and their subprojects inherit from.

The master POM file lists each sub-project as a module, and lists ``parent/pom.xml`` as its parent; each sub-project lists modules
in a Git submodule.  Thus each sub-project can be built as long as the parent POM can be downloaded, but the whole group can be built
as a group using the module-POM files that are part of this project.

To build all of the above, clone this repo and the run the ``init`` script in the root of the repository, to populate the Git submodules and do an initial build

