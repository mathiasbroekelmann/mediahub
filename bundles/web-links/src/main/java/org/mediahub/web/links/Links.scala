/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.web.links

import java.net.URI
import javax.ws.rs._
import core._
import scala.xml._
import scala.collection.JavaConversions._

import scala.reflect.ClassManifest._

object Links extends Links(null) {
  implicit def linkBuilderToString(builder: LinkBuilder): String = {
    builder.build.map(x =>
      x.toString).getOrElse(error("not a valid link spec: " + builder))
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

trait LinkContext {

  /**
   * resolve the UriBuilder initialized with the base uri for the given class type.
   */
  def baseUri[A<:AnyRef](clazz: ClassManifest[A]): Option[UriBuilder]

  /**
   * get all link resolvers that can handle the defined class type.
   */
  def resolverFor[A<:AnyRef](clazz: ClassManifest[A]): Seq[LinkResolver[A]]

  /**
   * convienience function to create a uri part builder with the base uri for the given class type.
   */
  def baseUriBuilder[A<:AnyRef](clazz: ClassManifest[A]) = new UriPartBuilder {
    def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
      chain(baseUri(clazz))
    }
  }
}

class Links(val context: LinkContext) {

  def linkTo[A<:AnyRef](implicit clazz: ClassManifest[A]): ResourceActionLinkBuilder[A] = 
    new ResourceLinkBuilderImpl(context).resolvedBy[A]

  
  def linkTo[A<:AnyRef](target: A): ResourceActionLinkBuilder[A] = {
    new ResourceLinkBuilderImpl(context).resolvedBy(target)
  }
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

  def action(methodName: String): ResourceLinkBuilder = {
    def path = new UriPartBuilder {
      def apply(uriBuilder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        chain(uriBuilder.map(_.path(clazz, methodName)))
      }
    }
    new ResourceLinkBuilderImpl(context, builders :+ path)
  }

  def action(methodName: String, params: Map[String, AnyRef]): ResourceLinkBuilder = {
    def path = new UriPartBuilder {
      def apply(uriBuilder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
        chain(uriBuilder.map(_.path(clazz, methodName)), params)
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
    target.getClass.asInstanceOf[Class[A]] match {
      case RootResource(clazz) => resolvedBy(fromClass(clazz))
      case other => new ResourceActionLinkBuilderImpl[A](context, 
                                                         builders :+ instanceBuilder(other),
                                                         other)
    }
  }

  def instanceBuilder[A<:AnyRef](instance: A)(implicit clazz: ClassManifest[A]) = new UriPartBuilder {
    def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI] = {
      def fallback: Option[URI] = {
        def linkBuilder = new ResourceLinkBuilderImpl(context, Seq.empty)
        val start: Option[LinkBuilder] = None
        val resolvers = context.resolverFor(clazz)
        val resolvedLinkBuilder = resolvers.foldLeft(start)((current, resolve) => current.orElse(resolve(instance, linkBuilder)))
                                           .getOrElse(error(format("Could not determine uri for %s", instance)))
        resolvedLinkBuilder.build.flatMap(uri => chain(Some(UriBuilder.fromUri(uri))))
      }
      // if a uri builder is defined the caller already defined how the sub resource is resolved
      // otherwise use the fallback which itself uses the defined sequece of link resolvers
      builder.flatMap(defined => chain(Some(defined))).orElse(fallback)
    }
  }
}

trait UriPartBuilder {
  def apply(builder: Option[UriBuilder], chain: UriBuilderChain): Option[URI]
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

trait UriBuilderChain {
  def apply(builder: Option[UriBuilder]): Option[URI] = apply(builder, Map[String, AnyRef]())

  def apply(builder: Option[UriBuilder], params: Map[String, AnyRef]): Option[URI]

  def apply(builder: Option[UriBuilder], param: (String, AnyRef), others: (String, AnyRef)*): Option[URI] = others match {
    case null => apply(builder, Map(param))
    case more => apply(builder, Map(param) ++ more)
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

trait LinkBuilder {
  /**
   * build the actual uri.
   */
  def build: Option[URI]
}

trait FragmentLinkBuilder extends LinkBuilder {
  /**
   * define the fragment of the link.
   */
  def fragment(fragment: String): LinkBuilder
}

trait ResourceActionLinkBuilder[A] extends ResourceLinkBuilder {
  /**
   * specify the action (method) that is used to resolve the resource. The actual method is not executed.
   * Only the passed parameters where collected to build the link.
   * @param call call the method with the parameters that should be used to build the link.
   * Use null for any @Context annotated or other uri unrelated parameters.
   * It is also possible to pass null for uri related parameters that have a given default value.
   */
  def action(call: (A => Any)): ResourceLinkBuilder
  
  /**
   * define a method which with a @Path annotation to build the link
   */
  def action(methodName: String): ResourceLinkBuilder

  def action(methodName: String, params: Map[String, AnyRef]): ResourceLinkBuilder

  def action(methodName: String, params: Seq[AnyRef]): ResourceLinkBuilder

  def action(methodName: String, param: (String, AnyRef), others: (String, AnyRef)*): ResourceLinkBuilder = {
    action(methodName, Map(param) ++ others)
  }

  def action(methodName: String, param: AnyRef, others: AnyRef*): ResourceLinkBuilder = {
    action(methodName, Seq(param) ++ others)
  }
}

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

@Path("path")
class RootResource {
  @GET
  def get = None

  @Path("{id}")
  def get(@PathParam("id") id: String) = None
}

//@ResolvedBy(classOf[MyResource])
class SubResource {
  @GET
  def getSub = None

  @GET
  @Path("{id}")
  def getSub(@PathParam("id") id: String) = None
}

object Test {
  val root = new RootResource
  val sub = new SubResource

  import Links._

  val html = <p>
    <a href={linkTo(root)}>Link to a root resource with path /path</a>
    <!-- <a href={linkTo(root).action(_.get("foo"))}>Link to a root resource with path /path/foo</a> -->
    <a href={linkTo(sub)}>Link to a sub resource. If no link resolver is registered that is capable of resolving a path to such resource a runtime error is thrown.</a>
    <a href={linkTo(sub).fragment("divid")}>Link to a sub resource with a defined fragment id. If no link resolver is registered that is capable of resolving a path to such resource a runtime error is thrown.</a>
    <a href={linkTo(sub).resolvedBy[RootResource]}>Link to a sub resource by explicitly defining the root resource.</a>
    <!-- <a href={linkTo(sub).action(_.getSub("bar"))
             .resolvedBy[RootResource]
             .action(_.get("foo"))}>Link to a sub resource resolved by explicitly defining the root resource with path /path/foo/bar</a>-->
             </p>
}
