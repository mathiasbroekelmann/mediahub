/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest

import org.junit._
import Assert._

import org.hamcrest.CoreMatchers._

import javax.ws.rs.core.Application
import javax.ws.rs._
import javax.ws.rs.ext._

import scala.collection.JavaConversions._

class RestApplicationRegistrationTest {

  var appregistry: ApplicationRegistry = _

  var registration: Registration = _

  var restAppReg: RestApplicationRegistration = _

  @volatile
  var registerCalled: Int = _

  @volatile
  var unregisterCalled: Int = _

  @volatile
  var registeredApp: Application = _

  @Before
  def setUp: Unit = {
    registerCalled = 0
    unregisterCalled = 0
    registration = new Registration {
      def unregister {
        unregisterCalled += 1
      }
    }
    appregistry = new ApplicationRegistry {
      def register(app: Application): Registration = {
        println("app registered: " + app)
        registerCalled += 1
        registeredApp = app
        assertThat(app, not(nullValue[Application]))
        registration
      }
    }
    restAppReg = new RestApplicationRegistration(appregistry)
  }

  @Test
  def spike = {
    val myregistrar = registrar
    restAppReg.collector ! Added(myregistrar)
    assertThat(registerCalled, is(0))
    Thread.sleep(100)
    restAppReg.collector ! Added(registrar2)
    assertThat(registerCalled, is(0))
    Thread.sleep(2000)
    assertThat(registerCalled, is(1))
    assertThat(registeredApp, not(nullValue[Application]))
    val rootResourceClass = registeredApp.getClasses.find(_ == classOf[RootResource])
    assertThat(rootResourceClass, not(nullValue[Any]))
    restAppReg.collector ! Removed(myregistrar)
    assertThat(unregisterCalled, is(0))
    Thread.sleep(2000)
    assertThat(unregisterCalled, is(1))
    assertThat(registerCalled, is(2))
    val rootResourceClass2 = registeredApp.getClasses.find(_ == classOf[RootResource])
    assertThat(rootResourceClass2, nullValue[Any])
    val providerSinglton = registeredApp.getSingletons.find(_.isInstanceOf[SomeProvider])
    assertThat(providerSinglton, not(nullValue[Any]))
  }

  val registrar = new RestRegistrar {
    def register(registry: RestRegistry) {
      registry.register[RootResource]
    }
  }

  val registrar2 = new RestRegistrar {
    def register(registry: RestRegistry) {
      registry.registerSingleton(new SomeProvider)
    }
  }
}

@Provider
class SomeProvider

@Path("foo")
class RootResource
