package org.mediahub.web.scopes.guice

import com.google.inject.{AbstractModule, Module, Provider, Binder, Key, Scope, Inject}
import com.google.inject.name._
import org.ops4j.peaberry.Peaberry.service
import org.ops4j.peaberry.Import
import org.ops4j.peaberry.util.Filters._

import com.google.inject.servlet._

import scala.collection.JavaConversions._
import scala.collection._

/**
 * guice module to support scoped beans to request or session.
 * Example for usage:
 * <pre>
 * import com.google.inject.servlet.RequestScoped
 * import com.google.inject.servlet.SessionScoped
 * 
 * Guice.createInjector(new WebScopes(), new MyModule(), ...)
 * // or install WebScopes
 * class MyModule extends AbstractModule {
 *  ...
 *  install(new WebScopes())
 *  // bind a bean in request scope
 *  bind(SomeRequestScopedBean.class).in(RequestScoped.class)
 *  
 *  // bind a bean in session scope
 *  bind(SomeSessionScopedBean.class).in(SessionScoped.class)
 *  
 *  // to use the scope instance:
 *  WebScopes scopes = new WebScopes();
 *  install(scopes)
 *  bind(SomeRequestScopedBean.class).in(scopes.request())
 *  bind(SomeSessionScopedBean.class).in(scopes.session())
 * }
 * </pre>
 */
class WebScopes extends AbstractModule {
    
    def configure {
    
        def bindScopeService(name: String) {
            bind(classOf[Scope]).annotatedWith(Names.named(name))
                                .toProvider(service(classOf[Scope])
                                            .filter(attributes(mutable.Map("scope" -> name)))
                                            .single)
        }

        // bind the web scopes to inject guice the imported scope services
        bindScopeService("request")
        bindScopeService("session")
        bindScope(classOf[RequestScoped], request)
        bindScope(classOf[SessionScoped], session)
    }

    /**
     * Request scope
     */
    val request = scopeService(requestScope.get)

    /**
     * Session scope
     */
    val session = scopeService(sessionScope.get)

    @Inject
    @Named("request")
    private[this] val requestScope: Import[Scope] = requestScope
    
    @Inject
    @Named("session")
    private[this] val sessionScope: Import[Scope] = requestScope
    
    /**
     * create a wrapper around the intended scope. 
     * This is necessary since the real scope is not available at configuration time.
     */
    private[this] def scopeService(f: => Scope) = new Scope {
            def scope[A](key: Key[A], provider: Provider[A]) = new Provider[A] {
                def get = f.scope(key, provider).get
            }
        }

    /**
     * return true if other is a WebModule instance since only one instance of WebModule should be installed in the binder.
     */
    override def equals(other: Any) = other match {
        case that: WebScopes => true
        case _ => false
    }

    override def hashCode = 42
}