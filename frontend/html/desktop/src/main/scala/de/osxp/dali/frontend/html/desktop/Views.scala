package org.mediahub.frontend.html.desktop

import scala.xml._
import com.google.inject._

trait Page extends Content {
  def title: String
  def teaser: Seq[Content]
}

trait Content

abstract case class ViewClassifier[A]
abstract case class ParamViewClassifier[A, B]

trait ViewModule {
  def bindView[A](classifier: ViewClassifier[A]): ViewBindingBuilder[A]
  def bindView[A, B](classifier: ParamViewClassifier[A, B]): ParamViewBindingBuilder[A, B]

  def render[A<:Any](bean: A): IncludeViewBuilder[A]
}

trait ParamViewBindingBuilder[A, B] {
  def to[C<:Any](f: (C => (B => A))): Unit
}

trait ViewBindingBuilder[A] {
  def to[B<:Any](f: (B => A)): Unit

  // add more to ... methods for other kinds of view implemntations.
}

trait IncludeViewBuilder[A] {

  /**
   * include the view for the given classifier
   */
  def as[B](classifier: ViewClassifier[B]): B

  def as[B, C](classifier: ParamViewClassifier[B, C]): ParamIncludeViewBuilder[B, C]
}

/**
 * A: bean type
 * B: param type
 */
trait ParamIncludeViewBuilder[A, B] {
  def withParameter(p: B): A
}

object Body extends ViewClassifier[NodeSeq]
object Content extends ViewClassifier[NodeSeq]
object ParamView extends ParamViewClassifier[NodeSeq, (String, Content)]

trait  MyViewModule extends ViewModule {
  bindView(Body).to { page: Page =>
    <body>
      <h1>{page.title}</h1>
      {
        <!-- variable assignment -->
        val pageTeaser = page.teaser
        <!-- iteration over content -->
        if(!pageTeaser.isEmpty) {
          <ul>
            {
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
        {render(page) as ParamView withParameter ("foo", page.teaser.head)}
      </div>
    </body>
  }

  bindView(Content) to { page: Page =>
    <div>
      <div>{page.title}</div>
    </div>
  }

  bindView(ParamView) to { page: Page =>
    { param =>
      val (str, content) = param
      NodeSeq.Empty
    }
  }
}