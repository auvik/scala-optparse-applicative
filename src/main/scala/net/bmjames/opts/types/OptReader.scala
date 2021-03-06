package net.bmjames.opts.types

import scalaz.{Functor, MonadPlus, \/}
import scalaz.syntax.applicative._
import scalaz.std.option._

import CReader._

sealed trait OptReader[A] {

  final def names: List[OptName] =
    this match {
      case OptionReader(ns, _, _) => ns
      case FlagReader(ns, _)   => ns
      case _ => Nil
    }
}

case class OptionReader[A](ns: List[OptName], cr: OptCReader[A], e: ParseError) extends OptReader[A]

case class FlagReader[A](ns: List[OptName], a: A) extends OptReader[A]

case class ArgReader[A](cr: ArgCReader[A]) extends OptReader[A]

case class CmdReader[A](ns: List[String], f: String => Option[ParserInfo[A]]) extends OptReader[A]

object OptReader {

  implicit val optReaderFunctor: Functor[OptReader] =
    new Functor[OptReader] {
      def map[A, B](fa: OptReader[A])(f: A => B): OptReader[B] =
        fa match {
          case OptionReader(ns, cr, e) => OptionReader(ns, cr.map(f), e)
          case FlagReader(ns, a)       => FlagReader(ns, f(a))
          case ArgReader(cr)           => ArgReader(cr.map(f))
          case CmdReader(ns, g)        => CmdReader(ns, g.andThen(_.map(_.map(f))))
        }
    }

}

/** A newtype over the Either monad used by option readers.
  */
final case class ReadM[A](run: ParseError \/ A)

object ReadM {

  /** Abort option reader by exiting with a ParseError. */
  def abort[A](e: ParseError): ReadM[A] =
    ReadM(\/.left(e))

  /** Abort option reader by exiting with an error message. */
  def readerError[A](e: String): ReadM[A] =
    abort(ErrorMsg(e))

  implicit val readMMonadPlus: MonadPlus[ReadM] =
    new MonadPlus[ReadM] {
      def bind[A, B](fa: ReadM[A])(f: A => ReadM[B]): ReadM[B] =
        ReadM(fa.run.flatMap(a => f(a).run))

      def point[A](a: => A): ReadM[A] = ReadM(\/.right(a))

      def empty[A]: ReadM[A] = ReadM(\/.left(UnknownError))

      def plus[A](a: ReadM[A], b: => ReadM[A]): ReadM[A] =
        a.run.fold(_ => b, a => a.point[ReadM])
    }
}

sealed trait OptName
case class OptShort(name: Char) extends OptName
case class OptLong(name: String) extends OptName

object OptName {
  implicit val optNameOrdering: Ordering[OptName] =
    Ordering.fromLessThan {
      case (OptShort(n1), OptShort(n2)) => n1 < n2
      case (OptLong(n1),  OptLong(n2))  => n1 < n2
      case (OptShort(_), _)             => true
      case (OptLong(_), _)              => false
    }

}
