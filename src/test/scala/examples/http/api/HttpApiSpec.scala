package examples.http.api

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json.enrichAny

class HttpApiSpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest
  with PssApiSubsNMocks with JsonSupport with TestAppConfiguration with JWTSupport {

  lazy val testKit: ActorTestKit = ActorTestKit()

  implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  val dataKeeperProbe: TestProbe[DataKeeper.Request] = testKit.createTestProbe[DataKeeper.Request]()
  val secretsKeeperProbe: TestProbe[SecretsKeeper.Request] = testKit.createTestProbe[SecretsKeeper.Request]()

  val mockedDataKeeper: ActorRef[DataKeeper.Request] =
    testKit.spawn(Behaviors.monitor(dataKeeperProbe.ref, MockedBehaviors.dataKeeperBehavior))

  val mockedSecretsKeeper: ActorRef[SecretsKeeper.Request] =
    testKit.spawn(Behaviors.monitor(secretsKeeperProbe.ref, MockedBehaviors.secretsKeeperBehavior))

  lazy val routes: Route = new HttpRoutes(mockedDataKeeper, mockedSecretsKeeper).routes

  private final val SECRET_KEY = SecurityConfig.SECRET_KEY
  private final val TOKEN_ISSUER = SecurityConfig.TOKEN_ISSUER
  private final val ACCESS_TOKEN_EXPIRES_days = SecurityConfig.ACCESS_TOKEN_EXPIRES_days
  private final val REFRESH_TOKEN_EXPIRES_days = SecurityConfig.REFRESH_TOKEN_EXPIRES_days
  private final val TOKEN_TYPE = SecurityConfig.TOKEN_TYPE
  private final val TOKEN_SUBJECT = "test-user"
  private final val ID_USERS_PATH = ApiRoutesConfig.ID_USERS_PATH
  private final val EVENTS_PATH = ApiRoutesConfig.EVENTS_PATH
  private final val LOGIN_PATH = ApiRoutesConfig.LOGIN_PATH
  private final val REFRESH_TOKEN_PATH = ApiRoutesConfig.REFRESH_TOKEN_PATH

  private def loginSuccessResponseStub = {
    val accessTokenExpMillis = TimeUnit.DAYS.toMillis(ACCESS_TOKEN_EXPIRES_days)
    val accessTokenExpSec = TimeUnit.DAYS.toMillis(REFRESH_TOKEN_EXPIRES_days)

    val jwtTools = JWTFactory(SECRET_KEY, Some(TOKEN_SUBJECT), Some(TOKEN_ISSUER))
    val refreshTokenExpMillis = TimeUnit.DAYS.toMillis(REFRESH_TOKEN_EXPIRES_days)
    LoginSuccessResponse(
      TOKEN_TYPE,
      jwtTools.newAccessToken(accessTokenExpMillis),
      jwtTools.newRefreshToken(refreshTokenExpMillis),
      accessTokenExpSec)
  }

  private val validAccessToken = loginSuccessResponseStub.accessToken
  private val invalidAccessToken = loginSuccessResponseStub.accessToken + "hgf67afg"
  private val validRefreshToken = loginSuccessResponseStub.refreshToken
  private val invalidRefreshToken = loginSuccessResponseStub.refreshToken + "hgf67afg"

  "Route /id-users Route" should {
    "respond with 401 to (POST /id-users) request without access-token" in {
      val requestEntity: MessageEntity =
        Marshal(idUsersRouteRequestToReplyWithSuccess).to[MessageEntity].futureValue
      val request: HttpRequest = Post(ID_USERS_PATH).withEntity(requestEntity)
      request ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
        // we expect the response to be json:
        contentType should ===(ContentTypes.NoContentType)
        // and we know what message we're expecting back:
        responseEntity shouldBe HttpEntity.Empty
      }
    }

    "respond with 401 to (POST /id-users) request with invalid access-token" in {
      val requestEntity: MessageEntity =
        Marshal(idUsersRouteRequestToReplyWithSuccess).to[MessageEntity].futureValue
      val request: HttpRequest = Post(ID_USERS_PATH).withEntity(requestEntity)
      request ~> addCredentials(OAuth2BearerToken(invalidAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
        // we expect the response to be json:
        contentType should ===(ContentTypes.NoContentType)
        // and we know what message we're expecting back:
        responseEntity shouldBe HttpEntity.Empty
      }
    }

    "respond with status 200 and UserEventsResponse to (POST /id-users) request with valid access-token" in {
      val shouldBeResponse: String = UserEventsResponse(userEventsStub).toJson.toString()
      val requestEntity: MessageEntity =
        Marshal(idUsersRouteRequestToReplyWithSuccess).to[MessageEntity].futureValue
      val request: HttpRequest = Post(ID_USERS_PATH).withEntity(requestEntity)

      request ~> addCredentials(OAuth2BearerToken(validAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.OK)
        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)
        // and we know what message we're expecting back:
        entityAs[String] should ===(shouldBeResponse)
      }
    }

    "respond with status 404 and empty UserEventsResponse to (POST /id-users) request with valid access-token" in {
      val shouldBeResponse: String = UserEventsResponse(Seq.empty).toJson.toString()
      val requestEntity: MessageEntity =
        Marshal(idUsersRouteRequestToReplyWithNotFound).to[MessageEntity].futureValue
      val request: HttpRequest =
        Post(ID_USERS_PATH).withEntity(requestEntity)
      request ~> addCredentials(OAuth2BearerToken(validAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.NotFound)
        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===(shouldBeResponse)
      }
    }

    "respond with status 400 to (POST /id-users) request with valid access-token" in {
      val requestEntity: MessageEntity =
        Marshal("some weird string").to[MessageEntity].futureValue
      val request: HttpRequest = Post(ID_USERS_PATH).withEntity(requestEntity)
      request ~> addCredentials(OAuth2BearerToken(validAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.BadRequest)
      }
    }
  }
  "Route /events Route" should {
    "respond with 401 to (POST /id-users) request without access-token" in {
      val requestEntity: MessageEntity =
        Marshal(eventsRouteRequestToReplyWithNotFound).to[MessageEntity].futureValue
      val request: HttpRequest = Post(EVENTS_PATH).withEntity(requestEntity)
      request ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
        // we expect the response to be json:
        contentType should ===(ContentTypes.NoContentType)
        // and we know what message we're expecting back:
        responseEntity shouldBe HttpEntity.Empty
      }
    }

    "respond with 401 to (POST /id-users) request with invalid access-token" in {
      val requestEntity: MessageEntity =
        Marshal(eventsRouteRequestToReplyWithNotFound).to[MessageEntity].futureValue
      val request: HttpRequest = Post(EVENTS_PATH).withEntity(requestEntity)
      request ~> addCredentials(OAuth2BearerToken(invalidAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
        // we expect the response to be json:
        contentType should ===(ContentTypes.NoContentType)
        // and we know what message we're expecting back:
        responseEntity shouldBe HttpEntity.Empty
      }
    }

    "respond with status 200 and DeviceEventsResponse to (POST /events) request with valid access-token" in {
      val shouldBeResponse: String =
        DeviceEventsResponse(deviceEventsStub).toJson.toString()

      val requestEntity: MessageEntity =
        Marshal(eventsRouteRequestToReplyWithSuccess).to[MessageEntity].futureValue
      val request: HttpRequest =
        Post(EVENTS_PATH).withEntity(requestEntity)

      request ~> addCredentials(OAuth2BearerToken(validAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.OK)
        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)
        // and we know what message we're expecting back:
        entityAs[String] should ===(shouldBeResponse)
      }
    }

    "respond with status 404 and empty DeviceEventsResponse to (POST /events) request with valid access-token" in {
      val shouldBeResponse: String = DeviceEventsResponse(Seq.empty).toJson.toString()
      val requestEntity: MessageEntity =
        Marshal(eventsRouteRequestToReplyWithNotFound).to[MessageEntity].futureValue
      val request: HttpRequest =
        Post(EVENTS_PATH).withEntity(requestEntity)
      request ~> addCredentials(OAuth2BearerToken(validAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.NotFound)
        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===(shouldBeResponse)
      }
    }

    "respond with status 400 to (POST /events) request with valid access-token" in {
      val requestEntity: MessageEntity =
        Marshal("some weird string").to[MessageEntity].futureValue
      val request: HttpRequest =
        Post(EVENTS_PATH).withEntity(requestEntity)
      request ~> addCredentials(OAuth2BearerToken(validAccessToken)) ~> routes ~> check {
        status should ===(StatusCodes.BadRequest)
      }
    }
  }
  "Route /auth/token" should {
    "successfully respond with status 200 and tokens to (POST /auth/token) request" in {
      val requestEntity: MessageEntity =
        Marshal(loginRequestToAccept).to[MessageEntity].futureValue
      val request: HttpRequest =
        Post(LOGIN_PATH).withEntity(requestEntity)
      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)
        val entity = entityAs[LoginSuccessResponse]
        entity shouldBe a[LoginSuccessResponse]
        entity.tokenType shouldEqual "Bearer"
        entity.accessToken should fullyMatch regex """^.+\..+\..+$"""
        entity.refreshToken should fullyMatch regex """^.+\..+\..+$"""
        entity.expiresIn shouldEqual TimeUnit.DAYS.toSeconds(ACCESS_TOKEN_EXPIRES_days)
      }
    }

    "respond with 401 to (POST /auth/token) request" in {
      val requestEntity: MessageEntity =
        Marshal(loginRequestToReject).to[MessageEntity].futureValue
      val request: HttpRequest =
        Post(LOGIN_PATH).withEntity(requestEntity)
      request ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
      }
    }
  }
  "Route /user/token" should {
    "successfully respond with status 200 and tokens to (POST /user/token) request" in {
      val request: HttpRequest = Post(REFRESH_TOKEN_PATH)
      request ~> addCredentials(OAuth2BearerToken(validRefreshToken)) ~> routes ~> check {
        status should ===(StatusCodes.OK)
        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)
        val entity = entityAs[LoginSuccessResponse]
        entity shouldBe a[LoginSuccessResponse]
        entity.tokenType shouldEqual "Bearer"
        entity.accessToken should fullyMatch regex """^.+\..+\..+$"""
        entity.refreshToken should fullyMatch regex """^.+\..+\..+$"""
        entity.expiresIn shouldEqual TimeUnit.DAYS.toSeconds(ACCESS_TOKEN_EXPIRES_days)
      }
    }

    "respond with 401 to (POST /user/token) request without token header" in {
      val request: HttpRequest = Post(REFRESH_TOKEN_PATH)
      request ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
      }
    }

    "respond with 401 to (POST /user/token) request with invalid refresh token" in {
      val request: HttpRequest = Post(REFRESH_TOKEN_PATH)
      request ~> addCredentials(OAuth2BearerToken(invalidRefreshToken)) ~> routes ~> check {
        status should ===(StatusCodes.Unauthorized)
      }
    }
  }
}
