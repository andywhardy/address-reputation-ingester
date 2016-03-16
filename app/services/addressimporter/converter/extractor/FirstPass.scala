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

package services.addressimporter.converter.extractor

import org.apache.commons.compress.archivers.zip.ZipFile
import services.addressimporter.converter.Extractor.{Blpu, Street}
import services.addressimporter.converter._
import services.addressimporter.converter.extractor.FirstPass.CSVOutput

import scala.collection.immutable.HashMap
import scala.util.Try

object FirstPass {

  type CSVOutput = (CSVLine) => Unit

  def exportDPA(dpa: OSDpa)(out: CSVOutput): Unit = {
    val line = CSVLine(
      dpa.uprn,
      (dpa.subBuildingName + " " + dpa.buildingName).trim,
      (dpa.buildingNumber + " " + dpa.dependentThoroughfareName + " " + dpa.thoroughfareName).trim,
      (dpa.doubleDependentLocality + " " + dpa.dependentLocality).trim,
      dpa.postTown,
      dpa.postcode
    )
    out(line)
  }

  def processFile(csvIterator: Iterator[Array[String]], streetsMap: HashMap[Long, Street],
                  lpiLogicStatusMap: HashMap[Long, Byte], out: CSVOutput): ForwardData = {

    csvIterator.foldLeft(ForwardData.empty.copy(streets = streetsMap, lpiLogicStatus = lpiLogicStatusMap)) {
      case (fd, csvLine) => FirstPassLine.processLine(fd, csvLine, out)
    }
  }

  def firstPass(zipFiles: Vector[ZipFile], out: CSVOutput): Try[ForwardData] = Try {
    def findData(f: ZipFile, fd: ForwardData): Try[ForwardData] =
      LoadZip.zipReader(f)(processFile(_, fd.streets, fd.lpiLogicStatus, out))

    zipFiles.foldLeft(ForwardData.empty) {
      case (accFD, f) =>
        val updates = findData(f, accFD)
        accFD.update(updates.get)
    }
  }


  object FirstPassLine {
    def processLine(fd: ForwardData, csvLine: Array[String], out: CSVOutput): ForwardData =
      csvLine(OSCsv.RecordIdentifier_idx) match {

        case OSHeader.RecordId =>
          OSCsv.csvFormat = if (csvLine(OSHeader.Version_Idx) == "1.0") 1 else 2
          fd // no change

        case OSBlpu.RecordId if OSBlpu.isSmallPostcode(csvLine) =>
          val blpu = OSBlpu(csvLine)
          ForwardData(fd.blpu + (blpu.uprn -> Blpu(blpu.postcode, blpu.logicalStatus)), fd.dpa, fd.streets, fd.lpiLogicStatus)

        case OSDpa.RecordId =>
          val osDpa = OSDpa(csvLine)
          exportDPA(osDpa)(out)
          ForwardData(fd.blpu, fd.dpa + osDpa.uprn, fd.streets, fd.lpiLogicStatus)

        case OSStreet.RecordId =>
          val street = OSStreet(csvLine)

          def updatedStreet(): Street = fd.streets.get(street.usrn).fold(Street(street.recordType)) {
            aStreet: Street =>
              Street(street.recordType, aStreet.streetDescription, aStreet.localityName, aStreet.townName)
          }

          ForwardData(fd.blpu, fd.dpa, fd.streets + (street.usrn -> updatedStreet), fd.lpiLogicStatus)

        case OSStreetDescriptor.RecordId if OSStreetDescriptor.isEnglish(csvLine) =>
          val sd = OSStreetDescriptor(csvLine)

          def updateStreet(): Street = fd.streets.get(sd.usrn).fold(
            Street('A', sd.description, sd.locality, sd.town)) {
            aStreet: Street =>
              Street(aStreet.recordType, sd.description, sd.locality, sd.town)
          }

          ForwardData(fd.blpu, fd.dpa, fd.streets + (sd.usrn -> updateStreet), fd.lpiLogicStatus)

        case _ => fd
      }

  }

}
