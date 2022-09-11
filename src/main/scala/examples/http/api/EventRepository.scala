package examples.http.api

import com.typesafe.config.Config
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import scala.concurrent.Future

class EventRepository(private val conf: Config) extends DBSupport {
  lazy val db: JdbcProfile#Backend#Database = newDb(conf.getConfig("connection"))

  case class Passage(userId: String, deviceId: String, tsStart: Long, tsStop: Long)

  class PassageTable(tag: Tag) extends Table[Passage](tag, "pss_passages_dummy") {
    def id: Rep[Long] = column("id", O.PrimaryKey, O.AutoInc)

    def userId: Rep[String] = column("user_id")

    def deviceId: Rep[String] = column("src_id")

    def tsStart: Rep[Long] = column("ts_start")

    def tsStop: Rep[Long] = column("ts_stop")

    def * : ProvenShape[Passage] = (userId, deviceId, tsStart, tsStop) <> (Passage.tupled, Passage.unapply)
  }

  private lazy val passages: TableQuery[PassageTable] = TableQuery[PassageTable]

  def getByDeviceTimePeriod(deviceId: String, timeFrom: Long, timeTo: Long): Future[Seq[Passage]] = {
    val query: Query[PassageTable, Passage, Seq] = passages.filter(p =>
      p.deviceId === deviceId && (p.tsStart < timeTo && p.tsStop > timeFrom))
    db.run(query.result)
  }

  def testRequest(): Future[Seq[Passage]] = {
    val query: Query[PassageTable, Passage, Seq] = passages.take(3)
    db.run(query.result)
  }

  def getByUserTimePeriod(userId: String, timeFrom: Long, timeTo: Long): Future[Seq[Passage]] = {
    val query: Query[PassageTable, Passage, Seq] = passages.filter(p =>
      p.userId === userId && p.tsStop > timeFrom && p.tsStart < timeTo
    )
    db.run(query.result)
  }

  override def close(): Unit = db.close()
}
