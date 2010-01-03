package de.osxp.dali.album

import com.google.inject.{AbstractModule, Provider}
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.Peaberry.service
import javax.ws.rs.ext.{MessageBodyWriter}
import de.osxp.dali.page.{Page}

import org.osgi.framework._

/**
 * album service module.
 */
class AlbumOsgiModule extends AbstractModule {
    def configure {
        val clazz = classOf[AlbenOperations]
        val props = names("service.exported.interfaces=%s".format(clazz.getName), 
                          "service.exported.configs=org.apache.cxf.rs",
                          "org.apache.cxf.rs.httpservice.context=/dali/alben")
        val provider = service(classOf[Alben]).attributes(props).export
        bind(export(clazz)).toProvider(provider)
    }
}