/*
 *
 *  * Copyright 2016 HM Revenue & Customs
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package fetch

import java.io.File
import java.net.URL

import ingest.StubWorkerFactory
import ingest.writers.OutputFileWriterFactory
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.exec.WorkQueue
import services.model.{StateModel, StatusLogger}
import uk.co.hmrc.logging.StubLogger

@RunWith(classOf[JUnitRunner])
class FetchControllerTest extends PlaySpec with MockitoSugar {

  val base = "http://somedavserver.com:81/webdav"
  val url = new URL(base)
  val username = "foo"
  val password = "bar"
  val zip1 = WebDavFile(new URL(base + "/product/123/variant/DVD1.zip"), "DVD1.zip", isZipFile = true)
  val zip2 = WebDavFile(new URL(base + "/product/123/variant/DVD2.zip"), "DVD2.zip", isZipFile = true)

  val outputDirectory = new File(System.getProperty("java.io.tmpdir") + "/fetch-controller-test")
  val downloadDirectory = new File(outputDirectory, "downloads")

  trait context {
    val logger = new StubLogger
    val status = new StatusLogger(logger)
    val worker = new WorkQueue(status)
    val workerFactory = new StubWorkerFactory(worker)
    val webdavFetcher = mock[WebdavFetcher]
    val unzipper = mock[ZipUnpacker]
    val request = FakeRequest()

    val fetchController = new FetchController(status, workerFactory, webdavFetcher, unzipper, url)

    def parameterTest(product: String, epoch: Int, variant: String): Unit = {
      val writerFactory = mock[OutputFileWriterFactory]
      val request = FakeRequest()

      intercept[IllegalArgumentException] {
        await(call(fetchController.doFetch(product, epoch, variant, None), request))
      }
    }

    def teardown() {
      worker.terminate()
    }
  }


  "parameter test" should {
    """
       when an invalid product is passed to ingest
       then an exception is thrown
    """ in {
      new context {
        parameterTest("$%", 40, "full")
      }
    }

    """
       when an invalid variant is passed to ingest
       then an exception is thrown
    """ in {
      new context {
        parameterTest("abi", 40, ")(")
      }
    }
  }

  "file fetching" should {
    "use the work queue to download files via webdav" in {
      new context {
        // given
        val f1Txt = new File("/a/b/f1.txt")
        val f1Zip = new File("/a/b/f1.zip")
        val f2Txt = new File("/a/b/f2.txt")
        val f2Zip = new File("/a/b/f2.zip")
        val files = List(f1Txt, f1Zip, f2Txt, f2Zip)
        val items = List(DownloadItem.fresh(f1Txt), DownloadItem.fresh(f1Zip), DownloadItem.fresh(f2Txt), DownloadItem.fresh(f2Zip))
        when(webdavFetcher.fetchAll(anyString, anyString, any[Boolean])) thenReturn items

        // when
        val response = await(call(fetchController.doFetch("product", 123, "variant", Some(true)), request))

        // then
        worker.awaitCompletion()
        assert(response.header.status === 202)
        verify(webdavFetcher).fetchAll(anyString, anyString, any[Boolean])
        verify(unzipper).unzipList(any[List[File]], anyString)
        assert(logger.size === 2)
        assert(logger.infos.map(_.message) === List(
          "Info:Starting fetching product/123/variant.",
          "Info:Finished fetching product/123/variant after {}."
        ))
        teardown()
      }
    }

    "download files using webdav then unzip every zip file" in {
      new context {
        // given
        val model1 = StateModel("product", 123, Some("variant"))
        val f1Txt = new File("/a/b/f1.txt")
        val f1Zip = new File("/a/b/f1.zip")
        val f2Txt = new File("/a/b/f2.txt")
        val f2Zip = new File("/a/b/f2.zip")
        val files = List(f1Txt, f1Zip, f2Txt, f2Zip)
        val items = List(DownloadItem.fresh(f1Txt), DownloadItem.fresh(f1Zip), DownloadItem.fresh(f2Txt), DownloadItem.fresh(f2Zip))
        when(webdavFetcher.fetchAll(s"$url/product/123/variant", "product/123/variant", false)) thenReturn items

        // when
        val model2 = fetchController.fetch(model1)

        // then
        assert(model2 === model1)
        verify(webdavFetcher).fetchAll(s"$url/product/123/variant", "product/123/variant", false)
        verify(unzipper).unzipList(files, "product/123/variant")
        assert(logger.size === 0)
        teardown()
      }
    }

    "download files using webdav but only unzip fresh zip files" in {
      new context {
        // given
        val model1 = StateModel("product", 123, Some("variant"))
        val f1Txt = new File("/a/b/f1.txt")
        val f1Zip = new File("/a/b/f1.zip")
        val f2Txt = new File("/a/b/f2.txt")
        val f2Zip = new File("/a/b/f2.zip")
        val items = List(DownloadItem.stale(f1Txt), DownloadItem.stale(f1Zip), DownloadItem.fresh(f2Txt), DownloadItem.fresh(f2Zip))
        when(webdavFetcher.fetchAll(s"$url/product/123/variant", "product/123/variant", false)) thenReturn items

        // when
        val model2 = fetchController.fetch(model1)

        // then
        assert(model2 === model1)
        verify(webdavFetcher).fetchAll(s"$url/product/123/variant", "product/123/variant", false)
        verify(unzipper).unzipList(List(f2Txt, f2Zip), "product/123/variant")
        assert(logger.size === 0)
        teardown()
      }
    }

    """given a list of nno-existent files passed in via the model,
       fetch should download the files
       then unzip all of them
    """ in {
      new context {
        // given
        val product = OSGBProduct("product", 123, List(zip1))
        val model1 = StateModel("product", 123, Some("variant"), None, Some(product))

        val f1Txt = new File("/a/b/DVD1.txt")
        val f1Zip = new File("/a/b/DVD1.zip")
        val items = List(DownloadItem.fresh(f1Txt), DownloadItem.fresh(f1Zip))
        when(webdavFetcher.fetchList(product, "product/123/variant", false)) thenReturn items

        // when
        val model2 = fetchController.fetch(model1)

        // then
        assert(model2 === model1)
        verify(webdavFetcher).fetchList(product, "product/123/variant", false)
        verify(unzipper).unzipList(List(f1Txt, f1Zip), "product/123/variant")
        assert(logger.size === 0)
        teardown()
      }
    }

    """given a list of pre-existing (i.e. stale) files passed in via the model,
       fetch should not download the files
    """ in {
      new context {
        // given
        val product = OSGBProduct("product", 123, List(zip1))
        val model1 = StateModel("product", 123, Some("variant"), None, Some(product))

        val f1Txt = new File("/a/b/DVD1.txt")
        val f1Zip = new File("/a/b/DVD1.zip")
        val items = List(DownloadItem.stale(f1Txt), DownloadItem.stale(f1Zip))
        when(webdavFetcher.fetchList(product, "product/123/variant", false)) thenReturn items

        // when
        val model2 = fetchController.fetch(model1)

        // then
        assert(model2 === model1)
        verify(webdavFetcher).fetchList(product, "product/123/variant", false)
        verify(unzipper).unzipList(Nil, "product/123/variant")
        assert(logger.size === 0)
        teardown()
      }
    }

    """given a list of pre-existing (i.e. stale) files passed in via the model,
       when forceFetch is set,
       fetch should download the files
    """ in {
      new context {
        // given
        val product = OSGBProduct("product", 123, List(zip1))
        val model1 = StateModel("product", 123, Some("variant"), None, Some(product), forceChange = true)

        val f1Txt = new File("/a/b/DVD1.txt")
        val f1Zip = new File("/a/b/DVD1.zip")
        val items = List(DownloadItem.fresh(f1Txt), DownloadItem.fresh(f1Zip))
        when(webdavFetcher.fetchList(product, "product/123/variant", true)) thenReturn items

        // when
        val model2 = fetchController.fetch(model1)

        // then
        assert(model2.hasFailed === false)
        verify(webdavFetcher).fetchList(product, "product/123/variant", true)
        verify(unzipper).unzipList(List(f1Txt, f1Zip), "product/123/variant")
        assert(logger.size === 0)
        teardown()
      }
    }

    "when passed an empty file list, fetch should leave the model in a failed state" in {
      new context {
        // given
        val product = OSGBProduct("product", 123, List(zip1))
        val model1 = StateModel("product", 123, Some("variant"), None, Some(product))

        val items = List[DownloadItem]()
        when(webdavFetcher.fetchList(product, "product/123/variant", false)) thenReturn items

        // when
        val model2 = fetchController.fetch(model1)

        // then
        assert(model2 === model1.copy(hasFailed = true))
        verify(webdavFetcher).fetchList(product, "product/123/variant", false)
        verify(unzipper).unzipList(Nil, "product/123/variant")
        assert(logger.size === 0)
        teardown()
      }
    }
  }

  "cleanup" should {
    "when there are no files present, determineObsoleteFiles will return an empty list" in {
      new context {
        // given

        // when
        val files = fetchController.determineObsoleteFiles

        // then
        assert(files.isEmpty)
      }
    }

    "when there are no unwanted files, determineObsoleteFiles will return an empty list" in {
      new context {
        // given

        // when
        val files = fetchController.determineObsoleteFiles

        // then
        assert(files.isEmpty)
      }
    }

    """
      given that there are some collections in the DB
      and their existence implies that the corresponding files are still needed,
      and there exist some other files from earlier versions
      when determineObsoleteFiles is called
      then it will return the directories containing the obsolete files""".stripMargin in {
      new context {
        // given

        // when
        val files = fetchController.determineObsoleteFiles

        // then
      }
    }
  }
}