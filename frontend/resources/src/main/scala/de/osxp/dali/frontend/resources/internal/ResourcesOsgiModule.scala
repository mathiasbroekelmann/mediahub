package de.osxp.dali.frontend.resources.internal

import org.osgi.framework._

import scala.collection.mutable.{Map, Set}

import de.osxp.dali.frontend.resources._

import com.google.inject._
import com.google.inject.name._

import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.Peaberry.service
import org.ops4j.peaberry.activation.{Start, Stop}

import javax.activation._

import javax.ws.rs.ext.MessageBodyWriter

class ResourcesOsgiModule extends AbstractModule {
    
    def configure {
        bind(classOf[FileTypeMap]).toInstance(new MimetypesFileTypeMap)

        //bind(classOf[ContentTypes]).to(classOf[FileTypeMapContentTypes]).in(Scopes.SINGLETON)

        val provider = service(classOf[FileTypeMapContentTypes]).export
        bind(classOf[ContentTypes]).toProvider(provider.direct)
        bind(classOf[BundleExtender]).to(classOf[StaticResourcesBundleExtender]).in(Scopes.SINGLETON)
        
        bind(export(classOf[MessageBodyWriter[Resource]]))
            .toProvider(service(classOf[ResourceMessageWriter]).export)
    }
}
