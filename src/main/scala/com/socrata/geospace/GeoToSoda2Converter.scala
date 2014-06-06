package com.socrata.geospace

import collection.JavaConverters._
import com.rojoma.json.ast.{JValue, JArray, JString, JObject}
import com.rojoma.json.io.JsonReader
import com.vividsolutions.jts.geom.{Geometry, MultiPolygon}
import org.geoscript.feature._
import org.geotools.geojson.geom.GeometryJSON
import org.opengis.feature.`type`.PropertyDescriptor

/**
 * Generates Soda2 requests from geo schemata and feature collections
 */
object GeoToSoda2Converter {
  /**
   * Maps shapefile types to Soda2 types
   */
  val soda2TypeMap = Map[Class[_], String](
    classOf[MultiPolygon] -> "multipolygon",
    classOf[String] -> "text",
    classOf[java.lang.Integer] -> "number",
    classOf[java.lang.Double] -> "double"
  )

  /**
   * Generates a Soda2 create request body
   * @param resourceName Resource identifier in Dataspace
   * @param schema Schema definition
   * @return Soda2 create request body
   */
  def getCreateBody(resourceName: String, schema: Schema): JValue = {
    val columnSchemata = schema.getDescriptors.asScala.map(columnToJObject(_))

    JObject(Map(
      "resource_name" -> JString(resourceName),
      "name" -> JString(resourceName),
      "columns" -> JArray(columnSchemata.toSeq)
    ))
  }

  /**
   * Generates a Soda2 upsert request body
   * @param resourceName Resource identifier in Dataspace
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   * @return Soda2 upsert request body
   */
  def getUpsertBody(resourceName: String, features: Traversable[Feature], schema: Schema): JValue = {
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
    val typ = soda2TypeMap.getOrElse(
      attr.getType.getBinding,
      throw new IllegalArgumentException(s"Unsupported type in shapefile: '${attr.getType.getBinding.getCanonicalName}'"))

    JObject(Map(
      "field_name" -> JString(name),
      "datatype" -> JString(typ),
      "name" -> JString(name)
    ))
  }

  /**
   * Converts a geo feature to a Dataspace JSON row definition
   * @param feature Feature to be converted to a row
   * @param attrNames List of column names
   * @return JSON representation of the row
   */
  private def rowToJObject(feature: Feature, attrNames: Seq[String]): JValue = {
    val geoJsonWriter = new GeometryJSON()
    require(feature.getAttributes.size == attrNames.size, "Inconsistency between shapefile schema and features")
    val fields = feature.getAttributes.asScala.zip(attrNames).map {
      case (g: Geometry, name) => name -> JsonReader.fromString(geoJsonWriter.toString(g))
      case (attr, name) => name -> JString(attr.toString)
    }

    JObject(fields.toMap)
  }
}
