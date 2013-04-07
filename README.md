#mastfrog-parent

Master build for a bunch of projects with the groupid com.mastfrog, including giulius, util, giulius-web, scopes and acteur. Also
contains the parent pom for all of the above, which defines global defaults. Subprojects are linked in as Git submodules.

The following structure is used to allow child projects to remain individually buildable:

  * ``CHILD_PROJECT``
     * CHILD_PROJECT_MODULE_LIST
         * SUBMODULE

``parent/pom.xml`` is the master POM file.

To build all of the above, clone this repo and then:

   git submodule init
   git submodule update
   mvn install


