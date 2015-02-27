package controllers

import play.api.mvc._
import model.User
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Results._

trait Security {
  val alwaysAuthenticated = false
  val alwaysAuthenticatedUser = User("simon", "123")

  val sessionUserId: String = "user_id"
  val loginFailText: String = "Invalid user id or password"

  val loginForm: Form[Pair[String, String]]

  def loginInitHtml: Form[Pair[String, String]] => play.api.templates.Html
  def loginFailHtml: Form[Pair[String, String]] => play.api.templates.Html

  def loginSuccess: play.api.mvc.Result
  def logoutSuccess: play.api.mvc.Result

  def Authenticated(f: AuthenticatedRequest => Result) = {
    Action { request =>
      request.session.get(sessionUserId).map( userId => 
        User.findLoggedIn(userId)
      ).getOrElse(
        if(alwaysAuthenticated) Some(alwaysAuthenticatedUser) else None
      ) match {
        case Some(user) => f(AuthenticatedRequest(user, request))
        case None => Results.Unauthorized
      }
    }
  }

  case class AuthenticatedRequest(user: User, private val request: Request[AnyContent]) extends WrappedRequest(request)

  def login = Action { implicit request =>
    Ok(loginInitHtml(loginForm))
  }

  def authenticate = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(loginFailHtml(formWithErrors)),
      user => loginSuccess.withSession(sessionUserId -> user._1)
    )
  }

  def logout = Authenticated { implicit request => 
    User.logout(request.user)
    logoutSuccess.withNewSession.flashing(
      "success" -> "You've been logged out"
    )
  }
}