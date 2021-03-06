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

package services.es

import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.unit.TimeValue
import org.scalatest.FunSuite
import uk.gov.hmrc.address.osgb.DbAddress
import uk.gov.hmrc.address.services.es.{ESSchema, ElasticDiskClientSettings, ElasticsearchHelper}

class EsAddressTest extends FunSuite {

  import DbAddress._

  val a1 = DbAddress("GB47070784", List("A1", "Line2", "Line3"), Some("Tynemouth"), "NE30 4HG", Some("GB-ENG"),
    Some("UK"), Some(1234), Some(English), Some(2), Some(1), Some(8), Some("1"), Some("1.0,-1.0"))
  val a2 = DbAddress("GB47070785", List("A2", "Line2", "Line3"), Some("Tynemouth"), "NE30 4HG", Some("GB-ENG"),
    Some("UK"), Some(1234), Some(English), Some(2), Some(1), Some(8), Some("1"), Some("1.0,-1.0"))

  val esDataPath = System.getProperty("java.io.tmpdir") + "/es"
  val esClient = ElasticsearchHelper.buildDiskClient(ElasticDiskClientSettings(esDataPath, true))
  val indexName = "test"

  esClient execute {
    ESSchema.createIndexDefinition(indexName)
  } await()


  test("write then read using ES") {

    val a1t = a1.tupledFlat.toMap + ("id" -> a1.id)
    val a2t = a2.tupledFlat.toMap + ("id" -> a2.id)

    esClient execute {
      index into indexName / "address" fields a1t id a1.id routing a1.postcode
    } await()

    esClient execute {
      index into indexName / "address" fields a2t id a2.id routing a1.postcode
    } await()

    esClient execute {
      refresh index indexName
    } await()

    greenHealth(indexName)

    val size = esClient execute {
      search in indexName / "address" size 0
    }

    assert(size.await.totalHits === 2)

    val searchResult = esClient execute {
      search in indexName / "address" size 100
    } await()

    val results = searchResult.hits.map(hit => DbAddress(hit.sourceAsMap)).toList
    assert(results.toSet === Set(a1, a2))
  }


  private def greenHealth(index: String*) = {
    esClient.java.admin().cluster().prepareHealth(index: _*).setWaitForGreenStatus().setTimeout(twoSeconds).get
  }

  private val twoSeconds = TimeValue.timeValueSeconds(2)
}
