package examples.http.api

import java.time.Duration

import com.typesafe.config.{Config, ConfigFactory}

trait TestAppConfiguration {
  lazy val config: Config = ConfigFactory.load()
  lazy val dbConfig: Config = config.atKey("app.db")

  object SecurityConfig {
    private val securityConfig = config.getConfig("app.routes.security")
    final lazy val SECRET_KEY = securityConfig.getString("token.secret-key")
    final lazy val SECURED_REALM = securityConfig.getString("token.secured-realm")
    final lazy val TOKEN_ISSUER = securityConfig.getString("token.issuer")
    final lazy val TOKEN_TYPE = securityConfig.getString("token.type")
    final lazy val ACCESS_TOKEN_NAME: String = securityConfig.getString("token.access-token.name")
    final lazy val ACCESS_TOKEN_EXPIRES_days = securityConfig
      .getInt("token.access-token.expires-in-days")
    final lazy val REFRESH_TOKEN_NAME: String = securityConfig.getString("token.refresh-token.name")
    final lazy val REFRESH_TOKEN_EXPIRES_days = securityConfig
      .getInt("token.refresh-token.expires-in-days")
  }

  object ApiRoutesConfig {
    final lazy val API_VERSION: String = config.getString("app.routes.api-version")
    final lazy val ASK_TIMEOUT: Duration = config.getDuration("app.routes.ask-timeout")
    final lazy val PATH_PREFIX: String = config.getString("app.routes.path-prefix")
    final lazy val ID_USERS_PATH = s"/$PATH_PREFIX/$API_VERSION/id-users"
    final lazy val EVENTS_PATH = s"/$PATH_PREFIX/$API_VERSION/events"
    final lazy val LOGIN_PATH = s"/$PATH_PREFIX/$API_VERSION/auth/token"
    final lazy val REFRESH_TOKEN_PATH = s"/$PATH_PREFIX/$API_VERSION/user/token"
  }

  object HttpServerConfig {
    final lazy val HOST: String = config.getString("app.http-server.host")
    final lazy val PORT: Int = config.getInt("app.http-server.port")
  }
}
