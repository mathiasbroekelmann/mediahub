package de.osxp.dali.album

import com.google.inject.{AbstractModule, Provider}
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.Peaberry.service
import javax.ws.rs.ext.{MessageBodyWriter}
import de.osxp.dali.page.{Page}

import org.osgi.framework._

/**
 * provides the JacksonJsonProvider service.
 */
class AlbumOsgiModule extends AbstractModule {
    def configure {
        val props = names("service.exported.interfaces=*", 
                          "service.exported.configs=org.apache.cxf.rs",
                          "org.apache.cxf.rs.httpservice.context=/alben")
        val provider = service(new Alben).attributes(props).export
        bind(export(classOf[Alben])).toProvider(provider)
    }
}

class AlbumActivator extends BundleActivator {
    def start(ctx: BundleContext) {
        val props = new java.util.Hashtable[String, String]()
        props.put("service.exported.interfaces", "*")
        props.put("service.exported.configs", "org.apache.cxf.rs")
        props.put("org.apache.cxf.rs.httpservice.context", "/alben")
        ctx.registerService(classOf[Alben].getName, new Alben, props)
    }
    
    def stop(ctx: BundleContext) {
        
    }
}