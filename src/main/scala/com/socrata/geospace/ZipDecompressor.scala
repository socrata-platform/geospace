package com.socrata.geospace

import collection.JavaConverters._
import com.rojoma.simplearm.util._
import java.io._
import java.nio.file.Files
import java.util.zip.ZipFile
import org.apache.commons.io.{FileUtils, FilenameUtils, IOUtils}

class TempZipDecompressor {
  def decompress(compressed: Array[Byte], useContents: File => Unit) = {
    if (compressed == null || compressed.length == 0) {
      throw new IllegalArgumentException("Null or empty zip file")
    }

    val zipTmp = File.createTempFile("shp_", ".zip")
    val contentsTmpDir = Files.createTempDirectory("shp")

    try {
      for {
        zipIn <- managed(new ByteArrayInputStream(compressed))
        zipOut <- managed(new FileOutputStream(zipTmp))
      } {
        // First, unzip the file and save locally
        IOUtils.copy(zipIn, zipOut)
        zipOut.flush

        for {
          zip <- managed(new ZipFile(zipTmp))
        } {
          // Save zip contents in a single directory
          // Any directory structure will be flattened
          zip.entries.asScala.filter(e => !e.isDirectory).foreach(e => {
            val contentFile = new File(FilenameUtils.concat(contentsTmpDir.toString, FilenameUtils.getName(e.getName)))
            for {
              contentOut <- managed(new FileOutputStream(contentFile))
            } {
              IOUtils.copy(zip.getInputStream(e), contentOut)
              contentOut.flush()
            }
          })

          useContents(contentsTmpDir.toFile)
        }
      }
    } catch {
      case _: IOException => throw new IllegalArgumentException("Zip file format is invalid")
    } finally {
      // Clean up original zip and extracted files
      zipTmp.delete()
      FileUtils.deleteDirectory(contentsTmpDir.toFile);
    }
  }
}