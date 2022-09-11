package examples.http.api

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Unauthorized}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives._
import akka.util.Timeout
import examples.http.api.DataKeeper.{TimeDevicesByUserTimePeriod, UsersByTimeDevices}
import examples.http.api.SecretsKeeper.{CredentialsByUsername, UserCredentials}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class HttpRoutes(dataKeeper: ActorRef[DataKeeper.Request], secretsKeeper: ActorRef[SecretsKeeper.Request])
                (implicit val system: ActorSystem[_]) extends Directives with JsonSupport with JWTSupport with AppConfiguration {

  private val logger: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private implicit val timeout: Timeout = Timeout.create(ApiRoutesConfig.ASK_TIMEOUT)
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  private final val API_PATH_PREFIX = ApiRoutesConfig.PATH_PREFIX
  private final val API_VERSION = ApiRoutesConfig.API_VERSION
  private final val TOKEN_TYPE = SecurityConfig.TOKEN_TYPE
  private final val ACCESS_TOKEN_EXPIRES_days = SecurityConfig.ACCESS_TOKEN_EXPIRES_days
  private final val REFRESH_TOKEN_EXPIRES_days = SecurityConfig.REFRESH_TOKEN_EXPIRES_days
  private final val SECRET_KEY = SecurityConfig.SECRET_KEY
  private final val SECURED_REALM = SecurityConfig.SECURED_REALM
  private final val TOKEN_ISSUER = SecurityConfig.TOKEN_ISSUER
  private final val REFRESH_TOKEN_NAME: String = SecurityConfig.REFRESH_TOKEN_NAME
  private final val ACCESS_TOKEN_TYPE: String = SecurityConfig.ACCESS_TOKEN_NAME

  private def accessTokenExpMillis = TimeUnit.DAYS.toMillis(ACCESS_TOKEN_EXPIRES_days)

  private def accessTokenExpSec = TimeUnit.DAYS.toSeconds(ACCESS_TOKEN_EXPIRES_days)

  implicit def allInOneRejectionsHandler: RejectionHandler =
    RejectionHandler.newBuilder()
      .handleAll[UnsupportedRequestContentTypeRejection] { _ =>
        complete(HttpResponse(BadRequest))
      }
      .handleAll[AuthenticationFailedRejection] { _ =>
        complete(HttpResponse(Unauthorized))
      }.result()

  private def usersByTimeDevice(userEventsReq: UserEventsRequest): Future[Seq[UserEvent]] =
    dataKeeper.ask(UsersByTimeDevices(userEventsReq.events, _))
      .flatMap(r => r.mayBeResult)

  private def eventsByUserTimePeriod(userTimePeriodsReq: DeviceEventsByUserTimePeriodRequest): Future[Seq[DeviceEventTime]] =
    dataKeeper.ask(TimeDevicesByUserTimePeriod(userTimePeriodsReq, _))
      .flatMap(r => r.mayBeResult)

  private def getCredentialsByUsername(username: String): Future[Option[UserCredentials]] = {
    val requestResult: Future[SecretsKeeper.CredentialsRequestResult] =
      secretsKeeper ? (ref => CredentialsByUsername(username, ref))
    requestResult.flatMap(r => r.mayBeResult)
  }

  private def responseBody[T](response: T)(implicit jsonWriter: JsonWriter[T]): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, response.toJson.toString())

  private def loginSuccessResponse(tokenSub: String) = {
    val jwtTools = JWTFactory(SECRET_KEY, Some(tokenSub), Some(TOKEN_ISSUER))
    val refreshTokenExpMillis = TimeUnit.DAYS.toMillis(REFRESH_TOKEN_EXPIRES_days)
    LoginSuccessResponse(
      TOKEN_TYPE,
      jwtTools.newAccessToken(accessTokenExpMillis),
      jwtTools.newRefreshToken(refreshTokenExpMillis),
      accessTokenExpSec)
  }

  private def responseWithUnauthorized =
    complete(HttpResponse(StatusCodes.Unauthorized))

  private def responseWithLoginSuccess(username: String) = {
    val loginResponse: LoginSuccessResponse = loginSuccessResponse(username)
    complete(StatusCodes.OK, responseBody(loginResponse))
  }

  private def loginRoute: Route = entity(as[LoginRequest]) {
    case LoginRequest(username, password) =>
      onComplete(getCredentialsByUsername(username)) {
        case Success(mayBeCredentials) => mayBeCredentials match {
          case Some(credentials) if credentials.password == password =>
            responseWithLoginSuccess(username)
          case _ =>
            responseWithUnauthorized
        }
        case Failure(e) =>
          logger.error(e.getMessage)
          responseWithUnauthorized
      }
    case _ => complete(HttpResponse(StatusCodes.BadRequest))
  }

  private def refreshAuthRoute: Route =
    authenticateOAuth2Async(SECURED_REALM, refreshTokenAuthenticator) {
      case sub: String => responseWithLoginSuccess(sub)
      case _ => responseWithUnauthorized
    }

  private def refreshTokenAuthenticator(credentials: Credentials): Future[Option[String]] = Future {
    credentials match {
      case _@Credentials.Provided(jwt) =>
        JWTSupport(jwt).toOption match {
          case Some(validator) if validator.validate(SECRET_KEY, Some(REFRESH_TOKEN_NAME)) =>
            validator.getClaimValue("sub")
          case _ => None
        }
      case _ => None
    }
  }

  private def idUsersRoute: Route = entity(as[UserEventsRequest]) { req =>
    onComplete(usersByTimeDevice(req)) {
      case Success(events) if events.nonEmpty =>
        complete(StatusCodes.OK, responseBody(UserEventsResponse(events)))
      case Success(events) if events.isEmpty =>
        complete(StatusCodes.NotFound, responseBody(UserEventsResponse(Seq.empty)))
      case Failure(e) =>
        logger.error(e.getMessage)
        complete(StatusCodes.NotFound, responseBody(UserEventsResponse(Seq.empty)))
    }
  }

  private def eventsRoute: Route = entity(as[DeviceEventsByUserTimePeriodRequest]) { req =>
    onComplete(eventsByUserTimePeriod(req)) {
      case Success(events) if events.nonEmpty =>
        complete(StatusCodes.OK, responseBody(DeviceEventsResponse(events)))
      case Success(events) if events.isEmpty =>
        complete(StatusCodes.NotFound, responseBody(DeviceEventsResponse(Seq.empty)))
      case Failure(e) =>
        logger.error(e.getMessage)
        complete(StatusCodes.NotFound, responseBody(DeviceEventsResponse(Seq.empty)))
    }
  }

  def accessTokenAuthenticator(credentials: Credentials): Future[Option[Directive1[RequestContext]]] = Future {
    credentials match {
      case _@Credentials.Provided(jwt) if {
        JWTSupport(jwt).toOption match {
          case Some(tokenValidator) => tokenValidator
            .validate(SECRET_KEY, Some(ACCESS_TOKEN_TYPE))
          case None => false
        }
      } =>
        Some(extractRequestContext)
      case _ => None
    }
  }

  val routes: Route = {
    pathPrefix(API_PATH_PREFIX / API_VERSION) {
      concat(
        path("auth" / "token") {
          post {
            Route.seal(loginRoute)
          }
        },
        path("user" / "token") {
          post {
            Route.seal(refreshAuthRoute)
          }
        },
        path("id-users") {
          Route.seal(
            authenticateOAuth2Async(SECURED_REALM, accessTokenAuthenticator) { _ =>
              post(idUsersRoute)
            })
        },
        path("events") {
          Route.seal(
            authenticateOAuth2Async(SECURED_REALM, accessTokenAuthenticator) { _ =>
              post(eventsRoute)
            })
        }
      )
    }
  }
}
