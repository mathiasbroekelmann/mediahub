/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.jcr

import scala.collection.JavaConversions._

import org.mediahub.util.Dates._

import javax.jcr.{Node, Repository, Property, Value, PropertyType}
import Repository._

object JcrDsl {

  implicit def nodeToRichNode(node: Node) = new RichNode(node)

  implicit def unstructuredNodeFactory(node: Node, name: String): Node = {
    node.addNode(name)
  }

  /**
   * wrap a jcr repository to provide a rich interface to common descriptors.
   */
  implicit def repositoryToRichRepository(repository: Repository) = new RichRepository(repository)

  /**
   * TODO: add more fields from magic descriptors in jcr Repository interface
   */
  class RichRepository(repository: Repository)  {
    import repository._
    import Repository._
    /**
     * The name of this repository implementation.
     */
    def name = getDescriptor(REP_NAME_DESC)
    /**
     * The name of the repository vendor.
     */
    def vendor = getDescriptor(REP_VENDOR_DESC)
    /**
     * The URL of the repository vendor.
     */
    def vendorUrl = getDescriptor(REP_VENDOR_URL_DESC)

    /**
     * The version of this repository implementation.
     */
    def version = getDescriptor(REP_VERSION_DESC)
    /**
     * The name of the specification that this repository
     * implements. For JCR 2.0 the value is "Content Repository for Java Technology API".
     */
    def specification = getDescriptor(SPEC_NAME_DESC)
    /**
     * The version of the specification that this
     * repository implements. For JCR 2.0 the value is "2.0".
     */
    def specificationVersion = getDescriptor(SPEC_VERSION_DESC)

    /**
     * Returns <code>true</code> if
     * and only if repository content can be updated through the JCR API (as
     * opposed to having read-only access).
     *
     * @since JCR 2.0
     */
    def writeable = getDescriptorValue(WRITE_SUPPORTED).getBoolean

    /**
     * Indicates the stability of identifiers.
     */
    def identifierStability: IdentifierStability = getDescriptor(IDENTIFIER_STABILITY) match {
      case IDENTIFIER_STABILITY_INDEFINITE_DURATION => Indefinite
      case IDENTIFIER_STABILITY_METHOD_DURATION => Method
      case IDENTIFIER_STABILITY_SAVE_DURATION => Save
      case IDENTIFIER_STABILITY_SESSION_DURATION => Session
    }
  }
}

sealed trait IdentifierStability {
  def id: String
  override def toString = id
}

/**
 * Indicates that identifiers are guaranteed stable within a single
 * save/refresh cycle.
 *
 * @since JCR 2.0
 */
case object Method extends IdentifierStability { val id = IDENTIFIER_STABILITY_METHOD_DURATION }
/**
 * Indicates that identifiers are guaranteed stable within a single
 * session.
 *
 * @since JCR 2.0
 */
case object Save extends IdentifierStability { val id = IDENTIFIER_STABILITY_SAVE_DURATION }
/**
 * Indicates that identifiers are guaranteed to be stable forever.
 *
 * @since JCR 2.0
 */
case object Session extends IdentifierStability { val id = IDENTIFIER_STABILITY_SESSION_DURATION }
/**
 * Indicates that identifiers are guaranteed to be stable forever.
 *
 * @since JCR 2.0
 */
case object Indefinite extends IdentifierStability { val id = IDENTIFIER_STABILITY_INDEFINITE_DURATION }

class RichNode(node: Node) {

  import JcrDsl._

  private def traverseAs[A](factory: => java.util.Iterator[_])(implicit clazz: ClassManifest[A]): Traversable[A] = {
    factory.asInstanceOf[java.util.Iterator[A]].toStream
  }

  /**
   * get all child nodes.
   */
  def nodes: Traversable[Node] =
    traverseAs[Node](node.getNodes)

  /**
   * get all child nodes.
   * @see Node#getNodes(String)
   */
  def nodes(namePattern: String): Traversable[Node] =
    traverseAs[Node](node.getNodes(namePattern))

