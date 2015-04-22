package com.socrata.geospace.http.ingestion

import javax.servlet.http.{HttpServletResponse => HttpStatus}

import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.io.{EndOfArrayEvent, StartOfArrayEvent, Position}
import com.socrata.geospace.http.client.CoreServerClient
import com.socrata.geospace.lib.client.{GeoToSoda1Converter, GeoToSoda2Converter, SodaResponse}
import com.socrata.geospace.lib.shapefile.FeatureJValueIterator
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.metrics.Metrics
import org.geoscript.feature.{Feature, Schema}
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success, Try}


/**
 * Ingests a set of features as a Socrata dataset
 */
object FeatureIngester extends Metrics {
  val logger = LoggerFactory.getLogger(getClass)

  case class Response(resourceName: String, upsertCount: Int)

  val coreIngestTimer = metrics.timer("core-ingest")

  /**
   * Uses Core server endpoint to ingest shapefile schema and rows into Dataspace
   * @param requester Core server requester object
   * @param friendlyName Human readable name for the created dataset
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   * @return 4x4 created by Core server, plus the number of rows upserted
   */
  def ingestViaCoreServer(requester: CoreServerClient#Requester,
                          sodaFountain: SodaFountainClient,
                          friendlyName: String,
                          features: Traversable[Feature],
                          schema: Schema): Try[Response] = {
    def sfResourceName(fourByFour: String): String = "_" + fourByFour
    coreIngestTimer.time {
      logger.info("Creating dataset for schema = {}", schema: Any)
      // HACK : Core doesn't seem to chunk the upsert payload properly when
      // passing it on to Soda Fountain. This hack bypasses Core for the upsert.
      // Auth is already validated in the DDL steps, so this should be ok.
      for { fourByFour <- createDatasetViaCoreServer(requester, friendlyName)
            addColumns <- addColumnsViaCoreServer(requester, schema, fourByFour)
            upsert     <- SodaResponse.check(sodaFountain.upsert(sfResourceName(fourByFour),
              GeoToSoda2Converter.getUpsertBody(features, schema)), HttpStatus.SC_OK)
            publish    <- requester.publish(fourByFour)
      } yield {
        // The region cache keys off the Soda Fountain resource name, but we currently
        // ingress the shapefile rows through Core, so we have to mimic the Core->SF
        // resource name mapping here. We can remove this once Core goes away.
        Response(sfResourceName(fourByFour), features.size)
      }
    }
  }


  /**
   * Uses Core server endpoint to ingest shapefile schema and rows into Dataspace
   * @param requester Core server requester object
   * @param friendlyName Human readable name for the created dataset
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   * @return 4x4 created by Core server, plus the number of rows upserted
   */
  def ingestViaCoreServer(requester: CoreServerClient#Requester,
                          sodaFountain: SodaFountainClient,
                          friendlyName: String,
                          features: FeatureJValueIterator,
                          schema: Schema): Try[Response] = {
    def sfResourceName(fourByFour: String): String = "_" + fourByFour
    coreIngestTimer.time {
      logger.info("Creating dataset for schema = {}", schema: Any)
      // HACK : Core doesn't seem to chunk the upsert payload properly when
      // passing it on to Soda Fountain. This hack bypasses Core for the upsert.
      // Auth is already validated in the DDL steps, so this should be ok.
      for { fourByFour <- createDatasetViaCoreServer(requester, friendlyName)
            addColumns <- addColumnsViaCoreServer(requester, schema, fourByFour)
            upsert     <- SodaResponse.check(sodaFountain.upsertStream(
                                          sfResourceName(fourByFour),features), HttpStatus.SC_OK)
            publish    <- requester.publish(fourByFour)
      } yield {
        // The region cache keys off the Soda Fountain resource name, but we currently
        // ingress the shapefile rows through Core, so we have to mimic the Core->SF
        // resource name mapping here. We can remove this once Core goes away.
        Response(sfResourceName(fourByFour), features.size)
      }
    }
  }

  private def createDatasetViaCoreServer(requester: CoreServerClient#Requester, friendlyName: String): Try[String] =
    for { create <- requester.create(GeoToSoda1Converter.getCreateBody(friendlyName)) } yield {
      create match {
        case JObject(map)  =>
          val JString(id) = map("id")
          id
        case x: JValue     =>
          throw new RuntimeException("Unexpected response from getCreateBody(): " + x)
      }
    }

  private def addColumnsViaCoreServer(requester: CoreServerClient#Requester,
                                      schema: Schema, fourByFour: String): Try[JValue] = {
    val results = GeoToSoda1Converter.getAddColumnBodies(schema).map { column =>
      requester.addColumn(fourByFour, column)
    }

    // This is kind of gross. There must be a cleaner way to
    // flatten an Iterable[Try[Foo]] to a Try[Foo]
    if (results.forall(_.isSuccess)) Success(JNull) else Failure(results.find(_.isFailure).get.failed.get)
  }
}
