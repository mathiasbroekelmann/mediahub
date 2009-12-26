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
        //bind(classOf[ContentTypes]).to(classOf[FileTypeMapContentTypes]).in(Scopes.SINGLETON)

        val provider = service(classOf[FileTypeMapContentTypes]).export
        bind(export(classOf[ContentTypes])).toProvider(provider)
        bind(classOf[ContentTypes]).toProvider(service(classOf[ContentTypes]).single())
        bind(classOf[BundleExtender]).to(classOf[StaticResourcesBundleExtender]).in(Scopes.SINGLETON)
        
        bind(export(classOf[MessageBodyWriter[Resource]]))
            .toProvider(service(classOf[ResourceMessageWriter]).export)
    }
    
    @Provides
    def provideFileTypeMap(context: BundleContext): FileTypeMap = {
        // TODO: collect mime.types files from all deployed bundles
        val resource = Option(context.getBundle.getResource("META-INF/mime.types"))
        resource.map(url => new MimetypesFileTypeMap(url.openStream))
                .getOrElse(new MimetypesFileTypeMap)
    }
}
