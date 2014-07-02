package com.socrata.geospace

import com.rojoma.json.ast._
import org.geoscript.feature.{Feature, Schema}
import org.slf4j.LoggerFactory
import scala.util.{Success, Failure, Try}


/**
 * Ingests a set of features as a Socrata dataset
 */
object FeatureIngester {
  val logger = LoggerFactory.getLogger(getClass)

  case class Response(uid: String, upsertCount: Int)

  /**
   * Ingests the shapefile schema and rows into Dataspace
   * @param sodaFountain Connection to Soda Fountain
   * @param resourceName Resource identifier in Dataspace
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   * @return Soda Fountain response
   */
  def ingestViaSodaServer(sodaFountain: SodaFountainClient, resourceName: String, features: Traversable[Feature], schema: Schema): Try[JValue] = {
    logger.info("Ingesting features for resource {}, schema = {}", resourceName, schema: Any)
    for { create  <- sodaFountain.create(GeoToSoda2Converter.getCreateBody(resourceName, schema))
          upsert  <- sodaFountain.upsert(resourceName, GeoToSoda2Converter.getUpsertBody(features, schema))
          publish <- sodaFountain.publish(resourceName)
    } yield publish
  }

  /**
   * Uses Core server endpoint to ingest shapefile schema and rows into Dataspace
   * @param coreServer Connection to Core server
   * @param friendlyName Human readable name for the created dataset
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   * @return 4x4 created by Core server, plus the number of rows upserted
   */
  def ingestViaCoreServer(coreServer: CoreServerClient, friendlyName: String, features: Traversable[Feature], schema: Schema): Try[Response] = {
    logger.info("Creating dataset for schema = {}", schema: Any)
    for { fourByFour <- createDatasetViaCoreServer(coreServer, friendlyName)
          addColumns <- addColumnsViaCoreServer(coreServer, schema, fourByFour)
          upsert     <- coreServer.upsert(fourByFour, GeoToSoda1Converter.getUpsertBody(features, schema))
          publish    <- coreServer.publish(fourByFour)
    } yield {
      Response(fourByFour, features.size)
    }
  }

  private def createDatasetViaCoreServer(coreServer: CoreServerClient, friendlyName: String): Try[String] =
    for { create <- coreServer.create(GeoToSoda1Converter.getCreateBody(friendlyName)) } yield {
      create match {
        case JObject(map)  =>
          val JString(id) = map("id")
          id
      }
    }

  private def addColumnsViaCoreServer(coreServer: CoreServerClient, schema: Schema, fourByFour: String): Try[JValue] = {
    val results = GeoToSoda1Converter.getAddColumnBodies(schema).map { column =>
      coreServer.addColumn(fourByFour, column)
    }

    // This is kind of gross. There must be a cleaner way to
    // flatten an Iterable[Try[Foo]] to a Try[Foo]
    if (results.forall(_.isSuccess)) Success(JNull)
    else Failure(results.find(_.isFailure).get.failed.get)
  }
}
