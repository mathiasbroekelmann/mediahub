/**
 * The cache module defines provides a cache which is able to track dependencies
 * upon cache value computations.
 *
 * Use Cache#get(CacheKey) to get the value of the given key.
 * Use Cache#dependsOn(...) to define a dependency during cache value computation.
 */
package org.mediahub.cache

import scala.util.DynamicVariable

/**
 * The cache allows cached retrival of computed values.
 * It supports tracking dependencies during the computation of an value.
 */
trait Cache {

  /**
   * Get or compute the value for the given cache key.
   */
  def get[A](key: CacheKey[A]): A

  /**
   * Invalidate all cache keys whose values share the given dependency.
   */
  def invalidate(dependency: Dependency)

  /**
   * evict the given key and its value from the cache.
   */
  def evict[A](key: CacheKey[A])

  /**
   * enumerate all cache keys in the cache.
   */
  def enumerate[A](f: CacheKey[_] => A): Seq[A]
}

/**
 * Identifies a cached value.
 */
trait CacheKey[A] {

  /**
   * Computes the value of this key.
   * Use Cache#dependsOn(Dependency) to define the dependencies for the returned value.
   */
  def compute: A
}

/**
 * Companion object for defining cache dependencies.
 */
object Cache {

  /**
   * The current computation context which collects all dependencies during a computation.
   */
  private val currentComputationContext = new DynamicVariable[Option[ComputationContext]](None)

  /**
   * Add a dependency to the current computation context.
   * If there is no such context this function will do nothing
   */
  def dependsOn(dependency: Dependency) {
    for(computation <- currentComputationContext.value)
      computation dependsOn dependency
  }

  /**
   * Add a dependency on time.
   * The computed value will be valid until the current system time is equal or after the given milliseconds since 1. january 1970 0 oclock
   */
  def validUntil(millis: Long) {
    // TODO: implement time based dependency
    error("NYI")
  }

  /**
   * Add a dependency on time.
   * The computed value will be valid for the timespan defined by the given milliseconds.
   * If millis is equal or lower than zero the computed value will be invalidated immediately
   */
  def validFor(millis: Long) {
    // TODO: implement time based dependency
    error("NYI")
  }

  /**
   * Run the given function without any dependencies.
   * Dependencies which are defined by executing this function will be ignored.
   */
  def withoutDependencies[A](f: => A): A = {
    withContext(DisabledDependenciesComputationContext)(f)
  }

  def apply: Cache = new CacheImpl

  /**
   * compute f in with the given computation context.
   */
  private[cache] def withContext[A](cc: ComputationContext) (f: => A): A = {
    val parentizedContext = for(parent <- currentComputationContext.value)
                              yield cc.withParent(parent)
    val context = parentizedContext.orElse(Some(cc))
    currentComputationContext.withValue(context) (f)
  }

  /**
   * Get the current computation context.
   */
  def currentContext = currentComputationContext.value
}

/**
 * a computation context which ignores any dependencies.
 */
private[cache] object DisabledDependenciesComputationContext extends ComputationContext {
  def dependsOn(dependency: Dependency) {}

  private[cache] def withParent(parent: ComputationContext): ComputationContext = this
}

/**
 * Identifies a dependency for a computed value.
 */
trait Dependency

/**
 * A computation context receives the dependency for a computed value.
 */
trait ComputationContext {

  /**
   * Add a dependency to this computation context.
   */
  def dependsOn(dependency: Dependency)

  /**
   * create a new computation context wich notifies the given parent context
   * about the received dependencies.
   */
  private[cache] def withParent(parent: ComputationContext): ComputationContext
}

trait CacheListener {
  /**
   * Notify the listener about a hit in the cache.
   *
   * @param key the cache key
   * @param value the cached value
   */
  def hit[A](key: CacheKey[A]) {}

  /**
   * Notify the listener about a miss on the cache
   *
   * @param key the cache key
   */
  def miss[A](key: CacheKey[A]) {}

  /**
   * Notify the listener that the value of the given key is evicted from the cache.
   */
  def evict[A](key: CacheKey[A]) {}

  /**
   * Notify the listener about a new or updated value for a cache key.
   * 
   * @param key the cache key
   * @param value the cached value
   * @param dependencies the dependencies of the computed value
   * @param time the time needed to compute the value.
   */
  def push[A](key: CacheKey[A], value: A, dependencies: Iterable[Dependency], time: Long) {}

  /**
   * Notify the listener about a dependency invalidation.
   *
   * @param dependency the dependency that was invalidated
   */
  def invalidated(dependency: Dependency) {}
}

/**
 * a factory which creates a cache listener for a given cache
 */
trait CacheListenerFactory {

  /**
   * Create the cache listener instance for the given cache.
   * Any returned listener will be added to the cache.
   *
   * @param cache the cache to create the listener for
   *
   * @return Some(listener) if the factory creates a listener, otherwise None
   */
  def create(cache: Cache): Option[CacheListener]
}

import scala.actors.Actor
import Actor._

