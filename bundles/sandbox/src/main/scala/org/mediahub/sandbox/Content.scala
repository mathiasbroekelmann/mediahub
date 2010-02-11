package org.mediahub.sandbox

import com.coremedia.cap.content.{Content => CapContent}
import com.coremedia.objectserver.beans._
import com.coremedia.cap.common._
import com.coremedia.xml.Markup
import IdHelper._

import org.joda.time.DateTime

import org.mediahub.util.Dates._

import scala.collection.JavaConversions._
import scala.xml._

object Content extends Doctype[Content]("Content")

trait Content extends ContentBean {

  /**
   * @return the content itself.
   */
  def content = getContent

  /**
   * @return the id of the content.
   */
  def id = content.getId

  /**
   * @return the name of the content.
   */
  def name = content.getName
}

object Page extends Doctype[Page]("Page") {
  object title extends StringProperty(this)
  object flag extends BooleanProperty(this)
  object number extends IntProperty(this)
  object date extends DateTimeProperty(this)
  object childs extends LinksProperty(this, Page)
  object parents extends ReferrersProperty(Page.childs)
  object xml extends XmlProperty(this)
}

/**
 * A page with some properties of common types.
 */
trait Page extends Content {
  def title = Page.title of content getOrElse name
  def flag = Page.flag of content getOrElse true
  def number = Page.number of content
  def date = Page.date of content
  def childs = Page.childs of this
  def firstChild = childs.headOption
  def parents = Page.parents of this
  def firstParent = parents.headOption
  def xmlAsNodeSeq = Page.xml of content asNodeSeq
  def xmlAsMarkup = Page.xml of content asMarkup
}

class SomePage extends AbstractContentBean with Page

/**
 * Identifies a single content doctype.
 *
 * @param name the name of the doctype
 */
case class Doctype[ContentBeanType<:ContentBean](name: String)

/**
 * Common base trait for a single property. A property is always owned by a specific doctype.
 * The name of the property is automatically determined by the used object name in the doctype object for this property.
 * If the names should be different overwrite #name
 */
trait PropertyType[OwnerType<:ContentBean] {

  /**
   * @return the owner doctype of the property.
   */
  def owner: Doctype[OwnerType]
  
  /**
   * The name of the property. Overwrite it to specify a custom name.
   *
   * @return the name of the property.
   */
  def name: String = propertyName

  /**
   * Computes the property name by using the declared field name for this property in the owner doctype instance.
   */
  private lazy val propertyName = {
    val fields = owner.getClass.getDeclaredFields
    fields foreach (_.setAccessible(true))
    val fieldName = for(field <- fields;
                        if(this == field.get(owner)))
                          yield(field.getName)
    fieldName.head
  }
}

/**
 * Resolves all referrers of a given link property.
 *
 * @param owner the doctypes that own the referrers. This is the doctype to which the link property is pointing to
 */
case class ReferrersProperty[TargetType<:ContentBean,
                             ReferrersType<:ContentBean](linkProperty: LinksProperty[ReferrersType, TargetType]) (implicit val referenceType: ClassManifest[ReferrersType]) extends References[ReferrersType] {

  assert(linkProperty != null, "link property is null")
  
  /**
   * get the content beans of this property of the defined type from the given content bean owner.
   */
  def of[A>:ReferrersType](contentBean: TargetType): Traversable[A] = {
    // create content beans for links
    of(contentBean.getContent, createBeanBy(contentBean))
  }

  /**
   * get the content beans of this property of the defined type from the given content owner.
   *
   * @param content the content instance of the links property to resolve the referrers from.
   */
  def of[A>:ReferrersType](content: CapContent, beanFactory: CapContent => Any): Traversable[A] = {

    // create content beans for links
    referencedBeans(beanFactory) {
      referrersOf(content)
    }
  }

  def referrersOf(content: CapContent): Traversable[CapContent] = {
    // get all referrers as a stream to lazy create content bean on demand.
    content.getReferrersWithDescriptor(linkProperty.owner.name, linkProperty.name).toStream
  }
}

/**
 * mixin trait for resolving references like links or referrers.
 */
trait References[ReferenceType] {

