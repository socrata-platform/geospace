package com.socrata.geospace.lib.shapefile

import com.socrata.geospace.lib.Utils

import collection.JavaConverters._
import com.rojoma.simplearm.util._
import Utils._
import com.typesafe.scalalogging.slf4j.Logging
import java.io._
import java.nio.file.Files
import java.util.zip.ZipFile
import org.apache.commons.io.{FileUtils, FilenameUtils, IOUtils}

import scala.annotation.tailrec


/**
  * Abstract class which is responsible for creating a temporary zip file from a source
  * then allows you to unarchive contents and return the directory with directory structure flattened.
  */
abstract class AbstractZip(tmpDir: Option[File] = None) extends Closeable with Logging {
  require(hasContents, "Null or empty zip file")

  val shpPrefix = "shp_"
  val zipSuffix = ".zip"

  /**
   * output temp file, override if declaring another directory.
   */
  lazy val tmpFile: File = {
    tmpDir match {
      case Some(x) =>
        x.mkdir()
        File.createTempFile(shpPrefix, zipSuffix, x)
      case None => File.createTempFile(shpPrefix, zipSuffix)
    }
  }

  /**
   * the zip file saved temporarily to disk.
   */
  lazy val archive: File = {
    logger.info("Temporarily copying shapefile zip to {}", tmpFile.getAbsolutePath)
    getArchive
  }

    /**
     * Implement to indicate how to get the file.
     */
  protected def getArchive: File

  /**
   * Validates that the zip file has contents
   */
  protected def hasContents: Boolean

  /**
   * The single directory containing the contents of the zip file.
   * The directory structure of the original zip file is flattened.
   */
  lazy val contents: File = {
    val contentsTmpDir = Files.createTempDirectory(shpPrefix)
    logMemoryUsage("Before zip file extraction")

    for { zip <- managed(new ZipFile(archive)) } {
      // Save zip contents in a single directory.
      // Any directory structure will be flattened.
      // If there is more than one file with the same name in different subdirectories,
      // we don't bother resolving the conflict and just overwrite the same base filename
      // in the same temp directory.
      val files = zip.entries.asScala.filter(e => !e.isDirectory)
      files.foreach {
        entry =>
          val contentFile = new File(contentsTmpDir.toString, FilenameUtils.getName(entry.getName))
          for {
            contentOut <- managed(new FileOutputStream(contentFile))
          } {
            IOUtils.copy(zip.getInputStream(entry), contentOut)
          }
      }
    }

    logger.info("Temporarily extracted contents of zip file to {}", contentsTmpDir)
    contentsTmpDir.toFile
  }

  /**
   * Removes all temporary files from the file system once they are no longer needed.
   */
  def close(): Unit = {
    FileUtils.deleteDirectory(contents)
    logger.info("Deleted temporary extracted shapefile contents")
    archive.delete()
    tmpDir.foreach(_.delete)
    logger.info("Deleted temporary shapefile zip")
  }
}


/** Saves the contents of a zip file in a single flattened directory
  * and cleans up all temporary files afterwards.
  *
  * @constructor create a new temporary zip given the zip file as a byte array.
  * @param compressed byte array representation of the zip file
  */
case class ZipFromArray(compressed: Array[Byte]) extends AbstractZip(None) {
  protected def hasContents: Boolean = Option(compressed).filter(_.nonEmpty).isDefined

  def getArchive: File = {
    for {
      in <- managed(new ByteArrayInputStream(compressed))
      out <- managed(new FileOutputStream(tmpFile))
    } {
      IOUtils.copy(in, out)
    }

    tmpFile
  }
}

/** Saves the contents of a zip file in a single flattened directory
  * and cleans up all temporary files afterwards.
  *
  * @constructor create a new temporary zip given the zip file inputstream.
  * @param compressed inputstream representation of the zip file
  * @param tmpDir temp directory where to place the zip file.
  */
case class ZipFromStream(compressed: InputStream, tmpDir: Option[File]) extends AbstractZip(tmpDir) {
  protected def hasContents: Boolean = Option(compressed).filter(_.available >= 0).isDefined

  def getArchive: File = {
    for {
      ops <- managed(new FileOutputStream(tmpFile))
    } {
      IOUtils.copy(compressed, ops)
      ops.flush()
    }

    tmpFile
  }
}
