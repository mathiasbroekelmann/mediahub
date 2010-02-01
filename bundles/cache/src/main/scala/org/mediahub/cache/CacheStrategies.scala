/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.cache

import scala.actors.Actor._

import org.mediahub.util.Types._

class LimitingCacheStrategy(defaultLimit: Int) extends CacheListener {

  assert(defaultLimit >= 0, "default limit must be greater or equal zero: " + defaultLimit)

  /**
   * Notify the listener about a hit in the cache.
   *
   * @param key the cache key
   * @param value the cached value
   */
  override def hit[A](key: CacheKey[A], value: A) {}

  /**
   * Notify the listener about a miss on the cache
   *
   * @param key the cache key
   */
  override def miss[A](key: CacheKey[A]) {}

  /**
   * Notify the listener that the value of the given key is evicted from the cache.
   */
  override def evict[A](key: CacheKey[A]) {}

  /**
   * Notify the listener about a new or updated value for a cache key.
   *
   * @param key the cache key
   * @param value the cached value
   * @param time the time needed to compute the value. This value can be used to optimize the values stored in the cache
   * @param control allows controlling the cache like flushing cache keys if needed.
   */
  override def push[A](key: CacheKey[A], value: A, time: Int, control: CacheControl) {}

  /**
   * Provide a rating which is used to determine if the cached value for the key should be evicted if the limit for the cache key is reached.
   * A higher value increases the chance that the cached value remains in the cache.
   *
   * @param metrics the metrics for a cache key
   * @return a rating which is used to determine if the cached value for the key should be evicted if the limit for the cache key is reached.
   */
  protected def rate(metrics: CacheKeyMetrics): Long = {
    import metrics._
    // TODO: this function may need fine tuning.
    (hits + (misses * 2)) * (stats.average + 1) - ageInMinutes.getOrElse(0L)
  }

  def defineLimitFor(keyType: Class[CacheKey[_]], limit: Int) {
    assert(limit >= 0, "invalid cache limit definition for key type " + keyType + ": " + limit)
    collector ! Limit(keyType, limit)
  }

  case class Push[A](key: CacheKey[A], time: Int, control: CacheControl)
  case class Limit(clazz: Class[_], limit: Int)

  private[this] val collector = actor {

    /**
     * ensures that there will always be an initial cache key metrics instance for a give cache key.
     */
    def defaultKeyTypeMetrics(keyType: Class[CacheKey[Any]]): Map[CacheKey[Any], CacheKeyMetrics] = {
      Map.empty[CacheKey[Any], CacheKeyMetrics].withDefault(key => CacheKeyMetrics(key))
    }

    /**
     * store the metrics for each cache key type and cache key
     */
    var keyTypeMetrics = Map.empty[Class[CacheKey[Any]], Map[CacheKey[Any], CacheKeyMetrics]].withDefault(defaultKeyTypeMetrics)

    /**
     * store the limits for each cache key type.
     */
    var limits = Map.empty[Class[_], Int]

    loop {
      react {
        case Push(key, time, control) => pushInternal(key, time, control)
        case Limit(clazz, limit) => limitInternal(clazz, limit)
        case other => error(self + " has received unexpected message of " + other)
      }
    }

    def update(cacheKey: CacheKey[Any], 
               keyTypeMetrics: Map[CacheKey[Any], CacheKeyMetrics])
                (f: CacheKeyMetrics => CacheKeyMetrics): Map[CacheKey[Any], CacheKeyMetrics] = {
      val keyType = cacheKey.getClass.asInstanceOf[Class[CacheKey[Any]]]
      keyTypeMetrics + (cacheKey -> f(keyTypeMetrics(cacheKey)))
    }

    def pushInternal(cacheKey: CacheKey[Any], time: Int, control: CacheControl) {
      val keyType = cacheKey.getClass.asInstanceOf[Class[CacheKey[Any]]]
      val updatedMetrics = update(cacheKey, keyTypeMetrics(keyType)) {_.computed(time)}
      evictOverflowingKeys(keyType, updatedMetrics, control)
      keyTypeMetrics += keyType -> updatedMetrics
    }

    def limitInternal(keyType: Class[_], limit: Int) {
      limits += keyType -> limit
    }

    def evictOverflowingKeys(keyType: Class[_], metrics: Map[CacheKey[Any], CacheKeyMetrics], control: CacheControl) {
      // first check if limit is exceded at all

      /**
       * resolve limit for a given key class.
       */
      def resolveLimit(limit: Option[Int], clazz: Class[_]): Option[Int] = {
        limit.orElse(limits.get(clazz))
      }

      /**
       * evict the cache keys by using the given limit.
       */
      def evict(limit: Int) {
        // sort the metrics by their hits ascending
        val sortedByHits = metrics.toSeq.sortBy(x => rate(x._2))
        // evict all cache keys with lowest hit
        for(entry <- sortedByHits.take(sortedByHits.size - limit)) {
          control.evict(entry._1)
        }
      }
      
      val none: Option[Int] = None
      val limit = typesOf(keyType).foldLeft(none)(resolveLimit)
                                  .getOrElse(defaultLimit)
      if(metrics.size > limit) evict(limit)
    }
  }
}

