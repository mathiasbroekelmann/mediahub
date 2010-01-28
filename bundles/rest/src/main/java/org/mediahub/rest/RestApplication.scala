/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest

import org.restmodules._
import org.restmodules.ioc._
import org.restmodules.filter._

import scala.collection.JavaConversions._

import scala.actors.Actor._
import scala.actors.Actor

import javax.ws.rs.core.Application

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

  /**
   * get all add/remove events and notifies the updater
   */
  val collector = actor {

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
      }
    }
  }

  /**
   * the updater actually manages the rest application by registering services from all provided registrars.
   */
  private val updater = actor {
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
        override val alias = Some("/")
        override val provider = new ProviderFactory {
          def apply[A](clazz: Class[A]) = reg.providers
                                             .get(clazz)
                                             .map(_.asInstanceOf[Provider[A]])
        }
      }
      publisher ! Publish(app)
    }
  }

  /**
   * the publisher register the application in the bundle context
   */
  private val publisher = actor {

    var registration: Option[Registration] = None

    loop {
      react {
        // TODO: remove any published application and publish the new application
        case Publish(app) => {
            for (reg <- registration) reg.unregister
            registration = Some(appRegistry.register(app))
        }
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
