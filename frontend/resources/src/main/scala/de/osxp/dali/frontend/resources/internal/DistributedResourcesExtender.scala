package de.osxp.dali.frontend.resources.internal

import de.osxp.dali.frontend.resources._
import org.osgi.framework._
import org.springframework.osgi.io.OsgiBundleResourceLoader
import org.springframework.core.io.{Resource => SpringResource}
import org.apache.commons.io.FilenameUtils.{concat => concatPath}
import org.apache.commons.io.output.{CountingOutputStream, NullOutputStream}
import org.apache.commons.io.CopyUtils.copy

import scala.collection.mutable.Map
import scala.collection.JavaConversions._

import java.io.OutputStream

import javax.ws.rs.ext.MessageBodyWriter

/**
 * Registers the distributed resources handler.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
class DistributedResourcesExtender extends AbstractExtender {
    
    override val deactivateOnStop = false
    
    val extenderBuilder = (context: BundleContext) => new Extender {
        
        val resourceWriterRegistration = context.registerService(classOf[MessageBodyWriter[Resource]].getName, new ResourceMessageWriter, null)
        
        /**
         * Actually perform the registration of distributed resources.
         * 
         * @param bundle the bundle to register resources for
         * @param alias the alias for which the resource should be registered
         * @param root the root folder where the resource are located in the bundle
         * 
         * @return Some activation callback instance or None if no deactivation is requried.
         */
        def register(bundle: Bundle, alias: String, base: String): Option[Activation] = {
            val resources = new StaticResources(loader(bundle, base))
            val properties = new java.util.Hashtable[String, String]
            properties.put("service.exported.interfaces", "*");
            properties.put("service.exported.configs", "org.apache.cxf.rs")
            //properties.put("org.apache.cxf.ws.address", "http://localhost:9090/test")
            properties.put("org.apache.cxf.rs.httpservice.context", "/" + alias)
            val registration = context.registerService(classOf[StaticResources].getName, resources, properties)
            Some(new Activation {
                def deactivate {
                    try {
                        registration.unregister
                    } catch {
                        case ex: IllegalStateException => // expected when the service is already unregistered
                        case ex: IllegalArgumentException => // avoid exceptions during unregistration
                    }
                }
            })
        }

        def close {
            // resourceWriterRegistration.unregister
        }
        
        def loader(bundle: Bundle, base: String): ResourceLoader = {
            new ResourceLoader {
                val loader = new OsgiBundleResourceLoader(bundle)
                
                def getResource(location: String): Option[Resource] = {
                    val res = loader.getResource(concatPath(base, location))
                    if(res.exists && res.isReadable) {
                        Some(asResource(res))
                    } else {
                        None
                    }
                }
            }
        }
        
        def asResource(resource: SpringResource): Resource = {
            new Resource {
                def lastModified: Option[Long] = {
                    val lm = resource.lastModified
                    if(lm > 0) Some(lm) else None
                }
                
                def size: Option[Long] = {
                    Some(writeTo(new CountingOutputStream(new NullOutputStream)).getByteCount)
                }
                
                def writeTo[A<:OutputStream](out: A): A = {
                    val in = resource.getInputStream
                    try {
                        copy(in, out)
                    } finally {
                        in.close
                    }
                    out
                }
                
                def uri = resource.getURI
            }
        }
        
        /**
         * called when a bundle is activated.
         */
        val activate = { bundle: Bundle => 
            register(bundle) { register(bundle, _, _) }
        }
        
        def register(bundle: Bundle) (f: (String, String) => Option[Activation]): Activation = {
            val header = Option(bundle.getHeaders.get("Distributed-Resources")).map(_.asInstanceOf[String])
            var activations: Seq[Activation] = Seq.empty
            for(someHeader <- header; clause <- someHeader.split(",")) {
                val parts = clause.trim.split("\\s*=\\s*").map(_.trim)
                val activation = parts match {
                    case Array(alias, base) => f(alias, base)
                    case _ => None
                }
                for(some <- activation) {
                    activations +:= some
                }
            }
            new Activation {
                def deactivate {
                    for(activation <- activations) {
                        activation.deactivate
                    }
                }
            }
        }
    }
}