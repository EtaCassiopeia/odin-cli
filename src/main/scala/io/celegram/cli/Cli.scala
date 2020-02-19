package io.celegram.cli

import java.util.Date

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.Console.implicits._
import cats.effect.{Console, ExitCode, IO, Resource}
import cats.implicits._
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.{Command, Opts}

import scala.util.Try

/** List of supporting commands
  * -- List all open orders
  * -- Create Order
  * -- Close Order
  * -- Close all orders ( with prefix )
  * -- Modify Order ( stop loss, stop limit )
  * -- Modify All Orders ( prefix )
  * -- Today's Profit loss along with one hour profit loss
  * -- List of all trades along with details like open price close price, profit/loss ( today, last hour, date range )
  * -- Get Trend Status
  * -- Get last price
  * */
sealed trait Commands extends Product with Serializable

object Commands {

  case object CloseAllOrders extends Commands

  case class CloseOrder(orderId: String) extends Commands

  case class CloseAllOrdersWithPrefix(prefix: String) extends Commands

  case class ModifyOrder(orderId: String, stopLoss: Option[Double] = None, stopLimit: Option[Double] = None)
      extends Commands

  case class ModifyAllOrders(stopLoss: Option[Double] = None, stopLimit: Option[Double] = None) extends Commands

  case object OneDayProfit extends Commands

  case object OneHourProfit extends Commands

  case object OneDayLoss extends Commands

  case object OneHourLoss extends Commands

  case object ListOpenOrders extends Commands

  case object ListOneDayTrades extends Commands

  case object ListOneHourTrades extends Commands

  case class ListTrades(startDate: Option[Date] = None, endDate: Option[Date] = None) extends Commands

  case class TrendStatus(currency: String) extends Commands

  case class LastPrice(currency: String) extends Commands

  case class GenerateJwtToken(user: String) extends Commands

  case class UpdateProperty(key: String, value: String) extends Commands

  private val format = new java.text.SimpleDateFormat("yyyy-MM-dd")

  private val configCommand: Opts[Commands] = Opts.subcommand(name = "config", help = "Update configuration") {
    val keyOption = Opts
      .option[String](long = "key", help = "The name of the property to change")

    val valueOption = Opts
      .option[String](long = "value", help = "The new value of the property to set")

    (keyOption, valueOption).tupled.map {
      case (key, value) => UpdateProperty(key, value)
    }
  }

  private val tokenCommand: Opts[Commands] = Opts.subcommand("token", help = "Generate JWT token") {
    Opts.option[String](long = "user", help = "User name tomgenerate token for").map(user => GenerateJwtToken(user))
  }

  private val orderCommand: Opts[Commands] =
    Opts.subcommand(name = "order", help = "Manage orders") {

      val listSwitch = Opts.flag(long = "list", help = "List all orders").map(_ => ListOpenOrders)

      val closeSubCommand = Opts
        .subcommand(name = "close", help = "Close order(s)") {
          Opts
            .flag(long = "all", help = "Close all orders")
            .map { _ =>
              CloseAllOrders
            }
            .orElse {
              Opts.option[String](long = "id", help = "Order ID to close").map(orderId => CloseOrder(orderId))
            }
            .orElse {
              Opts
                .option[String](long = "prefix", help = "Order prefix to close")
                .map(prefix => CloseAllOrdersWithPrefix(prefix))
            }
        }

      val modifySubCommand = Opts.subcommand(name = "modify", help = "Modify Order(s)") {
        val stopLoss = Opts
          .argument[Double](metavar = "stop-loss")
          .withDefault(default = 0d)
          .validate(message = "Specified stop loss must be positive.") {
            _ >= 0
          }
          .map(d => if (d > 0) Some(d) else None)
        val stopLimit = Opts
          .argument[Double](metavar = "stop-limit")
          .withDefault(default = 0d)
          .validate(message = "Specified stop limit must be positive.") {
            _ >= 0
          }
          .map(d => if (d > 0) Some(d) else None)

        val arguments: Opts[(Option[Double], Option[Double])] =
          (stopLoss, stopLimit).tupled
            .validate("At least one of stop-loss or stop-limit arguments should be specified") {
              case (sLoss, sLimit) => sLoss.isDefined || sLimit.isDefined
            }

        (
          Opts
            .flag(long = "all", help = "Modify all orders")
            .map(_ => ModifyAllOrders()),
          arguments
        ).tupled.map {
          case (modifyAllOrders, (stopLossArgument, stopLimitArgument)) =>
            modifyAllOrders.copy(stopLoss = stopLossArgument, stopLimit = stopLimitArgument)
        }.orElse {
          (Opts.option[String](long = "id", help = "Order ID to close").map(orderId => ModifyOrder(orderId)), arguments).tupled.map {
            case (modifyOrder, (stopLossArgument, stopLimitArgument)) =>
              modifyOrder.copy(stopLoss = stopLossArgument, stopLimit = stopLimitArgument)
          }
        }

      }

      listSwitch orElse closeSubCommand orElse modifySubCommand
    }

