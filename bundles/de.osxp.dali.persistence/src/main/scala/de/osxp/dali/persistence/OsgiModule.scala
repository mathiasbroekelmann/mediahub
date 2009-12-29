package de.osxp.dali.persistence

import com.google.inject.{AbstractModule, Provider, Provides, Key}
import com.google.inject.name._
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.Peaberry.service

import javax.sql.DataSource

import org.apache.commons.dbcp._

import org.osgi.framework._

class OsgiModule extends AbstractModule {
    def configure {
        // register a default datasource for dali
        val dsProvider = service(Key.get(classOf[DataSource], Names.named("default"))).export
        bind(export(classOf[DataSource])).toProvider(dsProvider)
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
