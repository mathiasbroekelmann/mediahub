/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

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
  def typesOf(clazz: Class[_]): Stream[Class[_]] = {

    def types(clazzes: List[Class[_]]): Stream[Class[_]] = {
      clazzes.map(of(_)).toStream.flatten
    }

    def interfacesOf(clazz: Class[_]): Stream[Class[_]] = {

      val interfaces = clazz.getInterfaces.toList
      interfaces.toStream.append(types(interfaces))
    }

    def of(clazz: Class[_]): Stream[Class[_]] = {
      Option(clazz.getSuperclass) match {
        case Some(superclass) => Stream.cons(clazz, (interfacesOf(clazz) :: of(superclass) :: Nil).toStream.flatten)
        case None => Stream.cons(clazz, interfacesOf(clazz))
      }
    }

    of(clazz)
  }
}
