/*
 * TODO: implement an osgi activator which tracks LinkResolvers and export a link renderer service
 */

package org.mediahub.web.links

import java.net.URI
import javax.ws.rs._
import core._
import scala.xml._
import scala.collection.JavaConversions._

import scala.reflect.ClassManifest._

import java.lang.reflect.{ParameterizedType}

/**
 * a link renderer provides entry methods to render links.
 */
trait LinkRenderer {

  /**
   * start building a link to a resource type.
   *
   * @param [A] the resource type.
   */
  def linkTo[A<:AnyRef](implicit clazz: ClassManifest[A]): ResourceActionLinkBuilder[A]

  /**
   * start building a link to a resource type identified by a class.
   */
  def linkTo[A<:AnyRef](clazz: java.lang.Class[A]): ResourceActionLinkBuilder[A] = linkTo[A](fromClass(clazz))

  /**
   * start building a link to a resource instance.
   *
   * @param target the target instance for the link.
   */
  def linkTo[A<:AnyRef](target: A): ResourceActionLinkBuilder[A]
}

/**
 * companion object to transform the build links.
 */
object LinkRenderer {

  /**
   * make a string from a link builder.
   */
  implicit def linkBuilderToString(builder: LinkBuilder): String = {
    builder.build.map(x =>
      x.toString).getOrElse(error("not a valid link spec: " + builder))
  }
}

/**
 * link renderer implementation which uses a link context to resolve the base uri and the internal link resolvers.
 */
class ContextLinkRenderer(context: LinkContext) extends LinkRenderer {

  def linkTo[A<:AnyRef](implicit clazz: ClassManifest[A]): ResourceActionLinkBuilder[A] =
    new ResourceLinkBuilderImpl(context).resolvedBy[A]

  def linkTo[A<:AnyRef](target: A): ResourceActionLinkBuilder[A] = {
    new ResourceLinkBuilderImpl(context).resolvedBy[A](target)
  }
}

/**
 * A link context provides contexutal information for building links.
 */
trait LinkContext {

  /**
   * Resolve the base uri for the given class type.
   */
  def baseUri[A<:AnyRef](implicit clazz: ClassManifest[A]): Option[URI] = None

  /**
   * Provide the list of all available link resolvers.
   */
  def resolver: Iterable[LinkResolver[_]] = Iterable.empty

  /**
   * Get all link resolvers that can handle the defined class type.
   * TODO: find a way to order the resolvers.
   */
  def resolverFor[A<:AnyRef](implicit clazz: ClassManifest[A]): Iterable[LinkResolver[A]] = {
    val myclass = clazz.erasure
    LinkContext.typeOf(resolver, classOf[LinkResolver[A]], myclass)
  }

  /**
   * convienience function to create a uri part builder with the base uri for the given class type.
   */
  def baseUriBuilder[A<:AnyRef](implicit clazz: ClassManifest[A]) = new UriPartBuilder {
    def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
      chain(
        baseUri[A]
        .map(UriBuilder.fromUri(_)))
    }
  }
}

object LinkContext {
  /**
   * collect all elements in the given list by the given filter.
   * use this if you have an service interface which defines generic type parameters and
   * implementations for certain type definitions of that generic types.
   * you can filter that list for specific types to collect the services whose generic type definitions passes the given matcher.
   */
  def typeOf[A<:AnyRef,B<:A](input: Iterable[A], clazz: Class[B], genericType: Class[_]): Iterable[B] = {
    for(element <- input;
        if element.isInstanceOf[B];
        itf <- element.asInstanceOf[B].getClass.getGenericInterfaces;
        if itf.isInstanceOf[ParameterizedType];
        pt <- Seq(itf.asInstanceOf[ParameterizedType]);
        if pt.getRawType == clazz;
        if pt.getActualTypeArguments()(0).asInstanceOf[Class[_]].isAssignableFrom(genericType))
          yield element.asInstanceOf[B]
  }
}

/**
 * A link resolver is used to resolve the resource that is used to determine a sub resource.
 */
trait LinkResolver[A] {

  /**
   * resolve the link to the given target instance.
   *
   * @param target the target instance for which the link should be build
   * @param builder the link builder to use to fill in the missing parts of the link.
   *
   * @return if this link resolver was capable of resolving the link the implementation must return Some linkbuilder instance. Otherwise None.
   */
  def apply(target: A, builder: ResourceLinkBuilder): Option[LinkBuilder]
}

/**
 * A uri part builder is used to build a part of the link.
 */
trait UriPartBuilder {

  /**
   * Implement the rule to build the part of the link.
   * Use the uri builder chain to pass any uri builder optionally with any parameters to the next uri part builder.
   *
   * @param builder the given uri builder. None if no uri builder could be determined before.
   *                If the implementation is not able to handle None it should simply pass it to the chain#apply method
   * @param chain   the uri builder chain which recives the given or new uri builder instance optionally with parameters that will be applied when building the final uri.
   *
   * @return the build uri. Normally this is just the return value from any chain#apply call.
   *         But it is also possible to provide a different uri if the chain could not build a uri for any reason.
   */
  def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI]
}

