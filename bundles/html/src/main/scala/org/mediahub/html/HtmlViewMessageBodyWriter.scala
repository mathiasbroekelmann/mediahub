/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.html

import javax.ws.rs.{Produces}
import javax.ws.rs.ext.{Provider, MessageBodyWriter}
import javax.ws.rs.core.{MediaType, MultivaluedMap}

import java.io.{OutputStream, OutputStreamWriter, PrintWriter}
import java.lang.annotation.Annotation
import java.lang.reflect.Type

import java.lang.reflect.Type

import org.mediahub.views.{ViewRenderer, ViewClassifier}

import scala.xml.{Xhtml, NodeSeq}
import Xhtml.toXhtml

import org.mediahub.html.XhtmlViews.xhtml

import java.nio.charset.Charset

/**
 * Uses a provided view renderer to render any kind of object.
 */
@Provider
@Produces(Array("application/xhtml+xml;charset=UTF-8", "text/html;charset=UTF-8"))
class HtmlViewMessageBodyWriter extends MessageBodyWriter[AnyRef] {

  /**
   * define the renderer to use.
   */
  def renderer: Option[ViewRenderer] = None

  def charset: Charset = Charset.forName(charsetName)

  def charsetName = "UTF-8"

  def isWriteable(clazz: Class[_], genericType: Type,
                  annotations: Array[Annotation], mediaType: MediaType): Boolean = true

  def getSize(self: AnyRef, clazz: Class[_], genericType: Type,
              annotations: Array[Annotation], mediaType: MediaType): Long = -1L

  def writeTo(self: AnyRef, clazz: Class[_], genericType: Type,
              annotations: Array[Annotation], mediaType: MediaType,
              httpHeaders: MultivaluedMap[String, Object],
              out: OutputStream): Unit = {

    for(someRenderer <- renderer) {
      val root = someRenderer.render(self) as xhtml withParameter(charset)
      val xhtmlAsString = toXhtml(root)
      val writer = new PrintWriter(new OutputStreamWriter(out, charset));
      writer.print(xhtmlAsString)
      writer.close
    }
  }
}
