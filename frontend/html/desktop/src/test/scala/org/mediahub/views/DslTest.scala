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
    val rootViewModule = new ViewInstallModule
    val injector = Guice.createInjector(new GuiceModule, rootViewModule)
    val viewBinder = injector.getInstance(classOf[ViewBinder])
    rootViewModule.configureViews(viewBinder)
    renderer = injector.getInstance(classOf[ViewRenderer])
  }

  @After
  def tearDown {
  }

  @Test
  def helloWorldTest {
    val someHtml = renderer.render("Hello World!").as(html)
    assertThat((someHtml \ "body" \ "p").text, is("Hello World!"))
  }
}

class ViewInstallModule extends AbstractModule {

  private var modules = Seq.empty[Provider[ViewModule]]

  def listener = new spi.TypeListener {
    def hear[A](typeLiteral: TypeLiteral[A], encounter: spi.TypeEncounter[A]) {
      if(classOf[ViewModule].isAssignableFrom(typeLiteral.getRawType)) {
        // TODO: find a way to avoid type cast here.
        modules :+= encounter.getProvider(Key.get(typeLiteral)).asInstanceOf[Provider[ViewModule]]
      }
    }
  }

  @Provides
  @Singleton
  def renderer(registry: ViewRegistry): ViewRenderer = {
    new ViewRendererImpl(registry)
  }

  @Provides
  @Singleton
  def binder(registry: ViewRegistry): ViewBinder = {
    new ViewBinderImpl(registry)
  }

  def configure {
    bind(classOf[ViewRegistry]).to(classOf[ViewRegistryImpl]).in(Scopes.SINGLETON)
    bindListener(matcher.Matchers.any, listener)
  }

  def configureViews(binder: ViewBinder) {
    for(module <- modules) {
      binder.install(module.get)
    }
  }
}

class GuiceModule extends AbstractModule {

  def configure {
    bind(classOf[MyViewModule]).in(Scopes.SINGLETON)
  }
}

object html extends ViewClassifier[NodeSeq]
object inHead extends ViewClassifier[NodeSeq]
object body extends ViewClassifier[NodeSeq]

class MyViewModule @Inject() (renderer: ViewRenderer) extends ViewModule {

  def configure(viewBinder: ViewBinder) {
    val myrenderer = renderer.withDefaultFor[NodeSeq] { (some, classifier) =>
      NodeSeq.Empty
    }
    import myrenderer._
    import viewBinder._

    install(new MySubViewModule(myrenderer))

    bindView(html).of[Any] to { self =>
      <html>
        <head>
           {render(self).as(inHead)}
        </head>
        {render(self).as(body)}
      </html>
    }
  }
}

class MySubViewModule(renderer: ViewRenderer) extends ViewModule {
  def configure(viewBinder: ViewBinder) {
    import renderer._
    import viewBinder._

    bindView(body).of[String] to { self =>
      <body>
        <p>{self}</p>
      </body>
    }
  }
}