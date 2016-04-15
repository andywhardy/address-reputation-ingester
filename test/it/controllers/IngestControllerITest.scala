/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.io.File
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Paths}

import helper.{AppServerUnderTest, EmbeddedMongoSuite}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

import scala.annotation.tailrec

class IngestControllerITest extends PlaySpec with EmbeddedMongoSuite with AppServerUnderTest {

  def appConfiguration: Map[String, String] = Map(
    "app.files.rootFolder" -> "/var/tmp",
    "app.files.outputFolder" -> "/var/tmp"
  )

  "ingest resource happy journey" must {
    """
       * observe quiet status
       * start ingest
       * observe busy status
       * await termination
       * observe quiet status
    """ in {
      setUpFixtures()

      verifyOK("/admin/status", "idle")

      val step2 = get("/ingest/to/file/abp/123456/test")
      step2.status mustBe OK

      verifyOK("/admin/status", "busy ingesting")

      waitWhile("/admin/status", "busy ingesting")

      verifyOK("/admin/status", "idle")

      val outFile = new File("/var/tmp/abp_123456.txt.gz")
      outFile.exists() mustBe true

      tearDownFixtures()
    }
  }

  private def verifyOK(path: String, expected: String) {
    val step = get(path)
    step.status mustBe OK
    step.body mustBe expected
  }

  @tailrec
  private def waitWhile(path: String, expected: String): Boolean = {
    Thread.sleep(200)
    val step = get(path)
    if (step.status != OK || step.body != expected) true
    else waitWhile(path, expected)
  }

  private def setUpFixtures() {
    val sample = getClass.getClassLoader.getResourceAsStream("SX9090-first3600.zip")
    val rootFolder = Paths.get("/var/tmp/abp/123456/test")
    rootFolder.toFile.mkdirs()
    Files.copy(sample, rootFolder.resolve("SX9090-first3600.zip"), REPLACE_EXISTING)
    sample.close()
  }

  private def tearDownFixtures() {
    val inFile = new File("/var/tmp/abp/123456/test/SX9090-first3600.zip")
    inFile.delete()
    val outFile = new File("/var/tmp/abp_123456.txt.gz")
    outFile.delete()
  }
}
