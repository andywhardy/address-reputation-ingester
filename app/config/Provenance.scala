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

package config

import com.fasterxml.jackson.core.`type`.TypeReference

import scala.io.Source

object Provenance {
  private val stream = getClass.getResourceAsStream("/provenance.json")
  val versionInfo = Source.fromInputStream(stream).mkString
  stream.close()

  private val tr = new TypeReference[Map[String, String]] {}
  private lazy val versionDetails = JacksonMapper.readValue[Map[String, String]](versionInfo, tr)

  private def versionDetailsWithoutDefault(key: String, default: String) = {
    val v = versionDetails.get(key)
    if (v.isEmpty || v.get == default) None
    else v
  }

  def version: Option[String] = versionDetailsWithoutDefault("version", "999-SNAPSHOT")

  def buildNumber: Option[String] = versionDetailsWithoutDefault("buildNumber", "")

  // others:
  //  "buildId"
  //  "jobUrl"
  //  "gitCommit"
  //  "gitBranch"
  //  "timestamp"
}
