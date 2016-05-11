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

package services.model

import org.scalatest.{FunSuite, Matchers}
import uk.co.hmrc.logging.StubLogger

class StatusLoggerTest extends FunSuite with Matchers {

  test(
    """
      When messages are put onto the statusLogger
      Then messages are updated in the statusLogger
      The status will return them
    """) {

    val tee = new StubLogger

    val statusLogger = new StatusLogger(tee, 2)

    statusLogger.info("a1")
    statusLogger.status should fullyMatch regex "a1"

    statusLogger.info("a2")
    statusLogger.status should fullyMatch regex "a1\na2"

    statusLogger.info("a3")
    statusLogger.status should fullyMatch regex "a1\na2\na3"

    statusLogger.info("a4")
    statusLogger.status should fullyMatch regex "a1\na2\na3\na4"

    statusLogger.startAfresh()
    statusLogger.info("b5")
    statusLogger.status should fullyMatch regex "a1\na2\na3\na4\nTotal .*s\n~~~~~~~~~~~~~~~\nb5"

    statusLogger.info("b6")
    statusLogger.status should fullyMatch regex "a1\na2\na3\na4\nTotal .*s\n~~~~~~~~~~~~~~~\nb5\nb6"

    statusLogger.startAfresh()
    statusLogger.info("c7")
    statusLogger.status should fullyMatch regex "a1\na2\na3\na4\nTotal .*s\n~~~~~~~~~~~~~~~\nb5\nb6\nTotal .*s\n~~~~~~~~~~~~~~~\nc7"

    statusLogger.info("c8")
    statusLogger.status should fullyMatch regex "a1\na2\na3\na4\nTotal .*s\n~~~~~~~~~~~~~~~\nb5\nb6\nTotal .*s\n~~~~~~~~~~~~~~~\nc7\nc8"

    statusLogger.startAfresh()
    statusLogger.info("d9")
    statusLogger.status should fullyMatch regex "b5\nb6\nTotal .*s\n~~~~~~~~~~~~~~~\nc7\nc8\nTotal .*s\n~~~~~~~~~~~~~~~\nd9"

    statusLogger.info("d10")
    statusLogger.status should fullyMatch regex "b5\nb6\nTotal .*s\n~~~~~~~~~~~~~~~\nc7\nc8\nTotal .*s\n~~~~~~~~~~~~~~~\nd9\nd10"
  }

}
