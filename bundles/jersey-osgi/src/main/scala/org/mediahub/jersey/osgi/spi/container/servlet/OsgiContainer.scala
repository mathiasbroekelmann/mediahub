package org.mediahub.jersey.osgi.spi.container.servlet

import com.sun.jersey.spi.container.servlet.ServletContainer
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.WebConfig;

import org.mediahub.jersey.osgi.spi.container.OsgiComponentProviderFactory

import org.osgi.service.http.HttpService

import javax.ws.rs.core.{Application, MediaType, Context, UriInfo, UriBuilder}
import javax.ws.rs.{Path, GET, Produces}

import org.osgi.framework.BundleContext

import scala.collection.JavaConversions._

import scala.xml._

/**
 * A {@link Servlet} or {@link Filter} for deploying root resource classes
 * with OSGi integration.
 * <p>
 * This class will be registered using {@link HttpService} if found.
 * <p>
 * This class extends {@link ServletContainer} and initiates the
 * {@link WebApplication} with an OSGi-based {@link IoCComponentProviderFactory},
 * {@link OsgiComponentProviderFactory}, such that instances of resource and
 * provider classes declared and managed by OSGi can be obtained.
 * <p>
 * OSGi-bound services will be automatically registered if such
 * services are root resource classes, provider classes or instances of {@link Application}.
 *
 * @author Mathias Broekelmann
 */
class OsgiContainer (bundleContext: BundleContext) extends ServletContainer {

    override protected def getDefaultResourceConfig(props: java.util.Map[String, AnyRef],
                                                    webConfig: WebConfig): ResourceConfig = {
        new DefaultResourceConfig()
    }
    
    trait Snapshot {
        def apply(config: ResourceConfig): Unit
    }
    
    private[this] var snapshot: Option[Snapshot] = None

    override protected def initiate(config: ResourceConfig, webapp: WebApplication) {
        if(snapshot.isEmpty) {
            config.getSingletons.add(new JerseyStatusResource(config))
            snapshot = Some(takeSnapshot(config))
        } else {
            snapshot.get(config)
        }
        webapp.initiate(config, new OsgiComponentProviderFactory(config, bundleContext));
    }

    def takeSnapshot(config: ResourceConfig) = new Snapshot {
        val classes = new java.util.HashSet(config.getClasses)
        val singletons = new java.util.HashSet(config.getSingletons)
        val features = new java.util.HashMap[String, java.lang.Boolean](config.getFeatures) 
        val properties = new java.util.HashMap[String, AnyRef](config.getProperties) 
        val mediaTypeMappings = new java.util.HashMap[String, MediaType](config.getMediaTypeMappings) 
        val languageMappings = new java.util.HashMap[String, String](config.getLanguageMappings) 
        val explicitRootResources = new java.util.HashMap[String, AnyRef](config.getExplicitRootResources)
        
        def apply(config: ResourceConfig) {
            config.getClasses.clear
            config.getClasses.addAll(classes)
            config.getSingletons.clear
            config.getSingletons.addAll(singletons)
            config.getFeatures.clear
            config.getFeatures.putAll(features)
            config.getProperties.clear
            config.getProperties.putAll(properties)
            config.getMediaTypeMappings.clear
            config.getMediaTypeMappings.putAll(mediaTypeMappings)
            config.getLanguageMappings.clear
            config.getLanguageMappings.putAll(languageMappings)
            config.getExplicitRootResources.clear
            config.getExplicitRootResources.putAll(explicitRootResources)
        }
    }
}

@Path("status")
class JerseyStatusResource(config: ResourceConfig) {
    
    @Context
    val uriInfo: UriInfo = uriInfo
    
    @GET
    @Produces(Array("text/html"))
    def info: String = {
        val html = 
            <html>
                <body>
                {
                    report("Root resource classes", config.getRootResourceClasses.toSeq, {
                        x: Class[_] => <li>{rootResourceClassInfo(x)}</li>
                    })
                }
                {
                    report("Root resources singletons", config.getRootResourceSingletons.toSeq, {
                        x: AnyRef => <li>{rootResourceSingletonInfo(x)}</li>
                    })
                }
                </body>
            </html>
        val out = new java.io.StringWriter
        scala.xml.XML.write(out, html, "UTF-8", true, null)
        out.toString
    }
    
    def report[A](title: String, elements: Seq[A], f: (A => NodeSeq)): NodeSeq = {
        <div>
            <h3>{title}</h3>
            {
                if(elements.isEmpty) {
                    <p>none</p>
                } else {
                    <ul>
                    {
                        for(item <- elements) yield f(item)
                    }
                    </ul>
                }
            }
        </div>
    }
    
    def rootResourceClassInfo(clazz: Class[_]): NodeSeq = {
        val annotatedClass = resolvePathAnnotatedClass(clazz)
        val path = annotatedClass.getAnnotation(classOf[Path]).value
        val href = uriInfo.getBaseUriBuilder.path(annotatedClass).build() 
        <a href={href.toString}>{
            "Path: %s, Class: %s".format(path, clazz.getName)
        }</a>
    }
    
    def resolvePathAnnotatedClass(clazz: Class[_]): Class[_] = {
        
        def resolve(intf: List[Class[_]]): Class[_] = intf match {
            case head :: tail => Option(head.getAnnotation(classOf[Path]))
                                    .map(_ => head)
                                    .getOrElse(resolve(tail))
        }

        if (clazz.getAnnotation(classOf[Path]) == null) {
            resolve(clazz.getInterfaces.toList)
        } else {
            clazz
        }
    }
    
    def rootResourceSingletonInfo(obj: AnyRef): NodeSeq = {
        rootResourceClassInfo(obj.getClass)
    }
}
