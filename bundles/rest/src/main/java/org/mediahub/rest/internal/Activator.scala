/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest.internal

import org.osgi.framework._
import org.mediahub.rest._

import org.restmodules.filter.FilterRegistrar

import org.osgi.util.tracker._

import scala.actors.Actor

import javax.ws.rs.core.Application

class Activator extends BundleActivator {

  def registry(ctx: BundleContext) = new ApplicationRegistry {
    def register(app: Application) = {
      new Registration {
        val registration = ctx.registerService(classOf[Application].getName, app, null)
        def unregister = registration.unregister
      }
    }
  }
  
  def tracker(ctx: BundleContext, collector: Actor) = new ServiceTrackerCustomizer {
    def addingService(reference: ServiceReference): AnyRef = {
      val service = ctx.getService(reference)
      collector ! Added(service)
      service
    }
    def modifiedService(reference: ServiceReference, service: AnyRef) {
    }

    def removedService(reference: ServiceReference, service: AnyRef) {
      collector ! Removed(service)
    }
  }

  private[this] var trackedServices = List.empty[ServiceTracker]
  private[this] var registration: Option[RestApplicationRegistration] = None

  def track[A](ctx: BundleContext, listener: ServiceTrackerCustomizer)(implicit clazz: ClassManifest[A]) = {
    new ServiceTracker(ctx, clazz.erasure.getName, listener)
  }

  def start(ctx: BundleContext) {
    registration = Some(new RestApplicationRegistration(registry(ctx)))
    implicit val serviceTracker = tracker(ctx, registration.get.collector)
    trackedServices = track[RestRegistrar](ctx, serviceTracker) :: track[FilterRegistrar](ctx, serviceTracker) :: Nil
    trackedServices.foreach(_.open)
  }

  def stop(ctx: BundleContext) {
    trackedServices.foreach(_.close)
    registration.foreach(_.close)
  }
}
