package org.mediahub.jackson

import com.google.inject.{AbstractModule, Provider}
import org.codehaus.jackson.jaxrs.JacksonJsonProvider
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.Peaberry.service
import javax.ws.rs.ext.{MessageBodyReader, MessageBodyWriter}

/**
 * provides the JacksonJsonProvider service.
 */
class JacksonOsgiModule extends AbstractModule {
    def configure {
        val provider = service(classOf[JacksonJsonProvider]).export
        bind(export(classOf[MessageBodyReader[AnyRef]])).toProvider(provider)
        bind(export(classOf[MessageBodyWriter[AnyRef]])).toProvider(provider)
    }
}
