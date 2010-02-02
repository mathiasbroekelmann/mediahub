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

/**
 * Root view classifier for an xhtml content.
 */
object xhtml extends ViewClassifier[NodeSeq]

/**
 * Uses a provided view renderer to render any kind of object.
 */
@Provider
@Produces(Array("application/xhtml+xml;charset=UTF-8", "text/html;charset=UTF-8"))
abstract class HtmlViewMessageBodyWriter extends MessageBodyWriter[AnyRef] {

  /**
   * define the renderer to use.
   */
  def renderer: ViewRenderer

  def isWriteable(clazz: Class[_], genericType: Type,
                  annotations: Array[Annotation], mediaType: MediaType): Boolean = true

  def getSize(some: AnyRef, clazz: Class[_], genericType: Type,
              annotations: Array[Annotation], mediaType: MediaType): Long = -1L

  def writeTo(some: AnyRef, clazz: Class[_], genericType: Type,
              annotations: Array[Annotation], mediaType: MediaType,
              httpHeaders: MultivaluedMap[String, Object],
              out: OutputStream): Unit = {
    val root = renderer.render(some) as xhtml
    val xhtmlAsString = Xhtml.toXhtml(root)
    val writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
    writer.print(xhtmlAsString)
    writer.close
  }
}
