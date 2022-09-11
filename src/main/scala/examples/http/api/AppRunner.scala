package examples.http.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

object AppRunner extends AppConfiguration {
  private lazy val HOST: String = HttpServerConfig.HOST
  private lazy val PORT: Int = HttpServerConfig.PORT
  private lazy val dataRepo = new EventRepository(config.getConfig("app.db"))
  private lazy val secretsRepo = new CredentialsRepository(config.getConfig("app.db"))

  def main(args: Array[String]): Unit = {
    val rootBehavior: Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      val dataKeeper =
        context.spawn(
          DataKeeper(dataRepo),
          "data-keeper-actor")
      context.watch(dataKeeper)
      val secretsKeeper =
        context.spawn(
          SecretsKeeper(secretsRepo),
          "secrets-keeper-actor")
      context.watch(dataKeeper)
      val routes =
        new HttpRoutes(dataKeeper, secretsKeeper)(context.system)
      val httpServer =
        context.spawn(
          HttpServer(HOST, PORT, routes.routes, context.system),
          "http-server-actor")
      context.watch(httpServer)
      Behaviors.empty
    }

    val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](rootBehavior, "http-api")
  }
}
