package msgpack4z

object NativeSpec extends SpecBase {
  override protected[this] def packer(): MsgPacker = MsgOutBuffer.create()
  override protected[this] def unpacker(bytes: Array[Byte]): MsgUnpacker = MsgInBuffer(bytes)
}
