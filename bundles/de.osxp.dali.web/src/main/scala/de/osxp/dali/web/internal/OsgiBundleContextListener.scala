package de.osxp.dali.web.internal


import com.google.inject.{Guice, AbstractModule, Provider}
import com.google.inject.Injector
import com.google.inject.servlet.GuiceServletContextListener

import org.osgi.framework.BundleContext

class OsgiBundleContextListener extends GuiceServletContextListener {
    
    def bundleModule = new AbstractModule {
        def configure {
            bind(classOf[BundleContext]).toProvider(new Provider[BundleContext] {
                def get = OsgiWebModule.bundlecontext.get
            })
        }
    }

    override def getInjector: Injector = {
        return Guice.createInjector(bundleModule, new de.osxp.dali.resteasy.WebModule())
    }
}
