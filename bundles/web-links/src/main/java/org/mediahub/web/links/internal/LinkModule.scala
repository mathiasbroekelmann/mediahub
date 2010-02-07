/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.web.links.internal

import org.mediahub.web.links._

import org.mediahub.cache.{Cache, CacheKey, Dependency}

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
  def linkContext(linkResolver: java.lang.Iterable[LinkResolver[_]],
                  cacheProvider: Provider[Cache]): LinkContext = {
    new LinkContext {
      override def baseUri[A<:AnyRef](implicit clazz: ClassManifest[A]): Option[URI] = {
        Some(URI.create("/"))
      }
      override def resolver = linkResolver

      override def resolverFor[A<:AnyRef](implicit clazz: ClassManifest[A]): Traversable[LinkResolver[A]] = {
        def computeLinkResolvers = super.resolverFor[A]
        def cache = cacheProvider.get
        val cacheKey = new LinkResolverForClass(clazz) {
          def compute =  {
            Cache.dependsOn(LinkResolvers)
            computeLinkResolvers
          }
        }
        cache.get(cacheKey)
      }
    }
  }

  @Provides
  @Singleton
  def linkResolverWatcher(cache: Provider[Cache]) =
    new DependencyFiringServiceWatcher[LinkResolver[_]](cache, LinkResolvers)
  
  def configure {


    def importLinkResolvers {
      // import link resolver services
      val linkResolver = service(classOf[LinkResolver[_]])
      val linkResolversWatcherLiteral = new TypeLiteral[DependencyFiringServiceWatcher[LinkResolver[_]]] {}
      linkResolver.out(Key.get(linkResolversWatcherLiteral))

      val linkResolversLiteral = new TypeLiteral[java.lang.Iterable[LinkResolver[_]]] {}
      bind(linkResolversLiteral).toProvider(linkResolver.multiple())
    }

    importLinkResolvers

    // export the link resolver service
    bind(export(classOf[LinkRenderer])).toProvider(service(classOf[LinkRenderer]).export())
    bind(classOf[Cache]).toProvider(service(classOf[Cache]).single)
  }
}

/**
 * used to mark a computation with a dependency to the available link resolvers.
 */
case object LinkResolvers extends Dependency

/**
 * cass class for a cache key to compute a set of link resolvers for a given class.
 */
abstract case class LinkResolverForClass[A](clazz: ClassManifest[A]) extends CacheKey[Traversable[LinkResolver[A]]]

/**
 * a peaberry service watcher which invalidates a dependency
 */
class DependencyFiringServiceWatcher[A](cacheProvider: Provider[Cache], dependency: Dependency) extends AbstractWatcher[A] {

  def cache = cacheProvider.get

  override def adding(service: Import[A]): A = {
    cache.invalidate(dependency)
    service.get
  }

  override def modified(service: A, attributes: java.util.Map[String, _]) =
    cache.invalidate(dependency)

  override def removed(service: A) =
    cache.invalidate(dependency)
}
