package msgpack4z

import io.circe._
import scalaprops._
import scalaz.{-\/, Equal, \/-}
import CirceMsgpack.{circeJsonEqual, circeJsonObjectEqual}

sealed abstract class SpecBase extends Scalaprops {

  private[this] implicit val scalaDoubleGen: Gen[Double] =
    Gen[Long].map { n =>
      java.lang.Double.longBitsToDouble(n) match {
        case x if x.isNaN => n
        case x => x
      }
    }

  private[this] implicit val bigDecimalGen: Gen[BigDecimal] =
    Gen[Double].map(BigDecimal(_))

  private[this] implicit val stringGen = Gen.alphaNumString

  private[this] val jsonNumberGen: Gen[Json] =
    Gen.oneOf(
      bigDecimalGen.map(Json.bigDecimal),
      Gen[Long].map(Json.long),
      Gen[Double].map(Json.numberOrNull)
    )

  private[this] val jsValuePrimitivesArb: Gen[Json] =
    Gen.oneOf(
      Gen.value(Json.Empty),
      Gen.value(Json.True),
      Gen.value(Json.False),
      jsonNumberGen,
      Gen[String].map(Json.string)
    )

  private[this] val jsObjectArb1: Gen[JsonObject] =
    Gen.listOfN(
      5,
      Gen.tuple2(
        Gen[String], jsValuePrimitivesArb
      )
    ).map(list => JsonObject.fromIndexedSeq(list.toVector))

  private[this] val jsArrayArb1: Gen[List[Json]] =
    Gen.listOfN(5, jsValuePrimitivesArb)

  implicit val jsValueArb: Gen[Json] =
    Gen.oneOf(
      jsValuePrimitivesArb,
      jsObjectArb1.map(Json.fromJsonObject),
      jsArrayArb1.map(Json.array)
    )

  implicit val jsObjectArb: Gen[JsonObject] =
    Gen.listOfN(
      5,
      Gen.tuple2(Gen[String], jsValueArb)
    ).map(list => JsonObject.fromIndexedSeq(list.toVector))

  implicit val jsArrayArb: Gen[List[Json]] =
    Gen.listOfN(5, jsValueArb)

  protected[this] def packer(): MsgPacker
  protected[this] def unpacker(bytes: Array[Byte]): MsgUnpacker

  private[this] def checkRoundTripBytes[A](implicit A: MsgpackCodec[A], G: Gen[A], E: Equal[A]) =
    Property.forAll { a: A =>
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

object Java06Spec extends SpecBase {
  override protected[this] def packer() = Msgpack06.defaultPacker()
  override protected[this] def unpacker(bytes: Array[Byte]) = Msgpack06.defaultUnpacker(bytes)
}

object Java07Spec extends SpecBase {
  override protected[this] def packer() = new Msgpack07Packer()
  override protected[this] def unpacker(bytes: Array[Byte]) = Msgpack07Unpacker.defaultUnpacker(bytes)
}

object NativeSpec extends SpecBase {
  override protected[this] def packer() = MsgOutBuffer.create()
  override protected[this] def unpacker(bytes: Array[Byte]) = MsgInBuffer(bytes)
}
