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

package controllers

import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ingester.exec.Task
import uk.gov.hmrc.play.microservice.controller.BaseController

object AdminController extends AdminController(Task.singleton)

class AdminController(task: Task) extends BaseController {

  def cancelTask(): Action[AnyContent] = Action {
    request => {
      handleCancelTask(request)
    }
  }

  def handleCancelTask(request: Request[AnyContent]): Result = {
    if (task.isBusy) {
      task.abort()
      Ok("Interrupt command issued")
    } else {
      BadRequest("Nothing currently executing")
    }
  }

  def status(): Action[AnyContent] = Action {
    request => {
      getStatus(request)
    }
  }

  def getStatus(request: Request[AnyContent]): Result = {
    Ok(task.status).withHeaders(CONTENT_TYPE -> "text/plain")
  }
}
