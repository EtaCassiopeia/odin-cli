package io.celegram.cli.util

import java.security.{KeyStore, SecureRandom}

import com.typesafe.scalalogging.LazyLogging
import io.celegram.cli.util.Config.KeyStoreConfig
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.language.reflectiveCalls
import scala.util.Try

import java.io._
import cats.effect._

class HttpsConnection[F[_]: Sync](keyStoreConfig: KeyStoreConfig) {

  private def reader(file: File): Resource[F, InputStream] = {
    val F = implicitly(Sync[F])
    Resource.fromAutoCloseable(F.delay {
      new FileInputStream(file)
    })
  }

  def getContext(): Resource[F, SSLContext] = {
    reader(new File(keyStoreConfig.file)).map { keyStore =>
      val password: Array[Char] = keyStoreConfig.password.toCharArray

      val ks: KeyStore = KeyStore.getInstance("PKCS12")

      ks.load(keyStore, password)

      val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
      keyManagerFactory.init(ks, password)

      val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
      tmf.init(ks)

      val sslContext: SSLContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
      sslContext
    }
  }
}

object HttpsConnection {
  def apply[F[_]: Sync](keyStoreConfig: KeyStoreConfig): HttpsConnection[F] = new HttpsConnection(keyStoreConfig)
}
