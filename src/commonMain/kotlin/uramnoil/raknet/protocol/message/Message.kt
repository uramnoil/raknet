package uramnoil.raknet.protocol.message

import kotlinx.io.Source

/**
 * RakNetメッセージの基本インターフェース
 */
interface Message {
    /**
     * メッセージをバイナリ形式にシリアライズする
     */
    fun encode(): Source
    
    /**
     * バイナリデータからメッセージをデシリアライズする
     */
    fun decode(packet: Source): Boolean
    
    /**
     * メッセージのIDを取得する
     */
    fun getID(): Byte
} 