package org.mediahub.sandbox

import com.coremedia.cap.content.{Content => CapContent}
import com.coremedia.objectserver.beans._
import com.coremedia.cap.common._
import com.coremedia.xml.Markup
import IdHelper._

object Content {
  type Source = {
    def getString(name: String): java.lang.String
    def getInteger(name: String): java.lang.Integer
    def getMarkup(name: String): Markup
  }
}

import Content._

trait Content {
  def id: String
  def contentId: Int
  def name: String

  type Self

  def origin: Source

}

trait PropertyType[A, B] {
  def propertyName: String
  def ownerManifest: ClassManifest[B]
  def ownerType: Class[B] = ownerManifest.erasure.asInstanceOf[Class[B]]
  def from(source: Source): A
}

case class StringType[A](propertyName: String)(implicit val ownerManifest: ClassManifest[A]) extends PropertyType[Option[String], A] {
  def from(content: Source) = Option(content.getString(propertyName))
}

case class IntType[A](propertyName: String)(implicit val ownerManifest: ClassManifest[A]) extends PropertyType[Option[Int], A] {
  def from(content: Source) = {
    content.getInteger(propertyName) match {
      case null => None
      case x => Some(x.intValue)
    }
  }
}

case class MarkupType[A](propertyName: String)(implicit val ownerManifest: ClassManifest[A]) extends PropertyType[Option[Markup], A] {
  def from(source: Source) = {
    source.getMarkup(propertyName) match {
      case null => None
      case x => Some(x)
    }
  }
}

/**
 * @param [A] defines the type for instances of this doctype.
 * @param name the name of the doctype.
 */
abstract case class Doctype[A](name: String) {
  type Parent >: A
  def superType: Option[Doctype[Parent]]
}

/**
 * Definition of a document type with name "Document"
 */
object Document extends Doctype[Document]("Document") {
  type Parent = Any
  def superType = None
  object title extends StringType[Document]("title")
  object someNumber extends IntType[Document]("someNumber")
}

trait Document extends Content {
  type Self <: Document
  // a title with a fallback to name
  def title: String = Document.title from origin getOrElse name

  // some optional int number
  def someNumber = Document.someNumber from origin
}

/**
 * Definition of a document type with name "Page"
 */
object Page extends Doctype[Page]("Page") {
  type Parent = Document
  def superType = Some(Document)
  /**
   * definition of a markup property with name text
   */
  object text extends MarkupType[Page]("text")
}

trait Page extends Document {
  type Self <: Page
  @scala.reflect.BeanProperty
  lazy val text = Page.text from origin
}

object CoremediaContent {
  implicit def contentToContentId(content: CapContent): Int = {
    IdHelper.parseContentId(content.getId)
  }
}

class CoremediaContent extends AbstractContentBean with Content {
  def origin = getContent
  def id = origin.getId
  def contentId = parseContentId(id)
  def name = origin.getName
}

class CoremediaDocument extends CoremediaContent with Document