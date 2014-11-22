package com.socrata.geospace.shapefile

import collection.JavaConverters._
import com.rojoma.simplearm.util._
import com.socrata.geospace.Utils._
import com.typesafe.scalalogging.slf4j.Logging
import java.io._
import java.nio.file.Files
import java.util.zip.ZipFile
import org.apache.commons.io.{FileUtils, FilenameUtils, IOUtils}

/** Saves the contents of a zip file in a single flattened directory
 * and cleans up all temporary files afterwards.
 *
 * @constructor create a new temporary zip given the zip file as a byte array.
 * @param compressed byte array representation of the zip file
 */
class TemporaryZip(compressed: Array[Byte]) extends Closeable with Logging {
  require(!compressed.isEmpty && compressed.length > 0, "Null or empty zip file")

  /**
   * the zip file saved temporarily to disk.
   */
  lazy val archive: File = {
    val tmpFile = File.createTempFile("shp_", ".zip")
    for {
      in <- managed(new ByteArrayInputStream(compressed))
      out <- managed(new FileOutputStream(tmpFile))
    } {
      IOUtils.copy(in, out)
      logger.info("Temporarily copied shapefile zip to {}", tmpFile.getAbsolutePath)
    }

    tmpFile
  }

  /**
   * The single directory containing the contents of the zip file.
   * The directory structure of the original zip file is flattened.
   */
  lazy val contents: File = {
    val contentsTmpDir = Files.createTempDirectory("shp_")
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
  def close() {
    FileUtils.deleteDirectory(contents)
    logger.info("Deleted temporary extracted shapefile contents")
    archive.delete()
    logger.info("Deleted temporary shapefile zip")
  }
}