/**
 * the uri builder chain is used by the link builders to build the uri.
 */
trait UriBuilderChain {

  /**
   * Continue building the link by using the given uri builder.
   *
   * @param builder the uri builder that should be used either by the next uri part builder or if this is the
   *                last one in the chain the uri builder that is used to build the final uri.
   *                
   * @return the build uri. This might be none if the uri could not be build for any reason.
   */
  def apply(builder: Option[UriBuilder]): Option[URI] = apply(builder, Map[String, AnyRef]())

  /**
   * Continue building the link by using the given uri builder.
   * This allows you to define a map of parameters for the final uri.
   *
   * @param builder the uri builder that should be used either by the next uri part builder or if this is the
   *                last one in the chain the uri builder that is used to build the final uri.
   * @param params  pass a map of params that will be applyed to build the uri.
   *
   * @return the build uri. This might be none if the uri could not be build for any reason.
   */
  def apply(builder: Option[UriBuilder], params: Map[String, AnyRef]): Option[URI]

  /**
   * Continue building the link by using the given uri builder.
   * This allows you to define a set of 2 arg tuples of parameters for the final uri.
   *
   * @param builder the uri builder that should be used either by the next uri part builder or if this is the
   *                last one in the chain the uri builder that is used to build the final uri.
   * @param params  pass a set of 2 arg tuples of params that will be applyed to build the uri.
   *
   * @return the build uri. This might be none if the uri could not be build for any reason.
   */
  def apply(builder: Option[UriBuilder], params: (String, AnyRef)*): Option[URI] = apply(builder, Map.empty ++ params)

}

/**
 * Build a uri.
 */
trait LinkBuilder {
  /**
   * build the actual uri.
   *
   * @return None if the link could not be determined or Some(uri) if a valid link could be determined.
   */
  def build: Option[URI]
}

/**
 * Allows you to specify the fragment for the link
 */
trait FragmentLinkBuilder extends LinkBuilder {
  /**
   * define the fragment of the link.
   */
  def fragment(fragment: String): LinkBuilder
}

/**
 * Allows to specify by which action a resource is resolved.
 */
trait ResourceActionLinkBuilder[A] extends ResourceLinkBuilder {
  /**
   * specify the action (method) that is used to resolve the resource. The actual method is not executed.
   * Only the passed parameters where collected to build the link.
   * @param call call the method with the parameters that should be used to build the link.
   * Use null for any @Context annotated or other uri unrelated parameters.
   * It is also possible to pass null for uri related parameters that have a given default value.
   */
  //def action(call: (A => Any)): ResourceLinkBuilder
  
  /**
   * define a method which with a @Path annotation to build the link
   */
  def action(methodName: String): ResourceLinkBuilder = action(methodName, Map.empty[String, AnyRef])

  def action(methodName: String, params: Map[String, AnyRef]): ResourceLinkBuilder

  // def action(methodName: String, params: Seq[AnyRef]): ResourceLinkBuilder

  def action(methodName: String, param: (String, AnyRef), others: (String, AnyRef)*): ResourceLinkBuilder = {
    action(methodName, Map(param) ++ others)
  }

  def action(methodName: String, param: AnyRef, others: AnyRef*): ResourceLinkBuilder = {
    action(methodName, Seq(param) ++ others)
  }
}

/**
 * allows you to specify by which other resource the current resource is resolved.
 */
trait ResourceLinkBuilder extends FragmentLinkBuilder {

  /**
   * Specify the root resource class which is capable to resolve the resource.
   */
  def resolvedBy[A<:AnyRef](implicit clazz: ClassManifest[A]): ResourceActionLinkBuilder[A]

  /**
   * specify the resource instance which is capable to resolve the resource.
   */
  def resolvedBy[A<:AnyRef](target: A): ResourceActionLinkBuilder[A]
}

class FragmentLinkBuilderImpl[A](builders: Seq[UriPartBuilder]) extends LinkBuilderImpl(builders)
                                                                with FragmentLinkBuilder {

  def fragment(fragment: String): LinkBuilder = {
    def part = new UriPartBuilder {
      def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        chain(builder.map(_.fragment(fragment)))
      }
    }
    new LinkBuilderImpl(builders :+ part)
  }
}

