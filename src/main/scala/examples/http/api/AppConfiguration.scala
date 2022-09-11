package examples.http.api

import java.time.Duration

import com.typesafe.config.{Config, ConfigFactory}

trait AppConfiguration {
  lazy val config: Config = ConfigFactory.load()
  lazy val dbConfig: Config = config.atKey("app.db")

  object SecurityConfig {
    private val securityConfig = config.getConfig("app.routes.security")
    final val SECURED_REALM = securityConfig.getString("secured-realm")
    final val SECRET_KEY = securityConfig.getString("token.secret-key")
    final val TOKEN_ISSUER = securityConfig.getString("token.issuer")
    final val TOKEN_TYPE = securityConfig.getString("token.type")
    final val ACCESS_TOKEN_NAME: String = securityConfig.getString("token.access-token.name")
    final val ACCESS_TOKEN_EXPIRES_days = securityConfig
      .getInt("token.access-token.expires-in-days")
    final val REFRESH_TOKEN_NAME: String = securityConfig.getString("token.refresh-token.name")
    final val REFRESH_TOKEN_EXPIRES_days = securityConfig
      .getInt("token.refresh-token.expires-in-days")
  }

  object ApiRoutesConfig {
    final val API_VERSION: String = config.getString("app.routes.api-version")
    final val ASK_TIMEOUT: Duration = config.getDuration("app.routes.ask-timeout")
    final val PATH_PREFIX: String = config.getString("app.routes.path-prefix")
  }

  object HttpServerConfig {
    final val HOST: String = config.getString("app.http-server.host")
    final val PORT: Int = config.getInt("app.http-server.port")
  }

  object DataKeeperConfig {
    final val TIME_WINDOW_WIDTH_seconds: Int = config.getInt("app.data-keeper.user-events.time-window-width-seconds")
  }
}
