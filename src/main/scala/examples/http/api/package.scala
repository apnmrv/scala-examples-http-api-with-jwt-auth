package examples.http

package object api {
  type DeviceId = String
  type EventTime = Long
  type UserId = String

  case class LoginRequest(user: String, password: String)

  case class LoginSuccessResponse(tokenType: String, accessToken: String, refreshToken: String, expiresIn: Long)

  /**
   * Types used in
   * request (and response) for users associated with the given devices at a given moment
   */
  case class UserEventsRequest(events: Seq[EventTimeDevice])

  case class EventTimeDevice(eventTime: EventTime, deviceId: DeviceId) {
    def tupled: (Long, String) = (eventTime, deviceId)
  }

  case class UserEventsResponse(events: Seq[UserEvent])

  case class UserEvent(userId: UserId, deviceId: DeviceId, eventTime: EventTime)

  /**
   * Types used in
   * request/response for events associated with the given user id at a given time period
   */
  case class DeviceEventsByUserTimePeriodRequest(userId: UserId, from: EventTime, to: EventTime)

  case class DeviceEventsResponse(events: Seq[DeviceEventTime])

  case class DeviceEventTime(deviceId: DeviceId, eventTime: EventTime)

}
