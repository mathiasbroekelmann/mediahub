/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest.internal

import org.restmodules._
import org.restmodules.ioc._
import org.restmodules.filter._

import org.mediahub.rest._

import scala.collection.JavaConversions._

import scala.actors.Actor._
import scala.actors.Actor
import scala.actors.Debug._

import javax.ws.rs.core.Application
import javax.ws.rs.Path

import org.osgi.framework._

trait ApplicationRegistry {
  def register(app: Application): Registration
}

trait Registration {
  def unregister
}

case class Added(registrar: AnyRef)
case class Removed(registrar: AnyRef)

class RestApplicationRegistration(appRegistry: ApplicationRegistry) {

  assert(appRegistry != null)

  case class Update(registrars: Seq[_])
  case class Publish(application: Application)
  case class Unpublish
  case class Exit

  def close {
    self.trapExit = true
    self.link(collector)
    self.link(updater)
    self.link(publisher)
    collector ! Exit
    self.receive { case scala.actors.Exit(from, message) => }
    updater ! Exit
    self.receive { case scala.actors.Exit(from, message) => }
    publisher ! Exit
    self.receive { case scala.actors.Exit(from, message) => }
  }

  /**
   * get all add/remove events and notifies the updater
   */
  val collector: Actor = actor {

    println("starting collector")

//    updater.link(collector)
//    publisher.link(collector)

    var registrars = Seq.empty[AnyRef]

    def add(registrar: AnyRef) {
      registrars :+= registrar
      update
    }

    def remove(registrar: AnyRef) {
      registrars = registrars.filter(_ != registrar)
      update
    }

    def update {
      updater ! Update(registrars)
    }

    loop {
      receive {
        case Added(registrar) => add(registrar)
        case Removed(registrar) => remove(registrar)
        case Exit => {
            println("exiting collector")
            exit()
          }
        case _ =>
      }
    }
  }

  /**
   * the updater actually manages the rest application by registering services from all provided registrars.
   */
  private val updater = actor {

    println("starting updater")

    loop {
      receive {
        case Update(registrars) => {
            // check the mail box size and only update registrars if no more elements are in the queue
            if(mailboxSize == 0) {
              update(registrars)
            }
            // wait some time to see if there are more update events comming in
            // TODO: find a good value for sleep time.
            Thread.sleep(500)
          }
        case Exit => {
            println("exiting updater")
            exit
        }
        case _ =>
      }
    }

    def update(registrars: Seq[_]) {
      val reg = registry
      registrars foreach {
        _ match {
          case registrar: RestRegistrar => registrar.register(reg)
          case _ =>
        }
      }
      val app = new RestApplication {
        override val classes = reg.classes
        override val singletons = reg.singletons
        override val filterRegistrars = registrars.filter(_.isInstanceOf[FilterRegistrar])
        .map(_.asInstanceOf[FilterRegistrar])
        // TODO: configure alias
        override val alias = Some("mediahub")
        override val provider = new ProviderFactory {
          def apply[A](clazz: Class[A]) = reg.providers
          .get(clazz)
          .map(_.asInstanceOf[Provider[A]])
        }
      }
      if(hasRootResource(app)) {
        publisher ! Publish(app)
      } else {
        publisher ! Unpublish
      }

      def hasRootResource(app: Application) = {

        def hasPathAnnotation(clazz: Class[_]) = clazz.getAnnotation(classOf[Path]) != null

        val rootResourceInClasses = app.getClasses.find(hasPathAnnotation(_))
        val rootResourceInSingletons = app.getSingletons.find(s => hasPathAnnotation(s.getClass))

        rootResourceInClasses.isDefined || rootResourceInSingletons.isDefined
      }
    }
  }
  
  /**
   * the publisher register the application in the bundle context
   */
  private val publisher = actor {

    println("starting publisher")
    var registration: Option[Registration] = None
    
    loop {
      react {
        // TODO: remove any published application and publish the new application
        case Publish(app) => {
            for (reg <- registration) reg.unregister
            registration = Some(appRegistry.register(app))
          }
        case Unpublish => {
            for (reg <- registration) reg.unregister
            registration = None
          }
        case Exit => {
            for (reg <- registration) reg.unregister
            println("exiting publisher")
            exit
        }
        case _ =>
      }
    }
  }

  /**
   * the registry is used to collect the application service and classes stuff.
   */
  private def registry = new RestRegistry {

    var classes = Seq.empty[Class[_]]
    var providers = Map.empty[Class[_], Provider[_]]
    var singletons = Seq.empty[AnyRef]

    /**
     * register a provider or root resource class. The class will be managed by the container.
     */
    def register[A<:AnyRef](implicit clazz: ClassManifest[A]) {
      register(clazz.erasure)
    }

    /**
     * register a set of jsr311 classes.
     */
    def register(clazzes: Class[_]*) {
      classes ++= clazzes
    }

    /**
     * register a provider or root resource class by defining a provider instance which is used to get an instance of it.
     */
    def register[A<:AnyRef](provider: com.google.inject.Provider[A])(implicit clazz: ClassManifest[A]) {
      register(clazz)
      providers += clazz.erasure -> new Provider[A] {
        def get = provider.get
      }
    }

    /**
     * register singletons.
     */
    def registerSingleton(singleton: AnyRef*) {
      singletons ++= singleton
    }
  }
}

trait ProviderFactory {
  def apply[A](clazz: Class[A]): Option[Provider[A]]
}

class RestApplication extends RestmodulesApplication {

  val classes: Seq[Class[_]] = Seq.empty
  val singletons: Seq[AnyRef] = Seq.empty
  val provider: ProviderFactory = new ProviderFactory { def apply[A](clazz: Class[A]) = None }
  val filterRegistrars: Seq[FilterRegistrar] = Seq.empty
  val alias: Option[String] = None

  override def getClasses: java.util.Set[Class[_]] = scala.collection.mutable.Set.empty ++ classes

  override def getSingletons: java.util.Set[Object] = scala.collection.mutable.Set.empty ++ singletons

  override def registerFilters(registry: FilterRegistry) {
    filterRegistrars foreach {_.registerFilters(registry)}
  }

  override def getProvider[A<:Any](clazz: Class[A]): Provider[A]  = provider(clazz).orNull

  override def getAlias = alias.orNull
}
