package examples.http.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.{ExecutionContextExecutor, Future}

object SecretsKeeper {

  // actor protocol
  trait Request

  trait QueryResult

  trait Success extends QueryResult

  trait Failure extends QueryResult

  final case class UserCredentials(username: String, password: String)

  final case class CredentialsByUsername(username: String,
                                         replyTo: ActorRef[CredentialsRequestResult]) extends Request

  final case class CredentialsRequestResult(mayBeResult: Future[Option[UserCredentials]]) extends Success

  final case class Validate(username: String, password: String,
                            replyTo: ActorRef[ValidationResult]) extends Request

  final case class ValidationResult(isValid: Future[Boolean]) extends Success

  final case class MalformedQueryParams[_](description: String) extends Failure

  final case class DbQueryFailure[_](description: String) extends Failure

  def apply(credentialsRepo: CredentialsRepository): Behavior[Request] = Behaviors.receive[Request] { (context, message) =>
    implicit val ec: ExecutionContextExecutor = context.system.executionContext
    message match {
      case CredentialsByUsername(username, replyTo) =>
        val mayBeCredentials: Future[Option[UserCredentials]] =
          credentialsRepo.getCredentialsByUsername(username)
        replyTo ! CredentialsRequestResult(mayBeCredentials)
        Behaviors.same
      case Validate(username, password, replyTo) =>
        val isValid: Future[Boolean] = credentialsRepo.getCredentialsByUsername(username).map {
          case Some(cr) => cr.password == password
          case None => false
        }

        replyTo ! ValidationResult(isValid)
        Behaviors.same
    }
  }
}
