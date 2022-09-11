package examples.http.api

import authentikat.jwt.{JsonWebToken, JwtClaimsSet, JwtClaimsSetMap, JwtHeader}

import scala.util.Try

trait JWTSupport {
  def currentTimeMs: EventTime = System.currentTimeMillis()

  case class JWTFactory(private val secretKey: String,
                        private val sub: Option[String] = None,
                        private val iss: Option[String] = None) {

    private val currentTimeMs = System.currentTimeMillis()

    def newAccessToken(expiresInMillis: Long): UserId = getToken(
      tokenClaims(Map(
        "iss" -> iss,
        "sub" -> sub,
        "iat" -> currentTimeMs,
        "exp" -> expiresInMillis
      )),
      secretKey,
      "access-token")

    def newRefreshToken(expiresInMillis: Long): UserId = {
      getToken(
        tokenClaims(Map(
          "iss" -> iss,
          "sub" -> sub,
          "iat" -> currentTimeMs,
          "exp" -> expiresInMillis
        )),
        secretKey,
        "refresh-token")
    }

    private def tokenHeader(contentType: String = null) = JwtHeader("HS256", contentType)

    private def tokenClaims(claims: Map[String, Any]): JwtClaimsSetMap =
      JwtClaimsSet(claims)

    private def getToken(claimsSet: JwtClaimsSetMap, secretKey: String, contentType: String = null): String =
      JsonWebToken(tokenHeader(contentType), claimsSet, secretKey)
  }

  case class JWTValidator(
                           private val _jwt: String,
                           private val _tokenType: String,
                           private val _claims: Option[Map[String, String]]) {

    type Validator[T] = (T, T) => Boolean
    private val strValidator: Validator[String] = (s1, s2) => s1.equals(s2)

    def getClaimValue(claimValueName: String): Option[UserId] = _claims match {
      case Some(c) => c.get(claimValueName)
      case None => None
    }

    def validate(secretKey: String, tokenType: Option[String] = None, iss: Option[String] = None, sub: Option[String] = None): Boolean =
      JsonWebToken.validate(_jwt, secretKey) && {
        tokenType match {
          case Some(t) => t.equals(_tokenType)
          case None => true
        }
      } && validateClaims(iss, sub)

    def refreshAccessToken(secretKey: String, expiresInMillis: Long): Try[UserId] = Try {
      val (sKey, sub, iss) = (secretKey, getClaimValue("sub"), getClaimValue("sub"))
      JWTFactory(sKey, sub, iss).newAccessToken(expiresInMillis)
    }

    private def validateClaim[T](
                                  claims: Map[String, T],
                                  name: String,
                                  validator: Validator[T],
                                  value: Option[T] = None): Boolean = value match {
      case Some(v) => claims.get(name) match {
        case Some(claimValue) => validator(claimValue, v)
        case None => false
      }
      case None => true
    }

    private def validateClaims(iss: Option[String] = None, sub: Option[String]): Boolean = _claims match {
      case Some(c) =>
        validateClaim(c, "iss", strValidator, iss) &&
          validateClaim(c, "sub", strValidator, sub) && {
          getClaimValue("exp") match {
            case Some(n) => n.toLong < currentTimeMs
            case None => false
          }
        }
      case None => false
    }

  }

  object JWTSupport {
    def apply(secretKey: String, sub: Option[String], iss: Option[String]): JWTFactory =
      JWTFactory(secretKey, sub, iss)

    def apply(jwt: String): Try[JWTValidator] = Try {
      jwt match {
        case JsonWebToken(header, claims, _) => header match {
          case JwtHeader(_, contentType, _) => contentType match {
            case Some(tokenType) => JWTValidator(
              jwt,
              tokenType,
              claims.asSimpleMap.toOption
            )
            case _ => throw new IllegalArgumentException("Valid JWT string expected")
          }
          case _ => throw new IllegalArgumentException("Valid JWT string expected")
        }
        case _ => throw new IllegalArgumentException("Valid JWT string expected")
      }
    }
  }

}
