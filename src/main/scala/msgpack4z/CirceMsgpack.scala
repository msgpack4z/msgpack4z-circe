package msgpack4z

import io.circe._
import scalaz.{-\/, \/, \/-, Equal}

object CirceMsgpack {
  implicit val circeJsonEqual: Equal[Json] = Equal.equalA[Json]
  implicit val circeJsonObjectEqual: Equal[JsonObject] = Equal.equalA[JsonObject]
  implicit val circeJsonNumberEqual: Equal[JsonNumber] = Equal.equalA[JsonNumber]

  def jsonCodec(options: CirceUnpackOptions): MsgpackCodec[Json] =
    new CodecCirceJson(options)

  def jsonObjectCodec(options: CirceUnpackOptions): MsgpackCodec[JsonObject] =
    new CodecCirceJsonObject(options)

  def allCodec(options: CirceUnpackOptions): (MsgpackCodec[Json], MsgpackCodec[JsonObject]) = (
    jsonCodec(options),
    jsonObjectCodec(options)
  )

  def jsonObject2msgpack(packer: MsgPacker, obj: JsonObject): Unit = {
    val fields = obj.toList
    packer.packMapHeader(fields.size)
    fields.foreach { field =>
      packer.packString(field._1)
      json2msgpack(packer, field._2)
    }
    packer.mapEnd()
  }

  def jsonArray2msgpack(packer: MsgPacker, array: Seq[Json]): Unit = {
    packer.packArrayHeader(array.size)
    array.foreach { x => json2msgpack(packer, x) }
    packer.arrayEnd()
  }

  def json2msgpack(packer: MsgPacker, json: Json): Unit = {
    json.fold(
      jsonNull = {
        packer.packNil()
      },
      jsonBoolean = value => {
        packer.packBoolean(value)
      },
      jsonNumber = value => {
        value.toLong match {
          case Some(l) =>
            packer.packLong(l)
          case None =>
            value.toBigDecimal match {
              case Some(b) =>
                if (b.isWhole && Long.MinValue <= b && b < BigDecimalLongMax2x) {
                  packer.packBigInteger(b.underlying.toBigIntegerExact)
                } else {
                  packer.packDouble(value.toDouble)
                }
              case None =>
                packer.packDouble(value.toDouble)
            }
        }
      },
      jsonString = string => {
        packer.packString(string)
      },
      jsonArray = array => {
        jsonArray2msgpack(packer, array)
      },
      jsonObject = obj => {
        jsonObject2msgpack(packer, obj)
      }
    )
  }

  def msgpack2json(unpacker: MsgUnpacker, unpackOptions: CirceUnpackOptions): UnpackResult[Json] = {
    val result = Result.empty[Json]
    if (msgpack2json0(unpacker, result, unpackOptions)) {
      \/-(result.value)
    } else {
      -\/(result.error)
    }
  }

  def msgpack2jsonObject(unpacker: MsgUnpacker, unpackOptions: CirceUnpackOptions): UnpackResult[JsonObject] = {
    val result = Result.empty[JsonObject]
    if (msgpack2jsObj0(unpacker, result, unpackOptions)) {
      \/-(result.value)
    } else {
      -\/(result.error)
    }
  }

  def msgpack2jsonArray(unpacker: MsgUnpacker, unpackOptions: CirceUnpackOptions): UnpackResult[List[Json]] = {
    val result = Result.empty[List[Json]]
    if (msgpack2jsArray0(unpacker, result, unpackOptions)) {
      \/-(result.value)
    } else {
      -\/(result.error)
    }
  }

  private[this] final case class Result[A](
    var value: A,
    var error: UnpackError
  )
  private[this] object Result {
    def fromEither[A](e: UnpackError \/ A, result: Result[A]): Boolean = e match {
      case \/-(r) =>
        result.value = r
        true
      case -\/(l) =>
        result.error = l
        false
    }

    def empty[A >: Null]: Result[A] = Result[A](null, null)
  }

