package ru.serega6531.clocktowerdle

private class JsCacheMap<K : Any, V : Any> : CacheMap<K, V> {
    private val map = mutableMapOf<K, V>()

    override fun getOrPut(key: K, provider: () -> V): V {
        return map.getOrPut(key) { provider() }
    }
}

actual fun <K : Any, V : Any> newCacheMap(): CacheMap<K, V> = JsCacheMap()
