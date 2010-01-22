package org.mediahub.frontend.html.desktop

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
 * Common view classifier. Identifies a view.
 * type A defines the output of the view (String, NodeSeq, ...)
 */
abstract case class ViewClassifier[A]

/**
 * Special view classifier which allows the definition of parameters
 * type A defines the output of the view (String, NodeSeq, ...)
 * type B defines the view parameters type(s).
 */
abstract case class ParamViewClassifier[A, B]

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
 * Allows binding of a view definition.
 */
trait ViewBindingBuilder[A] {
  /**
   * Define a view f(B) = A.
   */
  def to[B](f: (B => A)): Unit

  // add more to ... methods for other kinds of view implementations.
}

/**
 * bind the actual parameterized view.
 */
trait ParamViewBindingBuilder[A, B] extends ViewBindingBuilder[A] {
  def to[C](f: (C, B) => A): Unit

  // add more to ... methods for other kinds of view implementations.
}

/**
 * the contract for a view module that defines the #render function to include other views.
 */
trait ViewModule extends ViewBinder {

  /**
   * start defining an inclusion of a view for a given instance.
   */
  def include[A<:Any](bean: A): IncludeViewBuilder

  /**
   * start defining to render a view for a given instance.
   * convienience function to use where include doesn't sound good.
   */
  def render[A<:Any](bean: A): IncludeViewBuilder = include(bean)
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

// example module which binds some views
trait  MyViewModule extends ViewModule {

  // bind a view of Page to the classifier body
  bindView(Body).to { page: SomePage =>
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
        <!-- this will not pass the compiler: -->
        <!-- {render(page) as ParamView withParameter ("foo", 1234)} -->
      </div>
    </body>
  }

  /**
   * just a plain view binding
   */
  bindView(Content) to { page: SomePage =>
    <div>
      <div>{page.title}</div>
    </div>
  }

  /**
   * example for a view binding which provides additional parameters
   * param is of type (String, Content)
   */
  bindView(ParamView).to { (page: SomePage, param: MyParam) =>
    // we can import the param functions
    import param._
    // and use the parameters in the view
    Text(someString)
  }

  /**
   * It is also possible to bind a view to a parameterized view which ignores the given parameters.
   * This simplifies the view definitions where the view doesn't need the parameters.
   */
  bindView(ParamView).to { (page: SomePage) =>
    NodeSeq.Empty
  }

  /**
   * This would not compile!
  bindView(ParamView) to { page: SomePage =>
      NodeSeq.Empty
  }
   */
}