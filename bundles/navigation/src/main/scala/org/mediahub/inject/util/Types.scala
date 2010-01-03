package org.mediahub.inject.util

import java.lang.Class
import java.lang.reflect.{ParameterizedType, Type}
import scala.collection.immutable._
import com.google.inject.util.Types._

object Types {
    def seqOf[A](implicit clazz: Class[A]): ParameterizedType = {
        newParameterizedType(classOf[Seq[A]], clazz)
    }

    def listOf[A](implicit clazz: Class[A]): ParameterizedType = {
        newParameterizedType(classOf[List[A]], clazz)
    }

}
