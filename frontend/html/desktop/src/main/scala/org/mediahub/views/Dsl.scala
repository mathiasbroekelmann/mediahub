package org.mediahub.views

import scala.xml._

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

abstract class Classifier[A](clazz: ClassManifest[A]) {

  /**
   * the type of the view result.
   */
  val resultType = clazz.erasure.asInstanceOf[Class[A]]

  /**
   * determine of the binding is valid for this classifier.
   */
  def isValid(binding: ClassifiedBinding[_]): Boolean = false
}

/**
 * Common view classifier. Identifies a view.
 * type A defines the output of the view (String, NodeSeq, ...)
 */
abstract case class ViewClassifier[A](implicit clazz: ClassManifest[A]) extends Classifier[A](clazz) {

  override def isValid(binding: ClassifiedBinding[_]) = binding match {
    case x: ViewBinding[_, _] => true
    case _ => false
  }
}

/**
 * Special view classifier which allows the definition of parameters
 * type A defines the output of the view (String, NodeSeq, ...)
 * type B defines the view parameters type(s).
 */
abstract case class ParamViewClassifier[A, B](implicit clazz: ClassManifest[A]) extends Classifier[A](clazz) {

  override def isValid(binding: ClassifiedBinding[_]) = binding match {
    case x: ParameterViewBinding[_, _, _] => true
    case _ => false
  }
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
  def bindView[A](classifier: ViewClassifier[A]): ViewBindingBuilder[A]

  /**
   * Bind a view for a paramterized view. Variant of #bindView(ViewClassifier) to support view parameters.
   *
   * @param classifier the view classifier to bind a view for.
   * @return a view binding builder to bind the actual view definition optionally receiving the provided parameters.
   */
  def bindView[A, B](classifier: ParamViewClassifier[A, B]): ParamViewBindingBuilder[A, B]
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
trait ClassifiedBinding[A] {

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
 * Allows binding of a view definition.
 */
trait ViewBindingBuilder[A] {
  def of[B](implicit clazz: ClassManifest[B]): TypedViewBindingBuilder[A, B]
}

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

/**
 * bind the actual parameterized view.
 */
trait ParamViewBindingBuilder[A, B] {
  def of[C](implicit clazz: ClassManifest[C]): TypedParamViewBindingBuilder[A, B, C]
}

/**
 * a view renderer resolves is the starting point to render views for bean instances.
 */
trait ViewRenderer {

  /**
   * render a view for a given instance.
   */
  def render(bean: Any): IncludeViewBuilder

  /**
   * create a renderer which uses the given function if no view could be found for a given classifier and the expected result type.
   */
  def withDefaultFor[A](f: (Any, Classifier[_]) => A)(implicit resultType: ClassManifest[A]): ViewRenderer

  /**
   * create a view renderer which uses the given function to resolve the class from an instance to find view bindings.
   */
  def withClassResolver(resolver: ClassResolver): ViewRenderer
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
trait ViewModule extends ViewBinder with ViewRenderer {
  /**
   * start defining an inclusion of a view for a given instance.
   */
  def include(bean: AnyRef) = render(bean)
}

// example code to verify stuff is working
// domain class
trait SomePage extends Content {
  def title: String
  def teaser: Seq[Content]
}

// domain class
trait Content

// example view classifiers defining the result of a view
object Body extends ViewClassifier[NodeSeq]
object Content extends ViewClassifier[NodeSeq]
// example view classifier which accepts parameters. This defines the types of the parameters
case class MyParam(val someString: String, val someContent: Content)
object ParamView extends ParamViewClassifier[NodeSeq, MyParam]
object SimpleStringParamView extends ParamViewClassifier[NodeSeq, String]

// example module which binds some views
trait  MyViewModule extends ViewModule {

  val defaultRenderer = withDefaultFor[NodeSeq]((bean, classifier) => NodeSeq.Empty)

  // bind a view of Page to the classifier body
  bindView(Body).of[SomePage] to { page =>
    <body>
      <h1>{page.title}</h1>
      {
        // variable assignment
        val pageTeaser = page.teaser
        // we could also import that property:
        //import page.{teaser => pageTeaser}
        // verify a condition
        if(!pageTeaser.isEmpty) {
          <ul>
            {
              // iteration over content
              for(teaser <- pageTeaser) yield {
                <li>
                  <!-- render a nested view -->
                  {render(teaser) as Content}
                </li>
              }
            }
          </ul>
        }
      }
      <div>
        <!-- render a parameterized view -->
        {render(page) as ParamView withParameter MyParam("foo", page)}
        {render(page) as SimpleStringParamView withParameter "foobar"}
        <!-- this will not pass the compiler since "bar" is not of type Content: -->
        <!-- {render(page) as ParamView withParameter MyParam("foo", "bar")} -->
      </div>
    </body>
  }

  /**
   * just a plain view binding
   */
  bindView(Content).of[SomePage] to { page =>
    <div>
      <div>{page.title}</div>
    </div>
  }

  /**
   * just a plain view binding with parent wrapping
   */
  bindView(Content).of[SomePage] withParentTo { (page, parent) =>
    <div>
      <h1>{page.title}</h1>
      <!-- include the parent view (it's like calling super#Content(page) -->
      {parent.render(page)}
    </div>
  }

  /**
   * example for a view binding which provides additional parameters
   * param is of type (String, Content)
   */
  bindView(ParamView).of[SomePage] to { (page, param) =>
    // we can import the param functions
    import param._
    // and use the parameters in the view
    Text(someString)
  }

  /**
   * Now the same with a parent
   */
  bindView(ParamView).of[SomePage] withParentTo { (page, param, parent) =>
    <div>
      <h1>{param.someString}</h1>
      <!-- call parent - we can modify the parameter and of course the value of page as long as the types doesn't change -->
      {parent.render(page, MyParam("newvalue", page))}
    </div>
    
  }

  /**
   * It is also possible to bind a view to a parameterized view which ignores the given parameters.
   * This simplifies the view definitions where the view doesn't need the parameters.
   */
  bindView(ParamView).of[SomePage] to { page =>
    NodeSeq.Empty
  }

  /**
   * and now with calling the super view
   */
  bindView(ParamView).of[SomePage] withParentTo { (page, parent) =>
    <div class="foo">
      {parent.render(page)}
    </div>
  }

  bindView(SimpleStringParamView).of[SomePage] to { (page, strParam) =>
    NodeSeq.Empty
  }

  /**
   * It is also possible to bind a view to a parameterized view which ignores the given parameters.
   * This simplifies the view definitions where the view doesn't need the parameters.
   */
  bindView(ParamView).of[SomePage] to { page =>
    NodeSeq.Empty
  }

  /**
   * It is also possible to bind a view to a parameterized view which ignores the given parameters.
   * This simplifies the view definitions where the view doesn't need the parameters.
   */
  bindView(ParamView).of[SomePage] to { (page, param) =>
    NodeSeq.Empty
  }

  /**
   * and now with calling the super view
   */
  bindView(ParamView).of[SomePage] withParentTo { (page: SomePage, parent: ViewChain[SomePage, NodeSeq]) =>
    <div class="foo">
      {parent.render(page)}
    </div>
  }

  bindView(SimpleStringParamView).of[SomePage] to { (page: SomePage, strParam: String) =>
    NodeSeq.Empty
  }
}