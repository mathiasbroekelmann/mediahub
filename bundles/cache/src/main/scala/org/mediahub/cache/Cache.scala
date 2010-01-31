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
   * Computes the value of this key
   */
  def compute: A
}

object Cache {
  
  private val current = new DynamicVariable[Option[ComputationContext]](None)

  /**
   * Add a dependency to the current computation context.
   * If there is no such context this function will do nothing
   */
  def dependsOn(dependency: Dependency) {
    for(computation <- current.value)
      computation dependsOn dependency
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
    val parentizedContext = for(parent <- current.value)
                              yield cc.withParent(parent)
    val context = parentizedContext.orElse(Some(cc))
    current.withValue(context) (f)
  }

  def currentContext = current.value
}


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

import scala.actors.Actor
import Actor._

private[cache] class CacheImpl extends Cache {
  def get[A](key: CacheKey[A]): A = {

    /**
     * computes the value and pushes the result into the cache.
     */
    def computeAndPush: A = {
      val cc = new ComputationContextImpl()
      val value = Cache.withContext(cc)(key.compute)
      cache ! Push(key, cc.dependencies, value)
      value
    }

    cache ! Lookup(key, self)    
    val result = self.receive {
      case Found(value) => {
        def found: A = value.asInstanceOf[A]
        found _
      }
      case NotFound => computeAndPush _
    }
    // we need to execute the result outside of the blocking receive to avoid
    // dead locks while computing the value
    result()
  }

  def invalidate(dependency: Dependency) {
    cache ! Invalidated(dependency)
  }

  /**
   * lookup cache keys
   */
  case class Lookup[A](key: CacheKey[A], receiver: Actor)

  /**
   * respond that a given cache key was found in the cache
   */
  case class Found[A](value: A)

  /**
   * respond that a given cache key was not found in the cache
   * Use the provided computation context to compute the value
   */
  case class NotFound(cc: ComputationContext)

  /**
   * push a computed value into the cache.
   */
  case class Push[A](key: CacheKey[A], dependencies: Iterable[Dependency], value: A)

  /**
   * notify about invalidated dependency
   */
  case class Invalidated(dependency: Dependency)

  private val cache: Actor = actor {
    var cachedValues = Map.empty[CacheKey[_], (Iterable[Dependency], Any)]

    loop {
      react {
        case Lookup(key, receiver) => lookup(key, receiver)
        case Push(key, dependencies, value) => push(key, dependencies, value)
        case Invalidated(dependency) => invalidated(dependency)
        case other => error(self + " has received unexpected message of " + other)
      }
    }

    def lookup[A](key: CacheKey[A], receiver: Actor) {
      cachedValues.get(key) match {
        case Some((dependencies, value)) => receiver ! Found(value)
        case None => receiver ! NotFound
      }
    }

    def push[A](key: CacheKey[A], deps: Iterable[Dependency], value: A) {
      cachedValues += (key -> (deps, value))
    }

    def invalidated(dependency: Dependency) {
      def predicate(key: CacheKey[_], value: (Iterable[Dependency], Any)): Boolean = {
        val (deps, _) = value
        deps.exists(_ == dependency)
      }

      for(entry <- cachedValues;
          if(entry._2._1.exists(_ == dependency))) {
        cachedValues -= entry._1
      }
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
