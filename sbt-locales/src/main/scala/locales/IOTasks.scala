package locales

import better.files._
import better.files.Dsl._
import java.io.{BufferedInputStream, BufferedOutputStream, InputStream}
import java.io.{FileOutputStream, FileInputStream, File => JFile}
import java.nio.file.{Files, StandardCopyOption}
import java.net.URL
import cats._
import cats.implicits._
import cats.effect.IO
import sbt.Logger
import sbt.io.{IO => SbtIO}

object IOTasks {
  def downloadCLDR(
      log: Logger,
      resourcesDir: JFile,
      cldrVersion: LocalesPlugin.CLDRVersion
  ): IO[Unit] = {
    val localesDir = resourcesDir.toScala / "locales"
    val coreZip    = resourcesDir.toScala / "core.zip"
    if (!localesDir.exists) {
      // var url =
      //   s"http://unicode.org/Public/cldr/${cldrVersion.id}/core.zip"
      var url =
        s"file:///Users/cquiroz/core.zip"
      for {
        _ <- IO(
              log
                .info(s"CLDR data missing. downloading ${cldrVersion.id} version to $localesDir...")
            )
        _ <- IO(log.info(s"downloading from $url"))
        _ <- IO(log.info(s"to file $coreZip"))
        _ <- IO(mkdirs(localesDir))
        _ <- IO(SbtIO.unzipURL(new URL(url), localesDir.toJava))
        _ <- IO(log.info(s"CLDR files expanded on $localesDir"))
      } yield ()
    } else {
      IO(log.debug("cldr files already available"))
    }
  }

  def generateCLDR(
      base: JFile,
      data: JFile,
      filter: String => Boolean,
      nsFilter: NumberingSystemFilter
  ): IO[Seq[JFile]] =
    IO(ScalaLocaleCodeGen.generateDataSourceCode(base, data, filter, nsFilter.filter))

  def providerFile(base: JFile, name: String): IO[File] = IO {
    val pathSeparator   = JFile.separator
    val destinationPath = base.toScala
    destinationPath / name
  }

  def copyProvider(log: Logger, base: JFile, name: String, packageDir: String): IO[JFile] = IO {
    val pathSeparator       = JFile.separator
    val packagePath         = packageDir.replaceAll("\\.", pathSeparator)
    val stream: InputStream = getClass.getResourceAsStream("/" + name)
    val destinationPath     = base.toScala / packagePath
    mkdirs(destinationPath)
    val destinationFile = destinationPath / name
    rm(destinationFile)
    log.info(s"Copy $name to $destinationFile")
    SbtIO.transfer(stream, destinationFile.toJava)
    destinationFile.toJava
  }
}
