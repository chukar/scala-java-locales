package locales

import java.io.{File => JFile}
import better.files._
import sbt._
import sbt.util.Logger
import Keys._
import cats._
import cats.implicits._
import cats.effect
import org.scalajs.sbtplugin.ScalaJSPlugin

object LocalesPlugin extends AutoPlugin {
  sealed trait CLDRVersion {
    val id: String
  }
  case object LatestVersion extends CLDRVersion {
    val id: String = "latest"
  }
  final case class Version(version: String) extends CLDRVersion {
    val id: String = version
  }

  object autoImport {

    /**
      * Settings
      */
    val nsFilter       = settingKey[NumberingSystemFilter]("Filter for numbering systems")
    val calendarFilter = settingKey[CalendarFilter]("Filter for calendars")
    val localesFilter  = settingKey[LocalesFilter]("Filter for locales")
    val dbVersion      = settingKey[CLDRVersion]("Version of the cldr database")
    val localesCodeGen =
      taskKey[Seq[JFile]]("Generate scala.js compatible database of tzdb data")
    lazy val baseLocalesSettings: Seq[Def.Setting[_]] =
      Seq(
        sourceGenerators in Compile += Def.task {
          localesCodeGen.value
        },
        localesCodeGen := Def.task {
          val cacheLocation    = streams.value.cacheDirectory / s"cldr-locales"
          val log              = streams.value.log
          val resourcesManaged = (resourceManaged in Compile).value
          val coreZip          = resourcesManaged / "core.zip"
          val cachedActionFunction: Set[JFile] => Set[JFile] =
            FileFunction.cached(
              cacheLocation,
              inStyle = FilesInfo.hash
            ) { _ =>
              log.info(s"Building cldr library")
              localesCodeGenImpl(
                sourceManaged = (sourceManaged in Compile).value,
                resourcesManaged = (resourceManaged in Compile).value,
                localesFilter = localesFilter.value,
                nsFilter = nsFilter.value,
                calendarFilter = calendarFilter.value,
                dbVersion = dbVersion.value,
                log = log
              )
            }
          cachedActionFunction.apply(Set(coreZip)).toSeq
        }.value,
        libraryDependencies += "org.portable-scala" %% "portable-scala-reflect" % "1.0.0"
      )
  }

  import autoImport._
  override def trigger = noTrigger
  // override def requires = plugin.JSPlugin
  override def requires = org.scalajs.sbtplugin.ScalaJSPlugin
  // override def requires = ScalaJSPlugin // org.scalajs.sbtplugin.ScalaJSPlugin
  override lazy val buildSettings = Seq(
    localesFilter := LocalesFilter.Selection("root"),
    nsFilter := NumberingSystemFilter.Selection("latm"),
    calendarFilter := CalendarFilter.Selection("gregorian"),
    dbVersion := LatestVersion
  )
  // a group of settings that are automatically added to projects.
  override val projectSettings =
    inConfig(Compile)(baseLocalesSettings) // ++
  // inConfig(Test)(baseLocalesSettings)

  def localesCodeGenImpl(
      sourceManaged: JFile,
      resourcesManaged: JFile,
      localesFilter: LocalesFilter,
      nsFilter: NumberingSystemFilter,
      calendarFilter: CalendarFilter,
      dbVersion: CLDRVersion,
      log: Logger
  ): Set[JFile] =
    // val tzdbData: JFile = resourcesManaged / "tzdb"
    // val ttbp = IOTasks.copyProvider(sourceManaged,
    //                                 "TzdbZoneRulesProvider.scala",
    //                                 "org.threeten.bp.zone",
    //                                 false)
    // val jt =
    //   IOTasks.copyProvider(sourceManaged, "TzdbZoneRulesProvider.scala", "java.time.zone", true)
    // val providerCopy = if (includeTTBP) List(ttbp, jt) else List(jt)
    (for {
      _ <- IOTasks.downloadCLDR(log, resourcesManaged, dbVersion)
      // Use it to detect if files have been already generated
      m <- IOTasks.copyProvider(log, sourceManaged, "model.scala", "locales/cldr")
      c <- IOTasks.copyProvider(log, sourceManaged, "cldr.scala", "locales/cldr")
      p <- IOTasks.copyProvider(log, sourceManaged, "provider.scala", "locales/cldr")
      // s <- IOTasks.copyProvider(log, sourceManaged, "ScalaSafeName.scala", "locales/cldr")
      // e <- effect.IO(p.exists)
      // j <- if (e) effect.IO(List(p)) else providerCopy.sequence
      // f <- if (e) IOTasks.tzDataSources(sourceManaged, includeTTBP).map(_.map(_._3))
      f <- IOTasks.generateCLDR(
            sourceManaged,
            resourcesManaged / "locales",
            localesFilter,
            nsFilter,
            calendarFilter
          )

      //     else
      //       IOTasks.generateTZDataSources(sourceManaged, tzdbData, log, includeTTBP, zonesFilter)
    } yield Seq(m, c, p) ++ f).unsafeRunSync.toSet
}
