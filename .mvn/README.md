# `.mvn`

This directory exists only so the `mvn` launcher can locate the repository root.

The launcher walks up from the current directory looking for a `.mvn` directory to set
`maven.multiModuleProjectDirectory`; with no such directory it falls back to the current
directory. The root `pom.xml` resolves the checkstyle gate's `configLocation` and
`suppressionsLocation` against that property, so without this marker a Maven invocation
from anywhere but the repository root looks for `checkstyle.xml` /
`checkstyle-suppressions.xml` beside the wrong directory and fails.

Nothing is configured here. A `maven.config` / `jvm.config` would also work as the marker,
but those are argument files - keep this directory free of build settings unless the whole
repository is meant to inherit them.