  /**
   * define the reference type
   */
  def referenceType: ClassManifest[ReferenceType]

  /**
   * Create the factory function for content beans by using the content bean factory of the content bean.
   */
  def createBeanBy(contentBean: ContentBean): CapContent => Any =
    createBeanBy(contentBean.getContentBeanFactory)

  /**
   * Create the factory function for the content by using the given content bean factory.
   */
  def createBeanBy(contentBeanFactory: ContentBeanFactory): CapContent => Any =
    contentBeanFactory.createBeanFor _

  /**
   * Get the referenced beans for the given references.
   */
  def referencedBeans[A>:ReferenceType](createBean: CapContent => Any)(references: => Traversable[CapContent]): Traversable[A] = {
    // create the content beans
    val beans = for(reference <- references) yield createBean(reference)

    // get beans matching the given type only
    beans.partialMap {
      case validbean: ReferenceType if referenceType.erasure.isInstance(validbean) => validbean
    }
  }
}

/**
 * Resolves the references of a link property.
 *
 * @param owner the doctype which own this link property
 * @param target the doctype to which this link points to.
 */
case class LinksProperty[OwnerType<:ContentBean,
                         ReferenceType<:ContentBean](owner: Doctype[OwnerType],
                                                     target: Doctype[ReferenceType]) (implicit val referenceType: ClassManifest[ReferenceType]) extends PropertyType[OwnerType]
                                                                                                                                                             with References[ReferenceType] {
  assert(owner != null, "owner is null")
  assert(target != null, "target doctype is null")

  /**
   * get the content beans of this property of the defined type from the given content bean owner.
   */
  def of[A>:ReferenceType](contentBean: OwnerType): Traversable[A] = {
    // create content beans for links
    of(contentBean.getContent, createBeanBy(contentBean))
  }

  /**
   * get the content beans of this property of the defined type from the given content owner.
   *
   * @param content the owner content instance having
   */
  def of[A>:ReferenceType](content: CapContent, beanFactory: CapContent => Any): Traversable[A] = {

    // create content beans for links
    referencedBeans(beanFactory) {
      // get all links as a stream to lazy create content bean on demand.
      content.getLinks(name).toStream
    }
  }
}

/**
 * some support functions to transform markup instances to different types.
 */
object Markups {
  implicit def markupToNodeSeq(markup: Markup) = new scala.xml.NodeSeq {
    def theSeq: Seq[Node] = Option(markup) match {
      case Some(markup) => XML.load(markup.asSaxSource.getInputSource)
      case None => NodeSeq.Empty
    }
  }
}

import Markups._

/**
 * Identifies a xml property type.
 *
 * @param owner the owner of the property.
 */
case class XmlProperty[OwnerType<:ContentBean](owner: Doctype[OwnerType]) extends PropertyType[OwnerType] {
  def of(content: CapContent) = new {
    def asNodeSeq: NodeSeq = content.getMarkup(name)
    def asMarkup: Option[Markup] = Option(content.getMarkup(name))
  }
}

/**
 * Identifies a date property type.
 *
 * @param owner the owner of the property.
 */
case class DateTimeProperty[OwnerType<:ContentBean](owner: Doctype[OwnerType]) extends PropertyType[OwnerType] {
  def of(content: CapContent): Option[DateTime] =
    Option(content.getDate(name)).map(calendarToDate(_))
}

/**
 * Identifies an int property type.
 *
 * @param owner the owner of the property.
 */
case class IntProperty[OwnerType<:ContentBean](owner: Doctype[OwnerType]) extends PropertyType[OwnerType] {
  def of(content: CapContent) =
    Option(content.getInteger(name))
}

/**
 * Identifies a boolean property type.
 *
 * @param owner the owner of the property.
 */
case class BooleanProperty[OwnerType<:ContentBean](owner: Doctype[OwnerType]) extends PropertyType[OwnerType] {
  def of(content: CapContent) =
    Option(content.getBoolean(name))
}

/**
 * Identifies a string property type.
 *
 * @param owner the owner of the property.
 */
case class StringProperty[OwnerType<:ContentBean](owner: Doctype[OwnerType]) extends PropertyType[OwnerType] {
  def of(content: CapContent) =
    Option(content.getString(name))
}