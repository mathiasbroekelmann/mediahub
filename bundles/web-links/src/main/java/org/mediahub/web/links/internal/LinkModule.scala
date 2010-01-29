/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.web.links.internal

import org.mediahub.web.links._

import com.google.inject._

import java.net.URI

import scala.collection.JavaConversions.{asIterable}

import org.ops4j.peaberry._
import Peaberry._
import org.ops4j.peaberry.util._
import TypeLiterals._

/**
 * guice module to define the exported/imported services.
 */
class LinkModule extends AbstractModule {

  @Provides
  def linkRenderer(context: LinkContext): LinkRenderer =
    new ContextLinkRenderer(context)

  @Provides
  def linkContext(linkResolver: java.lang.Iterable[LinkResolver[_]]): LinkContext = {
    new LinkContext {
      override def baseUri[A<:AnyRef](implicit clazz: ClassManifest[A]): Option[URI] = {
        Some(URI.create("/"))
      }
      override def resolver = linkResolver
    }
  }

  def configure {
    // import link resolver services
    val literal = new TypeLiteral[java.lang.Iterable[LinkResolver[_]]] {}
    val provider = service(classOf[LinkResolver[_]]).multiple()
    bind(literal).toProvider(provider)
    // export the link resolver service
    bind(export(classOf[LinkRenderer])).toProvider(service(classOf[LinkRenderer]).export())
  }
}
