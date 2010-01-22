package org.mediahub.frontend.html.desktop

import scala.actors.Actor._
import org.mediahub.util.Types._

class ViewRegistryImpl extends ViewRegistry {

  private case class Resolve(classifier: Classifier, clazz: Class[_])
  private case class Add(binding: ClassifiedBinding[_])
  private case class Remove(binding: ClassifiedBinding[_])

  private val resolver = actor {

    case class BindingKey(classifier: Classifier, clazz: Class[_])

    var classifiedBindings = Map[BindingKey, Option[ClassifiedBinding[_]]]()
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
                  val resolvedBinding = resolve(bindings, classifier, clazz)
                  classifiedBindings += key -> resolvedBinding
                  resolvedBinding
                }
              )
              reply(binding)
          }
        }
      }
    }

    def resolve[A](bindings: Iterable[ClassifiedBinding[_]], classifier: Classifier, clazz: Class[A]): Option[ClassifiedBinding[A]] = {
      // TODO: implement actual resolving of view binding
      val types = typesOf(clazz)
      val binding = types.find { clazz =>
        bindings.find(b => b.classifier == classifier && b.clazz == clazz).isDefined
      }.map(_.asInstanceOf[ClassifiedBinding[A]])
      binding.flatMap { b =>
        if(classifier.isValid(b)) Some(b) else None
      }
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
  def resolve[A, B](classifier: ViewClassifier[B])(implicit clazz: ClassManifest[A]): Option[ViewBinding[A, B]] = {
    (resolver !? Resolve(classifier, clazz.erasure)).asInstanceOf[Option[ViewBinding[A, B]]]
  }

  /**
   * resolve a parameterized view binding
   */
  def resolve[A, B, C](classifier: ParamViewClassifier[C, B])(implicit clazz: ClassManifest[A]): Option[ParameterViewBinding[A, B, C]] = {
    (resolver !? Resolve(classifier, clazz.erasure)).asInstanceOf[Option[ParameterViewBinding[A, B, C]]]
  }
}
