package org.mediahub.rest

import com.google.inject.Provider

/**
 * The registrar is intended to be implemented by business code that wants do register jsr311 components.
 *
 * TODO: use the jsr 330 Provider interface.
 */
trait RestRegistrar {

  /**
   * called to actually register the resources and providers.
   */
  def register(registry: RestRegistry): Unit
}

/**
 * The rest registry is used to register jsr 311 provider and root resources.
 */
trait RestRegistry {
  /**
   * register a provider or root resource class. The class will be managed by the container.
   */
  def register[A<:AnyRef](implicit clazz: ClassManifest[A])

  /**
   * register a set of jsr311 classes.
   */
  def register(clazzes: Class[_]*)

  /**
   * register a provider or root resource class by defining a provider instance which is used to get an instance of it.
   */
  def register[A<:AnyRef](provider: Provider[A])(implicit clazz: ClassManifest[A])

  /**
   * register singletons.
   */
  def registerSingleton(singleton: AnyRef*)
}
