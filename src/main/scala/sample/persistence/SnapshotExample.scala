package sample.persistence

import akka.actor._
import akka.contrib.pattern.ShardRegion.Passivate
import akka.actor.ReceiveTimeout
import akka.persistence._
import scala.concurrent.duration._
import akka.contrib.pattern.{ShardRegion, ClusterSharding}

object SnapshotExample extends App {


  val idExtractor: ShardRegion.IdExtractor = {
    case cmd: String  => ((cmd.hashCode % 10).toString, cmd)
  }

  val shardResolver: ShardRegion.ShardResolver = msg => msg match {
    case s : String => (s.hashCode % 2).toString
  }

  val shardName: String = "SnapshotActor"

  def props(): Props = Props(new ExamplePersistentActor())


  case class ExampleState(received: List[String] = Nil) {
    def updated(s: String): ExampleState = copy(s :: received)
    override def toString = received.reverse.toString
  }

  class ExamplePersistentActor extends PersistentActor {
    override def persistenceId: String = self.path.parent.name + "-" + self.path.name

    // passivate the entity when no activity
    context.setReceiveTimeout(1 seconds)

    var state = ExampleState()

    def receiveCommand: Actor.Receive = {
      case "print"                               => println("current state = " + state)
      case "snap"                                => saveSnapshot(state)
      case SaveSnapshotSuccess(metadata)         => // ...
      case SaveSnapshotFailure(metadata, reason) => // ...
      case s: String =>
        persist(s) { evt => state = state.updated(evt); println("received: " + s) }
    }

    def receiveRecover: Actor.Receive = {
      case SnapshotOffer(_, s: ExampleState) =>
        println("offered state = " + s)
        state = s
      case evt: String =>
        state = state.updated(evt)
    }

    override def unhandled(msg: Any): Unit = msg match {
      case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
      case _              => super.unhandled(msg)
    }

  }

  val system = ActorSystem("example")

  val region: ActorRef = ClusterSharding(system).start(
    typeName = SnapshotExample.shardName,
    entryProps = Some(SnapshotExample.props()),
    idExtractor = SnapshotExample.idExtractor,
    shardResolver = SnapshotExample.shardResolver)



  for(i <- 1 to 10000){
    println("Send " + i)
    region ! i.toString()
    region ! "snap"
    Thread.sleep(600)


  }


  Thread.sleep(1000)
  system.shutdown()
}
