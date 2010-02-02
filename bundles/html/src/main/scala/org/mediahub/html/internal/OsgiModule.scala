/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.html.internal

import scala.xml.NodeSeq

import com.google.inject._

import org.ops4j.peaberry._
import builders._
import Peaberry._
import org.ops4j.peaberry.util._
import Attributes._
import TypeLiterals._

import java.util.Locale

import org.mediahub.views.{CustomizableViewRenderer, ViewRenderer}

import org.mediahub.html._

import org.mediahub.rest.{RestRegistrar, RestRegistry}

class OsgiModule extends AbstractModule {

  /**
   * configure the html view message body writer.
   */
  @Provides
  def htmlWriter(rendererProvider: Provider[ViewRenderer]): HtmlViewMessageBodyWriter = {
    new HtmlViewMessageBodyWriter {
      def renderer = rendererProvider.get
    }
  }

  /**
   * TODO: resolve the locale from a context
   */
  @Provides
  @Context
  def locale = Locale.getDefault

  @Provides
  def htmlViewModule(rendererProvider: Provider[ViewRenderer],
                     @Context contextLocale: Provider[Locale]): HtmlViewModule = {
    new HtmlViewModule {
      def renderer = rendererProvider.get
      def locale = contextLocale.get
    }
  }

  /**
   * provide a view renderer with some defaults.
   */
  @Provides
  def viewRenderer(renderer: CustomizableViewRenderer): ViewRenderer = {
    renderer.withDefaultFor[String] { (self, classifier) => ""}
            .withDefaultFor[NodeSeq] { (self, classifier) => NodeSeq.Empty}
  }

  def configure {
    bind(export(classOf[HtmlRestRegistrar])).toProvider(service(classOf[HtmlRestRegistrar]).export)
    bind(classOf[CustomizableViewRenderer]).toProvider(service(classOf[CustomizableViewRenderer]).single)
    bind(export(classOf[HtmlViewModule])).toProvider(service(classOf[HtmlViewModule]).export)
  }
}

/**
 * registers the the html message body writer into a rest registry.
 */
class HtmlRestRegistrar @Inject() (htmlWriter: Provider[HtmlViewMessageBodyWriter]) extends RestRegistrar {

  def register(registry: RestRegistry) {
    registry.register[HtmlViewMessageBodyWriter](htmlWriter)
  }
}