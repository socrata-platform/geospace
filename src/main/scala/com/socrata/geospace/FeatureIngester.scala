package com.socrata.geospace

import collection.JavaConverters._
import com.rojoma.json.ast.{JArray, JString, JObject}
import com.rojoma.json.io.JsonReader
import com.vividsolutions.jts.geom.{MultiPolygon, Geometry}
import org.geoscript.feature._
import org.geotools.geojson.geom.GeometryJSON
import org.opengis.feature.`type`.PropertyDescriptor

/**
 * Ingests a set of features as a Socrata dataset
 */
object FeatureIngester {
  val soda2TypeMap = Map[Class[_], String](
    classOf[MultiPolygon] -> "multipolygon",
    classOf[String] -> "text",
    classOf[Int] -> "number",
    classOf[Double] -> "double"
  )

  /**
   * Creates the dataset schema in Dataspace
   * @param resourceName Resource identifier in Dataspace
   * @param schema Schema definition
   */
  def createDataset(resourceName: String, schema: Schema) {
    val columnSchemata = schema.getDescriptors.asScala.map(columnToJObject(_))

    val request = JObject(Map(
      "resource_name" -> JString(resourceName),
      "name" -> JString(resourceName),
      "columns" -> JArray(columnSchemata.toSeq)
    ))

    // TODO : Send this request to Soda Fountain and handle response
    println(request.toString)
  }

  /**
   * Upserts rows to a dataset in Dataspace
   * @param resourceName Resource identifier in Dataspace
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   */
  def upsert(resourceName: String, features: Traversable[Feature], schema: Schema) {
    val attrNames = schema.getDescriptors.asScala.map(_.getName.toString.toLowerCase)
    val featuresAsJObject = features.map(rowToJObject(_, attrNames.toSeq))
    val request = JArray(featuresAsJObject.toSeq)

    // TODO : Send this request to Soda Fountain and handle response
    println(request.toString)
  }

  /**
   * Converts a geo schema attribute to a Dataspace JSON column definition
   * @param attr Attribute to be converted to a column
   * @return JSON representation of the column
   */
  private def columnToJObject(attr: PropertyDescriptor) = {
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
  private def rowToJObject(feature: Feature, attrNames: Seq[String]) = {
    val geoJsonWriter = new GeometryJSON()
    require(feature.getAttributes.size == attrNames.size, "Inconsistency between shapefile schema and features")
    val fields = feature.getAttributes.asScala.zip(attrNames).map {
      case (g: Geometry, name) => name -> JsonReader.fromString(geoJsonWriter.toString(g))
      case (attr, name) => name -> JString(attr.toString)
    }

    JObject(fields.toMap)
  }
}