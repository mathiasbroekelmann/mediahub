/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.rest

import org.junit._
import Assert._

import scala.actors.Actor._
import scala.actors.Actor

class ActorTest {

    @Before
    def setUp {
    }

    @After
    def tearDown {
    }

    @Test
    def example {
      val pong = new Pong
      val ping = new Ping(100000, pong)
      ping.start
      pong.start
      Thread.sleep(1000)
    }

}

case object Ping
case object Pong
case object Stop

class Ping(count: Int, pong: Actor) extends Actor {
  def act() {
    var pingsLeft = count - 1
    pong ! Ping
    loop {
      react {
        case Pong =>
          if (pingsLeft % 1000 == 0)
            Console.println("Ping: pong")
          if (pingsLeft > 0) {
            pong ! Ping
            pingsLeft -= 1
          } else {
            Console.println("Ping: stop")
            pong ! Stop
            exit()
          }
      }
    }
  }
}

class Pong extends Actor {
  def act() {
    var pongCount = 0
    loop {
      react {
        case Ping =>
          if (pongCount % 1000 == 0)
            Console.println("Pong: ping "+pongCount)
          sender ! Pong
          pongCount = pongCount + 1
        case Stop =>
          Console.println("Pong: stop")
          exit()
      }
    }
  }
}
