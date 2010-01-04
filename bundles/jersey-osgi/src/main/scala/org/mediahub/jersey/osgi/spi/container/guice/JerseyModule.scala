package org.mediahub.jersey.osgi.spi.container.guice

import org.mediahub.jersey.osgi.spi.container.servlet.OsgiContainer

import org.osgi.framework.BundleContext

import com.google.inject.{Provider, Inject, AbstractModule, Module, Scopes, Key, Provides, TypeLiteral, Singleton}
import com.google.inject.name.{Named, Names}
import com.google.inject.util.Providers.of

import org.ops4j.peaberry.Peaberry._
import org.ops4j.peaberry.Import
import org.ops4j.peaberry.util.AbstractWatcher
import org.ops4j.peaberry.ServiceWatcher
import org.ops4j.peaberry.util.TypeLiterals._

import org.osgi.service.http.{HttpService, HttpContext}

class JerseyModule(context: BundleContext) extends AbstractModule {
    
    private val serviceWatcherOfHttpServiceKey = Key.get(new TypeLiteral[ServiceWatcher[HttpService]]() {})
    private val serviceWatcherOfHttpContextKey = Key.get(new TypeLiteral[ServiceWatcher[HttpContext]]() {})
    private val optionOfHttpServiceKey = Key.get(new TypeLiteral[Option[HttpService]]() {})
    
    def configure {
        bind(classOf[javax.servlet.Servlet])
            .annotatedWith(Names.named("jerseyServlet"))
            .to(classOf[OsgiContainer])
            .in(Scopes.SINGLETON)
            
        bind(classOf[JerseyRegistrar])
            .to(classOf[DefaultJerseyRegistrar])
            .in(Scopes.SINGLETON)

        bind(serviceWatcherOfHttpContextKey)
            .to(classOf[HttpContextWatcher])
            .in(Scopes.SINGLETON)
            
        bind(serviceWatcherOfHttpServiceKey)
            .to(classOf[HttpServiceWatcher])
            .in(Scopes.SINGLETON)
            
        def httpService = service(classOf[HttpService])
                            .out(serviceWatcherOfHttpServiceKey)
                            .single.direct
                            
        bind(classOf[HttpService])
            .toProvider(httpService)
            .asEagerSingleton()
            
        bind(optionOfHttpServiceKey).toProvider(classOf[HttpServiceWatcher]).in(Scopes.SINGLETON)

        def httpContext = service(classOf[HttpContext])
                        .out(serviceWatcherOfHttpContextKey)
                        .single.direct
                            
        bind(classOf[HttpContext])
            .toProvider(httpContext)
            .asEagerSingleton()
    }
    
    @Inject
    val jerseyRegistrar: JerseyRegistrar = jerseyRegistrar
    
    @Inject
    val httpService: Import[HttpService] = httpService
    
    def stop {
        for(registrar <- Option(jerseyRegistrar)) {
            registrar.unregister
        }
    }
}

trait JerseyRegistrar {
    def register(httpService: HttpService, httpContext: Option[HttpContext]): HttpService

    /**
     * unregisters the jersey container from the http service. May return null if no registration was done.
     */
    def unregister: HttpService
}

class DefaultJerseyRegistrar @Inject() (@Named("jerseyServlet") servlet: javax.servlet.Servlet) extends JerseyRegistrar {

    @Inject(optional = true)
    @Named("jerseyServletAlias")
    val jerseyServletAlias: Provider[String] = of("/jersey")

    @Inject(optional = true)
    @Named("jerseyServletInitParams")
    val jerseyServletInitParams: Provider[java.util.Dictionary[String, AnyRef]] = null

    @Inject(optional = true)
    val jerseyServletHttpContext: Provider[HttpContext] = null
    
    private[this] var usedHttpService: Option[HttpService] = None
    private[this] var usedAlias: Option[String] = None
    
    def get[A<:AnyRef](option: Provider[A]): Option[A] = Option(option).flatMap(x => Some(x.get))

    def register(httpService: HttpService, httpContext: Option[HttpContext]): HttpService = synchronized {
        unregister
        val params = get(jerseyServletInitParams).orNull
        val context = httpContext.getOrElse(get(jerseyServletHttpContext).orNull)
        usedAlias = Some(jerseyServletAlias.get)
        httpService.registerServlet(usedAlias.get, servlet, params, context)
        usedHttpService = Some(httpService)
        httpService
    }

    def unregister = synchronized {
        for (httpservice <- usedHttpService) {
            httpservice.unregister(usedAlias.get)
        }
        val result = usedHttpService.orNull
        usedHttpService = None
        usedAlias = None
        result
    }
}

/**
 * Watches for HttpContext service to register the jersey servlet to.
 */
class HttpContextWatcher @Inject() (httpService: Option[HttpService], registrar: JerseyRegistrar) extends AbstractWatcher[HttpContext] {
    override def adding(context: Import[HttpContext]): HttpContext = {
        val httpContext = context.get
        for(srv <- httpService) {
            registrar.register(srv, Some(httpContext))
        }
        httpContext
    }
    
    override def removed(context: HttpContext) {
        for(srv <- httpService) {
            registrar.register(srv, None)
        }
    }
}

@Singleton()
class HttpServiceWatcher @Inject() (registrar: JerseyRegistrar) extends AbstractWatcher[HttpService] with Provider[Option[HttpService]] {
    
    private[this] var httpService: Option[HttpService] = None
    
    def get = httpService
    
    override def adding(service: Import[HttpService]): HttpService = {
        httpService = Some(service.get)
        registrar.register(httpService.get, None)
    }
    
    override def removed(service: HttpService) {
        httpService = None
        registrar.unregister
    }
}