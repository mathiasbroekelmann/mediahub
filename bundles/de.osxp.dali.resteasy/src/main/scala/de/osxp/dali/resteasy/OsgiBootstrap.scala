package de.osxp.dali.resteasy

import org.jboss.resteasy.plugins.server.servlet._

import com.google.inject._
import com.google.inject.name.Named

import com.google.inject.servlet._

import org.osgi.service.cm.ManagedService
import org.osgi.framework._

import javax.ws.rs.{Path}
import javax.ws.rs.ext.{Provider}

import scala.collection.JavaConversions._

import org.jboss.resteasy.spi._
import org.jboss.resteasy.core.Dispatcher

import Bundle._

import javax.servlet._

class OsgiRestEasyBootstrap @Inject() (@Named("resteasy") properties: Map[AnyRef, _]) extends ConfigurationBootstrap {
    def getScanningUrls: Array[java.net.URL] = Array.empty
    
    def getParameter(name: String): String = properties.get(name).map(_.asInstanceOf[String]).orNull
}

trait ResteasyIntegration {
    def onDeployment(deployment: ResteasyDeployment): Unit
}

/**
 * servlet service.

 * @author Mathias Broekelmann
 *
 * @since 02.01.2010
 *
 */
class ResteasyJaxRsDispacher @Inject() (context: BundleContext)  extends HttpServletDispatcher with ManagedService {
    
    private[this] var internalService: Option[ResteasyJaxRsService] = None
    private[this] var currentProps: Option[java.util.Dictionary[_, _]] = None
    
    private[this] val servletIntegration = new ResteasyIntegration {
        def onDeployment(deployment: ResteasyDeployment) {
              getServletContext.setAttribute(classOf[ResteasyProviderFactory].getName(), deployment.getProviderFactory)
              providerFactory = deployment.getProviderFactory
              getServletContext.setAttribute(classOf[Dispatcher].getName(), deployment.getDispatcher)
              dispatcher = deployment.getDispatcher
              getServletContext.setAttribute(classOf[Registry].getName(), deployment.getRegistry)
        }
    }
    
    def updated(props: java.util.Dictionary[_, _]) {
        currentProps = Some(props)
        internalService foreach (_.updated(props))
    }
    
    override def init(config: ServletConfig) {
        val service = new ResteasyJaxRsService(servletIntegration)
        val refs = context.getServiceReferences(null, null)
        context.addServiceListener(service.serviceListener)
        for(ref <- refs) {
            service.serviceRegistered(ref)
        }
        service.start
        internalService = Some(service)
        super.init(config)
    }
    
    override def destroy {
        internalService foreach (_.stop)
    }
}

/**
 * register this class a servlet context listener.
 * 
 * @author Mathias Broekelmann
 *
 * @since 02.01.2010
 *
 */
class ResteasyJaxRsService (integration: ResteasyIntegration) extends OsgiJaxRsService {

    private[this] var deployment: Option[ResteasyDeployment] = None 
    private[this] var serviceProps = Map[AnyRef, Any]() 

    val resourceRegistrar = scala.collection.mutable.Map[AnyRef, ResourceRegistrar]()
    val providerRegistrar = scala.collection.mutable.Map[AnyRef, ProviderRegistrar]()
    val registrations = scala.collection.mutable.Map[AnyRef, Registration]()

    /**
     * restart resteasy service
     */
    def restart {
        stop
        start
    }

    def start {
        if ((context.getBundle.getState & STARTING | ACTIVE) != 0) {
            synchronized {
                val config = new OsgiRestEasyBootstrap(serviceProps)
                val dep = config.createDeployment();
                deployment = Some(dep)
                dep.start();
                integration.onDeployment(dep)
            }
        }
    }

    def stop {
        synchronized {
            deployment.foreach(_.stop)
        }
    }

    def propertiesUpdated(props: Map[AnyRef, _]) {
        serviceProps = props
        restart
    }

