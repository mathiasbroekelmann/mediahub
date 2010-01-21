package org.mediahub.frontend.html.desktop

import scala.xml._
import com.google.inject._

/**
 * common view classifier
 * type A defines the output of the view (String, NodeSeq, ...)
 */
abstract case class ViewClassifier[A]

/**
 * special view classifier which allows the definition of parameters
 * type A defines the output of the view (String, NodeSeq, ...)
 * type B defines the view parameters type(s).
 */
abstract case class ParamViewClassifier[A, B]

/**
 * the view binder allows binding of views
 */
trait ViewBinder {
  /**
   * bind a common view which doesn't habe view parameters
   */
  def bindView[A](classifier: ViewClassifier[A]): ViewBindingBuilder[A]

  /**
   * bind a view with parameters.
   */
  def bindView[A, B](classifier: ParamViewClassifier[A, B]): ParamViewBindingBuilder[A, B]
}

/**
 * bind the actual view.
 */
trait ViewBindingBuilder[A] {
  def to[B<:Any](f: (B => A)): Unit

  // add more to ... methods for other kinds of view implementations.
}

/**
 * bind the actual parameterized view.
 */
trait ParamViewBindingBuilder[A, B] {
  def to[C<:Any](f: (C => (B => A))): Unit

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
   * convinience function to use where include doesn't sound good.
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
  def as[B](classifier: ViewClassifier[B]): B

  /**
   * render the given parameterized view for the previously defined bean instance.
   */
  def as[B, C](classifier: ParamViewClassifier[B, C]): ParamIncludeViewBuilder[B, C]
}

/**
 * provide the parameter(s) for the defined view.
 *
 * A: view output type
 * B: param type
 */
trait ParamIncludeViewBuilder[A, B] {
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
object ParamView extends ParamViewClassifier[NodeSeq, (String, Content)]

// example module which binds some views
trait  MyViewModule extends ViewModule {

  // bind a view of Page to the classifier body
  bindView(Body).to { page: SomePage =>
    <body>
      <h1>{page.title}</h1>
      {
        <!-- variable assignment -->
        val pageTeaser = page.teaser
        <!-- verify condition -->
        if(!pageTeaser.isEmpty) {
          <ul>
            {
              <!-- iteration over content -->
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
        {render(page) as ParamView withParameter ("foo", page.teaser.head)}
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
   */
  bindView(ParamView) to { page: SomePage =>
    // param is of type (String, Content)
    { param =>
      // str is implicit type String
      // content is implicit type Content
      val (str, content) = param
      NodeSeq.Empty
    }
  }

  /**
   * This would not compile!
  bindView(ParamView) to { page: SomePage =>
      NodeSeq.Empty
  }
   */
}