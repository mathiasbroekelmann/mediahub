/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

abstract case class Empty

case class ViewDefinition[SelfType, ResultType, ParamType](selfType: Class[SelfType],
                                                           viewType: Classifier[ResultType],
                                                           render: (SelfType, => ParamType) => ResultType)

trait Views {

  def views: Traversable[ViewDefinition[_, _, _]]

  /**
   * define a view.
   */
  def of[SelfType](implicit clazzManifest: ClassManifest[SelfType]) = new {
    val clazz = clazzManifest.erasure.asInstanceOf[Class[SelfType]]

    /**
     * define the view for the given classifier (short form).
     */
    def as[ResultType](view: ViewClassifier[ResultType])
                      (render: SelfType => ResultType) =
        ViewDefinition(clazz, view, { (some: SelfType, param: Any) => render(some) })

    /**
     * variant with a view that provides a parameter
     */
    def as[ResultType, ParamType](view: ParamViewClassifier[ResultType, ParamType])
                                 (render: (SelfType, => ParamType) => ResultType) =
        ViewDefinition(clazz, view, render)
  }
}

import com.google.inject.{Provider, Inject, TypeLiteral}
import com.google.inject.spi._

class ViewCollector extends TypeListener {
  def hear[A](typeLiteral: TypeLiteral[A], encounter: TypeEncounter[A]) {
  }
}

/**
 * mixin trait to provide a view renderer for a guice module.
 */
trait ProvidedViewRenderer {

  @Inject
  private var rendererProvider: Provider[ViewRenderer] = _

  def renderer = Option(rendererProvider).map(_.get) getOrElse error("no renderer provided for " + this)


  /**
   * render a view for the given instance.
   */
  implicit def render[SelfType](some: SelfType) = new {

    def as[ResultType](view: ViewClassifier[ResultType]): ResultType = renderer.render(some) as view

    def as[ResultType, ParamType](view: ParamViewClassifier[ResultType, ParamType]) = new {
      def withParameter[A<:ParamType](param: A): ResultType = renderer.render(some) as view withParameter param
    }
  }
}


// some examples

case object html extends ViewClassifier[scala.xml.NodeSeq]
case object body extends ViewClassifier[scala.xml.NodeSeq]

class HtmlViews extends Views with ProvidedViewRenderer {

  def views = {
    val any = of[Any]
    List(
      any.as(html) { any =>
        <html>
          <head>
          </head>
          {any as body}
        </html>
      },

      any.as(body) { any =>
        <body/>
      }
    )
  }
}