  private[this] def msgpack2jsObj0(unpacker: MsgUnpacker, result: Result[JsonObject], unpackOptions: CirceUnpackOptions): Boolean = {
    val size = unpacker.unpackMapHeader()
    var obj = JsonObject.empty
    var i = 0
    val mapElem = Result.empty[Json]
    var success = true

    def process(key: String): Unit = {
      if (msgpack2json0(unpacker, mapElem, unpackOptions)) {
        obj = obj.add(key, mapElem.value)
        i += 1
      } else {
        result.error = mapElem.error
        success = false
      }
    }

    while (i < size && success) {
      val tpe = unpacker.nextType()
      if (tpe == MsgType.STRING) {
        process(unpacker.unpackString())
      } else {
        unpackOptions.nonStringKey(tpe, unpacker) match {
          case Some(key) =>
            process(key)
          case None =>
            success = false
            result.error = Other("not string key")
        }
      }
    }
    unpacker.mapEnd()
    if (success) {
      result.value = obj
    }
    success
  }

  private[this] def msgpack2jsArray0(unpacker: MsgUnpacker, result: Result[List[Json]], unpackOptions: CirceUnpackOptions): Boolean = {
    val size = unpacker.unpackArrayHeader()
    val array = new Array[Json](size)
    var i = 0
    val arrayElem = Result[Json](null, null)
    var success = true
    while (i < size && success) {
      if (msgpack2json0(unpacker, arrayElem, unpackOptions)) {
        array(i) = arrayElem.value
        i += 1
      } else {
        result.error = arrayElem.error
        success = false
      }
    }
    unpacker.arrayEnd()
    if (success) {
      result.value = array.toList
    }
    success
  }

  private[this] val BigIntegerLongMax = java.math.BigInteger.valueOf(Long.MaxValue)
  private[this] val BigDecimalLongMax2x: BigDecimal = BigDecimal(Long.MaxValue) * 2
  private[this] val BigIntegerLongMin = java.math.BigInteger.valueOf(Long.MinValue)

  private def isValidLong(value: java.math.BigInteger): Boolean =
    (BigIntegerLongMin.compareTo(value) <= 0) && (value.compareTo(BigIntegerLongMax) <= 0)

  private[msgpack4z] def msgpack2json0(unpacker: MsgUnpacker, result: Result[Json], unpackOptions: CirceUnpackOptions): Boolean = {
    unpacker.nextType match {
      case MsgType.NIL =>
        unpacker.unpackNil()
        result.value = Json.Null
        true
      case MsgType.BOOLEAN =>
        if (unpacker.unpackBoolean()) {
          result.value = Json.True
        } else {
          result.value = Json.False
        }
        true
      case MsgType.INTEGER =>
        val value = unpacker.unpackBigInteger()
        if (isValidLong(value)) {
          result.value = Json.fromLong(value.longValue())
        } else {
          result.value = Json.fromBigDecimal(BigDecimal(value))
        }
        true
      case MsgType.FLOAT =>
        val f = unpacker.unpackDouble()
        if (f.isPosInfinity) {
          Result.fromEither(unpackOptions.positiveInf, result)
        } else if (f.isNegInfinity) {
          Result.fromEither(unpackOptions.negativeInf, result)
        } else if (java.lang.Double.isNaN(f)) {
          Result.fromEither(unpackOptions.nan, result)
        } else {
          result.value = Json.fromDoubleOrNull(f)
        }
        true
      case MsgType.STRING =>
        result.value = Json.fromString(unpacker.unpackString())
        true
      case MsgType.ARRAY =>
        val result0 = Result.empty[List[Json]]
        val r = msgpack2jsArray0(unpacker, result0, unpackOptions)
        result.error = result0.error
        result.value = Json.fromValues(result0.value)
        r
      case MsgType.MAP =>
        val result0 = Result.empty[JsonObject]
        val r = msgpack2jsObj0(unpacker, result0, unpackOptions)
        result.error = result0.error
        result.value = Json.fromJsonObject(result0.value)
        r
      case MsgType.BINARY =>
        Result.fromEither(unpackOptions.binary(unpacker), result)
      case MsgType.EXTENSION =>
        Result.fromEither(unpackOptions.extension(unpacker), result)
    }
  }
}

private final class CodecCirceJson(unpackOptions: CirceUnpackOptions)
  extends MsgpackCodecConstant[Json](
    CirceMsgpack.json2msgpack,
    unpacker => CirceMsgpack.msgpack2json(unpacker, unpackOptions)
  )

private final class CodecCirceJsonObject(unpackOptions: CirceUnpackOptions)
  extends MsgpackCodecConstant[JsonObject](
    CirceMsgpack.jsonObject2msgpack,
    unpacker => CirceMsgpack.msgpack2jsonObject(unpacker, unpackOptions)
  )