  /**
   * get all child nodes.
   * @see Node#getNodes(String[])
   */
  def nodes(namePattern: String, morePatterns: String*): Traversable[Node] =
    traverseAs[Node](node.getNodes((namePattern +: morePatterns).toArray))

  /**
   * access a node at the given relative path.
   * If the node or any of its parents does not exists it will be created by the given create function.
   */
  def node(path: String)(implicit create: (Node, String) => Node): Node = {
    if(node.hasNode(path)) {
      node.getNode(path)
    } else {
      path.split("/", 2) match {
        case Array(name) => create(node, name)
        case Array(name, tail) => node(name).node(tail)
      }
    }
  }

  /**
   * access a node alternative to #node which allows the definition of a
   * create method to specify how the node is created.
   */
  def |=(path: String)(implicit create: (Node, String) => Node): Node =
    node(path)

  /**
   * apply a function to the current node.
   */
  def |=[T](f: Node => T) = f(node)

  /**
   * add a single property value to the node
   */
  def +(prop: (String, Any)) = ++(prop)

  /**
   * Add one or mode properties to the node.
   * To remove a property define null as its value
   * use a traversable to define multiple values for a property.
   */
  def ++(props: (String, Any)*): Seq[Property] = {

    /**
     * resolve the jcr value from a given value.
     *
     * @param value the value to create a jcr value from
     *
     * @return an either having the created jcr value as left and the input value as right
     */
    def jcrValueFrom[A](value: A): Either[Value, A] = {

      val factory = node.getSession.getValueFactory

      /**
       * create a jcr binary from an inputstream
       */
      def binary(in: java.io.InputStream) = {
        factory.createValue(factory.createBinary(in))
      }

      /**
       * create a date jcr value from a calendar
       */
      def date(date: java.util.Calendar) = {
        factory.createValue(date)
      }

      def nodeReference(reference: Node) = {
        reference.addMixin("mix:referenceable")
        factory.createValue(reference)
      }

      (value match {
          case v: String => Some(factory createValue v)
          case v: Int => Some(factory createValue v)
          case v: Long => Some(factory createValue v)
          case v: Boolean => Some(factory createValue v)
          case v: Double => Some(factory createValue v)
          case v: java.net.URI => Some(factory.createValue(v.toString, PropertyType.URI))
          case v: java.math.BigDecimal => Some(factory createValue v)
          case v: java.io.InputStream => Some(binary(v))
          case v: javax.jcr.Binary => Some(factory createValue v)
          case v: javax.jcr.Value => Some(v)
          case v: javax.jcr.Node => Some(nodeReference(v))
          case v: Array[Byte] => Some(binary(new java.io.ByteArrayInputStream(v)))
          case v: java.util.Date => Some(date(v))
          case v: java.util.Calendar => Some(date(v))
          case v: org.joda.time.ReadableInstant => Some(date(v))
          case other => None
        }).toLeft(value)
    }

    /**
     * resolves all values from a given traversable into jcr values.
     */
    def jcrValuesFrom(t: Traversable[Any]): Traversable[Value] = {

      def validate(value: Either[Value, _]): Value = value match {
        case Left(valid) => valid
        case Right(invalid) => error("unknown value type: " + invalid)
      }

      for (value <- t) yield validate(jcrValueFrom(value))
    }

    /**
     * set the property value to the given value.
     * if the value is null the property will be removed.
     */
    def set(name: String, value: Any) = value match {

      case t: Traversable[_] => node.setProperty(name, jcrValuesFrom(t).toArray)
      case other => {
          jcrValueFrom(other) match {
            case Left(value) => node.setProperty(name, value)
            case Right(unkown) if(unkown == null) => node.setProperty(name, null.asInstanceOf[Value])
            case Right(unkown) => error("unknown property type: " + other.toString + " for property " + name + " at " + node)
          }
        }
    }

    for (p <- props) yield set(p._1, p._2)
  }

