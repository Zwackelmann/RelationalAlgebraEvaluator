package model

import play.api.libs.json.Json
import relation.Relation
import java.io.File

import anorm._
import anorm.SqlParser._

case class Query(val q: String)

object RelationHelper {
  val rootDataPath = "data"
  
  if(!new File(rootDataPath).exists) {
    new File(rootDataPath).mkdir()
  }

  def toJson(relation: Relation) = {
    Json.toJson(Map(
      "head" -> Json.toJson(relation.relationHead.atts.map(_.name)),
      "content" -> Json.toJson((
        for(tuple <- relation.content) yield relation.relationHead.atts.map(att => 
          if(tuple(att) != null) tuple(att).toString
          else "null"
        )
      ))
    ))
  }
}
