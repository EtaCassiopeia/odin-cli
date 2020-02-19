package io.celegram.cli.util

import cats.effect._
import cats.implicits._
import io.celegram.cli.util.Config.Token
import org.http4s.{AuthScheme, Credentials, Request, Uri}
import org.http4s.Uri.{RegName, Scheme}
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString

object ClientExt {
  type BracketThrow[F[_]] = Bracket[F, Throwable]

  def implicitHost[F[_]: BracketThrow](hostname: String, port: Int, scheme: Scheme): Client[F] => Client[F] = {
    val newAuthority = Some(Uri.Authority(host = RegName(CaseInsensitiveString(hostname)), port = Some(port)))

    client =>
      Client { req =>
        val newRequest = req.withUri(req.uri.copy(authority = newAuthority, scheme = Some(scheme)))
        client.run(newRequest)
      }
  }

  def logFailedResponse[F[_]: Console: Sync]: Client[F] => Client[F] = { client =>
    Client[F] { req =>
      client.run(req).evalMap {
        case response if response.status.isSuccess => response.pure[F]
        case response =>
          response.bodyAsText.compile.string
            .flatTap(text => Console[F].putError("Request failed, response: " + text))
            .map(response.withEntity(_))
      }
    }
  }

  def withToken[F[_]: Config.Ask: BracketThrow: Console]: Client[F] => Client[F] = {

    val loadToken = Resource.liftF(Config.ask[F]).map(_.smartConfigServer.token.value.trim)

    val warnEmptyToken: F[Unit] =
      Console[F].putStrLn("Token is empty")

    def withToken(token: String): Request[F] => Request[F] =
      _.withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))

    client =>
      Client[F] { req =>
        loadToken.flatMap {
          case ""    => Resource.liftF(warnEmptyToken) *> client.run(req)
          case token => client.run(withToken(token)(req))
        }
      }
  }
}
