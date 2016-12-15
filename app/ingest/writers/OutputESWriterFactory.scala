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

package ingest.writers

import com.google.inject.{Inject, Singleton}
import config.Provenance
import services.model.{StateModel, StatusLogger}
import uk.gov.hmrc.BuildProvenance
import uk.gov.hmrc.address.services.es.IndexMetadata
import uk.gov.hmrc.address.services.writers.{OutputESWriter, OutputWriter, WriterSettings}

import scala.concurrent.ExecutionContext

@Singleton
class OutputESWriterFactory @Inject()(elasticSearchService: IndexMetadata,
                                      statusLogger: StatusLogger,
                                      provenance: Provenance,
                                      ec: ExecutionContext) extends OutputWriterFactory {
  def writer(model: StateModel, settings: WriterSettings): OutputWriter = {
    new OutputESWriter(model, statusLogger, elasticSearchService, settings, ec,
      BuildProvenance(provenance.version, provenance.buildNumber))
  }
}
