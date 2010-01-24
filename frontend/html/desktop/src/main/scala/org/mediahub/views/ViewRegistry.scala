/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import scala.actors.Actor._
import org.mediahub.util.Types._

class ViewRegistryImpl extends ViewRegistry {

  private case class Resolve(classifier: Classifier[_], clazz: Class[_])
  private case class Add(binding: ClassifiedBinding[_])
  private case class Remove(binding: ClassifiedBinding[_])

  private val resolver = actor {

    case class BindingKey(classifier: Classifier[_], clazz: Class[_])

    var classifiedBindings = Map[BindingKey, Seq[ClassifiedBinding[_]]]()
    var bindings = Set[ClassifiedBinding[_]]()

    def act {
      loop {
        react {
          case Add(binding) => {
              bindings += binding
              classifiedBindings = Map.empty
          }
          case Remove(binding) => {
              bindings -= binding
              classifiedBindings = Map.empty
          }
          case Resolve(classifier, clazz) => {
              val key = BindingKey(classifier, clazz)
              val binding = classifiedBindings.getOrElse(key, {
                  val resolvedBindings = resolve(bindings, classifier, clazz)
                  classifiedBindings += key -> resolvedBindings
                  resolvedBindings
                }
              )
              reply(binding)
          }
        }
      }
    }

    def resolve[A, B](bindings: Iterable[ClassifiedBinding[_]], classifier: Classifier[_], clazz: Class[A]): Seq[ClassifiedBinding[A]] = {
      // TODO: implement actual resolving of view binding
      val foundBindings = for(typeClazz <- typesOf(clazz);
                              binding <- bindings.filter(b => b.classifier == classifier && b.clazz == typeClazz);
                              if(classifier isValid binding))
                                yield (binding.asInstanceOf[ClassifiedBinding[A]])
      foundBindings
    }
  }

  resolver.start

  /**
   * register a view binding.
   */
  def register[A](binding: ClassifiedBinding[A]): ViewBindingRegistration = {
    new ViewBindingRegistration {
      resolver ! Add(binding)
      def unregister {
        resolver ! Remove(binding)
      }
    }
  }

  /**
   * resolve a view binding.
   */
  def resolve[A, B](classifier: ViewClassifier[B])(implicit clazz: ClassManifest[A]): Seq[ViewBinding[A, B]] = {
    (resolver !? Resolve(classifier, clazz.erasure)).asInstanceOf[Seq[ViewBinding[A, B]]]
  }

  /**
   * resolve a parameterized view binding
   */
  def resolve[A, B, C](classifier: ParamViewClassifier[C, B])(implicit clazz: ClassManifest[A]): Seq[ParameterViewBinding[A, B, C]] = {
    (resolver !? Resolve(classifier, clazz.erasure)).asInstanceOf[Seq[ParameterViewBinding[A, B, C]]]
  }
}
