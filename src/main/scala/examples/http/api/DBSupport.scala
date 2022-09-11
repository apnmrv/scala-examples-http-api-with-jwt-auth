package examples.http.api

import com.typesafe.config.Config
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

trait DBSupport {
  Class.forName("org.postgresql.Driver")

  def newDb(config: Config): JdbcProfile#Backend#Database =
    Database.forConfig("", config)

  def close()
}
