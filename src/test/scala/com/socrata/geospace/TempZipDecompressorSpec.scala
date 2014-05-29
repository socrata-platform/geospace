package com.socrata.geospace

import org.scalatest.{FunSuite, Matchers}
import java.io.{FileInputStream, File}
import java.nio.file.{Paths, Files}
import org.apache.commons.codec.binary.Base64

class TempZipDecompressorSpec extends FunSuite with Matchers  {
  // Base 64 string of a zip file with the following contents:
  // - file1.txt
  // - sub/file2.txt
  val ZipFileWithTxt = "UEsDBAoAAAAAAGNWvUT7k1vJCQAAAAkAAAAJABwAZmlsZTEudHh0VVQJAAOKc4dTznSHU3V4CwABBPUBAAAEFAAAAGdpcmFmZmUxClBLAwQKAAAAAABtVr1EAAAAAAAAAAAAAAAABAAcAHN1Yi9VVAkAA51zh1PndIdTdXgLAAEE9QEAAAQUAAAAUEsDBAoAAAAAAG1WvUQ4wHbiCQAAAAkAAAANABwAc3ViL2ZpbGUyLnR4dFVUCQADnXOHU850h1N1eAsAAQT1AQAABBQAAABnaXJhZmZlMgpQSwECHgMKAAAAAABjVr1E+5NbyQkAAAAJAAAACQAYAAAAAAABAAAApIEAAAAAZmlsZTEudHh0VVQFAAOKc4dTdXgLAAEE9QEAAAQUAAAAUEsBAh4DCgAAAAAAbVa9RAAAAAAAAAAAAAAAAAQAGAAAAAAAAAAQAO1BTAAAAHN1Yi9VVAUAA51zh1N1eAsAAQT1AQAABBQAAABQSwECHgMKAAAAAABtVr1EOMB24gkAAAAJAAAADQAYAAAAAAABAAAApIGKAAAAc3ViL2ZpbGUyLnR4dFVUBQADnXOHU3V4CwABBPUBAAAEFAAAAFBLBQYAAAAAAwADAOwAAADaAAAAAAA="

  // Base 64 string of a simple text file
  val NotAZipFile = "Z2lyYWZmZTEK"

  test("Decompress and flatten valid zip file") {
    val zipBytes = Base64.decodeBase64(ZipFileWithTxt);
    val tzd = new TempZipDecompressor
    var tempDir = ""

    tzd.decompress(zipBytes,
    {
      dir =>
        val files = dir.listFiles
        files.size should be (2)
        files.filter(f => f.getName.equals("file1.txt")).size should be (1)
        files.filter(f => f.getName.equals("file2.txt")).size should be (1)
        files.filter(f => f.isDirectory).size should be (0)

        tempDir = dir.getAbsolutePath
    })

    Files.notExists(Paths.get(tempDir)) should be (true)
    // TODO : How to test that the saved zip file also get deleted afterwards?
  }

  test("Null byte array") {
    val tzd = new TempZipDecompressor

    a [IllegalArgumentException] should be thrownBy {
      tzd.decompress(null, {
        dir =>
      })
    }
  }

  test("Empty byte array") {
    val tzd = new TempZipDecompressor

    a [IllegalArgumentException] should be thrownBy {
      tzd.decompress(new Array[Byte](0), {
        dir =>
      })
    }
  }

  test("Byte array not a valid zip file") {
    val notAZipBytes = Base64.decodeBase64(NotAZipFile);
    val tzd = new TempZipDecompressor

    a [IllegalArgumentException] should be thrownBy {
      tzd.decompress(notAZipBytes, {
        dir =>
      })
    }
  }
}
