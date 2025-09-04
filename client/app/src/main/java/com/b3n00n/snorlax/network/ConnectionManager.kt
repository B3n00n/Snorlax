package com.b3n00n.snorlax.network

interface ConnectionManager {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    fun sendData(data: ByteArray)
    fun setConnectionListener(listener: ConnectionListener)

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: ByteArray)
        fun onError(e: Exception)
    }
}
