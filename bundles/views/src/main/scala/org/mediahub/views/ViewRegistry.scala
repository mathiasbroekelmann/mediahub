/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import scala.actors.Actor._
import org.mediahub.util.Types._

class ViewRegistryImpl extends ViewRegistry {


  private case class BindingKey(val classifier: Classifier[_], val clazz: Class[_])
  private var classifiedBindings = Map[BindingKey, Seq[ClassifiedBinding[_]]]()
  private var bindings = Set[ClassifiedBinding[_]]()

  def resolve[A](classifier: Classifier[_], clazz: Class[A]): Seq[ClassifiedBinding[A]] = {
    val key = BindingKey(classifier, clazz)
    val cachedBinding = classifiedBindings.get(key)
    val binding = cachedBinding.getOrElse{
        val resolvedBindings = resolve(bindings, classifier, clazz)
        classifiedBindings = classifiedBindings + (key -> resolvedBindings)
        resolvedBindings
      }
    binding.asInstanceOf[Seq[ClassifiedBinding[A]]]
  }

  def resolve[A, B](bindings: Iterable[ClassifiedBinding[_]], classifier: Classifier[_], clazz: Class[A]): Seq[ClassifiedBinding[A]] = {
    // TODO: implement actual resolving of view binding
    val foundBindings = for(typeClazz <- typesOf(clazz).toList;
                            binding <- bindings.filter(b => b.classifier == classifier && b.clazz == typeClazz);
                            if(classifier isValid binding))
                              yield (binding.asInstanceOf[ClassifiedBinding[A]])
    foundBindings.toSeq
  }

  case class Added(binding: ClassifiedBinding[_])
  case class Removed(binding: ClassifiedBinding[_])

  private val registrar = actor {
    loop {
      react {
        case Added(binding) => add(binding)
        case Removed(binding) => remove(binding)
      }
    }

    def add(binding: ClassifiedBinding[_]) {
      bindings += binding
      classifiedBindings = Map.empty
    }

    def remove(binding: ClassifiedBinding[_]) {
      bindings -= binding
      classifiedBindings = Map.empty
    }
  }

  /**
   * register a view binding.
   */
  def register[A](binding: ClassifiedBinding[A]): ViewBindingRegistration = {
    registrar ! Added(binding)
    new ViewBindingRegistration {
      def unregister {
        registrar ! Removed(binding)
      }
    }
  }

  /**
   * resolve a view binding.
   */
  def resolve[A, B](classifier: ViewClassifier[B])(implicit clazz: ClassManifest[A]): Seq[ViewBinding[A, B]] = {
    val bindings = resolve(classifier, clazz.erasure)
    bindings.asInstanceOf[Seq[ViewBinding[A, B]]]
  }

  /**
   * resolve a parameterized view binding
   */
  def resolve[A, B, C](classifier: ParamViewClassifier[C, B])(implicit clazz: ClassManifest[A]): Seq[ParameterViewBinding[A, B, C]] = {
    val bindings = resolve(classifier, clazz.erasure)
    bindings.asInstanceOf[Seq[ParameterViewBinding[A, B, C]]]
  }
}
