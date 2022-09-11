package examples.http.api

import akka.actor.typed.scaladsl.Behaviors
import examples.http.api.DataKeeper.{DeviceEventsQueryResult, TimeDevicesByUserTimePeriod, UserEventsQueryResult, UsersByTimeDevices}
import examples.http.api.SecretsKeeper.{CredentialsByUsername, CredentialsRequestResult, UserCredentials}

import scala.concurrent.{ExecutionContext, Future}

trait PssApiSubsNMocks extends JsonSupport {

  val deviceEventsStub: Seq[DeviceEventTime] = Seq (
    DeviceEventTime(
      "172.19.4.139",
      1590595406
    ),
    DeviceEventTime(
      "172.19.4.145",
      1590595456
    ),
  )

  val userEventsStub: Seq[UserEvent] = Seq (
    UserEvent(
      "e196d6fd4cb5de4a92181fcff285e553",
      "172.19.4.139",
      1590595406
    ),
    UserEvent(
      "e196d6fd4cb6th4a92181fcff285e553",
      "172.19.4.145",
      1590595406
    ))

  val correctCredentialsStub = UserCredentials("test-user", "correct")
  val correctPasswordStub = "correct"
  val incorrectPasswordStub = "wrong"

  object MockedBehaviors {
    def secretsKeeperBehavior(implicit ec: ExecutionContext) =
      Behaviors.receiveMessage[SecretsKeeper.Request] {
        case CredentialsByUsername(_, replyTo) => {
          replyTo ! CredentialsRequestResult(Future(Some(correctCredentialsStub)))
        }
      Behaviors.same
    }

    def dataKeeperBehavior(implicit ec: ExecutionContext) =
      Behaviors.receiveMessage[DataKeeper.Request] {
      case UsersByTimeDevices(timeDevices, replyTo)
        if timeDevices.forall(_.deviceId.contains("absent")) =>
        replyTo ! UserEventsQueryResult(Future(Seq.empty[UserEvent]))
        Behaviors.same
      case UsersByTimeDevices(timeDevices, replyTo)
        if timeDevices.exists(elem => elem.deviceId.contains("existing")) =>
        replyTo ! UserEventsQueryResult(Future(userEventsStub))
        Behaviors.same
      case TimeDevicesByUserTimePeriod(userTimePeriod, replyTo) =>
        if (userTimePeriod.userId.contains("absent")) {
          replyTo ! DeviceEventsQueryResult(Future(Seq.empty[DeviceEventTime]))
        } else {
          replyTo ! DeviceEventsQueryResult(Future(deviceEventsStub))
        }
        Behaviors.same
    }
  }

  val idUsersRouteRequestToReplyWithNotFound: UserEventsRequest =
    UserEventsRequest(Seq(
    EventTimeDevice(159723072,  "absent1"),
    EventTimeDevice(159723065,  "absent2")
  ))

  val idUsersRouteRequestToReplyWithSuccess: UserEventsRequest =
    UserEventsRequest(Seq(
    EventTimeDevice(159723072,  "existing1"),
    EventTimeDevice(159723065,  "existing2")
  ))

  val eventsRouteRequestToReplyWithNotFound: DeviceEventsByUserTimePeriodRequest =
    DeviceEventsByUserTimePeriodRequest(
    "absent",
    1590595406,
    1590597564
  )

  val eventsRouteRequestToReplyWithSuccess: DeviceEventsByUserTimePeriodRequest =
    DeviceEventsByUserTimePeriodRequest(
    "e196d6fd4cb6th4a92181fcff285e553",
    1590595406,
    1590597564
  )

  val loginRequestToAccept: LoginRequest = LoginRequest("test-user", correctPasswordStub)
  val loginRequestToReject: LoginRequest = LoginRequest("test-user", incorrectPasswordStub)
}
