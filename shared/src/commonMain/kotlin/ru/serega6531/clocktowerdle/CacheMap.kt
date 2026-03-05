package ru.serega6531.clocktowerdle

interface CacheMap<K : Any, V : Any> {
    fun getOrPut(key: K, provider: () -> V): V
}

expect fun <K : Any, V : Any> newCacheMap(): CacheMap<K, V>
