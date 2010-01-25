/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

import com.google.inject._

class ViewInstallModule extends AbstractModule {

  private[this] var modules = Seq.empty[Provider[ViewModule]]

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
  def renderer(registry: ViewRegistry): CustomizableViewRenderer = {
    val binder = new ViewBinderImpl(registry);
    val renderer = new ViewRendererImpl(registry)
    modules.foreach(_.get.configure(binder))
    renderer
  }

  @Provides
  @Singleton
  def renderer(renderer: CustomizableViewRenderer): ViewRenderer = renderer

  def configure {
    bind(classOf[ViewRegistry]).to(classOf[ViewRegistryImpl]).in(Scopes.SINGLETON)
    bindListener(matcher.Matchers.any, listener)
  }

}

/**
 * Base support class for defining views using guice.
 * <br>
 * Example usage:
 * <pre>
 * class MyViewModule extends AbstractViewModule {
 *   def configure {
 *     bindView(classifier).of[Type] to { self =>
 *       // render the view of self
 *     }
 *   }
 * }
 *
 * class MyGuiceModule extends AbstractModule {
 *   def configure {
 *     bind(classOf[MyViewModule])
 *   }
 * }
 *
 * val injector = Guice.createInjector(new MyGuiceModule, new ViewInstallModule)
 * val renderer = injector.getInstance(classOf[ViewRenderer])
 * </pre>
 */
abstract class AbstractViewModule extends ViewModule {
  @Inject
  private[this] val injectedRenderer: Provider[CustomizableViewRenderer] = injectedRenderer

  private[this] var _binder: ViewBinder = _

  private[this] var _renderer: Option[ViewRenderer] = None

  /**
   * Provides access to the view binder instance.
   */
  final protected def binder: ViewBinder = _binder

  /**
   * Provides access to the view renderer instance.
   */
  final protected def renderer = new Provider[ViewRenderer] {
    def get = _renderer.get
  }

  /**
   * render a view for a given instance.
   */
  final protected def render(bean: Any): IncludeViewBuilder = {
    if(_renderer.isEmpty) {
      _renderer = Some(customize(injectedRenderer.get))
    }
    _renderer.map(_.render(bean))
             .getOrElse(error("No view renderer available."))
  }

  /**
   * Binds an ordinary view which doesn't have view parameters.
   *
   * @param classifier the view classifier to bind a view for.
   *
   * @return a view binding builder to bind the actual view definition.
   */
  final protected def bindView[A](classifier: ViewClassifier[A]) = _binder.bindView(classifier)

  /**
   * Bind a view for a paramterized view. Variant of #bindView(ViewClassifier) to support view parameters.
   *
   * @param classifier the view classifier to bind a view for.
   * @return a view binding builder to bind the actual view definition optionally receiving the provided parameters.
   */
  final protected def bindView[A, B](classifier: ParamViewClassifier[A, B]) = _binder.bindView(classifier)

  /**
   * install the given module as a child module to this binder.
   */
  final protected def install(module: ViewModule) = _binder.install(module)

  final def configure(binder: ViewBinder) {
    this synchronized {
      _binder = binder
      configure
    }
  }

  /**
   * Overwrite in subclasses to customize the used view renderer.
   */
  protected def customize(renderer: CustomizableViewRenderer): ViewRenderer = renderer

  /**
   * Overwrite in subclasses to bind the views.
   */
  def configure: Unit
}
