package io.celegram.cli.util

import cats.Applicative
import io.celegram.cli.ConfigLoader
import io.celegram.cli.util.Config.{JwtConfig, ServerConfig}
import io.circe.generic.auto._

final case class Config(
  jwt: JwtConfig,
  smartConfigServer: ServerConfig
)

object Config extends AskFor[Config] {

  final case class JwtConfig(secret: String) extends AnyVal

  final case class Token(value: String) extends AnyVal {
    override def toString: String = s"token=$value"
  }

  final case class KeyStoreConfig(file: String, password: String)
  final case class ServerConfig(host: String, port: Int, protocol: String, keyStore: KeyStoreConfig, token: Token)

  object ServerConfig extends AskFor[ServerConfig] {}

  implicit def deriveAskFromLoader[F[_]: ConfigLoader: Applicative]: Config.Ask[F] = {
    Config.askLiftF {
      val config: F[Config] = ConfigLoader[F].loadConfig
      config
    }
  }

}