private[cache] class CacheImpl extends Cache {
  def get[A](key: CacheKey[A]): A = {

    /**
     * computes the value and pushes the result into the cache.
     */
    def computeAndPush: A = {
      def compute = {
        val start = System.currentTimeMillis
        (key.compute, System.currentTimeMillis - start)
      }
      val cc = new ComputationContextImpl()
      val (value, time) = Cache.withContext(cc)(compute)
      cache ! Push(key, cc.dependencies, time, value)
      value
    }

    cache ! Lookup(key, self)    
    val result = self.receive {
      case Hit(value) => {
        def found: A = value.asInstanceOf[A]
        found _
      }
      case Miss => computeAndPush _
    }
    // we need to execute the result outside of the blocking receive to avoid
    // dead locks while computing the value
    result()
  }

  def invalidate(dependency: Dependency) {
    cache ! Invalidated(dependency)
  }

  def evict[A](key: CacheKey[A]) {
    cache ! Evict(key)
  }

  def enumerate[A](f: CacheKey[_] => A): Seq[A] = {
    cache ! Enumerate(self)
    val keys = self.receive {
      case CacheKeys(keys) => keys
      case other => error(self + " has received unexpected message of " + other)
    }
    for(key <- keys) yield f(key)
  }

  def addListener(listener: CacheListener) {
    cache ! AddListener(listener)
  }

  def removeListener(listener: CacheListener) {
    cache ! RemoveListener(listener)
  }

  /**
   * lookup cache keys
   */
  case class Lookup[A](key: CacheKey[A], receiver: Actor)

  /**
   * respond that a given cache key was found in the cache
   */
  case class Hit[A](value: A)

  /**
   * respond that a given cache key was not found in the cache
   */
  case class Miss

  /**
   * push a computed value into the cache.
   */
  case class Push[A](key: CacheKey[A], dependencies: Iterable[Dependency], computationTime: Long, value: A)

  /**
   * notify about invalidated dependency
   */
  case class Invalidated(dependency: Dependency)

  /**
   * notify the cache to evict any value for the given cache key.
   */
  case class Evict[A](key: CacheKey[A])

  /**
   * notify the cache to enumerate all current cache keys.
   */
  case class Enumerate(receiver: Actor)

  /**
   * response upon Enumerate.
   */
  case class CacheKeys(keys: Seq[CacheKey[_]])

  case class AddListener(listener: CacheListener)

  case class RemoveListener(listener: CacheListener)

  private val cache: Actor = actor {
    var cachedValues = Map.empty[CacheKey[_], (Iterable[Dependency], Any)]
    var listeners = Set.empty[CacheListener]

    loop {
      react {
        case Lookup(key, receiver) => lookup(key, receiver)
        case Push(key, dependencies, time, value) => push(key, dependencies, time, value)
        case Invalidated(dependency) => invalidated(dependency)
        case Evict(key) => evict(key)
        case Enumerate(receiver) => receiver ! CacheKeys(cachedValues.keySet.toSeq)
        case AddListener(listener) => listeners += listener
        case RemoveListener(listener) => listeners -= listener
        case other => error(self + " has received unexpected message of " + other)
      }
    }

    def lookup[A](key: CacheKey[A], receiver: Actor) {
      cachedValues.get(key) match {
        case Some((dependencies, value)) => hit(key, value, receiver)
        case None => miss(key, receiver)
      }
    }

    def hit(key: CacheKey[_], value: Any, receiver: Actor) {
      receiver ! Hit(value)
      listeners foreach(_.hit(key))
    }

    def miss(key: CacheKey[_], receiver: Actor) {
      receiver ! Miss
      listeners foreach(_.miss(key))
    }

    def push[A](key: CacheKey[A], deps: Iterable[Dependency], time: Long, value: A) {
      cachedValues += (key -> (deps, value))
      listeners foreach(_.push(key, value, deps, time))
    }

    def invalidated(dependency: Dependency) {
      def predicate(key: CacheKey[_], value: (Iterable[Dependency], Any)): Boolean = {
        val (deps, _) = value
        deps.exists(_ == dependency)
      }

      for(entry <- cachedValues;
          if(entry._2._1.exists(_ == dependency))) {
        evict(entry._1)
      }
      listeners foreach(_.invalidated(dependency))
    }

    def evict[A](key: CacheKey[A]) {
      cachedValues -= key
      listeners foreach(_.evict(key))
    }
  }
}

private[cache] case class DependsOn(dependency: Dependency)

private[cache] class ComputationContextImpl[A](receiver: Option[Actor], parent: Option[ComputationContext]) extends ComputationContext {

  case class Lookup(receiver: Actor)
  case class Dependencies(dependencies: Set[Dependency])

  def this() = this(None, None)
  
  private[this] val worker = receiver.getOrElse {
    actor {
      var deps = Set.empty[Dependency]
      loop {
        react {
          case DependsOn(dependency) =>
            deps += dependency
          case Lookup(receiver) =>
            receiver ! Dependencies(deps)
          case other =>
            error(self + " has received unexpected message of " + other)
        }
      }
    }
  }

  def dependencies: Set[Dependency] = {
    worker ! Lookup(self)
    self.receive {
      case Dependencies(dependencies) => dependencies
      case other => error(self + " has received unexpected message of " + other)
    }
  }

  def dependsOn(dependency: Dependency) {
    worker ! DependsOn(dependency)
    for(someParent <- parent) {
      someParent.dependsOn(dependency)
    }
  }

  def withParent(parent: ComputationContext): ComputationContext = {
    new ComputationContextImpl(Some(worker), Some(parent))
  }
}
