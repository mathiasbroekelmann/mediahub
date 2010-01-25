/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import scala.xml.NodeSeq
import com.google.inject._

class DslTest {
  
  private[this] var renderer: ViewRenderer = _

  @Before
  def setUp {
    val injector = Guice.createInjector(new MyGuiceModule, new ViewInstallModule)
    renderer = injector.getInstance(classOf[ViewRenderer])
  }

  @Test
  def helloWorldTest {
    val someHtml = renderer.render("Hello World!").as(html)
    assertThat((someHtml \ "body" \ "p").text, is("Hello World!"))
  }
}

class MyGuiceModule extends AbstractModule {

  def configure {
    bind(classOf[MyViewModule]).in(Scopes.SINGLETON)
  }
}

object html extends ViewClassifier[NodeSeq]
object inHead extends ViewClassifier[NodeSeq]
object body extends ViewClassifier[NodeSeq]

class MyViewModule extends AbstractViewModule {

  override def customize(renderer: CustomizableViewRenderer) = {
    renderer.withDefaultFor[NodeSeq] { (some, classifier) =>
      NodeSeq.Empty
    }
  }

  def configure {
    bindView(html).of[Any] to { self =>
      <html>
        <head>
           {render(self).as(inHead)}
        </head>
        {render(self).as(body)}
      </html>
    }

    bindView(body).of[String] to { self =>
      <body>
        <p>{self}</p>
      </body>
    }
  }
}