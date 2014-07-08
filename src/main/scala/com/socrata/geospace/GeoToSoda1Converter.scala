package com.socrata.geospace

import collection.JavaConverters._
import com.rojoma.json.ast._
import com.vividsolutions.jts.geom.{Geometry, MultiPolygon}
import org.geoscript.feature._
import org.opengis.feature.`type`.PropertyDescriptor
import org.geotools.geojson.geom.GeometryJSON
import com.rojoma.json.io.JsonReader

/**
 * Generates Soda1 requests from geo schemata and feature collections
 */
object GeoToSoda1Converter {
  // The feature ID needs to be a part of every row of the shape dataset so we can correlate other datasets
  // such as points to the belonging feature.
  // Note: is there a better way to come up with a column name for feature ID?
  // This here is a hack.
  val FeatureIdColName = "_feature_id"
  val FeatureIdColumnDef = JObject(Map(
    "fieldName"    -> JString(FeatureIdColName),
    "dataTypeName" -> JString("text"),
    "name"         -> JString(FeatureIdColName)
  ))

  /**
   * Maps shapefile types to Soda1 types
   */
  val soda1TypeMap = Map[Class[_], String](
    classOf[MultiPolygon]      -> "multipolygon",
    classOf[String]            -> "text",
    classOf[java.lang.Integer] -> "number",
    classOf[java.lang.Double]  -> "number",
    classOf[java.lang.Long]    -> "number"
  )

  /**
   * Generates a Soda1 create request body
   * @param friendlyName Human readable name of the dataset
   * @return Soda2 create request body
   */
  def getCreateBody(friendlyName: String): JValue = JObject(Map("name" -> JString(friendlyName)))

  /**
   * Generates a Soda1 add column request body for each column in the schema
   * @param schema Schema definition
   * @return List of Soda1 add column request bodies
   */

  def getAddColumnBodies(schema: Schema): Iterable[JValue] =
    Seq(FeatureIdColumnDef) ++ schema.getDescriptors.asScala.map(columnToJObject)

  /**
   * Generates a Soda1 upsert request body
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   * @return Soda1 upsert request body
   */
  def getUpsertBody(features: Traversable[Feature], schema: Schema): JValue = {
    val attrNames = schema.getDescriptors.asScala.map(_.getName.toString.toLowerCase)
    val featuresAsJObject = features.map(rowToJObject(_, attrNames.toSeq))
    JArray(featuresAsJObject.toSeq)
  }

  /**
   * Converts a geo schema attribute to a Dataspace JSON column definition
   * @param attr Attribute to be converted to a column
   * @return JSON representation of the column
   */
  private def columnToJObject(attr: PropertyDescriptor): JValue = {
    val name = attr.getName.toString.toLowerCase
    val typ = soda1TypeMap.getOrElse(
      attr.getType.getBinding,
      throw new IllegalArgumentException(s"Unsupported type in shapefile: '${attr.getType.getBinding.getCanonicalName}'"))

    JObject(Map(
      "fieldName"    -> JString(name),
      "dataTypeName" -> JString(typ),
      "name"         -> JString(name)
    ))
  }

  /**
   * Converts a geo feature to a Core server JSON row definition
   * @param feature Feature to be converted to a row
   * @param attrNames List of column names
   * @return JSON representation of the row
   */
  private def rowToJObject(feature: Feature, attrNames: Seq[String]): JValue = {
    val geoJsonWriter = new GeometryJSON()
    require(feature.getAttributes.size == attrNames.size, "Inconsistency between shapefile schema and features")
    val fields = feature.getAttributes.asScala.zip(attrNames).map {
      case (g: Geometry, name) => name -> JsonReader.fromString(geoJsonWriter.toString(g))
      case (attr, name)        => name -> JString(attr.toString)
    }

    JObject(fields.toMap + (FeatureIdColName -> JString(feature.getID)))
  }
}
