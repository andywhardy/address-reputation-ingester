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

package services.ingester.converter

import java.io.File

import play.api.Logger
import services.ingester.converter.extractor.{FirstPass, SecondPass}
import uk.co.bigbeeconsultants.http.util.DiagnosticTimer
import uk.co.hmrc.address.osgb.DbAddress
import uk.co.hmrc.logging.{LoggerFacade, SimpleLogger}

object Extractor {
  case class Blpu(postcode: String, logicalStatus: Char)

  case class Street(recordType: Char, streetDescription: String = "", localityName: String = "", townName: String = "") {

    def filteredDescription: String = if (recordType == '1') streetDescription else ""
  }
}


class Extractor {
  private def listFiles(file: File): List[File] =
    if (!file.isDirectory) Nil
    else file.listFiles().filter(f => f.getName.toLowerCase.endsWith(".zip")).toList


  def extract(rootDir: File, out: (DbAddress) => Unit, logger: SimpleLogger = new LoggerFacade(Logger.logger)) {
    val dt = new DiagnosticTimer
    val files = listFiles(rootDir).toVector
    val fd = new FirstPass(files, out, dt).firstPass
    logger.info(s"First pass complete at $dt")
    SecondPass.secondPass(files, fd, out, dt)
    logger.info(s"Finished at $dt")
  }
}

class ExtractorFactory {
  def extractor = new Extractor
}
