package org.mediahub.views

/**
 * Scala based DSL for view definitions.
 *
 * This DSL is 100% independent of the context where views are used. It is possible to use it in a jee environment but also in plain jse environent.
 *
 * The core concept is that one can define a view for a given type.
 * A view can be expressed as view(x) = y. x is a given instance and y is the result of the view.
 * 
 * To find a view(x) the type hierarchy of x is evaluated from bottom to top to find the most concrete view definition for x.
 *
 * A view classifier is used to differenciate views for x. That classifier also defines the type of y.
 *
 * Since views may need parameters from the context where they are used it is also possible to define view classifiers with parameter types.
 *
 * TODO: it is actually possible to define more than one view definition for the same view classifier and type.
 */

/**
 * A Classifier for a view.
 */

trait Classifier[A] {

  /**
   * The type of the view result.
   */
  def resultType: Class[A]

  /**
   * Determine of this classifier matches on the given classifier.
   * This implementation will do an equals test with this classifier.
   */
  def matches[B](classifier: Classifier[B]): Boolean =
    this == classifier

  /**
   * Implements equals to check reference equality.
   * Since classifiers are used as constant identifiers.
   */
  override def equals(obj: Any): Boolean = {
    obj match {
      case ref: AnyRef => eq(ref)
      case _ => false
    }
  }
}

/**
 * Common view classifier. Identifies a view.
 * type A defines the output of the view (String, NodeSeq, ...)
 */
abstract class ViewClassifier[A](implicit clazz: ClassManifest[A]) extends Classifier[A] {
  val resultType = clazz.erasure.asInstanceOf[Class[A]]
}

/**
 * Special view classifier which allows the definition of parameters
 * type A defines the output of the view (String, NodeSeq, ...)
 * type B defines the view parameters type(s).
 */
abstract class ParamViewClassifier[A, B](implicit clazz: ClassManifest[A]) extends Classifier[A] {
  val resultType = clazz.erasure.asInstanceOf[Class[A]]
}

/**
 * The view binder allows binding of views definitions. The kind of view depends on the provided view classifier.
 */
trait ViewBinder {
  /**
   * Binds an ordinary view which doesn't have view parameters.
   *
   * @param classifier the view classifier to bind a view for.
   *
   * @return a view binding builder to bind the actual view definition.
   */
  def bindView[A](classifier: ViewClassifier[A]): UnRankedViewBindingBuilder[A]

  /**
   * Bind a view for a paramterized view. Variant of #bindView(ViewClassifier) to support view parameters.
   *
   * @param classifier the view classifier to bind a view for.
   * @return a view binding builder to bind the actual view definition optionally receiving the provided parameters.
   */
  def bindView[A, B](classifier: ParamViewClassifier[A, B]): UnRankedParamViewBindingBuilder[A, B]

  /**
   * install the given module as a child module to this binder.
   */
  def install(module: ViewModule): Unit

  /**
   * create a new view binder which uses the provided ranking for all bound views if not specified on a single view binding.
   */
  def withRanking(ranking: Int): ViewBinder
}

/**
 * The view registry holds view binding registrations and allows to resolve them.
 */
trait ViewRegistry {
  /**
   * register a view binding.
   */
  def register[A](binding: ClassifiedBinding[A]): ViewBindingRegistration

  /**
   * Resolve view bindings for a view classifier and a type.
   */
  def resolve[A, B](classifier: ViewClassifier[B])(implicit clazz: ClassManifest[A]): Seq[ViewBinding[A, B]]

  /**
   * Resolve view bindings for a parameterized view classifier and a type.
   */
  def resolve[A, B, C](classifier: ParamViewClassifier[C, B])(implicit clazz: ClassManifest[A]): Seq[ParameterViewBinding[A, B, C]]
}

/**
 * Identifies a single view binding registration
 */
trait ViewBindingRegistration {
  /**
   * Unregister the bound view.
   */
  def unregister: Unit
}

/**
 * A view binding.
 */
trait ClassifiedBinding[A] extends Ranking {

  /**
   * The class for this binding.
   */
  def clazz: Class[A]

  /**
   * The classifier of this binding.
   */
  def classifier: Classifier[_]
}

/**
 * Provides a ranking value which can be used order things.
 */
trait Ranking {

  /**
   * @return the actual ranking for this thing. None means that there was no ranking defined.
   */
  def ranking: Option[Int]
}

/**
 * The view chain allows a view to wrap a parent view.
 */
trait ViewChain[A, B] {

  /**
   * render to parent view.
   */
  def render(some: A): B
}

