package examples.http.api

import com.typesafe.config.Config
import examples.http.api.SecretsKeeper.UserCredentials
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import scala.concurrent.Future

class CredentialsRepository(private val conf: Config) extends DBSupport {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private lazy val db: JdbcProfile#Backend#Database = newDb(conf.getConfig("connection"))

  class CredentialsTable(tag: Tag) extends Table[UserCredentials](tag, "pss_credentials") {
    def id: Rep[Long] = column("id", O.PrimaryKey, O.AutoInc)

    def username: Rep[String] = column("username")

    def password: Rep[String] = column("password")

    def * : ProvenShape[UserCredentials] = (username, password) <> (UserCredentials.tupled, UserCredentials.unapply)
  }

  private lazy val credentials: TableQuery[CredentialsTable] = TableQuery[CredentialsTable]

  def getCredentialsByUsername(username: String): Future[Option[UserCredentials]] = {
    val query: Query[CredentialsTable, UserCredentials, Seq] = credentials
      .filter(_.username === username)
    db.run(query.result.headOption)
  }

  override def close(): Unit = db.close()
}
