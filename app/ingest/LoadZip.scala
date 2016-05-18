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

package ingest

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.{ZipEntry, ZipInputStream}

import uk.co.hmrc.address.services.CsvParser


case class EmptyFileException(msg: String) extends Exception(msg)


object LoadZip {
  def zipReader(file: File, accept: (String) => Boolean = (_) => true): ZipWrapper = new ZipWrapper(new ZipInputStream(new FileInputStream(file)), accept)
}


class ZipWrapper(zipFile: ZipInputStream, accept: (String) => Boolean, private var zipEntry: Option[ZipEntry] = None)
  extends Iterator[ZippedCsvIterator] with Closeable {

  private var open = true
  private var nextCache: Option[ZippedCsvIterator] = None
  private var nestedZip: Option[ZipWrapper] = None

  private def lookAhead() {
    if (zipEntry.isEmpty) {
      zipEntry = Option(zipFile.getNextEntry)
    }
    while (zipEntry.isDefined && nextCache.isEmpty && nestedZip.isEmpty) {
      val name = zipEntry.get.getName
      if (zipEntry.get.isDirectory) {
        zipEntry = Option(zipFile.getNextEntry)
      } else if (name.toLowerCase.endsWith(".zip")) {
        nestedZip = Some(new ZipWrapper(new ZipInputStream(zipFile, UTF_8), accept))
      } else if (accept(name)) {
        nextCache = Some(new ZippedCsvIterator(zipFile, zipEntry.get, this))
      } else {
        zipEntry = Option(zipFile.getNextEntry)
      }
    }
  }

  lookAhead()

  override def hasNext: Boolean = {
    if (nestedZip.isEmpty) open && nextCache.isDefined
    else if (nestedZip.get.hasNext) true
    else {
      nestedZip = None
      nextCache = None
      zipEntry = None
      lookAhead()
      hasNext
    }
  }

  override def next: ZippedCsvIterator = {
    if (nestedZip.isDefined && nestedZip.get.hasNext) {
      nextCache = None
      nestedZip.get.next

    } else {
      val thisNext = nextCache.get
      nextCache = None
      zipEntry = None
      lookAhead()
      thisNext
    }
  }

  /** Closes the entire ZIP archive. Should be done exactly once after reading all contents. */
  override def close() {
    if (open) {
      open = false
      zipFile.close()
    }
  }

  override def toString: String = zipFile.toString
}


class ZippedCsvIterator(is: InputStream, val zipEntry: ZipEntry, container: Closeable) extends Iterator[Array[String]] {
  private var open = true
  private val ncis = new NonClosingInputStream(is)
  private val data = new InputStreamReader(ncis)
  private val it = CsvParser.split(data)

  override def hasNext: Boolean = open && (ncis.open || it.hasNext)

  override def next: Array[String] = {
    try {
      it.next
    } catch {
      case e: Exception =>
        e.printStackTrace()
        close()
        throw e
    }
  }

  /** Closes the entire ZIP archive. Should be done exactly once after reading all contents. */
  def close() {
    if (open) {
      open = false
      container.close()
    }
  }

  override def toString: String = zipEntry.toString
}


class NonClosingInputStream(is: InputStream) extends FilterInputStream(is) {
  var open = true

  override def close() {
    open = false
  }
}