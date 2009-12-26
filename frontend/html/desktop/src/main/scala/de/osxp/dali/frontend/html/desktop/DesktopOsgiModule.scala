package de.osxp.dali.frontend.html.desktop

import com.google.inject.{AbstractModule, Provider}
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.Peaberry.service
import javax.ws.rs.ext.{MessageBodyWriter}
import de.osxp.dali.page.{Page}

/**
 * provides the JacksonJsonProvider service.
 */
class DesktopOsgiModule extends AbstractModule {
    def configure {
        val provider = service(classOf[DesktopHtmlPageWriter]).export
        bind(export(classOf[MessageBodyWriter[Page]])).toProvider(provider)
    }
}
