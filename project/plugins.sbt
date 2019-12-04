val scalaJSVersion =
  Option(System.getenv("SCALAJS_VERSION")).getOrElse("0.6.28")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "0.6.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "0.6.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.3.9")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

addSbtPlugin("org.scalastyle" % "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

// Dependencies for custom tasks
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    // if scala 2.11+ is used, add dependency on scala-xml module
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      libraryDependencies.value ++ Seq(
        "org.scala-lang.modules" %% "scala-xml" % "1.2.0")
    case _ =>
      libraryDependencies.value
  }
}

addSbtPlugin("io.github.cquiroz" % "sbt-locales" % "0.1.0-SNAPSHOT")
