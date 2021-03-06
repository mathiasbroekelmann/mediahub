/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.html

import org.mediahub.views._
import scala.xml.NodeSeq

import org.mediahub.web.links.LinkRenderer
import LinkRenderer._

import org.mediahub.resources.BundleResources._
import org.osgi.framework.Bundle

abstract class Xml extends ViewClassifier[NodeSeq]
abstract class Text extends ViewClassifier[String]

object XhtmlViews {
  
  /**
   * Root view classifier for an xhtml content.
   */
  object xhtml extends ParamViewClassifier[NodeSeq, java.nio.charset.Charset]

  /**
   * render the body tag in an html content
   */
  object body extends Xml

  /**
   * render the contents of the html/head tag
   */
  object head extends Xml

  /**
   * render the title content that is placed in the xhtml html/head/title tag
   */
  object title extends Text
  object description extends Text
  object author extends Text
  object keywords extends Text
  object metatags extends Xml

  /**
   * render the link tags in the html head section
   */
  object references extends Xml

  object referenceInHtmlHead extends ViewClassifier[scala.xml.Node]
}

/**
 * mixin trait to be used in view modules
 *
 * TODO: move to web-links module
 */
trait LinkRendererProxy extends LinkRenderer {

  def linkRenderer: LinkRenderer
  override def linkTo[A<:AnyRef](implicit clazz: ClassManifest[A]) = linkRenderer.linkTo(clazz)
  override def linkTo[A<:AnyRef](clazz: java.lang.Class[A]) = linkRenderer.linkTo(clazz)
  override def linkTo[A<:AnyRef](target: A) = linkRenderer.linkTo(target)
}

/**
 * mixin trait to be used in view modules
 *
 * TODO: move to views module
 */
trait ViewRendererProxy extends ViewRenderer {
  def renderer: ViewRenderer
  override def render(bean: Any) = renderer.render(bean)
}

/**
 * defines function for use in xml
 *
 * TODO: move to util module
 */
object Xml {
  implicit def optionOfStringToOptionOfText(value: Option[String]) = value.map(scala.xml.Text(_))
}

/**
 * define the core views for rendering html content.
 */
abstract class HtmlViewModule extends ViewModule with ViewRendererProxy with LinkRendererProxy {

  import XhtmlViews._

  def locale: java.util.Locale

  def lang = locale.getLanguage

  def bundle: Bundle

  def configure(binder: ViewBinder) {
    import binder._

    bindView(xhtml).of[Any] to { self =>
      <html xmlns="http://www.w3.org/1999/xhtml" xmlns:xml="http://www.w3.org/XML/1998/namespace"
        xml:lang={lang} lang={lang}>
        { render(self) as head }
        { render(self) as body }
      </html>
    }

    bindView(head).of[Any] to { self =>
      <head>
        <title>{render(self) as title}</title>
        {render(self) as metatags}
        {render(self) as references}
      </head>
    }

    bindView(title).of[Any] to { _.toString }

    bindView(metatags).of[Any] to { self =>
      <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
      <meta name="description" content={render(self) as description} />
      <meta name="author" content={render(self) as author} />
      <meta name="keywords" content={render(self) as keywords} />
      <meta http-equiv="Content-Style-Type" content="text/css" />
      <meta http-equiv="Content-Script-Type" content="text/javascript" />
    }

    bindView(references).of[Any] to { self =>
      <link rel="stylesheet" type="text/css" href={linkTo("/css/styles.css" in bundle)} />
    }
  }
}