  def ++(map: Map[String, Any]): Seq[Property] = ++(map.toSeq:_*)

  /**
   * remove the property from the node
   */
  def -(propertyName: String) = node.setProperty(propertyName, null.asInstanceOf[String])

  /**
   * remove the properties from the node
   */
  def --(propertyNames: String*) =
    for(name <- propertyNames) yield(this -(name))

  /**
   * returns the property for the given property name.
   * @throws a PathNotFoundException if the property does not exist.
   */
  def apply(propertyName: String): Property = {
    node.getProperty(propertyName)
  }

  /**
   * @return Some(property) if the property for the given name exists. Otherwise return None
   */
  def get(propertyName: String): Option[Property] = {
    if(node.hasProperty(propertyName)) {
      Some(node.getProperty(propertyName))
    } else {
      None
    }
  }

  /**
   * returns the string property value of the given property name.
   * None if no value is defined for that property. Otherwise Some(string)
   */
  def string(propertyName: String): Option[String] =
    strings(propertyName).headOption

  def boolean(propertyName: String): Option[Boolean] =
    booleans(propertyName).headOption

  def booleans(propertyName: String): Seq[Boolean] =
    valuesOf(propertyName) {v => Option(v.getBoolean)}

  def date(propertyName: String): Option[java.util.Calendar] =
    dates(propertyName).headOption

  def dates(propertyName: String): Seq[java.util.Calendar] =
    valuesOf(propertyName) {v => Option(v.getDate)}

  def decimal(propertyName: String): Option[java.math.BigDecimal] =
    decimals(propertyName).headOption

  def decimals(propertyName: String): Seq[java.math.BigDecimal] =
    valuesOf(propertyName) {v => Option(v.getDecimal)}

  def long(propertyName: String): Option[Long] =
    longs(propertyName).headOption

  def longs(propertyName: String): Seq[Long] =
    valuesOf(propertyName) {v => Option(v.getLong)}

  def reference(propertyName: String): Option[Node] =
    references(propertyName).headOption

  def references(propertyName: String): Seq[Node] = {
    valuesOf(propertyName) {v => 
      for(identifier <- Option(v.getString))
        yield node.getSession.getNodeByIdentifier(identifier)
    }
  }

  /**
   * returns all string value of the given property name.
   * Returns an empty sequence if no value is defined for that property.
   */
  def strings(propertyName: String): Seq[String] =
    valuesOf(propertyName) {v => Option(v.getString)}

  /**
   * resolves a single value from a property of the node
   * If the property is multivalued the first Some(x) value that is returned by the given function will be the result.
   * If there is no value or the property does not exist None is returned.
   *
   * @param propertyName the name of the property to get the value from
   * @param f the function which is used to actually get the value from a jcr value instance.
   *
   * @return a sequence of resolved values for the given property.
   */
  def valueOf[A](propertyName: String)(f: (Value => Option[A])): Option[A] = {
    valuesOf(propertyName)(f).headOption
  }

  /**
   * resolves the values from a property of the node.
   * It takes care if the property has either a multiple values or is single valued.
   * If there is no property for the given name None is retured.
   * This is also the case for an existent property with empty value list.
   * Results from the given function f that are None will be filtered out from the result set.
   *
   * @param propertyName the name of the property to get the values from
   * @param f the function which is used to actually get the value from a jcr value instance.
   *
   * @return a sequence of resolved values for the given property.
   */
  def valuesOf[A](propertyName: String)(f: (Value => Option[A])): Seq[A] = {
    if (node.hasProperty(propertyName)) {
      val property = node.getProperty(propertyName)
      if(property.isMultiple) {
        val values = for (value <- property.getValues;
                          result <- f(value))
                            yield(result)
        values
      } else {
        f(property.getValue).toSeq
      }
    } else {
      Seq.empty
    }
  }
}
