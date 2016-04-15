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

package services.ingester.writers

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.{DBCollection, DBObject, MongoException}
import config.ConfigHelper._
import play.api.Logger
import play.api.Play._
import uk.co.hmrc.address.osgb.DbAddress
import uk.co.hmrc.address.services.mongo.CasbahMongoConnection
import uk.co.hmrc.logging.{LoggerFacade, SimpleLogger}


class OutputDBWriterFactory extends OutputWriterFactory {

  private val mongoDbUri = mustGetConfigString(current.mode, current.configuration, "mongodb.uri")
  private val bulkSize = mustGetConfigString(current.mode, current.configuration, "mongodb.bulkSize").toInt
  private val cleardownOnError = mustGetConfigString(current.mode, current.configuration, "mongodb.cleardownOnError").toBoolean

  def writer(collectionNameRoot: String): OutputWriter =
    new OutputDBWriter(bulkSize, cleardownOnError, collectionNameRoot,
      new CasbahMongoConnection(mongoDbUri),
      new LoggerFacade(Logger.logger))
}


class OutputDBWriter(bulkSize: Int,
                     cleardownOnError: Boolean,
                     collectionNameRoot: String,
                     mongoDbConnection: CasbahMongoConnection,
                     logger: SimpleLogger) extends OutputWriter {

  private lazy val collection: DBCollection = mongoDbConnection.getConfiguredDb.getCollection(collectionName)
  private lazy val bulk = new BatchedBulkOperation(bulkSize, {
    collection
  })

  private var count = 0
  private var errored = false

  private def collectionName = {
    var collectionName = collectionNameRoot
    var iteration = 0
    while (mongoDbConnection.getConfiguredDb.collectionExists(collectionName)) {
      iteration += 1
      collectionName = s"${collectionNameRoot}_$iteration"
    }
    logger.info(s"Writing to collection $collectionName")
    collectionName
  }

  override def output(address: DbAddress) {
    try {
      bulk.insert(MongoDBObject(address.tupled))
      count += 1
    } catch {
      case me: MongoException =>
        logger.info(s"Caught Mongo Exception processing bulk insertion $me")
        errored = true
        throw me
    }
  }

  override def close() {
    try {
      bulk.close()
      collection.createIndex(MongoDBObject("postcode" -> 1), MongoDBObject("unique" -> false))
    } catch {
      case me: MongoException =>
        logger.info(s"Caught MongoException committing final bulk insert and creating index $me")
        errored = true
    }

    if (errored) {
      logger.info("Error detected while loading data into MongoDB.")
      if (cleardownOnError) collection.drop()
    } else {
      logger.info(s"Loaded $count documents.")
    }
    mongoDbConnection.close()
    errored = false
  }
}


class BatchedBulkOperation(bulkSize: Int, collection: => DBCollection) {
  require(bulkSize > 0)

  private var bulk = collection.initializeUnorderedBulkOperation
  private var count = 0

  private def reset() {
    bulk = collection.initializeUnorderedBulkOperation
    count = 0
  }

  def insert(document: DBObject) {
    bulk.insert(document)
    count += 1

    if (count == bulkSize) {
      bulk.execute()
      reset()
    }
  }

  def close() {
    if (count > 0) bulk.execute()
    reset()
  }
}
