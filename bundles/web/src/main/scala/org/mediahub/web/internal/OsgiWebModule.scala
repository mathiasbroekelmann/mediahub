package org.mediahub.web.internal

import com.google.inject.{AbstractModule, Provider, Provides, Key, Scope, Inject}
import com.google.inject.name._
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.Peaberry.service

import javax.servlet._
import javax.servlet.http._

import scala.collection.JavaConversions._
import scala.collection._

import org.osgi.framework.BundleContext

/**
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
class OsgiWebModule extends AbstractModule {
    
    @Inject
    def onContext(context: BundleContext) {
        OsgiWebModule.bundlecontext = Some(context)
    }

    def configure {
        // export the servlet filter service to match any request.
        bind(export(classOf[Filter]))
            .toProvider(service(classOf[GuiceContextFilter])
                        .attributes(mutable.Map("urlPatterns" -> "/*"))
                        .export)
        
        // export the request scope service
        bind(export(classOf[Scope])).annotatedWith(Names.named("request"))
            .toProvider(service(classOf[RequestScope])
                        .attributes(mutable.Map("scope" -> "request"))
                        .export)
        
        // export the session scope service
        bind(export(classOf[Scope])).annotatedWith(Names.named("session"))
            .toProvider(service(classOf[SessionScope])
                        .attributes(mutable.Map("scope" -> "session"))
                        .export)
    }
}

object OsgiWebModule {
    var bundlecontext: Option[BundleContext] = bundlecontext
}

/**
 * Implementation to support session scoped beans.
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
class SessionScope extends Scope {
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
    
    override def toString = "request scope"
}

/**
 * Implementation to support request scoped beans.
 * 
 * @author Mathias Broekelmann
 *
 * @since 30.12.2009
 *
 */
class RequestScope extends Scope {
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
