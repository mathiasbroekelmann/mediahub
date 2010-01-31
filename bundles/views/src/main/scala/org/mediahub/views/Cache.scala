/**
 * The cache module defines provides a cache which is able to track dependencies
 * upon cache value computations.
 *
 * Use Cache#get(CacheKey) to get the value of the given key.
 */
package org.mediahub.cache

import scala.util.DynamicVariable

/**
 *
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
}

/**
 * Identifies a cached value.
 */
trait CacheKey[A] {
  /**
   * Computes the value to cache
   */
  def compute: A
}

object Cache {
  
  private val currentContext = new DynamicVariable[Option[ComputationContext]](None)

  /**
   * Add a dependency to the current computation context.
   * If there is no such context this function will do nothing
   */
  def dependsOn(dependency: Dependency) {
    for(computation <- currentContext.value)
      computation dependsOn dependency
  }

  /**
   * compute f in with the given computation context.
   */
  private[cache] def withContext[A](cc: ComputationContext) (f: => A): A = {
    val parentizedContext = for(parent <- currentContext.value)
                              yield cc.withParent(parent)
    val context = parentizedContext.orElse(Some(cc))
    currentContext.withValue(context) (f)
  }
}

/**
 * Identifies a dependency for a computed value.
 */
abstract case class Dependency

/**
 * A computation context receives the dependency for a computed value.
 */
private[cache] trait ComputationContext {

  /**
   * Add a dependency to this computation context.
   */
  def dependsOn(dependency: Dependency)

  /**
   * create a new computation context wich notifies the given parent context
   * about the received dependencies.
   */
  def withParent(parent: ComputationContext): ComputationContext
}

import scala.actors.Actor
import Actor._

private[cache] class CacheImpl extends Cache {
  def get[A](key: CacheKey[A]): A = {
    worker ! Lookup(key, self)
    self.reactWithin(0) {
      case Result(value) => value
      case other => error(this + " has received unexpected message of " + other)
    }
  }

  def invalidate(dependency: Dependency) {
    dependencies ! Invalidated(dependency)
  }

  /**
   * lookup cache keys
   */
  case class Lookup[A](key: CacheKey[A], receiver: Actor)

  /**
   * flush cached value for the given key
   */
  case class Flush[A](key: CacheKey[A])

  /**
   * respond with computed value of a cache key
   */
  case class Result[A](value: A)

  /**
   * notify about invalidated dependency
   */
  case class Invalidated(dependency: Dependency)

  /**
   * notify about a dependency for a key
   */
  case class DependsOn[A](dependency: Dependency, key: CacheKey[A])

  case class Compute[A](f: () => A, receiver: A => Unit)

  private val worker: Actor = actor {
    var cachedValues = Map.empty[CacheKey[_], Any]

    loop {
      react {
        case Lookup(key, receiver) => lookup(key, { value: Any => receiver ! Result(value) })
        case Flush(key) => flush(key)
        case other => error(self + " has received unexpected message of " + other)
      }
    }

    def lookup[A](key: CacheKey[A], receiver: A => Unit) {
      val cachedValue = cachedValues.get(key)
      if(cachedValue.isDefined) {
        receiver(cachedValue.get.asInstanceOf[A])
      } else {
        def compute: A = key.compute
        def cachedReceiver(value: A) = {
          cachedValues += key -> value
          receiver(value.asInstanceOf[A])
        }
        computer ! Compute(compute _, cachedReceiver)
      }
    }

    def compute[A](key: CacheKey[A]): A = {
      val context = newComputationContext(key)
      val result = Cache.withContext(context) (key.compute)
      result
    }

    def flush(key: CacheKey[_]) {
      cachedValues -= key
      // TODO: optionally recompute the cache key
    }

    def newComputationContext[A](key: CacheKey[A]): ComputationContext = {
      new ComputationContextImpl(key, dependencies, None)
    }
  }

  /**
   * Helper actor to minimize the blocking time the computation of a cached value might take.
   */
  private val computer = actor {
    loop {
      react {
        case Compute(f, receiver) => receiver(f)
      }
    }
  }

  private val dependencies: Actor = actor {
    var dependencies = Map.empty[Dependency, Set[CacheKey[Any]]]

    loop {
      react {
        case DependsOn(dependency, key) => associate(dependency, key)
        case Invalidated(dependency) => invalidated(dependency)
        case other => error(self + " has received unexpected message of " + other)
      }
    }

    def associate(dependency: Dependency, key: CacheKey[Any]) {
      val keys = dependencies.get(dependency).getOrElse(Set[CacheKey[Any]]())
      dependencies += dependency -> (keys + key)
    }

    def invalidated(dependency: Dependency) {
      for(keys <- dependencies.get(dependency);
            key <- keys) {
        worker ! Flush(key)
      }
    }
  }
  
  class ComputationContextImpl[A](key: CacheKey[A],
                                  receiver: Actor,
                                  parent: Option[ComputationContext]) extends ComputationContext {

    def dependsOn(dependency: Dependency) {
      receiver ! DependsOn(dependency, key)
      for(someParent <- parent) {
        someParent.dependsOn(dependency)
      }
    }

    def withParent(parent: ComputationContext): ComputationContext = {
      new ComputationContextImpl(key, receiver, Some(parent))
    }
  }
}