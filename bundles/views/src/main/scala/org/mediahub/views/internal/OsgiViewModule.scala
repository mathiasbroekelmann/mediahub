/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views.internal

import com.google.inject._

import org.mediahub.views._
import org.mediahub.cache.Cache

import org.ops4j.peaberry._
import Peaberry._
import org.ops4j.peaberry.util._
import Attributes._
import TypeLiterals._

import scala.actors.Actor._

import org.osgi.framework._
import org.osgi.util.tracker._

/**
 * guice module to define the exported/imported services.
 */
class OsgiViewModule extends AbstractModule {

  @Provides
  @Singleton
  def viewRegistry(somecache: Provider[Cache]): ViewRegistry = {
    new ViewRegistryImpl() {
      override val cache = Some(somecache.get)
    }
  }

  @Provides
  def viewRenderer(registry: ViewRegistry): CustomizableViewRenderer =
    new ViewRendererImpl(registry)

  def configure {
    bind(classOf[Cache]).toProvider(service(classOf[Cache]).single)
    bind(classOf[ViewModuleTracker]).asEagerSingleton

    val exportedInterfaces = objectClass(classOf[CustomizableViewRenderer],
                                         classOf[ViewRenderer])
    bind(export(classOf[CustomizableViewRenderer])).toProvider(service(classOf[CustomizableViewRenderer])
                                                               .attributes(exportedInterfaces)
                                                               .export())
  }
}

/**
 * tracks view modules and register the views in the given registry.
 */
class ViewModuleTracker @Inject() (bc: BundleContext, viewRegistry: ViewRegistry) {

  def viewModuleListener = new ServiceTrackerCustomizer {
    def addingService(reference: ServiceReference) = {
      val viewModule = bc.getService(reference).asInstanceOf[ViewModule]
      val registry = new TrackingViewRegistry(viewRegistry)
      val binder = new ViewBinderImpl(registry)
      viewModule.configure(binder)
      registry
    }

    def modifiedService(reference: ServiceReference, service: AnyRef) {}

    def removedService(reference: ServiceReference, service: AnyRef) {
      val registry = service.asInstanceOf[TrackingViewRegistry]
      registry.flush
      bc.ungetService(reference)
    }
  }

  val tracker = new ServiceTracker(bc, classOf[ViewModule].getName, viewModuleListener)
  tracker.open
}

/**
 * helper class to register the views of a single view module.
 * We need this to unregister (#flush) the view bindings if the view module is gone.
 */
class TrackingViewRegistry(delegate: ViewRegistry) extends ViewRegistry {

  case class Added(registration: ViewBindingRegistration)
  case class Flush

  val registrations = actor {
    var bindingRegistrations = Seq.empty[ViewBindingRegistration]

    loop {
      react {
        case Added(reg) => bindingRegistrations :+= reg
        case Flush => {
            bindingRegistrations foreach (_.unregister)
            bindingRegistrations = Seq.empty
          }
      }
    }
  }

  def flush {
    registrations ! Flush
  }
  
  /**
   * register a view binding.
   */
  def register[A](binding: ClassifiedBinding[A]): ViewBindingRegistration = {
    val registration = delegate.register(binding)
    registrations ! Added(registration)
    registration
  }

  /**
   * Resolve view bindings for a view classifier and a type.
   */
  def resolve[A, B](classifier: ViewClassifier[B])(implicit clazz: ClassManifest[A]): Seq[ViewBinding[A, B]] = {
    delegate.resolve(classifier)
  }

  /**
   * Resolve view bindings for a parameterized view classifier and a type.
   */
  def resolve[A, B, C](classifier: ParamViewClassifier[C, B])(implicit clazz: ClassManifest[A]): Seq[ParameterViewBinding[A, B, C]] = {
    delegate.resolve(classifier)
  }
}