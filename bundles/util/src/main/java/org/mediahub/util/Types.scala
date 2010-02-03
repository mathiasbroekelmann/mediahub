/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import java.lang.reflect.{Type}

object Types {

  /**
   * Get the class hierarchy for  the given class. The hierarchy is build in the following order:
   *
   * <ul>
   * <li>the given clazz
   * <li>the direct interfaces of clazz in the order as they have been declared
   * <li>the parent interfaces of the interfaces (recursive)
   * <li>the super class (if any) repeating the previous steps for it 
   */
  def typesOf[A, B>:A](clazz: Class[A]): Stream[Class[B]] = {

    val filter = scala.collection.mutable.Set[Class[B]]()

    def types(clazzes: List[Class[B]]): Stream[Class[B]] = {
      clazzes.map(of(_)).toStream.flatten
    }

    def interfacesOf(clazz: Class[B]): Stream[Class[B]] = {

      val interfaces = clazz.getInterfaces.toList.asInstanceOf[List[Class[B]]]
      types(interfaces)
    }

    def of(clazz: Class[B]): Stream[Class[B]] = {
      if(filter.add(clazz)) {
        Option(clazz.getSuperclass.asInstanceOf[Class[B]]) match {
          case Some(superclass) => Stream.cons(clazz, interfacesOf(clazz)).append(of(superclass))
          case None => Stream.cons(clazz, interfacesOf(clazz))
        }
      } else {
        Stream.Empty
      }
    }

    of(clazz.asInstanceOf[Class[B]])
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
   * val genericType = com.google.inject.util.Types.newParameterizedType(classOf[Service[_], classOf[String])
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
