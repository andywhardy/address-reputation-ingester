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

package services.ingester.converter.extractor

import java.io.File

import services.ingester.converter.Extractor.{Blpu, Street}
import services.ingester.converter._
import uk.co.bigbeeconsultants.http.util.DiagnosticTimer
import uk.co.hmrc.address.osgb.DbAddress
import scala.collection.mutable

object SecondPass {

  def secondPass(files: Seq[File], fd: ForwardData, out: (DbAddress) => Unit, dt: DiagnosticTimer) {
    for (file <- files) {
      LoadZip.zipReader(file, dt) {
        csvIterator =>
          processLine(csvIterator, fd, out)
      }
    }
  }


  private[extractor] def processLine(csvIterator: Iterator[Array[String]], fd: ForwardData, out: (DbAddress) => Unit) {
    for (csvLine <- csvIterator) {
      if (csvLine(OSCsv.RecordIdentifier_idx) == OSLpi.RecordId) {
        val lpi = OSLpi(csvLine)
        val blpu = fd.blpu.get(lpi.uprn)

        blpu match {
          case Some(b) if b.logicalStatus == lpi.logicalStatus =>
            exportLPI(lpi, b, fd.streets)(out)

          case _ =>
        }
      }
    }
  }


  def exportLPI(lpi: OSLpi, blpu: Blpu, streets: mutable.Map[Long, Street])(out: (DbAddress) => Unit) {
    val street = streets.getOrElse(lpi.usrn, Street('X', "<SUnknown>", "<SUnknown>", "<TUnknown>"))

    val line1 = (lpi.saoText + " " + lpi.secondaryNumberRange + " " + lpi.paoText).trim

    val line2 = (lpi.primaryNumberRange + " " + street.filteredDescription).trim

    val line3 = street.localityName

    val line = DbAddress(
      "GB" + lpi.uprn.toString,
      OSCleanup.removeUninterestingStreets(line1),
      OSCleanup.removeUninterestingStreets(line2),
      OSCleanup.removeUninterestingStreets(line3),
      street.townName,
      blpu.postcode)

    out(line)
  }

}
