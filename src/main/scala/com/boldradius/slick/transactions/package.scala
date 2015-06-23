package com.boldradius.slick

import java.sql.{SQLException, Connection}

import slick.dbio.DBIO

import scala.annotation.tailrec
import slick.jdbc.JdbcBackend
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import slick.driver.PostgresDriver.JdbcActionExtensionMethods
import slick.driver._
import scala.concurrent.Await
import scalaz.effect.IO
import slick.backend.DatabaseComponent
import slick.profile.BasicProfile
import slick.driver.JdbcActionComponent
import slick.jdbc.TransactionIsolation._
import scala.concurrent.duration._

/**
 * Created by ppremont on 2014-11-03.
 */
package object transactions {
  /**
   * All database statements and transcations in the applicaction must be performed through
   * [[transaction]]. This method runs a [[DBIO]] transactionally
   * and wraps it in [[IO]] to prevent accidental nesting of transactions. The transaction will only
   * run when [[IO]] runs, through a call to [[IO.unsafePerformIO]]. This call should only occur near the
   * top level of the application (such as in a controller), where the calling context is clear and
   * no accidental nesting can occur.
   *
   * To avoid accidentally nested transactions, calls of [[IO.unsafePerformIO]] should definitely not occur
   * within utility functions. Functions that work on the database may instead
   *  a. return a Query that may then be composed into larger queries,
   *  b. return a DBIO so that they can be run as part of an eventual transaction
   *  c. or, as a last resort, return IO after having made one or more calls to [[transaction]].
   *
   * The transaction is set to use the serializable isolation level, and will retry immediately as many times
   * as necessary in the even of a serialization failure (when concurrent transactions have
   * invalidated the work of this transaction). No exponential back-off is currently implemented.
   */
  def transaction[A](db : PostgresDriver#Backend#DatabaseDef)(txn: DBIO[A]): IO[A] = {
    val serializationFailureErrorCode = "40001" // http://www.postgresql.org/docs/8.3/static/errcodes-appendix.html
    @tailrec def retryOnSerializationFailure: A =
      (try {
        Some(Await.result(db.run(txn.withTransactionIsolation(Serializable)), Duration.Inf))
      } catch {
        case e:SQLException =>
          if (e.getSQLState() == serializationFailureErrorCode) None
          else throw e
      }) match {
        case Some(x) => x
        case None => retryOnSerializationFailure
      }
    IO(retryOnSerializationFailure)
  }

}
