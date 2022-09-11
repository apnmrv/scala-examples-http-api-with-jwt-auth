package examples.http.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.{ExecutionContextExecutor, Future}

object DataKeeper extends AppConfiguration {

  // actor protocol
  trait Request

  trait QueryResult

  trait Failure extends QueryResult

  final case class UsersByTimeDevices(events: Seq[EventTimeDevice],
                                      replyTo: ActorRef[UserEventsQueryResult]) extends Request

  final case class TimeDevicesByUserTimePeriod(userTimePeriodsReq: DeviceEventsByUserTimePeriodRequest,
                                               replyTo: ActorRef[DeviceEventsQueryResult]) extends Request

  final case class UserEventsQueryResult(mayBeResult: Future[Seq[UserEvent]]) extends QueryResult

  final case class DeviceEventsQueryResult(mayBeResult: Future[Seq[DeviceEventTime]]) extends QueryResult

  private final val TIME_WINDOW_WIDTH_ms: Long = DataKeeperConfig.TIME_WINDOW_WIDTH_seconds*1000

  def apply(eventsRepo: EventRepository): Behavior[Request] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContextExecutor = context.system.executionContext
    message match {
      case UsersByTimeDevices(timeDeviceSeq, replyTo) => {
        val queryResult: Future[Seq[UserEvent]] =
          timeDeviceSeq.foldLeft(Future.successful(Seq.empty[UserEvent]))((acc, args) =>
            acc.flatMap { acc =>
              val timeFrom: EventTime = args.eventTime * 1000 - (TIME_WINDOW_WIDTH_ms) / 2
              val timeTo: EventTime = args.eventTime * 1000 + (TIME_WINDOW_WIDTH_ms) / 2
              eventsRepo.getByDeviceTimePeriod(args.deviceId, timeFrom, timeTo).map { ps =>
                ps.flatMap { p => acc :+ UserEvent(p.userId, p.deviceId, args.eventTime) }
              }
            }
          )
        replyTo ! UserEventsQueryResult(queryResult)
        Behaviors.same
      }
      case TimeDevicesByUserTimePeriod(userTimePeriod, replyTo) =>
        val queryResult: Future[Seq[DeviceEventTime]] =
          eventsRepo.getByUserTimePeriod(
            userTimePeriod.userId,
            userTimePeriod.from,
            userTimePeriod.to).map { ps =>
            ps.map(p =>
              DeviceEventTime(p.deviceId, (p.tsStop + p.tsStart) / 2))
          }
        replyTo ! DeviceEventsQueryResult(queryResult)
    }
    Behaviors.same
  }
}