  private val tradeCommand: Opts[Commands] = Opts.subcommand(name = "trade", help = "Trade details") {
    val oneDayTrades = Opts.flag(long = "day", help = "List today's trades").map(_ => ListOneDayTrades)
    val oneHourTrades = Opts.flag(long = "hour", help = "List last hour's trades").map(_ => ListOneHourTrades)

    val rangeTradesFlag =
      Opts.flag(long = "range", help = "List all trades within a date range").map(_ => ListTrades())
    val startDateOption = Opts
      .option[String](long = "from", help = "Show trades starting from this date")
      .validate("Supported format is yyyy-MM-dd")(str => Try(format.parse(str)).isSuccess)
      .map(format.parse(_).some)
      .withDefault(None)
    val endDateOption = Opts
      .option[String](long = "to", help = "Show trades until this date")
      .validate("Supported format is yyyy-MM-dd")(str => Try(format.parse(str)).isSuccess)
      .map(format.parse(_).some)
      .withDefault(None)

    oneDayTrades orElse oneHourTrades orElse {
      (rangeTradesFlag, startDateOption, endDateOption).tupled
        .validate(message = "At least one of `from` or `to` arguments must be specified") {
          case (_, from, to) => from.isDefined || to.isDefined
        }
        .map {
          case (rangeFlag, from, to) => rangeFlag.copy(startDate = from, endDate = to)
        }
    }
  }

  private val profitCommand: Opts[Commands] = Opts.subcommand(name = "profit", help = "Amount of profit") {
    val oneDayProfitFlag = Opts.flag(long = "day", help = "Today's profit").map(_ => OneDayProfit)
    val oneHourProfitFlag = Opts.flag(long = "hour", help = "Last hour's profit").map(_ => OneHourProfit)

    oneDayProfitFlag orElse oneHourProfitFlag
  }

  private val lossCommand: Opts[Commands] = Opts.subcommand(name = "loss", help = "Amount of loss") {
    val oneDayProfitFlag = Opts.flag(long = "day", help = "Today's loss").map(_ => OneDayLoss)
    val oneHourProfitFlag = Opts.flag(long = "hour", help = "Last hour's loss").map(_ => OneHourLoss)

    oneDayProfitFlag orElse oneHourProfitFlag
  }

  private val trendCommand: Opts[Commands] = Opts.subcommand(name = "trend", help = "Trend details") {
    Opts.argument[String](metavar = "cur").withDefault("BTC").map(TrendStatus)
  }

  private val priceCommand: Opts[Commands] = Opts.subcommand(name = "price", help = "Current currency price") {
    Opts.argument[String](metavar = "cur").withDefault("BTC").map(LastPrice)
  }

  val opts: Opts[Commands] =
    NonEmptyList
      .of[Opts[Commands]](
        orderCommand,
        tradeCommand,
        trendCommand,
        profitCommand,
        lossCommand,
        priceCommand,
        tokenCommand,
        configCommand
      )
      .reduceK
}

object Cli
    extends CommandIOApp(
      name = "cli",
      header = "Celegram Manager (A command line interface to manage Celegram)",
      version = "1.0"
    ) {

  import Program._

  private def render[A]: A => Unit = a => println(a)

  def runApp[F[_]: Console: Monad]: Celegram[F] => Commands => F[Unit] = celegram => {
    case Commands.ListOpenOrders                 => celegram.listAllOpenTrades.map(render)
    case Commands.ListOneDayTrades               => celegram.listDailyOpenTrades.map(render)
    case Commands.ListOneHourTrades              => celegram.listHourlyOpenTrades.map(render)
    case Commands.ListTrades(startDate, endDate) => celegram.listOpenTradesInRange(startDate, endDate).map(render)
    case Commands.GenerateJwtToken(user)         => celegram.generateToken(user).map(render)
    case Commands.UpdateProperty(key, value)     => celegram.changeProperty(key, value).map(render)
    case _                                       => ().pure[F].widen
  }

  import Console.io._

  val makeProgram: Resource[IO, Commands => IO[Unit]] =
    makeLoader[IO].flatMap { implicit loader =>
      makeJwtService[IO].flatMap { jwtService =>
        makeClient[IO].map(client => makeCelegram[IO](client, jwtService)).map(runApp[IO])
      }
    }

  val mainOpts: Opts[IO[Unit]] = Commands.opts.map { choice =>
    makeProgram.use(_.apply(choice))
  }

  val runRepl: IO[Unit] = {
    val input = fs2.Stream
      .repeatEval(putStr("> ") *> readLn.map(Option(_)))
      .map(_.map(_.trim))
      .filter(_.forall(_.nonEmpty))
      .unNoneTerminate
      .map(_.split("\\s+").toList)
      .onComplete(fs2.Stream.eval_(putStrLn("Bye!")))

    def reportError(e: Throwable): IO[Unit] =
      putError("Command failed with exception: ") *> IO(e.printStackTrace())

    fs2.Stream.eval_(putStrLn("Celegram REPL")) ++
      fs2.Stream
        .resource(makeProgram)
        .map(Command("", "")(Commands.opts).map(_))
        .flatMap { command =>
          input
            .map(command.parse(_, sys.env).leftMap(_.toString))
            .evalMap(_.fold(putStrLn(_), _.handleErrorWith(reportError)))
        }
  }.compile.drain

  val repl: Opts[Unit] = Opts
    .subcommand("repl", "Interactive Mode")(Opts.unit)

  val main: Opts[IO[ExitCode]] =
    (mainOpts <+> repl.as(runRepl)).map(_.as(ExitCode.Success))

}