/**
 * stores cache metrics for a cache key
 *
 * @param key the cache key of the metrics
 * @param computationTime the times in milliseconds it took to compute the value of the cache.
 *                        Each entry in the list is the time it actually took to compute a value
 * @param hits the number of hits for the cache key. 0 if there are no hits
 * @param lastHitTime the time in milliseconds when the last hit happened
 * @param misses the number of misses for the cache key. 0 if there are not misses.
 * @param computations how many times a value was computed for the key
 * @param maxTimes define the max count of computations times that are stored in memory to calculate the computation stats.
 *
 * TODO: it is currently possible that hits and misses counters are overflowing.
 */
case class CacheKeyMetrics(key: CacheKey[_],
                           computationTimes: Seq[Int],
                           hits: Long,
                           misses: Long,
                           accessTime: Option[Long],
                           computations: Long,
                           maxTimes: Int) {

  override def toString() = format("key: %s, hits: %d, access time: %s, misses: %d, computations: %d, stats: %s",
                                   key, hits, accessTime.map(new java.util.Date(_).toString).getOrElse("-"),
                                   misses, computations, stats)

  /**
   * Create a new instance with an added hit on the cache key.
   */
  def hit = CacheKeyMetrics(key, computationTimes, hits + 1, misses, Some(now), computations, maxTimes)

  /**
   * Create a new instance with an added miss on the cache key.
   */
  def miss = CacheKeyMetrics(key, computationTimes, hits, misses + 1, Some(now), computations, maxTimes)

  private[this] def now: Long = System.currentTimeMillis

  /**
   * Create a new instance with the computation time for the value of the cache key.
   *
   * @param time the computation time of a value for the cache key
   */
  def computed(time: Int)  = {
    assert(time >= 0, "time must be greater or equal to 0. was: " + time)
    val times = time +: computationTimes
    // keep only the last maxTimes values of computation times to reduce memory consumption
    CacheKeyMetrics(key, times.slice(0, maxTimes), hits, misses, accessTime, computations + 1, maxTimes)
  }

  /**
   * define how many computation times stats should be stored in memory to calculate the stats for this key.
   */
  def withMaxComputationTimes(count: Int) = {
    assert(count >= 0)
    CacheKeyMetrics(key, computationTimes, hits, misses, accessTime, computations, count)
  }

  /**
   * returns the age of the last access in minutes.
   */
  def ageInMinutes: Option[Long] = {
    accessTime.map(time => (System.currentTimeMillis - time) / 1000 / 60)
  }

  /**
   * return the computation stats for the key.
   */
  def stats = new {
    /**
     * average computation time.
     */
    lazy val average: Long = computationTimes.foldLeft(0)(_ + _) / computationTimes.size

    /**
     * min computation time.
     */
    lazy val min: Long = computationTimes.foldLeft(Long.MaxValue)(Math.min(_, _))

    /**
     * max computation time.
     */
    lazy val max: Long = computationTimes.foldLeft(0)(Math.max(_, _))

    /**
     * string representation of the stats.
     */
    override def toString() = format("average: %d, min: %d, max: %d", average, min, max)
  }

}

/**
 * companion object for cache metrics.
 */
object CacheKeyMetrics {
  
  /**
   * Create a new instance of cache metrics for the given cache key.
   * It will strore the last 10 computation times by default.
   */
  def apply(key: CacheKey[_]): CacheKeyMetrics = CacheKeyMetrics(key, Seq.empty, 0, 0, None, 0, 10)
}

