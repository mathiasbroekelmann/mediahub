/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.html.internal

import com.google.inject._

import org.ops4j.peaberry._
import Peaberry._
import org.ops4j.peaberry.util._
import Attributes._
import TypeLiterals._

import org.mediahub.views.ViewRenderer

import org.mediahub.html._

import org.mediahub.rest.{RestRegistrar, RestRegistry}

class OsgiModule extends AbstractModule {

  @Provides
  def htmlWriter(rendererProvider: Provider[ViewRenderer]): HtmlViewMessageBodyWriter = {
    new HtmlViewMessageBodyWriter {
      def renderer = rendererProvider.get
    }
  }

  def configure {
    bind(export(classOf[HtmlRestRegistrar])).toProvider(service(classOf[HtmlRestRegistrar]).export)
    bind(classOf[ViewRenderer]).toProvider(service(classOf[ViewRenderer]).single)
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
