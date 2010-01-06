package org.mediahub.jersey.osgi.spi.container.servlet

import com.sun.jersey.spi.container.servlet.ServletContainer
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.WebConfig;

import org.mediahub.jersey.osgi.spi.container._

import org.osgi.service.http.HttpService

import javax.ws.rs.core.{Application, MediaType, Context, UriInfo, UriBuilder}
import javax.ws.rs.{Path, GET, Produces}

import java.net.URL
import java.util.concurrent.Callable

import org.osgi.framework.{BundleContext, Bundle}

import scala.collection.JavaConversions._

import scala.xml._

import org.ops4j.pax.swissbox.core.ContextClassLoaderUtils.doWithClassLoader
import org.ops4j.pax.swissbox.core.BundleClassLoader.newPriviledged

import org.apache.commons.collections.IteratorUtils

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
class OsgiContainer extends ServletContainer {

    private[this] val bundleContextProviderPropertyName = classOf[BundleContextProvider].getName
    private[this] val bundleContextPropertyName = classOf[BundleContext].getName

    private[this] def bundleContext(webConfig: WebConfig): BundleContext = {
        val className = Option(webConfig.getInitParameter(bundleContextProviderPropertyName))
        val clazz = className.map { name =>
            Thread.currentThread.getContextClassLoader.loadClass(name)
        }.getOrElse(error("no bundle context provider class defined in param " + bundleContextProviderPropertyName))
        val providerClazz = if(classOf[BundleContextProvider].isAssignableFrom(clazz)) {
            clazz.asInstanceOf[Class[BundleContextProvider]]
        } else {
            error("bundle context provider class " + clazz + " does not implement " + bundleContextProviderPropertyName)
        }
        providerClazz.newInstance.bundleContext(webConfig.getServletContext)
    }
    
    private[this] def resolveBundleContext(config: ResourceConfig) = {
        val value = config.getProperties.get(bundleContextPropertyName)
        value.asInstanceOf[BundleContext]
    }

    override protected def getDefaultResourceConfig(props: java.util.Map[String, AnyRef],
                                                    webConfig: WebConfig): ResourceConfig = {
        val resourceConfig = new DefaultResourceConfig
        resourceConfig.getProperties.put(bundleContextPropertyName, bundleContext(webConfig))
        resourceConfig
    }
    
    /**
     * creates a classloader which sees all bundle classspaces.
     */
    def bundlesClassLoader(context: BundleContext): ClassLoader = {
        val cls = for(bundle <- context.getBundles; 
                      if (bundle.getState & (Bundle.STARTING | Bundle.ACTIVE)) != 0)
            yield (newPriviledged(bundle))
        new ChainedClassLoader(cls:_*)
    }
    
    private[this] trait Snapshot {
        def apply(config: ResourceConfig): Unit
    }
    
    private[this] var snapshot: Option[Snapshot] = None
    
    private[this] def superInit(webConfig: WebConfig) {
        super.init(webConfig)
    }
    
    override protected def init(webConfig: WebConfig) {
        val cl = bundlesClassLoader(bundleContext(webConfig))
        doWithClassLoader(cl, new Callable[Unit] {
            def call {
                superInit(webConfig)
            }
        })
    }

    override protected def initiate(config: ResourceConfig, webapp: WebApplication) {
        if(snapshot.isEmpty) {
            snapshot = Some(takeSnapshot(config))
        } else {
            snapshot.get(config)
        }
        config.getSingletons.add(new JerseyStatusResource(config))
        webapp.initiate(config, new OsgiComponentProviderFactory(config, resolveBundleContext(config)));
    }

    private[this] def takeSnapshot(config: ResourceConfig) = new Snapshot {
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
    
    var uriInfo: UriInfo = _
    
    @Context
    def setUriInfo(info: UriInfo) {
        uriInfo = info
    }
    
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
            case Nil => error("could not locate @Path annotation for " + clazz)
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

class ChainedClassLoader(classloaders: ClassLoader*) extends ClassLoader {
    
    val classloaderList = classloaders.toList
    
    def find[A<:AnyRef](loaders: List[ClassLoader],
                        f: ClassLoader => Option[A]) : Option[A] = loaders match {
        case head :: tail => f(head).orElse(find(tail, f))
        case Nil => None
    }
    
    override def getResource(name: String): URL = {
        find(classloaderList, cl => Option(cl.getResource(name))).orNull
    }
    
    override def findResources(name: String): java.util.Enumeration[URL] = {
        val listOfIterators = for (cl <- classloaderList) yield (IteratorUtils.asIterator(cl.getResources(name)))
        val result = IteratorUtils.chainedIterator(listOfIterators);
        IteratorUtils.asEnumeration(result).asInstanceOf[java.util.Enumeration[URL]]
    }
    
    override def loadClass(name: String): Class[_] = {
        
        def loadClass(cl: ClassLoader): Option[Class[_]] = {
            try {
                Some(cl.loadClass(name))
            } catch {
                case ex: ClassNotFoundException => None
            }
        }
        
        find(classloaderList, loadClass(_)).getOrElse(throw new ClassNotFoundException(name))
    }
}