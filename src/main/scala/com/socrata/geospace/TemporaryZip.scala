package com.socrata.geospace

import collection.JavaConverters._
import com.rojoma.simplearm.util._
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
class TemporaryZip(compressed: Array[Byte]) extends Closeable {
  /**
   * the zip file saved temporarily to disk.
   */
  lazy val archive: File = {
    if (compressed == null || compressed.length == 0) {
      throw new IllegalArgumentException("Null or empty zip file")
    }

    val tmpFile = File.createTempFile("shp_", ".zip")
    for {
      in <- managed(new ByteArrayInputStream(compressed))
      out <- managed(new FileOutputStream(tmpFile))
    } {
      IOUtils.copy(in, out)
    }

    tmpFile
  }

  /**
   * The single directory containing the contents of the zip file.
   * The directory structure of the original zip file is flattened.
   */
  lazy val contents: File = {
    val contentsTmpDir = Files.createTempDirectory("shp_")

    for { zip <- managed(new ZipFile(archive)) } {
      // Save zip contents in a single directory.
      // Any directory structure will be flattened.
      // If there is more than one file with the same name in different subdirectories,
      // we don't bother resolving the conflict and just overwrite the same base filename
      // in the same temp directory.
      val files = zip.entries.asScala.filter(e => !e.isDirectory)
      files.foreach {
        e =>
          val contentFile = new File(contentsTmpDir.toString, FilenameUtils.getName(e.getName))
          for {
            contentOut <- managed(new FileOutputStream(contentFile))
          } {
            IOUtils.copy(zip.getInputStream(e), contentOut)
          }
      }
    }

    contentsTmpDir.toFile
  }

  /**
   * Removes all temporary files from the file system once they are no longer needed.
   */
  def close() {
    FileUtils.deleteDirectory(contents)
    archive.delete()
  }
}