class ResourceActionLinkBuilderImpl[A](context: LinkContext,
                                       builders: Seq[UriPartBuilder],
                                       clazz: Class[A]) extends ResourceLinkBuilderImpl(context,
                                                                                        builders)
                                                        with ResourceActionLinkBuilder[A] {
  def action(call: (A => Any)): ResourceLinkBuilder = {
    // TODO: implement proxy based method invokation to action call
    error("not yet implemented")
  }

  def action(methodName: String, params: Map[String, AnyRef]): ResourceLinkBuilder = {
    val method = clazz.getMethods
                      .find(_.getName == methodName)
                      .getOrElse(error("could not find method with name %s in class %s".format(methodName, clazz)))
    val pathParamNames = for(paramAnnotations <- method.getParameterAnnotations;
                             a <- paramAnnotations;
                             if(a.isInstanceOf[PathParam]))
                         yield (a.asInstanceOf[PathParam].value)
    val pathParams = Map.empty ++ params.filterKeys(pathParamNames.contains(_))
    val queryParamNames = for(paramAnnotations <- method.getParameterAnnotations;
                              a <- paramAnnotations;
                              if(a.isInstanceOf[QueryParam]))
                          yield (a.asInstanceOf[QueryParam].value)
    val queryParams = params.filterKeys(queryParamNames.contains(_))
    def path = new UriPartBuilder {
      def apply(uriBuilder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        val myUriBuilder = uriBuilder.map(b => {
            queryParamNames.foldLeft(b)((builder, name) =>
              params.get(name)
                    .map(builder.queryParam(name, _))
                    .getOrElse(builder))
          })
        chain(myUriBuilder.map(_.path(method)), pathParams)
      }
    }
    new ResourceLinkBuilderImpl(context, builders :+ path)
  }

  def action(methodName: String, params: Seq[AnyRef]): ResourceLinkBuilder = {
    // TODO: implement action
    error("not yet implemented")
  }
}

class ResourceLinkBuilderImpl(context: LinkContext,
                              builders: Seq[UriPartBuilder]) extends FragmentLinkBuilderImpl(builders)
                                                             with ResourceLinkBuilder {
  def this(context: LinkContext) = this(context, Seq.empty)

  def resolvedBy[A<:AnyRef](implicit clazz: ClassManifest[A]): ResourceActionLinkBuilder[A] = {
    def rootPathBuilder(clazz: Class[_]) = new UriPartBuilder {
      def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        chain(builder.map(_.path(clazz)))
      }
    }

    def subPathBuilder = new UriPartBuilder {
      def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        chain(builder)
      }
    }

    clazz match {
      case RootResource(c) => new ResourceActionLinkBuilderImpl[A](context,
                                                                   Seq(context.baseUriBuilder(clazz),
                                                                       rootPathBuilder(clazz.erasure)),
                                                                   c)
      case other => new ResourceActionLinkBuilderImpl[A](context,
                                                         Seq(subPathBuilder),
                                                         clazz.erasure.asInstanceOf[Class[A]])
    }
  }

  def resolvedBy[A<:AnyRef](target: A): ResourceActionLinkBuilder[A] = {
    val clazz = target.getClass.asInstanceOf[Class[A]]
    if(Option(clazz.getAnnotation(classOf[Path])).map(_ => true).getOrElse(false)) {
      resolvedBy(fromClass(clazz))
    } else {
      new ResourceActionLinkBuilderImpl[A](context,
                                           builders :+ instanceBuilder(target),
                                           clazz)
    }
  }

  def instanceBuilder[A<:AnyRef](instance: A) = {
    val clazz = instance.getClass
    val calzzManifest = fromClass(clazz).asInstanceOf[ClassManifest[A]]
    new UriPartBuilder {
      def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        def fallback: Option[URI] = {
          def linkBuilder = new ResourceLinkBuilderImpl(context, Seq.empty)
          val start: Option[LinkBuilder] = None
          val resolvers = context.resolverFor(calzzManifest)
          val resolvedLinkBuilder = resolvers.foldLeft(start)((current, resolve) => current.orElse(resolve(instance, linkBuilder)))
                                             .getOrElse(error(format("Could not determine uri for %s", instance)))
          resolvedLinkBuilder.build.flatMap(uri => chain(Some(UriBuilder.fromUri(uri))))
        }
        // if a uri builder is defined the caller already defined how the sub resource is resolved
        // otherwise use the fallback which itself uses the defined sequence of link resolvers
        builder.flatMap { defined =>
          chain(Some(defined))
        }.orElse {
          fallback
        }
      }
    }
  }
}

class LinkBuilderImpl(builders: Seq[UriPartBuilder]) extends LinkBuilder {

  def build: Option[URI] = {

    def chain(tail: Seq[UriPartBuilder]) = new UriBuilderChain {
      val more = tail.iterator
      var allparams: Map[String, AnyRef] = Map.empty
      def apply(builder: Option[UriBuilder], params: Map[String, AnyRef]): Option[URI] = {
        allparams = allparams ++ params
        if(more.hasNext) {
          more.next.apply(builder, this)
        } else {
          builder.map(_.buildFromMap(asMap(scala.collection.mutable.Map.empty ++ allparams)))
        }
      }
    }

    builders match {
      case head :: tail => head(None, chain(tail))
      case _ => None
    }
  }
}

object RootResource {

  def unapply[A<:AnyRef](clazz: ClassManifest[A]): Option[Class[A]] = {
    unapply(clazz.erasure.asInstanceOf[Class[A]])
  }

  def unapply[A<:AnyRef](clazz: Class[A]): Option[Class[A]] = {
    Option(clazz.getAnnotation(classOf[Path])).map(x => clazz)
  }
}