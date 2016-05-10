enablePlugins(ScalaJSPlugin)

scalaVersion := "2.11.8"

persistLauncher in Compile := true

persistLauncher in Test := false

val cldrVersion = settingKey[String]("The version of CLDR used.")
lazy val downloadFromZip = taskKey[Unit]("Download the sbt zip and extract it to ./temp")

cldrVersion := "29"

downloadFromZip := {
  if(java.nio.file.Files.notExists((resourceDirectory in Compile).value.toPath)) {
    println("CLDR files missing, downloading...")
    IO.unzipURL(new URL(s"http://unicode.org/Public/cldr/${cldrVersion.value}/core.zip"), (resourceDirectory in Compile).value)
  } else {
    println("CLDR files already available")
  }
}

lazy val root = project.in(file(".")).
  aggregate(localegenJS, localegenJVM).
  settings(
    publish := {},
    publishLocal := {}
  )

lazy val localegen = crossProject.in(file(".")).
  settings(
    name := "cldr",
    organization := "com.github.cquiroz.locale-gen",
    version := "0.1-SNAPSHOT"
  )

lazy val localegenJVM = localegen.jvm
lazy val localegenJS = localegen.js

compile in Compile <<= (compile in Compile).dependsOn(downloadFromZip)