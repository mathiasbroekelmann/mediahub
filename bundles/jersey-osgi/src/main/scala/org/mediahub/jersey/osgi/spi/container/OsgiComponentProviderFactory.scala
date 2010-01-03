package org.mediahub.jersey.osgi.spi.container

import com.sun.jersey.core.spi.component.ioc._
import com.sun.jersey.core.spi.component.ComponentContext
import com.sun.jersey.api.core.ResourceConfig
import ResourceConfig.{isProviderClass, isRootResourceClass}
import com.sun.jersey.spi.container.{ContainerNotifier, ContainerListener}

import javax.ws.rs.core.Application
import com.sun.jersey.core.spi.component.ComponentScope

import org.osgi.framework.{BundleContext, ServiceListener, ServiceEvent, ServiceReference}

/**
 * Tracks for osgi services and register provider and root resources in the provided resource config instance.
 * 
 * @author Mathias Broekelmann
 *
 * @since 03.01.2010
 *
 */
class OsgiComponentProviderFactory (val config: ResourceConfig, val bundleContext: BundleContext) extends IoCComponentProviderFactory {

    private[this] val containerListener = scala.collection.mutable.Seq[ContainerListener]()
    private[this] val usedServices = scala.collection.mutable.Seq[ServiceReference]()

    def containerNotifier = new ContainerNotifier {
        def addListener(listener: ContainerListener) {
            containerListener :+ listener
        }
    }

    def reload {
        containerListener foreach (_.onReload)
    }

    def isUsableService(service: AnyRef): Boolean = {
        val clazz = service.getClass
        isProviderClass(clazz) || isRootResourceClass(clazz) || service.isInstanceOf[Application]
    }

    def serviceListener = new ServiceListener {
        def serviceChanged(event: ServiceEvent) {
            val ref = event.getServiceReference
            if(event.getType == ServiceEvent.UNREGISTERING && usedServices.contains(ref)) {
                bundleContext.ungetService(ref)
                reload
            } else {
                val service = bundleContext.getService(ref)
                if(isUsableService(service)) {
                    reload
                } else {
                    bundleContext.ungetService(ref)
                }
            }
        }
    }

    trait ServiceRegistration {
        def register: Unit
    }

    class ApplicationRegistration(val app: Application) extends ServiceRegistration {
        def register {
            config.add(app)
        }
    }

    class SingletonRegistration(val singleton: AnyRef) extends ServiceRegistration {
        def register {
            config.getSingletons.add(singleton)
        }
    }

    def usableServiceRegistration(ref: ServiceReference): Option[ServiceRegistration] = {
        bundleContext.getService(ref) match {
            case app: Application => Some(new ApplicationRegistration(app))
            case Singleton(provider) => Some(new SingletonRegistration(provider))
            case other => {
                bundleContext.ungetService(ref)
                None
            }
        }
    }

    def registerServices {
        for(ref <- bundleContext.getServiceReferences(null, null);
            serviceReg <- usableServiceRegistration(ref)) {
            usedServices :+ ref
            serviceReg.register
        }
    }

    config.getProperties.put(ResourceConfig.PROPERTY_CONTAINER_NOTIFIER, containerNotifier)
    bundleContext.addServiceListener(serviceListener)
    registerServices

    def getComponentProvider(clazz: Class[_]): IoCComponentProvider = {
        getComponentProvider(null, clazz)
    }

    def getComponentProvider(cc: ComponentContext, clazz: Class[_]): IoCComponentProvider = {
        val reference = Option(bundleContext.getServiceReference(clazz.getName))
        reference.map(new OsgiManagedComponentProvider(bundleContext, _))
                 .orNull
    }
}

/**
 * extractor for singleton instances which are provider or root resources.
 */
object Singleton {
    def unapply[A<:AnyRef](obj: A) = {
        val clazz = obj.getClass
        if(isProviderClass(clazz) || isRootResourceClass(clazz)) Some(obj) else None
    }
}

class OsgiManagedComponentProvider(context: BundleContext, reference: ServiceReference) 
    extends IoCManagedComponentProvider {

        def getInjectableInstance(obj: AnyRef) = obj

        def getInstance = context.getService(reference)
        
        def getScope = ComponentScope.Undefined
}