trait ViewBinding[A, B] extends ClassifiedBinding[A] {
  /**
   * render the given instance of type A to produce B.
   *
   * @param some the instance to render
   * @param parent the callback to optionally render a parent view
   */
  def render(some: A, parent: ViewChain[A, B]): B
}

trait ParameterViewChain[A, B, C] {
  /**
   * render the given instance of type A and the param B to produce B
   *
   * @param some the instance to render
   * @param param the parameter instance that is passed to the view.
   */
  def render(some: A, param: B): C
}

trait ParameterViewBinding[A, B, C] extends ClassifiedBinding[A] {
  /**
   * render the given instance of type A and the parameter of type B to produce C.
   */
  def render(some: A, param: B, parent: ParameterViewChain[A, B, C]): C
}

trait TypedViewBindingBuilder[A, B] {
  /**
   * Define a view f(B) = A.
   */
  def to(f: (B => A)): ViewBindingRegistration

  /**
   * Define a view f(B, parent) = A
   *
   * Variant of #to if the view is going to wrap a parent view.
   */
  def withParentTo(f: (B, ViewChain[B, A]) => A): ViewBindingRegistration

  // add more to ... methods for other kinds of view implementations.
}

/**
 * Allows the definition of a ranking.
 */
trait RankedViewBindingBuilder[A] {

  /**
   * define the ranking for the view.
   */
  def withRanking(ranking: Int): A
}

/**
 * Intermediate view binding builder which also allows the definition of a ranking.
 */
trait UnRankedViewBindingBuilder[A] extends RankedViewBindingBuilder[ViewBindingBuilder[A]]
                                       with ViewBindingBuilder[A]

/**
 * Allows binding of a view definition.
 */
trait ViewBindingBuilder[A] {
  def of[B](implicit clazz: ClassManifest[B]): TypedViewBindingBuilder[A, B]
}

/**
 * Intermediate parameter view binding builder which also allows the definition of a ranking.
 */
trait UnRankedParamViewBindingBuilder[A, B] extends RankedViewBindingBuilder[ParamViewBindingBuilder[A, B]]
                                               with ParamViewBindingBuilder[A, B]

/**
 * bind the actual parameterized view.
 */
trait ParamViewBindingBuilder[A, B] extends RankedViewBindingBuilder[ParamViewBindingBuilder[A, B]] {
  def of[C](implicit clazz: ClassManifest[C]): TypedParamViewBindingBuilder[A, B, C]
}

/**
 * Variant of a view binding builder which also provides a parameter.
 */
trait TypedParamViewBindingBuilder[A, B, C] extends TypedViewBindingBuilder[A, C] {
  def to(f: (C, B) => A): ViewBindingRegistration

  /**
   * Define a view f(B, parent) = A
   *
   * Variant of #to if the view is going to wrap a parent view.
   */
  def withParentTo(f: (C, B, ParameterViewChain[C, B, A]) => A): ViewBindingRegistration

  // add more to ... methods for other kinds of view implementations.
}

trait ViewRenderer {

  /**
   * render a view for a given instance.
   */
  def render(bean: Any): IncludeViewBuilder
}

/**
 * a view renderer resolves is the starting point to render views for bean instances.
 */
trait CustomizableViewRenderer extends ViewRenderer {

  /**
   * create a renderer which uses the given function if no view could be found for a given classifier and the expected result type.
   */
  def withDefaultFor[A](f: (Any, Classifier[_]) => A)(implicit resultType: ClassManifest[A]): CustomizableViewRenderer

  /**
   * create a view renderer which uses the given function to resolve the class from an instance to find view bindings.
   */
  def withClassResolver(resolver: ClassResolver): CustomizableViewRenderer
}

trait ClassResolver {
  def resolveClass[A, B>:A](some: A): Class[B]
}

/**
 * Allows the definition of a view classifier for a included view
 *
 * @TODO: find a better name than IncludeViewBuilder
 */
trait IncludeViewBuilder {

  /**
   * render the given common view for the previously defined bean instance.
   */
  def as[A](classifier: ViewClassifier[A]): A

  /**
   * render the given parameterized view for the previously defined bean instance.
   */
  def as[A, B](classifier: ParamViewClassifier[A, B]): ParamIncludeViewBuilder[A, B]
}

/**
 * provide the parameter(s) for the defined view.
 *
 * A: view output type
 * B: param type
 */
trait ParamIncludeViewBuilder[A, B] {

  /**
   * Define the parameter instance to be passed to the view definition.
   */
  def withParameter(p: B): A
}

/**
 * the contract for a view module that defines the #render function to include other views.
 */
trait ViewModule {
  def configure(viewBinder: ViewBinder): Unit
}