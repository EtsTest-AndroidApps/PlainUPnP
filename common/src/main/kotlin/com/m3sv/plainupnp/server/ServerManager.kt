package com.m3sv.plainupnp.server

interface ServerManager {
    suspend fun start()
    suspend fun resume()
    suspend fun pause()
    suspend fun shutdown()
}
