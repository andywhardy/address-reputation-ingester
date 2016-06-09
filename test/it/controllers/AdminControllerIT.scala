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

package controllers

import helper.{AppServerUnderTest, EmbeddedMongoSuite}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

class AdminControllerIT extends PlaySpec with EmbeddedMongoSuite with AppServerUnderTest {

  def appConfiguration: Map[String, String] = Map()

  // only light-weight tests are provided; mostly, manually testing happens here
  "endpoints should not cause error" must {
    "status" in {
      // already covered elsewhere
    }

    "fullStatus" in {
      val response = get("/admin/fullStatus")
      assert(response.status === OK)
      assert(response.body.nonEmpty)
    }

    "cancelTask" in {
      val response = get("/admin/cancelTask")
      assert(response.status === BAD_REQUEST) // when not busy
    }

    "dirTree" in {
      val response = get("/admin/dirTree")
      assert(response.status === OK)
      assert(response.body.nonEmpty)
    }
  }

}
