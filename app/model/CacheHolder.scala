package model

import java.util.concurrent.{TimeUnit,Callable}
import com.google.common.cache.{CacheBuilder,Cache}

class CacheHolder[K <: AnyRef, V <: AnyRef](initialCapacity: Int = 3, timeToLive: (Long, TimeUnit) = (1L, TimeUnit.MINUTES)) {
 
  private val cache: Cache[K, V] = 
    CacheBuilder.newBuilder.initialCapacity(initialCapacity).expireAfterWrite(timeToLive._1, timeToLive._2).build[K,V]()
 
  def get(key: K): Option[V] = Option(cache.getIfPresent(key))
 
  def getOrElseUpdate(key: K, value: => V): V = {
    cache.get(key, new Callable[V] {
      def call(): V = value
    })
  }
 
  def put(key: K, value: V) {
    cache.put(key, value)
  }
 
  def remove(key: K) {
    cache.invalidate(key)
  }
 
  def clear() {
    cache.invalidateAll()
  }
 
  def size: Long = {
    cache.size()
  }
}