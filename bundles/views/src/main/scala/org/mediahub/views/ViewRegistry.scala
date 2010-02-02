/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import scala.actors._
import Actor._

import org.mediahub.util.Types._
import org.mediahub.cache.{Cache, Dependency, CacheKey}

/**
 * View registry implementatione that uses the type hierarchy to resolve the views.
 */
class ViewRegistryImpl extends ViewRegistry {

  /**
   * Defines how concurrent view bindings are ranked.
   * By default the ranking of the view binding is used.
   */
  def bindingOrdering: Option[Ordering[ClassifiedBinding[_]]] = Some(ByRanking)

  /**
   * Provide the cache to use. If not specified (default) the resolved view bindings will not be cached at all.
   */
  def cache: Option[Cache] = None

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

  /**
   * actually resolve the view binding. if a cache is provided use it otherwise the view is resolved without caching the result.
   */
  protected def resolve[A, B](classifier: Classifier[A], clazz: Class[B]): Seq[ClassifiedBinding[B]] = {
    val key = BindingKey(classifier, clazz)
    cache.map(_.get(key)).getOrElse(key.compute)
  }

  /**
   * the binding key knows how to compute the view binding for a classifier and a given class.
   */
  private case class BindingKey[A, B](val classifier: Classifier[A], val clazz: Class[B]) extends CacheKey[Seq[ClassifiedBinding[B]]] {
    def compute = {
      def resolve(bindings: Set[ClassifiedBinding[_]]): Seq[ClassifiedBinding[B]] = {

        /**
         * verifies that the type class of the binding matches the given binding.
         * If that is the case we also delegate to Classifier#matches of the binding to let
         * the classifier match against itself and possibly a context like the current language, site or whatever.
         */
        def matches(binding: ClassifiedBinding[_], clazz: Class[_]) = {
          binding.clazz == clazz && binding.classifier.matches(classifier)
        }

        /**
         * Determine the first binding from a set of bindings
         * It uses the provided bindingOrdering to sort the bindings before the first binding is returned
         * If no ordering is defined the first binding will be returned.
         */
        def firstOf(matchedBindings: Iterable[ClassifiedBinding[_]]): Option[ClassifiedBinding[_]] = {
          bindingOrdering.map(matchedBindings.toSeq.sortWith(_))
                         .getOrElse(matchedBindings)
                         .headOption
        }

        // resolve the bindings
        val foundBindings = for(typeClazz <- typesOf(clazz).toList;
                                binding <- firstOf(bindings.filter(matches(_, typeClazz))))
                                  yield (binding.asInstanceOf[ClassifiedBinding[B]])
        foundBindings.toSeq
      }

      // place the dependency
      Cache.dependsOn(viewBindings)

      // lookup the bindings
      registrar ! ResolveBindings(self)

      // receive the bindings and resolve the result if possible.
      self.receive {
        case Bindings(bindings) => resolve(bindings)
        case other => error(self + " has received unexpected message of " + other)
      }
    }
  }

  private case class Added(binding: ClassifiedBinding[_])
  private case class Removed(binding: ClassifiedBinding[_])
  private case class ResolveBindings(receiver: Actor)
  private case class Bindings(bindings: Set[ClassifiedBinding[_]])

  object viewBindings extends Dependency

  private[this] val registrar = actor {
    var bindings = Set[ClassifiedBinding[_]]()
    loop {
      react {
        case Added(binding) => add(binding)
        case Removed(binding) => remove(binding)
        case ResolveBindings(receiver) => receiver ! Bindings(bindings)
        case other => error(self + " has received unexpected message of " + other)
      }
    }

    def add(binding: ClassifiedBinding[_]) {
      bindings += binding
      invalidateBindings
    }

    def remove(binding: ClassifiedBinding[_]) {
      bindings -= binding
      invalidateBindings
    }

    def invalidateBindings {
      cache.map(_.invalidate(viewBindings))
    }
  }

}

/**
 * orders classified bindings by their defined ranking.
 * If a binding does not define a ranking it will automatically have the lowest ranking
 */
object ByRanking extends Ordering[ClassifiedBinding[_]] {
  def compare(lhs: ClassifiedBinding[_], rhs: ClassifiedBinding[_]): Int = {
    val defined = for(lhsRanking <- lhs.ranking;
                      rhsRanking <- rhs.ranking)
                        yield(lhsRanking.compare(rhsRanking))
    defined.getOrElse {
      if (lhs.ranking.isDefined) {
        1
      } else if (rhs.ranking.isDefined) {
        -1
      } else {
        0
      }
    }
  }
}