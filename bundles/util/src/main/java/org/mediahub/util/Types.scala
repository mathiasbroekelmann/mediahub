/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import java.lang.reflect.{Type}

/**
 * Provides functions related to classes and generic types.
 *
 * To get the class hierarchy for a class use
 * <pre>
 * classOf[SomeClass] hierarchy
 * </pre>
 *
 * To identify class es that implement a generic interface for a specific type use this:
 * <pre>
 * trait SomeService[A]
 * trait FooType
 * val someServiceForFooType = classOf[SomeService] withTypeArguments(classOf[FooType])
 * </pre>
 * This is mostly used where you need to filter out the implementations which can be applied for a conrete class type.
 * see #implementedFor to resolve services by their generic type definition.
 */
object Types {

  /**
   * provide a builder to resolve the class hierarchy of a given class.
   *
   * Use it like this:
   * <pre>
   * import org.mediahub.util.Types._
   *
   * assertThat(classOf[String] hierarchy, is(Seq(classOf[String], classOf[Serializable], classOf[Comparable[String]], classOf[CharSequence], classOf[Object]))
   * </pre>
   *
   * @param providedClazz the class to resolve the class hierarchy of.
   */
  implicit def classHierarchyOf[A](providedClazz: Class[A]) = new {
    /**
     * Get the class hierarchy for  the given class. The hierarchy is build in the following order:
     *
     * <ul>
     * <li>the given clazz
     * <li>the direct interfaces of clazz in the order as they have been declared
     * <li>the parent interfaces of the interfaces (recursive)
     * <li>the super class (if any) repeating the previous steps for it
     *
     * @return the class hierarchy ranging from buttom to root type.
     */
    def hierarchy[B>:A]: Seq[Class[B]] = {

      // the filter is used to unique the result set.
      val filter = scala.collection.mutable.Set[Class[B]]()

      // collect all the types of the given class list
      def types(clazzes: List[Class[B]]): Stream[Class[B]] = {
        clazzes.map(hierarchyOf(_)).toStream.flatten
      }

      // collect the types of all interfaces implemented by the given class
      def interfacesOf(clazz: Class[B]): Stream[Class[B]] = {
        val interfaces = clazz.getInterfaces.toList.asInstanceOf[List[Class[B]]]
        types(interfaces)
      }

      // collect the hierarchy of the given class
      def hierarchyOf(clazz: Class[B]): Stream[Class[B]] = {
        if(filter.add(clazz)) {
          Option(clazz.getSuperclass.asInstanceOf[Class[B]]) match {
            case Some(superclass) => Stream.cons(clazz, interfacesOf(clazz)).append(hierarchyOf(superclass))
            case None => Stream.cons(clazz, interfacesOf(clazz))
          }
        } else {
          Stream.Empty
        }
      }

      hierarchyOf(providedClazz.asInstanceOf[Class[B]])
    }
  }

  /**
   * resolve the class hierarchy for a given class type.
   */
  @deprecated("use 'classOf[SomeClass] hierarchy'")
  def typesOf[A, B>:A](clazz: Class[A]): Seq[Class[B]] = clazz hierarchy

  /**
   * start building a parameterized type.
   *
   * Use it like this:
   *
   * <pre>
   * trait SomeService[A]
   * trait FooType
   * // create a generic type to idenfity classes that implement SomeService for FooType
   * classOf[SomeService] withTypeArguments(classOf[FooType])
   * </pre>
   */
  implicit def parameterizedType(genericType: Type) = new {
    def withTypeArguments(typeArguments: Type*) = {
      com.google.inject.util.Types.newParameterizedType(genericType, typeArguments:_*)
    }
  }

  /**
   * Selector for list of services which should implement a specific generic type.
   * Example usage:
   *
   * <pre>
   * trait Service[A]
   * class ServiceA extends Service[String]
   * class ServiceB extends Service[Other]
   * // create a generic type that identifies a Service[String] implementation
   * val genericType = classOf[Service[_]] withTypeArguments classOf[String])
   * val services = List(new ServiceA(), new ServiceB())
   * val servicesOfString = services.flatMap (implementedFor[Service[String]](genericType))
   * assertThat(servicesOfString, is(new ServiceA() :: List.empty[Service[String]]))
   * </pre>
   */
  def implementedFor[A<:AnyRef](genericType: Type)(implicit clazz: ClassManifest[A]): (AnyRef => Traversable[A]) = { a =>
    def implementsType(clazz: Class[_]): Boolean = {
      clazz.getGenericInterfaces find(_ == genericType) match {
        case Some(x) => true
        case None => {
            val superclass = clazz.getSuperclass
            if(superclass == null) {
              false
            } else {
              implementsType(superclass)
            }
          }
      }
    }
    if(implementsType(a.getClass)) {
      List(a.asInstanceOf[A])
    } else {
      Nil
    }
  }
}
