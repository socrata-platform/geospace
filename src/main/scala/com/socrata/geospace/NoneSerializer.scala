package com.socrata.geospace

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JNull

class NoneSerializer extends CustomSerializer[Option[_]] ( format => (
  { case JNull => None },
  { case None => JNull }
))
