package org.mediahub.web.scopes.internal

import com.google.inject.{AbstractModule, Provider, Provides, Key, Scope => GuiceScope, Inject, TypeLiteral, Singleton}
import com.google.inject.name.{Named, Names}
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.Peaberry._
import org.ops4j.peaberry.Import
import org.ops4j.peaberry.util.AbstractWatcher
import org.ops4j.peaberry.ServiceWatcher
import org.ops4j.peaberry.util.TypeLiterals._

import javax.servlet._
import javax.servlet.http._

import scala.collection.JavaConversions._
import scala.collection._

import org.osgi.framework.BundleContext

import org.osgi.service.http.{HttpContext}

import org.ops4j.pax.web.service.WebContainer

import org.springframework.beans.factory.config.{Scope => SpringScope}
import org.springframework.beans.factory.ObjectFactory

/**
 * Exposes the scope services in the osgi context. These are currently guice scope and spring scopes.
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
class OsgiWebModule extends AbstractModule {
    
    private val serviceWatcherOfWebContainerKey = Key.get(new TypeLiteral[ServiceWatcher[WebContainer]]() {})
    private val serviceWatcherOfHttpContextKey = Key.get(new TypeLiteral[ServiceWatcher[HttpContext]]() {})

    var httpContext: Option[HttpContext] = None
    
    var webContainer: Option[WebContainer] = None
    
    val scopeFilter: Filter = new GuiceContextFilter
    
    def updateFilter(container: Option[WebContainer], context: Option[HttpContext]) {
        synchronized {
            for(ctnr <- webContainer) {
                ctnr.unregisterFilter(scopeFilter)
            }
            httpContext = context
            webContainer = container
            for(ctnr <- webContainer) {
                ctnr.registerFilter(scopeFilter, Array("/*"), null, null, httpContext.orNull)
            }
        }
    }
    
    def httpContextWatcher = new AbstractWatcher[HttpContext] {
        override def adding(service: Import[HttpContext]): HttpContext = {
            val context = service.get
            updateFilter(webContainer, Some(context))
            context
        }

        override def removed(service: HttpContext) {
            updateFilter(webContainer, None)
        }
    }
    
    def webContainerWatcher = new AbstractWatcher[WebContainer] {
        override def adding(service: Import[WebContainer]): WebContainer = {
            val container = service.get
            updateFilter(Some(container), httpContext)
            container
        }

        override def removed(service: WebContainer) {
            updateFilter(None, httpContext)
        }
    }

    def configure {

        def httpContext = service(classOf[HttpContext])
                            .out(httpContextWatcher)
                            .single.direct

        bind(classOf[HttpContext])
            .toProvider(httpContext)
            .asEagerSingleton()

        def webContainer = service(classOf[WebContainer])
                            .out(webContainerWatcher)
                            .single.direct

        bind(classOf[WebContainer])
            .toProvider(webContainer)
            .asEagerSingleton()

        // export the guice request scope service
        bind(export(classOf[GuiceScope])).annotatedWith(Names.named("request"))
            .toProvider(service(classOf[GuiceRequestScope])
                        .attributes(mutable.Map("scope" -> "request"))
                        .export)

        // export the guice session scope service
        bind(export(classOf[GuiceScope])).annotatedWith(Names.named("session"))
            .toProvider(service(classOf[GuiceSessionScope])
                        .attributes(mutable.Map("scope" -> "session"))
                        .export)

        // export the spring request scope service
        bind(export(classOf[SpringScope])).annotatedWith(Names.named("request"))
            .toProvider(service(classOf[SpringRequestScope])
                        .attributes(mutable.Map("scope" -> "request"))
                        .export)

        // export the spring session scope service
        bind(export(classOf[SpringScope])).annotatedWith(Names.named("session"))
            .toProvider(service(classOf[SpringSessionScope])
                        .attributes(mutable.Map("scope" -> "session"))
                        .export)
    }
}

/**
 * Implementation to support session scoped beans.
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
class GuiceSessionScope extends GuiceScope {
    def scope[A](key: Key[A], provider: Provider[A]): Provider[A] = new Provider[A] {
        def get: A = {
            val name = key.toString
            
            def create(session: HttpSession): A = {
                val value = provider.get
                session.setAttribute(name, value)
                value
            }
            
            def getOrCreate(session: HttpSession): A = {
                val attributeValue = Option(session.getAttribute(name))
                attributeValue.map(_.asInstanceOf[A]).getOrElse(create(session))
            }
            
            def httpSession: Option[HttpSession] = {
                requestContext.get
                              .filter(_.isInstanceOf[HttpServletRequest])
                              .map(_.asInstanceOf[HttpServletRequest])
                              .map(_.getSession(true))
            }
            
            httpSession.map(getOrCreate(_))
                       .getOrElse(error("Could not session scope binding for {}. " +
                                        "No request is currently processed.".format(key)))
        }
    }
    
    override def toString = "session scope"
}

/**
 * Implementation to support request scoped beans.
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
class GuiceRequestScope extends GuiceScope {
    def scope[A](key: Key[A], provider: Provider[A]): Provider[A] = new Provider[A] {
        def get: A = {
            val name = key.toString
            
            def create(request: ServletRequest): A = {
                val value = provider.get
                request.setAttribute(name, value)
                value
            }
            
            def getOrCreate(request: ServletRequest): A = {
                val attributeValue = Option(request.getAttribute(name))
                attributeValue.map(_.asInstanceOf[A]).getOrElse(create(request))
            }
            
            requestContext.get.map(getOrCreate(_))
                              .getOrElse(error("Could not request scope binding for {}. " +
                                               "No request is currently processed.".format(key)))
        }
    }
    
    override def toString = "request scope"
}

class SpringSessionScope extends SpringScope {

    def create(name: String, session: HttpSession, objectFactory: ObjectFactory): AnyRef = {
        val value = objectFactory.getObject
        session.setAttribute(name, value)
        value
    }
    
    def getOrCreate[A](name: String, session: HttpSession, f: HttpSession => A): A = {
        val attributeValue = Option(session.getAttribute(name))
        attributeValue.map(_.asInstanceOf[A]).getOrElse(f(session))
    }
    
    def get(name: String, objectFactory: ObjectFactory): Object = {
        requestContext.get
                      .filter(_.isInstanceOf[HttpServletRequest])
                      .map(_.asInstanceOf[HttpServletRequest].getSession(true))
                      .map(getOrCreate(name, _, create(name, _, objectFactory)))
                      .getOrElse(error("Could not session scope binding for {}. " +
                                       "No request is currently processed or no session could be determined.".format(name)))
    }

    def remove(name: String): Object = {
        requestContext.get.map(_.setAttribute(name, null))
    }

    def registerDestructionCallback(name: String, callback: Runnable) {
    }

    def getConversationId: String = null

    override def toString = "session scope"
}

class SpringRequestScope extends SpringScope {

    def create(name: String, request: ServletRequest, objectFactory: ObjectFactory): AnyRef = {
        val value = objectFactory.getObject
        request.setAttribute(name, value)
        value
    }
    
    def getOrCreate[A](name: String, request: ServletRequest, f: ServletRequest => A): A = {
        val attributeValue = Option(request.getAttribute(name))
        attributeValue.map(_.asInstanceOf[A]).getOrElse(f(request))
    }
    
    def get(name: String, objectFactory: ObjectFactory): Object = {
        requestContext.get.map(getOrCreate(name, _, create(name, _, objectFactory)))
                          .getOrElse(error("Could not request scope binding for {}. " +
                                           "No request is currently processed.".format(name)))
    }

    def remove(name: String): Object = {
        requestContext.get.map(_.setAttribute(name, null))
    }

    def registerDestructionCallback(name: String, callback: Runnable) {
    }

    def getConversationId: String = null

    override def toString = "request scope"
}

/**
 * Holds the servlet request for the actual thread.
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
object requestContext extends ThreadLocal[Option[ServletRequest]] {
    override def initialValue = None
}

/**
 * Maintains a request context to support request and session scoped beans.
 */
class GuiceContextFilter extends Filter {
    
    def destroy {}
    
    def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        requestContext.set(Some(request))
        try {
            chain.doFilter(request, response)
        } finally {
            requestContext.set(None)
        }
    }
    
    def init(config: FilterConfig) {}
}