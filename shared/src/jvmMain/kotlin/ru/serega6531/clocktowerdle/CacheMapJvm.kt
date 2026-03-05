package ru.serega6531.clocktowerdle

import java.util.concurrent.ConcurrentHashMap

private class ConcurrentCacheMap<K : Any, V : Any> : CacheMap<K, V> {
    private val map = ConcurrentHashMap<K, V>()

    override fun getOrPut(key: K, provider: () -> V): V {
        return map.computeIfAbsent(key) { provider() }
    }
}

actual fun <K : Any, V : Any> newCacheMap(): CacheMap<K, V> = ConcurrentCacheMap()
