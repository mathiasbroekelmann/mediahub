/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.html

import org.mediahub.views._

import com.google.inject.{Inject, Provider}

import scala.xml.NodeSeq

case class Xml extends ViewClassifier[NodeSeq]
case class Text extends ViewClassifier[String]

object XhtmlViews {
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
  object links extends Xml

  /**
   * render the script tags in the html head section
   */
  object scripts extends Xml
}


/**
 * define the core views for rendering html content.
 */
class HtmlViewModule @Inject() (val renderer: Provider[ViewRenderer],
                                @Context val locale: Provider[java.util.Locale]) extends ViewModule {

  import XhtmlViews._
  
  def render = renderer.get.render _

  def lang = locale.get.getLanguage

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
        {render(self) as links}
        {render(self) as scripts}
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
  }
}
