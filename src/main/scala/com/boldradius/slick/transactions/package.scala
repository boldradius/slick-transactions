package com.boldradius.slick

import java.sql.{SQLException, Connection}

import scala.annotation.tailrec
import scala.slick.jdbc.JdbcBackend
import scala.slick.driver.PostgresDriver
import scalaz.effect.IO
import scala.slick.backend.DatabaseComponent
import scala.slick.profile.BasicProfile

/**
 * Created by ppremont on 2014-11-03.
 */
package object transactions {
  /**
   * All database statements and transcations in the applicaction must be performed through
   * [[transaction]]. This method records the function to be executed as a transaction,
   * and wraps it in [[IO]] to prevent accidental nesting of transactions. The transaction will only
   * run when [[IO]] runs, through a call to [[IO.unsafePerformIO]]. This call should only occur near the
   * top level of the application (such as in a controller), where the calling context is clear and
   * no accidental nesting can occur.
   *
   * To avoid accidentally nested transactions, calls of [[IO.unsafePerformIO]] should definitely not occur
   * within utility functions. Functions that work on the database may instead
   *  a. return a Query that may then be composed into larger queries,
   *  b. or take in the session, so that they can be run as part of an eventual transaction
   *  c. or, as a last resort, return IO after having made one or more calls to [[transaction]].
   *
   * The transaction is set to use the serializable isolation level, and will retry immediately as many times
   * as necessary in the even of a serialization failure (when concurrent transactions have
   * invalidated the work of this transaction). No exponential back-off is currently implemented.
   *
   * Care must be taken not to leak the session out of the call to transaction. The returned type A should
   * not contain the session: it will be closed and unusable. This can happen accidentally if you create a closure
   * that closes over the session. In the presence of nested transactions, a call to [[IO.map]] on the inner transaction
   * would have the outer session in lexical scope but its execution would be delayed until after the session is closed.
   * We may solve this in the future by no longer exposing the session and using a monad to compose parts of the
   * transaction.
   */
  def transaction[A](db : PostgresDriver.backend.DatabaseDef)(txn: PostgresDriver.backend.Session => A): IO[A] = {
    val serializationFailureErrorCode = "40001" // http://www.postgresql.org/docs/8.3/static/errcodes-appendix.html
    @tailrec def retryOnSerializationFailure: A =
      (try {
        db.withTransaction { s =>
          s.conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE)
          Some(txn(s))
        }
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
