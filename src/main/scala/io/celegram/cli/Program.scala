package io.celegram.cli

import java.nio.file.Paths

import cats.effect.{Blocker, ConcurrentEffect, Console, ContextShift, Resource, Sync, Timer}
import io.celegram.cli.util.Config.ServerConfig
import io.celegram.cli.util.{ClientExt, Config, HttpsConnection, JwtTokenService}
import javax.net.ssl.SSLContext
import org.http4s.Uri.Scheme
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}

import scala.concurrent.ExecutionContext

object Program {

  val configPath = Paths.get(System.getProperty("user.home")).resolve(".celegram/config.json")

  def makeLoader[F[_]: Sync: ContextShift: Console]: Resource[F, ConfigLoader[F]] = Blocker[F].evalMap { blocker =>
    ConfigLoader
      .cached[F]
      .apply(ConfigLoader.default[F](configPath, blocker))
  }

  def makeJwtService[F[_]: ConfigLoader: Sync]: Resource[F, JwtTokenService] =
    Resource.liftF(Config.ask[F]).map(_.jwt).map(jwtConfig => JwtTokenService(jwtConfig.secret))

  def makeClient[F[_]: ConfigLoader: ConcurrentEffect: ContextShift: Timer: Console]: Resource[F, Client[F]] = {
    val serverConfig: Resource[F, ServerConfig] = Resource.liftF(Config.ask[F]).map(_.smartConfigServer)

    serverConfig.flatMap { config =>
      import config._

      val sslContextResource: Resource[F, SSLContext] = HttpsConnection[F](config.keyStore).getContext()

      sslContextResource.flatMap { sslContext =>
        BlazeClientBuilder(ExecutionContext.global)
          .withSslContext(sslContext)
          .resource
          .map(RequestLogger(logHeaders = true, logBody = true))
          .map(ResponseLogger(logHeaders = true, logBody = true))
          .map(ClientExt.implicitHost(host, port, Scheme.unsafeFromString(protocol)))
          .map(ClientExt.withToken)
          .map(ClientExt.logFailedResponse)
      }
    }
  }

  def makeCelegram[F[_]: Console: Sync](client: Client[F], jwtService: JwtTokenService): Celegram[F] = {
    implicit val theClient: Client[F] = client

    Celegram.instance[F](jwtService)
  }
}
