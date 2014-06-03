package com.socrata.geospace

import collection.JavaConverters._
import com.rojoma.json.ast.{JArray, JString, JObject}
import com.rojoma.json.io.JsonReader
import com.vividsolutions.jts.geom.Geometry
import org.geoscript.feature._
import org.geotools.geojson.geom.GeometryJSON
import org.opengis.feature.`type`.PropertyDescriptor

object FeatureIngester {
  val soda2TypeMap = Map(
    "com.vividsolutions.jts.geom.MultiPolygon" -> "multipolygon",
    "java.lang.String" -> "text"
  )

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

  def upsert(resourceName: String, layer: Traversable[Feature], schema: Schema) {
    val attrNames = schema.getDescriptors.asScala.map(_.getName.toString.toLowerCase)
    val features = layer.map(rowToJObject(_, attrNames))
    val request = JArray(features.toSeq)

    // TODO : Send this request to Soda Fountain and handle response
    println(request.toString)
  }

  private def columnToJObject(attr: PropertyDescriptor) = {
    val name = attr.getName.toString.toLowerCase
    val typ = soda2TypeMap.getOrElse(
      attr.getType.getBinding.getCanonicalName,
      throw new IllegalArgumentException(s"Unsupported type in shapefile: '${attr.getType.getBinding.getCanonicalName}'"))

    JObject(Map(
      "field_name" -> JString(name),
      "datatype" -> JString(typ),
      "name" -> JString(name)
    ))
  }

  private def rowToJObject(feature: Feature, attrNames: Iterable[String]) = {
    val geoJsonWriter = new GeometryJSON()
    val fields = feature.getAttributes.asScala.zip(attrNames).map {
      case (attr, name) =>
        val jValue = attr match {
          case g: Geometry => JsonReader.fromString(geoJsonWriter.toString(g))
          case x => JString(x.toString)
        }
        name -> jValue
    }

    JObject(fields.toMap)
  }
}