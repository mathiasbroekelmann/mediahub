/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import scala.reflect.ClassManifest._

class ViewRendererImpl(settings: ViewRendererSettings) extends ViewRenderer {
  
  def this(registry: ViewRegistry) = this(ViewRendererSettings(registry))

  /**
   * render a view for a given instance.
   */
  def render(bean: Any): IncludeViewBuilder = 
    new IncludeViewBuilderImpl(settings, bean)

  /**
   * create a renderer which uses the given function if no view could be found for a given classifier and the expected result type.
   */
  def withDefaultFor[A](f: (Any, Classifier[_]) => A)(implicit resultType: ClassManifest[A]): ViewRenderer =
    new ViewRendererImpl(settings.withDefaultFor(f))

  /**
   * create a view renderer which uses the given function to resolve the class from an instance to find view bindings.
   */
  def withClassResolver(resolver: ClassResolver): ViewRenderer =
    new ViewRendererImpl(settings.withClassResolver(resolver))
}

/**
 * @param registry the view registry which is used to resolve the views.
 * @param classResolver a strategy to resolve classes from instance.
 * @param defaultView a mapping of class to view which are used if no view could be found for a given classifier and instance class.
 *                    The key of the mapping is the expected result type of a view classifier
 */
case class ViewRendererSettings(registry: ViewRegistry,
                                classResolver: ClassResolver,
                                defaultView: Map[Class[_], (Any, Classifier[_]) => Any]) {

  def withClassResolver(resolver: ClassResolver) =
    new ViewRendererSettings(registry, resolver, defaultView)

  def withDefaultFor[A](f: (Any, Classifier[_]) => A)(implicit resultType: ClassManifest[A]) =
    new ViewRendererSettings(registry, classResolver, defaultView + (resultType.erasure -> f))

  def resolve[A, B](classifier: ViewClassifier[B], bean: A): Seq[ViewBinding[A, B]] = {
    val clazz = fromClass(classResolver.resolveClass(bean))
    registry.resolve(classifier)(clazz)
  }

  def resolve[A, B, C](classifier: ParamViewClassifier[C, B], bean: A): Seq[ParameterViewBinding[A, B, C]] = {
    val clazz = fromClass(classResolver.resolveClass(bean))
    registry.resolve(classifier)(clazz)
  }

  def renderDefault[A](some: Any, classifier: Classifier[_], resultType: Class[A]): Option[A] = {
    defaultView.get(resultType)
               .map(view => {
                   Option(view(some, classifier))
                     .map(_.asInstanceOf[A])
                     .getOrElse(error(format("View %s returned null for view of %s as %s", view, some, classifier)))
                 })
  }
}

object ViewRendererSettings {
  def apply(registry: ViewRegistry): ViewRendererSettings =
    ViewRendererSettings(registry, ViewRendererSettings.defaultClassResolver, Map.empty)

  def defaultClassResolver = new ClassResolver {
    def resolveClass[A, B>:A](some: A): Class[B] = {
      some match {
        case ref: AnyRef => ref.getClass.asInstanceOf[Class[B]]
        case unknown => error(format("could not resolve class from %s", unknown))
      }
    }
  }
}

class IncludeViewBuilderImpl[A](settings: ViewRendererSettings, bean: A) extends IncludeViewBuilder {

  def as[B](classifier: ViewClassifier[B]): B = {
    /**
     * create a chain for the given views.
     */
    def chain(views: Seq[ViewBinding[A, B]]): ViewChain[A, B] = new ViewChain[A, B] {
      def render(some: A): B = {
        views.headOption
              // render the first found view
             .map(_.render(some, chain(views.tail)))
              // or if that is not found render the fallback for the expected result type
             .orElse(settings.renderDefault(some, classifier, classifier.resultType))
              // of that is also not possible raise an error
             .getOrElse(error(format("Could not resolve view %s for %s of type %s", classifier, bean, classifier.resultType)))
      }
    }

    val bindings = settings.resolve(classifier, bean)
    
    chain(bindings).render(bean)
  }

  /**
   * render the given parameterized view for the previously defined bean instance.
   */
  def as[B, C](classifier: ParamViewClassifier[B, C]) =
    new ParamIncludeViewBuilder[B, C] {
      def withParameter(params: C): B = {
        /**
         * create a chain for the given views.
         */
        def chain(views: Seq[ParameterViewBinding[A, C, B]]): ParameterViewChain[A, C, B] = new ParameterViewChain[A, C, B] {
          def render(some: A, param: C): B = {
            views.headOption
                  // render the first found view
                 .map(_.render(some, param, chain(views.tail)))
                  // or if that is not found render the fallback for the expected result type
                 .orElse(settings.renderDefault(some, classifier, classifier.resultType))
                  // of that is also not possible raise an error
                 .getOrElse(error(format("Could not resolve view %s for %s of type %s", classifier, bean, classifier.resultType)))
          }
        }

        val bindings = settings.resolve(classifier, bean)

        chain(bindings).render(bean, params)
      }
    }
}
