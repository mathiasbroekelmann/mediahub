/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import java.net.URI

import javax.jcr.Node

import org.mediahub.resources._

import org.mediahub.jcr.JcrDsl._

import scala.collection.JavaConversions._

/**
 * Service definition to track resources.
 * Resources will be identified through their uri.
 * The lastmodified timestamp will be used to determine if a resource has been changed
 *
 *<ul>
 * <li>Tracked resources will be stored in the jcr.
 * <li>Below a given base node the hashcode of the uri will be used to cluster the resources
 * <li>A node of type nt:resources contains a property @resources which holds the node references to the actual resources
 * in the same folder sharing the same hascode.
 * <li>The name of each resource node uses the name of the refered resource optionally appended by a suffix
 * if that more resources share the same hashcode and the same name.
 * <li>raise an event about added/updated/deleted resources.
 * <li>deleted resources where collected regularly by checking if they exists. TODO: regularly depends on the resource location
 * (ex. its ok to check files quite often but thats probably not true for http based resources.)
 */
object ResourceTracking {

  implicit def encodeString(string: String) = new {
    def md5Hex: String = {
      val md5 = java.security.MessageDigest.getInstance("MD5")
      md5.reset()
      md5.update(string.getBytes("UTF-8"))
      md5.digest().map(0xFF & _).map { "%02x".format(_) }.mkString
    }
  }

  implicit def encodeUri(uri: URI) = new {
    def md5Hex: String = uri.toString.md5Hex
  }

  def collect(resources: Traversable[Resource]) = new {
    def into(baseNode: Node): Traversable[(Resource, Node)] = {
      for(resource <- resources)
        yield (resource, resolveResourceNode(baseNode, resource))
    }
  }

  /**
   * get or create the resource node for the given resource somewhere below base.
   * populate is used to when the does not exist to fill the properties in the node.
   */
  def resolveResourceNode(base: Node, resource: Resource): Node = {
    val md5Hex = resource.uri.md5Hex

    def resourceNodeFactory(node: Node, name: String): Node = {
      node.addNode(name, "nt:resource")
    }

    def uniqueName(existingNames: Traversable[String], name: String, pattern: String): String = {

      /**
       * create indexed names
       */
      def indexedName(index: Int): Stream[String] = {
        Stream.cons(format(pattern, name, index), indexedName(index + 1))
      }

      /**
       * create a list of names. Limit number to 100 to avoid endless recursion.
       */
      val names: Traversable[String] = Stream.cons(name, indexedName(1)).take(100)

      def available(name: String) = existingNames.find(_ == name).isEmpty

      names.find(available _).getOrElse(error("could not determine an unique name for the node of " + resource))
    }

    // make a path like this: ABC/DEF/ABCDEF123456789
    val partLen = 2
    val pathToResourcesOfSameMd5 = format("%s/%s/%s",
                                          md5Hex.take(partLen),
                                          md5Hex.drop(partLen).take(partLen),
                                          md5Hex)

    // TODO: thats not nice. make the jcr dsl smarter so this is a charme
    val resourcesNode = (base |= pathToResourcesOfSameMd5)
    val existingNode = resourcesNode.nodes.find(_.string("uri") == resource.uri)
    existingNode match {
      case Some(node) => node
      case None => {
          val existingNames = resourcesNode.nodes.map(_.getName)
          val node = resourcesNode |= uniqueName(existingNames, resource.name, "%s_%d")
          node ++ ("uri" -> resource.uri)
          for(lastModified <- resource.lastModified) {
            node ++ ("lastModified" -> lastModified)
          }
          node
        }
    }
  }

}

case class Resources(resources: Traversable[Resource])
