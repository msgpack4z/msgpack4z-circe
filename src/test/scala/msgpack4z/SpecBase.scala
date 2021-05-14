package msgpack4z

import io.circe._
import scalaprops._
import scalaz.{-\/, Equal, \/-}
import CirceMsgpack.{circeJsonEqual, circeJsonObjectEqual}
import java.lang.Double.doubleToLongBits

abstract class SpecBase extends Scalaprops {
  private[this] implicit val scalaDoubleGen: Gen[Double] = {
    val minusZero = doubleToLongBits(-0.0)
    Gen[Long].map { n =>
      java.lang.Double.longBitsToDouble(n) match {
        case x if x.isNaN => n
        case _ if n == minusZero => 0.0
        case x => x
      }
    }
  }

  private[this] implicit val bigDecimalGen: Gen[BigDecimal] =
    Gen[Double].map(BigDecimal(_))

  private[this] implicit val stringGen: Gen[String] = Gen.alphaNumString

  private[this] val jsonNumberGen: Gen[Json] =
    Gen.oneOf(
      bigDecimalGen.map(Json.fromBigDecimal),
      Gen[Long].map(Json.fromLong),
      Gen[Double].map(Json.fromDoubleOrNull)
    )

  private[this] val jsValuePrimitivesArb: Gen[Json] =
    Gen.oneOf(
      Gen.value(Json.Null),
      Gen.value(Json.True),
      Gen.value(Json.False),
      jsonNumberGen,
      Gen[String].map(Json.fromString)
    )

  private[this] val jsObjectArb1: Gen[JsonObject] =
    Gen
      .listOfN(
        5,
        Gen.tuple2(
          Gen[String],
          jsValuePrimitivesArb
        )
      )
      .map(list => JsonObject.fromIterable(list))

  private[this] val jsArrayArb1: Gen[List[Json]] =
    Gen.listOfN(5, jsValuePrimitivesArb)

  implicit val jsValueArb: Gen[Json] =
    Gen.oneOf(
      jsValuePrimitivesArb,
      jsObjectArb1.map(Json.fromJsonObject),
      jsArrayArb1.map(Json.arr)
    )

  implicit val jsObjectArb: Gen[JsonObject] =
    Gen
      .listOfN(
        5,
        Gen.tuple2(Gen[String], jsValueArb)
      )
      .map(list => JsonObject.fromIterable(list))

  implicit val jsArrayArb: Gen[List[Json]] =
    Gen.listOfN(5, jsValueArb)

  protected[this] def packer(): MsgPacker
  protected[this] def unpacker(bytes: Array[Byte]): MsgUnpacker

  private[this] def checkRoundTripBytes[A](implicit A: MsgpackCodec[A], G: Gen[A], E: Equal[A]) =
    Property.forAll { (a: A) =>
      A.roundtripz(a, packer(), unpacker _) match {
        case None =>
          true
        case Some(\/-(b)) =>
          println("fail roundtrip bytes " + a + " " + b)
          false
        case Some(-\/(e)) =>
          println(e)
          false
      }
    }

  val json = {
    implicit val instance = CirceMsgpack.jsonCodec(
      CirceUnpackOptions.default
    )
    checkRoundTripBytes[Json]
  }

  val jsonObject = {
    implicit val instance = CirceMsgpack.jsonObjectCodec(
      CirceUnpackOptions.default
    )
    checkRoundTripBytes[JsonObject]
  }

  override val param = super.param.copy(minSuccessful = 10000)
}
