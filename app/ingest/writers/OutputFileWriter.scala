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
 */

package ingest.writers

import java.io.{OutputStreamWriter, _}
import java.util.Date
import java.util.zip.GZIPOutputStream

import com.google.inject.{Inject, Singleton}
import controllers.ControllerConfig
import services.model.{StateModel, StatusLogger}
import uk.gov.hmrc.address.osgb.DbAddress
import uk.gov.hmrc.address.services.writers.{Algorithm, OutputWriter, WriterSettings}

import scala.concurrent.ExecutionContext


class OutputFileWriter(cc: ControllerConfig,
                       model: StateModel,
                       statusLogger: StatusLogger,
                       settings: WriterSettings,
                       fieldSeparator: String = "\t") extends OutputWriter {

  private var hasFailed = false

  val algChoice = settings.algorithm match {
    case Algorithm(true, true, true, _, _, _) => "DPA+LPI"
    case Algorithm(true, true, false, _, _, _) => "LPI+DPA"
    case Algorithm(true, false, true, _, _, _) => "DPA"
    case Algorithm(false, true, false, _, _, _) => "LPI"
    case _ => ""
  }
  val filter = settings.algorithm.streetFilter
  val fileRoot = model.indexName.toString
  val kind = if (fieldSeparator == "\t") "tsv" else "txt"
  val outputFile = new File(cc.outputFolder, s"$fileRoot-$algChoice-$filter.$kind.gz")

  private val bufSize = 32 * 1024
  private var outCSV: PrintWriter = _

  private var count = 0

  def existingTargetThatIsNewerThan(date: Date): Option[String] =
    if (outputFile.exists() && outputFile.lastModified() >= date.getTime)
      Some(outputFile.getPath)
    else
      None

  def begin() {
    cc.outputFolder.mkdirs()
    val outfile = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), bufSize))
    outCSV = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outfile), bufSize))
  }

  def output(a: DbAddress) {
    // scalastyle:off
    outCSV.println(string(a))
    count += 1
  }

  private def string(a: DbAddress) = {
    // allow the fields to be changed without needing to rewrite this often
    val fields = a.productIterator.toList
    val asStrings: List[String] = fields.map {
      case list: List[_] => list.mkString(":")
      case Some(v) => v.toString
      case None => ""
      case x => x.toString
    }
    asStrings.mkString(fieldSeparator)
  }

  def end(completed: Boolean): Boolean = {
    if (outCSV.checkError()) {
      statusLogger.warn(s"Failed whilst writing to $outputFile")
      hasFailed = true
    }
    outCSV.close()
    println(s"*** document count = $count")
    hasFailed
  }
}

@Singleton
class OutputFileWriterFactory @Inject() (cc: ControllerConfig, statusLogger: StatusLogger, ec: ExecutionContext) extends OutputWriterFactory {
  def writer(model: StateModel, settings: WriterSettings) =
    new OutputFileWriter(cc, model, statusLogger, settings)
}
