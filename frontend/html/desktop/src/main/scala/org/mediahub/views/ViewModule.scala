/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.views

trait ViewModule {
  def configure(viewBinder: ViewBinder): Unit
}

class AbstractViewModule extends ViewModule {
  final def configure(viewBinder: ViewBinder): Unit = {
    
  }
}
