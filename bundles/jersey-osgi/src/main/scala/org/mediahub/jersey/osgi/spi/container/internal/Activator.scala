package org.mediahub.jersey.osgi.spi.container.internal

import org.mediahub.jersey.osgi.spi.container.servlet.OsgiContainer

import org.mediahub.jersey.osgi.spi.container.guice.JerseyModule

import org.osgi.framework.{BundleActivator, BundleContext}

import com.google.inject.{Guice, Inject, AbstractModule, Module, Injector, Scopes, Key, Provides, TypeLiteral}
import com.google.inject.name.{Named, Names}

import org.ops4j.peaberry.Peaberry._
import org.ops4j.peaberry.Import
import org.ops4j.peaberry.util.AbstractWatcher
import org.ops4j.peaberry.ServiceWatcher
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