package com.socrata.geospace

import com.rojoma.simplearm.util._
import java.io.IOException
import java.nio.file.{Paths, Files}
import org.apache.commons.codec.binary.Base64
import org.scalatest.{FunSuite, Matchers}

class TemporaryZipSpec extends FunSuite with Matchers  {
  // Base 64 string of a zip file with the following contents:
  // - file1.txt
  // - sub/file2.txt
  val ZipFileWithTxt = "UEsDBAoAAAAAAGNWvUT7k1vJCQAAAAkAAAAJABwAZmlsZTEudHh0VVQJAAOKc4dTznSHU3V4CwABBPUBAAAEFAAAAGdpcmFmZmUxClBLAwQKAAAAAABtVr1EAAAAAAAAAAAAAAAABAAcAHN1Yi9VVAkAA51zh1PndIdTdXgLAAEE9QEAAAQUAAAAUEsDBAoAAAAAAG1WvUQ4wHbiCQAAAAkAAAANABwAc3ViL2ZpbGUyLnR4dFVUCQADnXOHU850h1N1eAsAAQT1AQAABBQAAABnaXJhZmZlMgpQSwECHgMKAAAAAABjVr1E+5NbyQkAAAAJAAAACQAYAAAAAAABAAAApIEAAAAAZmlsZTEudHh0VVQFAAOKc4dTdXgLAAEE9QEAAAQUAAAAUEsBAh4DCgAAAAAAbVa9RAAAAAAAAAAAAAAAAAQAGAAAAAAAAAAQAO1BTAAAAHN1Yi9VVAUAA51zh1N1eAsAAQT1AQAABBQAAABQSwECHgMKAAAAAABtVr1EOMB24gkAAAAJAAAADQAYAAAAAAABAAAApIGKAAAAc3ViL2ZpbGUyLnR4dFVUBQADnXOHU3V4CwABBPUBAAAEFAAAAFBLBQYAAAAAAwADAOwAAADaAAAAAAA="

  // Base 64 string of a zip file with the following contents:
  // - file1.txt
  // - sub/file1.txt
  // - sub/file2.txt
  // We want to test that the class doesn't explode
  val ZipFileWithConflict = "UEsDBAoAAAAAAGNWvUT7k1vJCQAAAAkAAAAJABwAZmlsZTEudHh0VVQJAAOKc4dTY8SIU3V4CwABBPUBAAAEFAAAAGdpcmFmZmUxClBLAwQKAAAAAACUhb5EAAAAAAAAAAAAAAAABAAcAHN1Yi9VVAkAA+cXiVMOGIlTdXgLAAEE9QEAAAQUAAAAUEsDBAoAAAAAAJSFvkR58W37CQAAAAkAAAANABwAc3ViL2ZpbGUxLnR4dFVUCQAD5xeJU+cXiVN1eAsAAQT1AQAABBQAAABnaXJhZmZlMwpQSwMECgAAAAAAbVa9RDjAduIJAAAACQAAAA0AHABzdWIvZmlsZTIudHh0VVQJAAOdc4dTY8SIU3V4CwABBPUBAAAEFAAAAGdpcmFmZmUyClBLAQIeAwoAAAAAAGNWvUT7k1vJCQAAAAkAAAAJABgAAAAAAAEAAACkgQAAAABmaWxlMS50eHRVVAUAA4pzh1N1eAsAAQT1AQAABBQAAABQSwECHgMKAAAAAACUhb5EAAAAAAAAAAAAAAAABAAYAAAAAAAAABAA7UFMAAAAc3ViL1VUBQAD5xeJU3V4CwABBPUBAAAEFAAAAFBLAQIeAwoAAAAAAJSFvkR58W37CQAAAAkAAAANABgAAAAAAAEAAACkgYoAAABzdWIvZmlsZTEudHh0VVQFAAPnF4lTdXgLAAEE9QEAAAQUAAAAUEsBAh4DCgAAAAAAbVa9RDjAduIJAAAACQAAAA0AGAAAAAAAAQAAAKSB2gAAAHN1Yi9maWxlMi50eHRVVAUAA51zh1N1eAsAAQT1AQAABBQAAABQSwUGAAAAAAQABAA/AQAAKgEAAAAA"


  // Base 64 string of a simple text file
  val NotAZipFile = "Z2lyYWZmZTEK"

  test("Decompress and flatten valid zip file") {
    val bytes = Base64.decodeBase64(ZipFileWithTxt);
    var tempDir = ""
    var zipPath = ""

    for { zip <- managed( new TemporaryZip(bytes))} {
      val files = zip.contents.listFiles
      files.size should be (2)
      files.filter(f => f.getName.equals("file1.txt")).size should be (1)
      files.filter(f => f.getName.equals("file2.txt")).size should be (1)
      files.filter(f => f.isDirectory).size should be (0)

      tempDir = zip.contents.getAbsolutePath
      zipPath = zip.archive.getAbsolutePath
    }

    Files.notExists(Paths.get(tempDir)) should be (true)
    Files.notExists(Paths.get(zipPath))
  }

  test("Null byte array") {
    an [IllegalArgumentException] should be thrownBy {
      for {zip <- managed(new TemporaryZip(null))} {
        zip.contents.listFiles
      }
    }
  }

  test("Empty byte array") {
    an [IllegalArgumentException] should be thrownBy {
      for { zip <- managed(new TemporaryZip(Array[Byte](0))) } {
        zip.contents.listFiles
      }
    }
  }

  test("Byte array not a valid zip file") {
    val bytes = Base64.decodeBase64(NotAZipFile);

    an [IOException] should be thrownBy {
      for {zip <- managed(new TemporaryZip(bytes))} {
        zip.contents.listFiles
      }
    }
  }

  test("Zip containing 2 files with same name in different subdirectories") {
    val bytes = Base64.decodeBase64(ZipFileWithTxt);

    for { zip <- managed( new TemporaryZip(bytes)) } {
      val files = zip.contents.listFiles
      files.size should be (2)
      files.filter(f => f.getName.equals("file1.txt")).size should be (1)
      files.filter(f => f.getName.equals("file2.txt")).size should be (1)
      files.filter(f => f.isDirectory).size should be (0)
    }
  }

  // TODO : Add test to ensure directory traversal is not possible
}
