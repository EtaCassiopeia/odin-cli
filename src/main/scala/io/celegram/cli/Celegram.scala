package io.celegram.cli

import java.text.SimpleDateFormat
import java.util.Date

import cats.data.Kleisli
import cats.effect.{Console, Sync}
import cats.implicits._
import io.celegram.cli.Celegram.MockResponse
import io.celegram.cli.util.Config.Token
import io.celegram.cli.util.JwtTokenService
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.{Request, Uri}
import io.circe.generic.auto._

trait Celegram[F[_]] {
  def listAllOpenTrades: F[Seq[MockResponse]]
  def listDailyOpenTrades: F[Seq[MockResponse]]
  def listHourlyOpenTrades: F[Seq[MockResponse]]
  def listOpenTradesInRange(from: Option[Date], to: Option[Date]): F[Seq[MockResponse]]
  def price(currency: String): F[MockResponse]

  def generateToken(user: String): F[Token]
  def changeProperty(key: String, value: String): F[MockResponse]
}

object Celegram {
  sealed trait Error extends Throwable
  case class CelegramGenericError(cause: String) extends Error

  case class MockResponse(message: String)

  private val formatter = new SimpleDateFormat("yyyy-MM-dd")

  def instance[F[_]: Client: Console: Sync](jwtService: JwtTokenService): Celegram[F] = new Celegram[F] {
    val client: Client[F] = implicitly[Client[F]]

    val console: Console[F] = Console[F]

    import console._

    override def listAllOpenTrades: F[Seq[MockResponse]] =
      putStrLn("Open Trades: ") *>
        methods.listDailyTrades[F].run(client)

    override def listDailyOpenTrades: F[Seq[MockResponse]] =
      putStrLn("Open Daily Trades: ") *>
        methods.listDailyTrades[F].run(client)

    override def listHourlyOpenTrades: F[Seq[MockResponse]] =
      putStrLn("Open Daily Trades: ") *>
        methods.listHourlyTrades[F].run(client)

    override def listOpenTradesInRange(
      startDate: Option[Date],
      endDate: Option[Date]
    ): F[Seq[MockResponse]] =
      putStrLn("Open Daily Trades: ") *>
        methods.listTradesInRange[F](startDate.map(formatter.format), endDate.map(formatter.format)).run(client)

    override def price(currency: String): F[MockResponse] = ???

    override def generateToken(user: String): F[Token] =
      putStrLn(s"Generating JWT token [user=`$user`]:") *> methods.generateToken(user).run(jwtService)

    override def changeProperty(key: String, value: String): F[MockResponse] =
      putStrLn("Changing configuration ") *>
        methods.changeConfiguration[F](key, value).run(client)
  }

  object methods {
    type Method[F[_], A] = Kleisli[F, Client[F], A]

    def generateToken[F[_]: Sync](user: String): Kleisli[F, JwtTokenService, Token] = Kleisli { jwtService =>
      Token(jwtService.generateToken(user)).pure[F]
    }

    def listAllTrades[F[_]: Sync]: Method[F, Seq[MockResponse]] = Kleisli {
      _.expectOr[Seq[MockResponse]]("/api/trade/list")(
        response => CelegramGenericError(response.status.reason).pure[F].widen
      )
    }

    def listDailyTrades[F[_]: Sync]: Method[F, Seq[MockResponse]] = Kleisli {
      _.expectOr[Seq[MockResponse]]("/api/trade/daily")(
        response => CelegramGenericError(response.status.reason).pure[F].widen
      )
    }

    def listHourlyTrades[F[_]: Sync]: Method[F, Seq[MockResponse]] = Kleisli {
      _.expectOr[Seq[MockResponse]]("/api/trade/hourly")(
        response => CelegramGenericError(response.status.reason).pure[F].widen
      )
    }

    def listTradesInRange[F[_]: Sync](
      startDate: Option[String],
      endDate: Option[String]
    ): Method[F, Seq[MockResponse]] = Kleisli { client =>
      val request: Request[F] =
        Request[F](
          uri = Uri.uri("/api/trade/range").withOptionQueryParam("from", startDate).withOptionQueryParam("to", endDate)
        )

      client.expectOr[Seq[MockResponse]](request)(
        response => CelegramGenericError(response.status.reason).pure[F].widen
      )
    }

    def changeConfiguration[F[_]: Sync](
      key: String,
      value: String
    ): Method[F, MockResponse] = Kleisli { client =>
      val request: Request[F] =
        Request[F](
          uri = Uri.uri("/api/update").withOptionQueryParam("key", Some(key)).withOptionQueryParam("value", Some(value))
        )

      client.expectOr[MockResponse](request)(
        response => CelegramGenericError(response.status.reason).pure[F].widen
      )
    }
  }
}
