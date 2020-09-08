package msgpack4z

import msgpack4z.CirceUnpackOptions.NonStringKeyHandler
import io.circe.Json
import scalaz.{\/, \/-}

final case class CirceUnpackOptions(
  extension: Unpacker[Json],
  binary: Unpacker[Json],
  positiveInf: UnpackResult[Json],
  negativeInf: UnpackResult[Json],
  nan: UnpackResult[Json],
  nonStringKey: NonStringKeyHandler
)

object CirceUnpackOptions {
  val binaryToNumberArray: Binary => Json = { bytes => Json.fromValues(bytes.value.iterator.map(byte => Json.fromLong(byte)).toSeq) }

  val binaryToNumberArrayUnpacker: Unpacker[Json] = { unpacker => CodecInstances.binary.binaryCodec.unpack(unpacker).map(binaryToNumberArray) }

  val extUnpacker: Unpacker[Json] = { unpacker =>
    val header = unpacker.unpackExtTypeHeader
    val data = unpacker.readPayload(header.getLength)
    val dataArray = Json.fromValues(data.iterator.map(byte => Json.fromLong(byte)).toSeq)
    val result = Json.obj(
      ("type", Json.fromLong(header.getType)),
      ("data", dataArray)
    )
    \/-(result)
  }

  type NonStringKeyHandler = (MsgType, MsgUnpacker) => Option[String]

  private[this] val jNullRight: UnpackError \/ Json = \/-(Json.Null)

  val default: CirceUnpackOptions = CirceUnpackOptions(
    extUnpacker,
    binaryToNumberArrayUnpacker,
    jNullRight,
    jNullRight,
    jNullRight,
    { case (tpe, unpacker) =>
      PartialFunction.condOpt(tpe) {
        case MsgType.NIL =>
          "null"
        case MsgType.BOOLEAN =>
          unpacker.unpackBoolean().toString
        case MsgType.INTEGER =>
          unpacker.unpackBigInteger().toString
        case MsgType.FLOAT =>
          unpacker.unpackDouble().toString
        case MsgType.STRING =>
          unpacker.unpackString()
      }
    }
  )
}
