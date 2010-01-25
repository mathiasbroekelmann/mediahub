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
    new ViewRendererImpl(registry)
  }

  @Provides
  @Singleton
  def renderer(renderer: CustomizableViewRenderer): ViewRenderer = renderer

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
