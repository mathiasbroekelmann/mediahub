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

trait ResourceListener {
  def added(resource: Resource, node: Node)
  def modified(resource: Resource, node: Node)
  def removed(resource: Resource, node: Node)
}

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

  /**
   * collect the given resources.
   */
  def collect(resources: Traversable[Resource]) = new {

    /**
     * use the given base node to store the references to the collected resources.
     */
    def into(baseNode: Node): Traversable[(Resource, Node)] = {
      for(resource <- resources)
        yield (resource, resolveResourceNode(baseNode, resource))
    }
  }

  /**
   * get the resources that have been collected previously.
   */
  def resources = new {
    /**
     * get the resources below the node.
     * @param baseNode the node that is used as the base where the resources are stored.
     * @param factory create the resource for the given uri.
     *
     * @return a tuple of the resource and the corresponsing node that identifies the resource.
     *          Use the identifier of the node to referr to this resource
     */
    def from(baseNode: Node) (factory: PartialFunction[URI, Resource]): Traversable[(Resource, Node)] = {

      /**
       * create the resource for the given resource node.
       *
       * @return None if no such resource could be created.
       */
      def resourceFrom(node: Node): Option[Resource] = {

        def create(uri: URI): Option[Resource] = {
          if(factory.isDefinedAt(uri)) {
            Some(factory(uri))
          } else {
            None
          }
        }

        for(uriString <- node.string("uri");
            resource <- create(URI.create(uriString)))
              yield (resource)
      }

      // we have a three level hierachy of nodes to cluster the resources.
      for(first <- baseNode.nodes.toStream;
          second <- first.nodes.toStream;
          resourcesNode <- second.nodes.toStream;
          resourceNode <- resourcesNode.nodes.toStream;
          resource <- resourceFrom(resourceNode))
            yield (resource, resourceNode)
    }
  }

  /**
   * get or create the resource node for the given resource somewhere below base.
   * populate is used to when the does not exist to fill the properties in the node.
   */
  private def resolveResourceNode(base: Node, resource: Resource): Node = {

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

    /**
     * create a node for the resource
     */
    def createNodeForResource(in: Node): Node = {
      val existingNames = in.nodes.map(_.getName)
      val node = in |= uniqueName(existingNames, resource.name, "%s_%d")
      node ++ ("uri" -> resource.uri)
      for(lastModified <- resource.lastModified) {
        node ++ ("lastModified" -> lastModified)
      }
      node
    }

    /**
     * make a path like this: ABC/DEF/ABCDEF123456789
     */
    def pathToResources = {
      val md5Hex = resource.uri.md5Hex
      val partLen = 2
      // TODO: thats not nice. make the jcr dsl smarter so this is a charme
      format("%s/%s/%s", 
             md5Hex.take(partLen),
             md5Hex.drop(partLen).take(partLen),
             md5Hex)
    }

    // get the node that contains the resources sharing the same path
    val resourcesNode = (base |= pathToResources)
    
    // find an existing node that shares the same uri
    val uriToFindNodeFor = Some(resource.uri.toString)
    val existingNode = resourcesNode.nodes.find { 
      _.string("uri") == uriToFindNodeFor
    }
    
    // return the node or create a new node for the resource
    existingNode getOrElse createNodeForResource(resourcesNode)
  }

}

case class Resources(resources: Traversable[Resource])
