/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.cache.internal

import com.google.inject._
import org.mediahub.cache._

import org.ops4j.peaberry._
import Peaberry._
import org.ops4j.peaberry.util._
import TypeLiterals._

import scala.actors.Actor._

class OsgiModule extends AbstractModule {

  @Provides
  @Singleton
  def cache = new CacheImpl()

  def configure {
    bind(export(classOf[Cache])).toProvider(service(classOf[CacheImpl]).export)
    bind(iterable(classOf[CacheListenerFactory]))
      .toProvider(service(classOf[CacheListenerFactory])
                  .out(Key.get(classOf[CacheListenerFactoryWatcher]))
                  .multiple)
  }
}

class CacheListenerFactoryWatcher @Inject() (cache: CacheImpl) extends AbstractWatcher[CacheListenerFactory] {

  override def adding(service: Import[CacheListenerFactory]) = {
    val factory = service.get
    worker ! Add(factory)
    factory
  }

  override def removed(factory: CacheListenerFactory) {
    worker ! Remove(factory)
  }

  case class Add(factory: CacheListenerFactory)
  case class Remove(factory: CacheListenerFactory)

  private[this] val worker = actor {

    var mapping = Map.empty[CacheListenerFactory, CacheListener]

    loop {
      react {
        case Add(factory) => {
            for(listener <- factory.create(cache)) {
              cache.addListener(listener)
              mapping += factory -> listener
            }
        }
        case Remove(factory) => {
            for(listener <- mapping.get(factory)) {
              cache.removeListener(listener)
              mapping -= factory
            }
        }
        case other => error(self + " has received unexpected message of " + other)
      }
    }
  }
}