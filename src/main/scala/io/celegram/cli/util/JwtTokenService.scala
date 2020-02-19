package io.celegram.cli.util

import cats.implicits._
import io.celegram.cli.util.JwtTokenService.{JwtContent, JwtDecodeError}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

class JwtTokenService(secret: String, algorithm: JwtHmacAlgorithm) {
  def generateToken(user: String): String = Jwt.encode(JwtContent(user, "admin").asJson.noSpaces, secret, algorithm)

  def verifyToken(token: String): Either[JwtDecodeError.type, (String, String, String)] =
    Jwt.decodeRawAll(token, secret, Seq(algorithm)).toEither.leftMap(_ => JwtDecodeError)

  private def fetchPayload(token: String): Either[JwtDecodeError.type, JwtClaim] = {
    Jwt.decode(token, secret, Seq(algorithm)).toEither.leftMap(_ => JwtDecodeError)
  }

  def getContent(token: String): Either[JwtDecodeError.type, JwtContent] = {
    fetchPayload(token).flatMap(claim => decode[JwtContent](claim.content).leftMap(_ => JwtDecodeError))
  }
}

object JwtTokenService {

  sealed trait JwtError

  case object JwtDecodeError extends JwtError

  type Header = String
  type Claim = String
  type Signature = String

  case class JwtContent(user: String, role: String)

  def apply(secret: String, algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS256): JwtTokenService =
    new JwtTokenService(secret, algorithm)

}
