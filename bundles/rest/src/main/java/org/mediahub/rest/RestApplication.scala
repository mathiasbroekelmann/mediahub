/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest

import org.restmodules.RestmodulesApplication

import scala.collection.JavaConversions._

class RestApplication extends RestmodulesApplication {
  def getClasses: java.util.Set[Class[Object]] = scala.collection.mutable.Set.empty[Class[Object]]
}
