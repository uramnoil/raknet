package uramnoil.raknet.protocol

/**
 * RakNetプロトコルの定数定義
 */
object Protocol {
    // Minecraft Bedrock Edition用のRakNetプロトコルバージョン
    const val PROTOCOL_VERSION: Byte = 11
    
    // MTUサイズの制限
    const val MIN_MTU_SIZE: UShort = 576u
    const val MAX_MTU_SIZE: UShort = 1492u
    const val MAX_WINDOW_SIZE: UShort = 2048u
    
    // パケットフラグ
    const val BIT_FLAG_DATAGRAM: Byte = 0x80
    const val BIT_FLAG_ACK: Byte = 0x40
    const val BIT_FLAG_NACK: Byte = 0x20
    const val BIT_FLAG_NEEDS_B_AND_AS: Byte = 0x04
    
    // 信頼性タイプ
    const val RELIABILITY_UNRELIABLE: Byte = 0
    const val RELIABILITY_UNRELIABLE_SEQUENCED: Byte = 1
    const val RELIABILITY_RELIABLE: Byte = 2
    const val RELIABILITY_RELIABLE_ORDERED: Byte = 3
    const val RELIABILITY_RELIABLE_SEQUENCED: Byte = 4
    
    // 分割フラグ
    const val SPLIT_FLAG: Byte = 0x10
    
    // メッセージID
    object MessageID {
        const val CONNECTED_PING: Byte = 0x00
        const val UNCONNECTED_PING: Byte = 0x01
        const val CONNECTED_PONG: Byte = 0x03
        const val CONNECTION_REQUEST: Byte = 0x09
        const val CONNECTION_REQUEST_ACCEPTED: Byte = 0x10
        const val NEW_INCOMING_CONNECTION: Byte = 0x13
        const val DISCONNECTION_NOTIFICATION: Byte = 0x15
        const val INCOMPATIBLE_PROTOCOL_VERSION: Byte = 0x19
        const val UNCONNECTED_PONG: Byte = 0x1C
        const val OPEN_CONNECTION_REQUEST_1: Byte = 0x05
        const val OPEN_CONNECTION_REPLY_1: Byte = 0x06
        const val OPEN_CONNECTION_REQUEST_2: Byte = 0x07
        const val OPEN_CONNECTION_REPLY_2: Byte = 0x08
    }
    
    // RakNetの特殊なシーケンス
    val UNCONNECTED_MESSAGE_SEQUENCE = byteArrayOf(
        0x00, 0xFF, 0xFF, 0x00, 0xFE, 0xFE, 0xFE, 0xFE, 
        0xFD, 0xFD, 0xFD, 0xFD, 0x12, 0x34, 0x56, 0x78
    )
} 