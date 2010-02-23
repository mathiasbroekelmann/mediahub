/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import org.specs.SpecificationWithJUnit

import com.google.inject._

class GuiceTest extends SpecificationWithJUnit {

  "guice type listener spike test" in {
    val injector = Guice.createInjector(new RendererModule, new TestModule1)
    println("getting instance of ViewRenderer")
    val renderer = injector.getInstance(classOf[ViewRenderer])

    renderer.render("foo").as(text) must be equalTo("Foo")
  }
}

class TestModule1 extends AbstractModule {
  def configure {
    bind(classOf[SomeViews])
  }
}

case object text extends ViewClassifier[String]

class SomeViews extends Views {
  def views = {
    List(
      of[String].as(text) {value => value.capitalize}
    )
  }
}

class RendererModule extends AbstractModule {

  def configure {
    bind(classOf[ViewRenderer]).toProvider(classOf[ViewRendererProvider])
  }
}

import scala.collection.JavaConversions._

class ViewRendererProvider @Inject() (injector: Injector) extends Provider[ViewRenderer] {
  def get = {

    println("creating instance of ViewRenderer")
    /**
     * predicate to filter all keys whose raw type implements Views.
     */
    def byProvidedViews(key: Key[_]): Boolean = {
      classOf[Views].isAssignableFrom(key.getTypeLiteral.getRawType)
    }

    // get all bindings that define views
    val viewsBindings = injector.getBindings.filterKeys(byProvidedViews).valuesIterable

    print("found view bindings: ")
    viewsBindings foreach println

    // get all view definitions of that bindings
    val viewDefinitions = viewsBindings.map(_.getProvider.get).partialMap {
      case views: Views => views.views
      case _ => Seq.empty
    }.flatten
    
    print("found view definitions: ")
    viewDefinitions foreach println

    ViewDefinitionsRenderer(viewDefinitions)
  }
}

import org.mediahub.util.Types._

case class ViewDefinitionsRenderer(views: Traversable[ViewDefinition[_, _, _]]) extends ViewRenderer {

  def render(bean: Any) = new IncludeViewBuilder {

    def as[ResultType](classifier: ViewClassifier[ResultType]): ResultType = as(classifier.asInstanceOf[Classifier[ResultType]])

    def as[ResultType](classifier: Classifier[ResultType]): ResultType = {

      // get all views for the given classifier
      val viewCandidatesByClassifier = for(view <- views;
                                           if view.viewType matches classifier)
                                             yield view.asInstanceOf[ViewDefinition[Any, ResultType, Any]]

      // use the bean type hierarchy to find the most concrete view
      val beanTypes = bean.asInstanceOf[AnyRef].getClass hierarchy
      val viewCandidatesByType = for(clazz <- beanTypes;
                                     view <- viewCandidatesByClassifier.find(_.selfType == clazz)) 
                                       yield view

      // verify that at least one view exists.
      viewCandidatesByType.headOption match {
        case Some(view) => view.render(bean, error("no param defined"))
        case None => error("no view defined for " + classifier + " of type " + bean.asInstanceOf[AnyRef].getClass)
      }
    }

    def as[A, B](classifier: ParamViewClassifier[A, B]): ParamIncludeViewBuilder[A, B] = error("nyi")
  }
}
