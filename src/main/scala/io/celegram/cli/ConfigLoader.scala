package io.celegram.cli

import java.nio.file.Path

import cats.Applicative
import cats.effect.{Sync, _}
import cats.effect.concurrent.Ref
import cats.implicits._
import io.celegram.cli.util.Config
import io.circe.generic.auto._

trait ConfigLoader[F[_]] {
  def loadConfig: F[Config]
}

object ConfigLoader {

  def apply[F[_]](implicit loader: ConfigLoader[F]): ConfigLoader[F] = loader

  def cached[F[_]: Sync]: ConfigLoader[F] => F[ConfigLoader[F]] =
    underlying =>
      underlying.loadConfig.flatMap(Ref.of(_)).map { ref =>
        new ConfigLoader[F] {
          val loadConfig: F[Config] = ref.get
        }
      }

  def default[F[_]: Sync: ContextShift](configPath: Path, blocker: Blocker): ConfigLoader[F] = new ConfigLoader[F] {
    val loadConfig: F[Config] =
      fs2.io.file
        .readAll[F](configPath, blocker, 4096)
        .through(io.circe.fs2.byteStreamParser[F])
        .through(io.circe.fs2.decoder[F, Config])
        .compile
        .lastOrError
  }
}
