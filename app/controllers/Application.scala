package controllers

import play.api._
import play.api.Play.current
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.db._
import play.api.libs.json.Json
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Promise
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsValue
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import model.Query
import model.RelationHelper
import model.User
import relation.RelAlgParser
import relation.Scope
import relation.Relation
import scala.concurrent.Future
import scala.collection.mutable
import anorm._
import anorm.SqlParser._


object Application extends Controller with Security {
  val relationContentMaxLength = 10000

  val queryForm = Form(
    mapping(
      "q" -> nonEmptyText
    )(Query.apply)(Query.unapply)
  )

  val loginForm = Form(
    tuple(
      "name" -> text,
      "password" -> text
    ) verifying (loginFailText, result => result match {
      case (name, password) => User.authenticate(name, password).isDefined
    })
  )

  val relationNameRe = """[a-zA-Z][a-zA-Z0-9_]*""".r
  val createRelationForm = Form(
    tuple(
      "relation_name" -> text(maxLength=100),
      "relation_content" -> text(maxLength=relationContentMaxLength)
    ) verifying ("Invalid relation name", result => result match {
      case (relationName, _) => relationName match {
        case relationNameRe() => true
        case _ => false
      }
    })
  )

  val editRelationForm = Form(
    single(
      "relation_content" -> text(maxLength=relationContentMaxLength)
    )
  )
  
  def loginInitHtml = form => views.html.login(form)
  def loginFailHtml = formWithErrors => views.html.login(formWithErrors)

  def loginSuccess = Redirect(routes.Application.query)
  def logoutSuccess = Redirect("/login")

  def query = Authenticated { implicit request =>
    Ok(views.html.query(queryForm, request.user.relations, None))
  }

  def result = Authenticated { implicit request =>
    queryForm.bindFromRequest.fold(
      formWithErrors => Ok(Json.toJson(Map("success" -> "false", "msg" -> "invalid form parameters"))),
      query => {
        val promiseOfResult = Future {
          val parser = request.user.parser
          try {
            val result = RelationHelper.toJson(parser.eval(query.q))
            Json.toJson(Map("success" -> Json.toJson("true"), "result" -> result))
          } catch {
            case e => Json.toJson(Map("success" -> Json.toJson("false"), "msg" -> Json.toJson(e.getMessage())))
          }
        }
        
        Async { Future.firstCompletedOf(Seq(promiseOfResult, Promise.timeout("Query too long", 3000))).map { 
          case result: JsValue => Ok(result)
          case t: String => Ok(Json.toJson(Map("success" -> "false", "msg" -> "timeout")))
        }}
      }
    )
  }

  def createRelation = Authenticated { implicit request => 
    Ok(views.html.createRelation(createRelationForm, relationContentMaxLength))
  }

  def postRelation = Authenticated { implicit request =>
    val bindedForm = createRelationForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => BadRequest(views.html.createRelation(formWithErrors, relationContentMaxLength)),
      relation => {
        val (relationName, relationContent) = relation
        val relationContentErrors = Relation.relationContentErrors(relationContent.trim() + "\n")

        if(relationContentErrors.isDefined) {
          BadRequest(views.html.createRelation(bindedForm.withGlobalError(relationContentErrors.get), relationContentMaxLength))
        } else {
          val relationFile = new File(request.user.relationPath(relationName))

          if(relationFile.exists) {
            BadRequest(views.html.createRelation(bindedForm.withGlobalError("Die Relation existiert bereits"), relationContentMaxLength))
          } else if(request.user.relations.size >= 10) {
            BadRequest(views.html.createRelation(bindedForm.withGlobalError("Maximum von 10 Relationen überschritten"), relationContentMaxLength))
          } else {
            val bw = new BufferedWriter(new FileWriter(relationFile))
            bw.write(relationContent.trim() + "\n")
            bw.close;
            request.user.dropScopeCache

            Ok(views.html.query(queryForm, request.user.relations, None))
          }
        }
      }
    )
  }

  def editRelation(relationName: String) = Authenticated { implicit request => 
    if(request.user.hasRelation(relationName)) {
      Ok(views.html.editRelation(editRelationForm.bind(Map("relation_content" -> request.user.relationContent(relationName))), relationName, relationContentMaxLength))
    } else {
      BadRequest(views.html.query(queryForm.withGlobalError("Die Relation existiert nicht"), request.user.relations, None))
    }
  }

  def updateRelation(relationName: String) = Authenticated { implicit request =>
    val bindedForm = editRelationForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => BadRequest(views.html.editRelation(formWithErrors, relationName, relationContentMaxLength)),
      relationContent => {
        val relationContentErrors = Relation.relationContentErrors(relationContent.trim() + "\n")

        if(relationContentErrors.isDefined) {
          BadRequest(views.html.editRelation(bindedForm.withGlobalError(relationContentErrors.get), relationName, relationContentMaxLength))
        } else {
          val relationFile = new File(request.user.relationPath(relationName))

          if(relationFile.exists) {
            val bw = new BufferedWriter(new FileWriter(relationFile))
            bw.write(relationContent.trim() + "\n")
            bw.close;
            request.user.dropScopeCache

            Ok(views.html.query(queryForm, request.user.relations, None))
          } else {
            BadRequest(views.html.editRelation(bindedForm.withGlobalError("Die Relation existiert nicht"), relationName, relationContentMaxLength))
          }
        }
      }
    )
  }

  def deleteRelation(relationName: String) = Authenticated { implicit request =>
    val relationFile = new File(request.user.relationPath(relationName))

    if(relationFile.exists) {
      relationFile.delete()
      request.user.dropScopeCache

      Ok(views.html.query(queryForm, request.user.relations, None))
    } else {
      BadRequest(views.html.editRelation(editRelationForm.bind(Map("relation_content" -> request.user.relationContent(relationName))), relationName, relationContentMaxLength))
    }
  }
}
