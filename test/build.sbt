scalaVersion := "2.10.3"

libraryDependencies += "h2-mac" % "h2-mac" % "0.1-SNAPSHOT" from "file:///mnt/tb/h2/h2/bin/h2-1.3.174.jar"

resolvers += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"

libraryDependencies += "org.specs2" % "specs2_2.10" % "2.3.3"

libraryDependencies += "org.scalacheck" % "scalacheck_2.10" % "1.11.0" % "test"

libraryDependencies += "org.jooq" % "jooq" % "3.2.0"
