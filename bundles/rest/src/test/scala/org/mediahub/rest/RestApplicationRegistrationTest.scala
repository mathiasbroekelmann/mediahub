/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest.internal

import org.junit._
import Assert._

import org.hamcrest.CoreMatchers._

import javax.ws.rs.core.Application
import javax.ws.rs._
import javax.ws.rs.ext._

import org.mediahub.rest._

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

  var someProvider: SomeProvider = _

  @Before
  def setUp {
    registerCalled = 0
    unregisterCalled = 0
    someProvider = new SomeProvider
    registration = new Registration {
      def unregister {
        unregisterCalled += 1
      }
    }
    appregistry = new ApplicationRegistry {
      def register(app: Application): Registration = {
        registerCalled += 1
        registeredApp = app
        assertThat(app, not(nullValue[Application]))
        registration
      }
    }
    restAppReg = new RestApplicationRegistration(appregistry)
  }

  @After
  def tearDown {
    restAppReg.close
  }

  @Test
  def spike = {
    val myregistrar = registrar
    restAppReg.collector ! Added(myregistrar)
    assertThat(registerCalled, is(0))
    restAppReg.collector ! Added(registrar2)
    assertThat(registerCalled, is(0))
    Thread.sleep(1000)
    val calledRegisterMethods = registerCalled
    assertThat(registeredApp, not(nullValue[Application]))
    val rootResourceClass = registeredApp.getClasses.find(_ == classOf[RootResource])
    assertThat(rootResourceClass, is(Some(classOf[RootResource]).asInstanceOf[Option[Class[_]]]))
    val calledUnRegisterMethods = unregisterCalled
    restAppReg.collector ! Removed(myregistrar)
    Thread.sleep(1000)
    assertThat(registerCalled, is(calledRegisterMethods)) // should not register since no root resource is deployed
    assertThat(unregisterCalled, is(calledUnRegisterMethods + 1)) // should not register since no root resource is deployed
  }

  val registrar = new RestRegistrar {
    def register(registry: RestRegistry) {
      registry.register[RootResource]
    }
  }

  val registrar2 = new RestRegistrar {
    def register(registry: RestRegistry) {
      registry.registerSingleton(someProvider)
    }
  }
}

@Provider
class SomeProvider

@Path("foo")
class RootResource
