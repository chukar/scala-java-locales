import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import sbt.Keys._

val cldrVersion = settingKey[String]("The version of CLDR used.")

Global / onChangedBuildSource := ReloadOnSourceChanges

val commonSettings: Seq[Setting[_]] = Seq(
  cldrVersion := "35",
  version := s"0.6.0-cldr${cldrVersion.value}-SNAPSHOT",
  organization := "io.github.cquiroz",
  scalaVersion := "2.13.1",
  crossScalaVersions := {
    if (scalaJSVersion.startsWith("0.6")) {
      Seq("2.10.7", "2.11.12", "2.12.10", "2.13.1")
    } else {
      Seq("2.11.12", "2.12.10", "2.13.1")
    }
  },
  scalacOptions ++= Seq("-deprecation", "-feature"),
  scalacOptions := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        scalacOptions.value ++ Seq("-deprecation:false", "-Xfatal-warnings")
      case Some((2, 10)) =>
        scalacOptions.value
    }
  },
  scalacOptions in (Compile, doc) := Seq(),
  mappings in (Compile, packageBin) ~= {
    // Exclude CLDR files...
    _.filter(!_._2.contains("core"))
  },
  useGpg := true,
  exportJars := true,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots".at(nexus + "content/repositories/snapshots"))
    else
      Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
  },
  pomExtra :=
    <url>https://github.com/cquiroz/scala-java-locales</url>
    <licenses>
      <license>
        <name>BSD-style</name>
        <url>http://www.opensource.org/licenses/bsd-license.php</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:cquiroz/scala-java-locales.git</url>
      <connection>scm:git:git@github.com:cquiroz/scala-java-locales.git</connection>
    </scm>
    <developers>
      <developer>
        <id>cquiroz</id>
        <name>Carlos Quiroz</name>
        <url>https://github.com/cquiroz/</url>
      </developer>
    </developers>
    <contributors>
      <contributor>
        <name>Eric Peters</name>
        <url>https://github.com/er1c</url>
      </contributor>
      <contributor>
        <name>A. Alonso Dominguez</name>
        <url>https://github.com/alonsodomin</url>
      </contributor>
      <contributor>
        <name>Marius B. Kotsbak</name>
        <url>https://github.com/mkotsbak</url>
      </contributor>
      <contributor>
        <name>Timothy Klim</name>
        <url>https://github.com/TimothyKlim</url>
      </contributor>
    </contributors>,
  pomIncludeRepository := { _ =>
    false
  }
)

lazy val api = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("api"))
  .settings(commonSettings: _*)
  .settings(
    name := "cldr-api",
    scalaVersion := "2.12.10",
    libraryDependencies += "org.portable-scala" %%% "portable-scala-reflect" % "1.0.0"
  )

lazy val sbt_locales = project
  .in(file("sbt-locales"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "sbt-locales",
    scalaVersion := "2.12.10",
    crossScalaVersions := Seq(),
    publishArtifact in (Compile, packageDoc) := false,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
    addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "0.6.32"),
    addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0"),
    libraryDependencies ++= Seq(
      "com.eed3si9n"           %% "gigahorse-okhttp" % "0.5.0",
      "org.scala-lang.modules" %% "scala-xml"        % "1.2.0",
      "com.github.pathikrit"   %% "better-files"     % "3.8.0",
      "org.typelevel"          %% "cats-core"        % "2.1.0",
      "org.typelevel"          %% "cats-effect"      % "2.1.0",
      "com.eed3si9n"           %% "treehugger"       % "0.4.4"
    )
  )
  .dependsOn(api.jvm)

lazy val scalajs_locales: Project = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "root",
    publish := {},
    publishLocal := {},
    publishArtifact := false
  )
  // don't include scala-native by default
  .aggregate(api.js, api.jvm, sbt_locales, core.js, core.jvm, testSuite.js, testSuite.jvm)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings: _*)
  .settings(
    name := "scala-java-locales"
  )
  .jsSettings(
    scalacOptions ++= {
      val tagOrHash =
        if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
        else s"v${version.value}"
      (sourceDirectories in Compile).value.map { dir =>
        val a = dir.toURI.toString
        val g = "https://raw.githubusercontent.com/cquiroz/scala-java-locales/" + tagOrHash + "/core/src/main/scala"
        s"-P:scalajs:mapSourceURI:$a->$g/"
      }
    }
  )
  .nativeSettings(
    sources in (Compile, doc) := Seq.empty
  )
  .dependsOn(api)

lazy val testSuite = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .settings(commonSettings: _*)
  .settings(
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    scalaVersion := "2.12.10",
    libraryDependencies += "com.lihaoyi" %%% "utest" % "0.6.4" % "test",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
  .jsSettings(
    parallelExecution in Test := false,
    name := "scala-java-locales testSuite on JS"
  )
  .jsConfigure(_.dependsOn(core.js, macroUtils))
  .jsConfigure(_.enablePlugins(ScalaJSJUnitPlugin))
  .jvmSettings(
    // Fork the JVM test to ensure that the custom flags are set
    fork in Test := true,
    // Use CLDR provider for locales
    // https://docs.oracle.com/javase/8/docs/technotes/guides/intl/enhancements.8.html#cldr
    javaOptions in Test ++= Seq(
      "-Duser.language=en",
      "-Duser.country=",
      "-Djava.locale.providers=CLDR",
      "-Dfile.encoding=UTF8"
    ),
    name := "scala-java-locales testSuite on JVM"
  )
  .jvmConfigure(_.dependsOn(core.jvm, macroUtils))

lazy val macroUtils = project
  .in(file("macroUtils"))
  .settings(commonSettings)
  .settings(
    name := "macroutils",
    organization := "io.github.cquiroz",
    version := "0.0.1",
    libraryDependencies := {
      Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value) ++ {
        CrossVersion.partialVersion(scalaVersion.value) match {
          // if Scala 2.11+ is used, quasiquotes are available in the standard distribution
          case Some((2, scalaMajor)) if scalaMajor >= 11 =>
            libraryDependencies.value
          // in Scala 2.10, quasiquotes are provided by macro paradise
          case Some((2, 10)) =>
            libraryDependencies.value ++ Seq(
              compilerPlugin(("org.scalamacros" % "paradise" % "2.1.0").cross(CrossVersion.full)),
              ("org.scalamacros" %% "quasiquotes" % "2.1.0").cross(CrossVersion.binary)
            )
        }
      }
    }
  )
