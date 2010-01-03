package de.osxp.dali.persistence

import com.google.inject.{AbstractModule, Provider, Provides, Key, Inject}
import com.google.inject.Scopes.SINGLETON
import com.google.inject.name._
import org.ops4j.peaberry.{Import}
import org.ops4j.peaberry.util.TypeLiterals.{export, iterable}
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.util.AbstractWatcher
import org.ops4j.peaberry.Peaberry.service

import javax.sql.DataSource

import javax.servlet.{Filter => ServletFilter, ServletRequest, ServletResponse, FilterChain, FilterConfig}
import javax.servlet.http.HttpServletRequest

import org.apache.commons.dbcp._

import org.osgi.framework._

import scala.collection._
import scala.collection.JavaConversions._

import javax.persistence.EntityManagerFactory

import java.lang.Iterable

import org.springframework.transaction.support.TransactionSynchronizationManager
import TransactionSynchronizationManager.{hasResource, bindResource, unbindResource}
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.orm.jpa.EntityManagerFactoryUtils.closeEntityManager

class OsgiModule extends AbstractModule {
    def configure {
        // register a default datasource for dali
        val dsProvider = service(Key.get(classOf[DataSource], Names.named("default"))).export
        bind(export(classOf[DataSource])).toProvider(dsProvider)
        
        // import all exposed entity manager factories
        bind(iterable(classOf[EntityManagerFactory]))
            .toProvider(service(classOf[EntityManagerFactory])
                        .multiple)

        // bind a servlet filter for open session in view filter support.
        bind(export(classOf[ServletFilter]))
            .toProvider(service(classOf[GuiceOpenEntityManagerInViewFilter])
                        .attributes(mutable.Map("urlPatterns" -> "/*", 
                                                "urlPatterns" -> "/*"))
                        .export)
            .in(SINGLETON)
    }
    
    @Provides
    @Named("default")
    def daliDatasource: DataSource = {
        val ds = new BasicDataSource
        ds.setDriverClassName(classOf[org.hsqldb.jdbcDriver].getName)
        ds.setUsername("SA")
        ds.setPassword("")
        ds.setUrl("jdbc:hsqldb:file:db/dali;shutdown=true")
        ds
    }
}

/**
 * Link spring's open entity manager in view filter this filter uses a sequence of entity manager factories. 
 */
class GuiceOpenEntityManagerInViewFilter @Inject() (emfs: Provider[Iterable[EntityManagerFactory]]) extends ServletFilter {
    
    def destroy {}
    
    def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) = {
        
        // bind entity managers if not already bound
        val boundFactories = for(emf <- emfs.get; if bind(emf)) yield emf
        try {
            chain.doFilter(request, response)
        } finally {
            boundFactories foreach { unbindAndClose(_) }
        }
    }
    
    private[this] def bind(emf: EntityManagerFactory): Boolean = {
        if (!hasResource(emf)) {
            bindResource(emf, new EntityManagerHolder(emf.createEntityManager))
            true
        } else {
            false
        }
    }
    
    private[this] def unbindAndClose(emf: EntityManagerFactory) = {
        val holder = unbindResource(emf).asInstanceOf[EntityManagerHolder]
        closeEntityManager(holder.getEntityManager)
    }

    def init(config: FilterConfig) {}
}