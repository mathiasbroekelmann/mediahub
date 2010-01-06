package org.mediahub.jersey.osgi.spi.container.utils

import org.osgi.framework.{BundleContext, Bundle}

import scala.collection.JavaConversions._

import org.ops4j.pax.swissbox.core.BundleClassLoader.newPriviledged

import org.apache.commons.collections.IteratorUtils

import java.net.URL

object ClassLoader {
    /**
     * creates a classloader which sees all bundle classspaces.
     */
    def bundlesClassLoader(context: BundleContext): ClassLoader = {
        val cls = for(bundle <- context.getBundles; 
                      if (bundle.getState & (Bundle.STARTING | Bundle.ACTIVE | Bundle.RESOLVED)) != 0)
            yield (newPriviledged(bundle))
        new ChainedClassLoader(cls:_*)
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