    type ResourceRegistrar = {
        def register(registry: Registry): Registration
    }

    type ProviderRegistrar = {
        def register(registry: ResteasyProviderFactory): Registration
    }

    type Registration = {
        def unregister: Unit
    }
    
    def registerResource(service: AnyRef, props: Map[String, _]) {
        val registrar = new {
            val base = props.get("resource.base").map(_.asInstanceOf[String]).orNull
            def register(registry: Registry): Registration = {
                registry.addSingletonResource(service, base)
                new {
                    def unregister {
                        registry.removeRegistrations(service.getClass, base)
                    }
                }
            }
        }
        resourceRegistrar += service -> registrar
    }
    
    def unregisterResource(service: AnyRef) {
        resourceRegistrar.remove(service)
        registrations.remove(service).foreach(_.unregister)
    }
    
    def registerProvider(service: AnyRef, props: Map[String, _]) {
        val registrar = new {
            def register(registry: ResteasyProviderFactory): Registration = {
                registry.registerProviderInstance(service)
                new {
                    def unregister {
                        restart
                    }
                }
            }
        }
        providerRegistrar += service -> registrar
    }
    
    def unregisterProvider(service: AnyRef) {
        providerRegistrar.remove(service)
        registrations.remove(service).foreach(_.unregister)
    }
    
}

abstract class OsgiJaxRsService extends ManagedService {
    
    private[this] var currentProps: Option[java.util.Dictionary[_, _]] = None
    
    @Inject
    val context: BundleContext = context
    
    def updated(props: java.util.Dictionary[_, _]) {
        if(!currentProps.map(_.equals(props)).getOrElse(false)) {
            propertiesUpdated(Map.empty.withDefault(k => props.get(k)))
        }
    }
    
    def propertiesUpdated(props: Map[AnyRef, _])

    val serviceListener = new ServiceListener {
        def serviceChanged(event: ServiceEvent) {
            synchronized {
                if(event.getType == ServiceEvent.REGISTERED) {
                    serviceRegistered(event.getServiceReference)
                } else if(event.getType == ServiceEvent.UNREGISTERING) {
                    serviceUnregistered(event.getServiceReference)
                }
            }
        }
    }
    
    def properties(ref: ServiceReference): Map[String, AnyRef] = {
        Map.empty.withDefault(k => ref.getProperty(k))
    }
    
    def serviceRegistered(ref: ServiceReference) {
        val service = context.getService(ref)
        if(isResource(service)) {
            registerResource(service, properties(ref))
            used(ref) {
                unregisterResource(service)
                context.ungetService(ref)
                null
            }
        } else if (isProvider(service)) {
            registerProvider(service, properties(ref))
            used(ref) {
                unregisterProvider(service)
                context.ungetService(ref)
                null
            }
        } else {
            context.ungetService(ref)
        }
    }
    
    def registerResource(service: AnyRef, props: Map[String, _])
    
    def unregisterResource(service: AnyRef)
    
    def registerProvider(service: AnyRef, props: Map[String, _])
    
    def unregisterProvider(service: AnyRef)
    
    def isResource(service: AnyRef): Boolean = {
        service.getClass.isAnnotationPresent(classOf[Path])
    }
    
    def isProvider(service: AnyRef): Boolean = {
        service.getClass.isAnnotationPresent(classOf[Provider])
    }

    private[this] val usages = scala.collection.mutable.Map[ServiceReference, () => Any]()
    
    def used[A](ref: ServiceReference) (f: () => A): Unit = { 
        usages += ref -> f
    }
    
    def serviceUnregistered(ref: ServiceReference) {
        for(unregister <- unregistration(ref)) {
            unregister()
        }
    }

    def unregistration(ref: ServiceReference): Option[()=>Unit] = {
        usages.get(ref).map { unregister => 
            new (() => Unit) { 
                def apply {
                    usages.remove(ref)
                    unregister()
                }
            }
        }
    }
}
