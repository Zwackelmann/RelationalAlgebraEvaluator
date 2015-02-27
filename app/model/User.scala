package model

import anorm._
import anorm.SqlParser._
import scala.collection.mutable
import play.api.db._
import play.api.Play.current
import relation.Scope
import relation.RelAlgParser
import java.io.File
import scala.io.Source

object User {
  val simple = {
    get[String]("users.name") ~
    get[String]("users.password") map {
      case name~password => User(name, password)
    }
  }

  private val loggedInUsers = new mutable.HashMap[String, User]();
  def authenticate(name: String, password: String): Option[User] = {
    val user = DB.withConnection { implicit connection =>
      SQL(
        """
          SELECT * FROM users WHERE 
          name = {name} AND password = {password}
        """
      ).on(
        'name -> name,
        'password -> password
      ).as(User.simple.singleOpt)
    }

    if(user.isDefined) {
      loggedInUsers(name) = user.get
    }

    user
  }

  def findLoggedIn(name: String) = loggedInUsers.get(name)

  def logout(name: String): Unit = loggedInUsers -= name
  def logout(user: User): Unit = logout(user.name)
}

case class User(name: String, password: String) {
  def relationsPath = RelationHelper.rootDataPath + "/" + name
  def relationPath(relationName: String) = relationsPath + "/" + relationName + ".rel"

  if(!new File(relationsPath).exists) {
    new File(relationsPath).mkdir()
  }

  def dropScopeCache() {
    scopeCache = None
  }

  var scopeCache: Option[Scope] = None
  def scope() = scopeCache getOrElse {
    scopeCache = Some(Scope.fromDir(new File(relationsPath)))
    scopeCache.get
  }

  def hasRelation(relationName: String) = {
    relations().map(_._1).contains(relationName)
  }

  def relationContent(relationName: String) = {
    Source.fromFile(relationPath(relationName)).getLines.mkString("\n")
  }

  def relations() = {
    val rels = scope().relations
    (for((relname, rel) <- rels) yield (relname, rel.relationHead.atts.map(a => a.name))).toList
  }

  def parser() = {
    new RelAlgParser(scope())
  }
}