package org.mediahub.jersey.osgi.spi.container.internal

import org.mediahub.jersey.osgi.spi.container.servlet.OsgiContainer

import org.osgi.framework.{BundleActivator, BundleContext}

import com.google.inject.{Guice, Inject, AbstractModule, Module, Injector}

import org.ops4j.peaberry.Peaberry._
import org.ops4j.peaberry.Import
import org.ops4j.peaberry.util.AbstractWatcher
import org.ops4j.peaberry.util.TypeLiterals._

import org.osgi.service.http.HttpService

/**
 * @author Mathias Broekelmann
 *
 * @since 03.01.2010
 *
 */
class Activator extends BundleActivator {
    
    private[this] var module: Option[JerseyModule] = None
    private[this] var injector: Injector = _
    
    def start(context: BundleContext) {
        module = Some(new JerseyModule(context))
        injector = Guice.createInjector(osgiModule(context), module.get)
    }   
    
    def stop(context: BundleContext) {
        module foreach (_.stop)
    }
}

class JerseyModule(context: BundleContext) extends AbstractModule {
    
    def stop {
        cleanups foreach (_())
    }
    
    type Cleanup = ()=>Unit
    
    private[this] val cleanups = scala.collection.mutable.Seq[Cleanup]()
    
    def configure {
        def httpService = service(classOf[HttpService]).out(httpServiceWatcher).multiple
        bind(iterable(classOf[HttpService])).toProvider(httpService).asEagerSingleton()
    }
    
    def httpServiceWatcher = new AbstractWatcher[HttpService] {
        override def adding(service: Import[HttpService]): HttpService = {
            val httpService = service.get
            httpService.registerServlet("/", new OsgiContainer(context), null, null)
            cleanups :+ {
                if(service.available) {
                    httpService.unregister("/")
                    service.unget
                }
            }
            null
        }
    }
}
