package org.mediahub.resources.internal

import org.osgi.framework._

import org.mediahub.resources._
import org.mediahub.web.links._

import org.mediahub.rest._

import com.google.inject._

import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.Peaberry.service
import org.ops4j.peaberry.activation.{Start, Stop}

import javax.activation._

class ResourcesOsgiModule extends AbstractModule {

  def configure {
    bind(export(classOf[ContentTypes])).toProvider(service(classOf[ContentTypes]).export)
    bind(export(classOf[RestRegistrar])).toProvider(service(classOf[RestRegistrar]).export)
    bind(export(classOf[LinkResolver[BundleResource]])).toProvider(service(classOf[BundleResourceLinkResolver]).export)
  }

  /**
   * provide the rest bundle resources instance
   */
  @Provides
  def bundleResources(ctx: Provider[BundleContext], ct: Provider[ContentTypes]): BundleResources = {
    new BundleResources {
      override def bundleContext = Some(ctx.get)
      override def contentTypes = Some(ct.get)
    }
  }

  /**
   * provide the rest registrar which is used to register the rest services.
   */
  @Provides
  @Singleton
  def restRegistrar(bundleResources: Provider[BundleResources]): RestRegistrar = new RestRegistrar {
    def register(registry: RestRegistry) {
      registry.register[BundleResources](bundleResources)
      registry.register[ResourceMessageBodyWriter]
    }
  }
  
  @Provides
  @Singleton
  def contentTypes(fileTypeMap: Provider[FileTypeMap]): ContentTypes = {
    new ContentTypes {
      def contentType(filename: String): String = fileTypeMap.get.getContentType(filename)
    }
  }

  @Provides
  @Singleton
  def fileTypeMap(context: BundleContext): FileTypeMap = {
    // TODO: collect mime.types files from all deployed bundles
    val resource = Option(context.getBundle.getResource("META-INF/mime.types"))
    resource.map(url => new MimetypesFileTypeMap(url.openStream))
            .getOrElse(new MimetypesFileTypeMap)
  }
}