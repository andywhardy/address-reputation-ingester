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

package helper

import org.scalatest.Assertions
import play.api.Application
import play.api.libs.ws.{InMemoryBody, WS, WSRequestHolder, WSResponse}
import play.api.test.Helpers._

import scala.annotation.tailrec

trait AppServerTestApi extends Assertions {
  def appEndpoint: String

  def app: Application

  val textPlainUTF8 = "text/plain; charset=UTF-8"

  //-----------------------------------------------------------------------------------------------

  def newRequest(method: String, path: String): WSRequestHolder = {
    WS.url(appEndpoint + path)(app).withMethod(method).withRequestTimeout(120 * 1000)
  }

  def newRequest(method: String, path: String, body: String): WSRequestHolder = {
    val wsBody = InMemoryBody(body.trim.getBytes("UTF-8"))
    newRequest(method, path).withHeaders("Content-Type" -> "application/json").withBody(wsBody)
  }

  //-----------------------------------------------------------------------------------------------

  def request(method: String, path: String, hdrs: (String, String)*): WSResponse =
    await(newRequest(method, path).withHeaders(hdrs:_*).execute())

  def request(method: String, path: String, body: String, hdrs: (String, String)*): WSResponse =
    await(newRequest(method, path, body).withHeaders(hdrs:_*).execute())

  def get(path: String): WSResponse =
    await(newRequest("GET", path).withHeaders("User-Agent" -> "xyz").execute())

  def delete(path: String): WSResponse =
    await(newRequest("DELETE", path).withHeaders("User-Agent" -> "xyz").execute())

  def post(path: String, body: String, ct: String = "application/json") =
    await(newRequest("POST", path, body).withHeaders("Content-Type" -> ct, "User-Agent" -> "xyz").execute())

  def put(path: String, body: String, ct: String = "application/json") =
    await(newRequest("PUT", path, body).withHeaders("Content-Type" -> ct, "User-Agent" -> "xyz").execute())

  //-----------------------------------------------------------------------------------------------

  def verifyOK(path: String, expectedBody: String, expectedContent: String = "text/plain") {
    verify(path, OK, expectedBody, expectedContent)
  }

  def verify(path: String, expectedStatus: Int, expectedBody: String, expectedContent: String = "text/plain") {
    val step = get(path)
    assert(step.status === expectedStatus)
    assert(step.header("Content-Type") === Some(expectedContent))
    assert(step.body === expectedBody)
  }

  @tailrec
  final def waitWhile(path: String, currentBody: String, timeout: Int): Boolean = {
    if (timeout < 0) {
      false
    } else {
      Thread.sleep(200)
      val step = get(path)
      if (step.status != OK || step.body != currentBody) true
      else waitWhile(path, currentBody, timeout - 200)
    }
  }

  @tailrec
  final def waitUntil(path: String, currentBody: String, timeout: Int): Boolean = {
    if (timeout < 0) {
      false
    } else {
      Thread.sleep(200)
      val step = get(path)
      if (step.status == OK && step.body == currentBody) true
      else waitUntil(path, currentBody, timeout - 200)
    }
  }

  def dump(response: WSResponse) =
    new WSResponseDumper(response) // provides a lazy wrapper containing a toString method
  // (normally there is no need to dump the response)
}

class WSResponseDumper(response: WSResponse) {
  override def toString =
    "\n  Got " + response.status + ":" + response.body
}
