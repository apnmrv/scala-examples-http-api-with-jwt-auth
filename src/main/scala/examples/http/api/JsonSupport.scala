package examples.http.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.collection.LinearSeq

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)

  implicit object DeviceEventTimeFormat extends RootJsonFormat[DeviceEventTime] {
    override def read(json: JsValue): DeviceEventTime =
      json.asJsObject.getFields("id", "time") match {
        case Seq(JsString(id), JsNumber(time)) => DeviceEventTime(id, time.toLong)
        case _ => throw DeserializationException("Valid json expected")
      }

    override def write(obj: DeviceEventTime): JsValue = JsObject(
      "id" -> JsString(obj.deviceId),
      "time" -> JsNumber(obj.eventTime)
    )
  }


  implicit object EventTimeDeviceFormat extends RootJsonFormat[EventTimeDevice] {
    override def read(json: JsValue): EventTimeDevice =
      json.asJsObject.getFields("time", "id") match {
        case Seq(JsNumber(time), JsString(id)) => EventTimeDevice(time.toLong, id)
        case _ => throw DeserializationException("Valid json expected")
      }

    override def write(obj: EventTimeDevice): JsValue = JsObject(
      "time" -> JsNumber(obj.eventTime),
      "id" -> JsString(obj.deviceId)
    )
  }

  implicit object EventsByUserTimePeriodRequestFormat extends RootJsonFormat[DeviceEventsByUserTimePeriodRequest] {
    override def read(json: JsValue): DeviceEventsByUserTimePeriodRequest =
      json.asJsObject.getFields("id", "from", "to") match {
        case Seq(JsString(userId), JsNumber(from), JsNumber(to)) =>
          DeviceEventsByUserTimePeriodRequest(userId, from.toLong, to.toLong)
        case _ => throw DeserializationException("Valid json expected")
      }

    override def write(obj: DeviceEventsByUserTimePeriodRequest): JsValue = JsObject(
      "id" -> JsString(obj.userId),
      "from" -> JsNumber(obj.from),
      "to" -> JsNumber(obj.to)
    )
  }

  implicit object LoginSuccessResponseFormat extends RootJsonFormat[LoginSuccessResponse] {

    override def write(obj: LoginSuccessResponse): JsValue = JsObject(
      "token_type" -> JsString(obj.tokenType),
      "access_token" -> JsString(obj.accessToken),
      "refresh_token" -> JsString(obj.refreshToken),
      "expires_in" -> JsNumber(obj.expiresIn)
    )

    override def read(json: JsValue): LoginSuccessResponse = json.asJsObject
      .getFields("token_type", "access_token", "refresh_token", "expires_in") match {
      case Seq(JsString(typ), JsString(at), JsString(rt), JsNumber(exp)) =>
        LoginSuccessResponse(typ, at, rt, exp.toLong)
      case _ => throw DeserializationException("Valid json expected")
    }
  }

  implicit object UserEventsResponseFormat extends RootJsonFormat[UserEventsResponse] {

    private def writeUserEvent(elem: UserEvent): JsValue = JsObject(
      "id" -> JsString(elem.userId),
      "place_id" -> JsString(elem.deviceId),
      "time" -> JsNumber(elem.eventTime.toString)
    )

    override def write(obj: UserEventsResponse): JsValue = JsObject(
      "enters" -> JsArray(
        obj.events.foldLeft(List.empty[JsValue])((acc, elem) =>
          writeUserEvent(elem) :: acc).toVector
      ))

    private def readUserEvent(json: JsValue): UserEvent =
      json.asJsObject.getFields("id", "place_id", "time") match {
        case Seq(JsString(id), JsString(deviceId), JsNumber(ts)) => UserEvent(id, deviceId, ts.toLong)
        case _ => throw DeserializationException("Valid json expected")
      }

    override def read(json: JsValue): UserEventsResponse = {
      json.asJsObject.getFields("events") match {
        case Seq(JsArray(elements)) =>
          val userEvents: LinearSeq[UserEvent] =
            elements.foldLeft(LinearSeq.empty[UserEvent])((acc, elem) =>
              acc :+ readUserEvent(elem))
          UserEventsResponse(userEvents.toIndexedSeq)
        case _ => throw DeserializationException("Valid json expected")
      }
    }
  }

  implicit object UserEventsRequestFormat extends RootJsonFormat[UserEventsRequest] {

    private def writeEventTimeDevice(elem: EventTimeDevice): JsValue = JsObject(
      "time" -> JsNumber(elem.eventTime),
      "place_id" -> JsString(elem.deviceId)
    )

    override def write(obj: UserEventsRequest): JsValue = JsObject(
      "events" -> JsArray(
        obj.events.foldLeft(List.empty[JsValue])((acc, elem) =>
          writeEventTimeDevice(elem) :: acc).toVector
      ))

    private def readEventTimeDevice(json: JsValue): EventTimeDevice =
      json.asJsObject.getFields("time", "place_id") match {
        case Seq(JsNumber(ts), JsString(id)) => EventTimeDevice(eventTime = ts.toLong, deviceId = id)
        case _ => throw DeserializationException("Valid json expected")
      }

    override def read(json: JsValue): UserEventsRequest =
      json.asJsObject.getFields("events") match {
        case Seq(JsArray(elements)) =>
          val eventTimeDeviceRequests: LinearSeq[EventTimeDevice] =
            elements.foldLeft(LinearSeq.empty[EventTimeDevice])((acc, elem) =>
              acc :+ readEventTimeDevice(elem))
          UserEventsRequest(eventTimeDeviceRequests.toIndexedSeq)
        case _ => throw DeserializationException("Valid json expected")
      }
  }

  implicit object DeviceEventsResponseFormat extends RootJsonFormat[DeviceEventsResponse] {

    private def writeDeviceEventTime(elem: DeviceEventTime): JsValue = JsObject(
      "place_id" -> JsString(elem.deviceId),
      "time" -> JsNumber(elem.eventTime.toString)
    )

    override def write(obj: DeviceEventsResponse): JsValue = JsObject(
      "events" -> JsArray(
        obj.events.foldLeft(List.empty[JsValue])((acc, elem) =>
          writeDeviceEventTime(elem) :: acc).toVector
      )
    )

    override def read(json: JsValue): DeviceEventsResponse = ???
  }

}
