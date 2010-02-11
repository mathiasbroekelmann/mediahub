/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.sandbox

import com.coremedia.cap.content.{Content => CapContent}
import com.coremedia.objectserver.beans._
import com.coremedia.cap.common._

import com.google.inject._
import com.google.inject.spi.{TypeListener, TypeEncounter}
import com.google.inject.matcher.Matchers.any

import org.mediahub.cache._

import scala.collection.JavaConversions._

/**
 * guice module which tracks bound content beans and provides a content bean factory.
 *
 * bind content beans by using the ContentBeanBinding annotation to specify the name of the doctype to which this
 * content bean should be used for:
 *
 * <pre>
 * bind(classOf[SomeContentBean]).annotatedWith(new ContentBeanBinding("SomeDoctype")
 * </pre>
 */
case class ContentBeans extends AbstractModule {

  def configure() {
    val factory = beanFactory
    bindListener(any, factory.typeListener)
    bind(classOf[ContentBeanFactory]).toProvider(factory.provider).in(Scopes.SINGLETON)
  }

  def beanFactory = new {
    var contentBeans = Set[TypeLiteral[AbstractContentBean]]()
    def typeListener = new TypeListener {
      def hear[A](typeLiteral: TypeLiteral[A], encounter: TypeEncounter[A]) {
        if (classOf[AbstractContentBean].isAssignableFrom(typeLiteral.getRawType)) {
          contentBeans += typeLiteral.asInstanceOf[TypeLiteral[AbstractContentBean]]
        }
      }
    }

    def provider = new Provider[ContentBeanFactory] {

      @Inject 
      var injector: Injector = _
      
      @Inject 
      var cache: Cache = _

      def get = new AbstractContentBeanFactory {
        protected def getDefinition(doctype: String): ContentBeanDefinition = {
          cache.get(ContentBeanDefinitionForDoctype(doctype, this)).orNull
        }
      }

      case class ContentBeanDefinitionForDoctype(doctype: String,
                                                 beanFactory: ContentBeanFactory) extends CacheKey[Option[ContentBeanDefinition]] {

        def contentBeanProvider(typeLiteral: TypeLiteral[AbstractContentBean]): Option[Provider[AbstractContentBean]] = {

          val providers = for(binding <- injector.findBindingsByType(typeLiteral).toStream;
                              annotation <- Option(binding.getKey.getAnnotation);
                              if annotation.isInstanceOf[ContentBeanBinding];
                              if annotation.asInstanceOf[ContentBeanBinding].value == doctype)
                                yield binding.getProvider

          providers.headOption
        }

        /**
         * 
         */
        def contentbeanDefinitionFor(contentBeanProvider: Provider[AbstractContentBean]) = new ContentBeanDefinition {
          def getContentBeanFactory: ContentBeanFactory = beanFactory
          def init(bean: AbstractContentBean) = injector.injectMembers(bean)
          def instantiate = contentBeanProvider.get
        }

        /**
         * @return the content bean definition for the given doctype. Returns Some(cbd) if a definition could be found, otherwise returns None
         */
        def compute = {
          // place a dependency to enable invalidation of the computed values when the content bean definitions have changed.
          Cache.dependsOn(ContentBeanDefinitions)
          // determine the content bean definitions by the bound content beans.
          val definitions = for(cbType <- contentBeans.toStream;
                                cbProvider <- contentBeanProvider(cbType))
                                  yield (contentbeanDefinitionFor(cbProvider))
          // return the first content bean definition
          definitions.headOption
        }
      }
    }
  }
}

/**
 * dependency which can be used to invalidate the cache for the resolved content bean definitions.
 * 
 * @see Cache#invalidate(Dependency)
 */
case object ContentBeanDefinitions extends Dependency