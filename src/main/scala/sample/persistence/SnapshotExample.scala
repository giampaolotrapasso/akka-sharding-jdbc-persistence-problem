package sample.persistence

import akka.actor._
import akka.contrib.pattern.ShardRegion.Passivate
import akka.actor.ReceiveTimeout
import akka.persistence._
import scala.concurrent.duration._
import akka.contrib.pattern.{ShardRegion, ClusterSharding}
import scalikejdbc._

import scala.util.{Success, Failure, Try}

object MysqlTableInit {
  implicit val session = AutoSession

  def createJournalTable() = Try {
    SQL(
      """
        |CREATE TABLE IF NOT EXISTS journal (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_number BIGINT NOT NULL,
        |  marker VARCHAR(255) NOT NULL,
        |  message TEXT NOT NULL,
        |  created TIMESTAMP NOT NULL,
        |  PRIMARY KEY(persistence_id, sequence_number)
        |)
      """.stripMargin).update().apply
  }

  def createSnapshotTable() = Try {
    SQL(
      """
        |CREATE TABLE IF NOT EXISTS snapshot (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  snapshot TEXT NOT NULL,
        |  created BIGINT NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
      """.stripMargin).update().apply
  }
}

class ExampleKernel extends akka.kernel.Bootable {
  override def startup(): Unit = {
    SnapshotExample.main(Array.empty[String])
  }

  override def shutdown(): Unit = {

  }
}

object SnapshotExample extends App {

  def retry(num: Int, retried: Int = 0)(block: => Try[Unit]): Try[Unit] = block match {
    case r if r.isSuccess => r
    case r if r.isFailure && num >= retried =>
      Thread.sleep(1000)
      retry(num, retried + 1)(block)
    case u => Failure(new RuntimeException("Retries exceeded"))
  }

  val system = ActorSystem("example")

  retry(100) {
    MysqlTableInit.createJournalTable().flatMap { _ =>
      MysqlTableInit.createSnapshotTable().map(_ => ())
    }
  }.recover { case t: Throwable =>
    println("Could not initialize the database; exitting...")
    System.exit(1)
  }

  val idExtractor: ShardRegion.IdExtractor = {
    case (cmd: String, id: String) => ((id.hashCode % 10).toString, cmd)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case (_, _) => "0"
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
      case SaveSnapshotSuccess(metadata)         => println("SaveSnapshotSuccess: " + metadata)
      case SaveSnapshotFailure(metadata, reason) => println("SaveSnapshotFailure: " + metadata + " failure: " + reason)
      case s: String =>
        persist(s) { evt =>
          state = state.updated(evt);
          println("received: " + s + " state is: " + state)
        }
    }

    def receiveRecover: Actor.Receive = {
      case SnapshotOffer(_, s: ExampleState) =>
        println("[RECOVER] => offered state = " + s)
        state = s
      case evt: String =>
        println("[RECOVER] => offered event: " + evt)
        state = state.updated(evt)
    }

    override def unhandled(msg: Any): Unit = msg match {
      case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
      case _              => super.unhandled(msg)
    }

  }


  val region: ActorRef = ClusterSharding(system).start(
    typeName = SnapshotExample.shardName,
    entryProps = Some(SnapshotExample.props()),
    idExtractor = SnapshotExample.idExtractor,
    shardResolver = SnapshotExample.shardResolver)


  for(i <- 1 to 5) {
    println("Send " + i)
    region ! (i.toString, i.toString)
    region ! ("print", i.toString)
    region ! ("snap", i.toString)
    Thread.sleep(1500)      // receive timeout
    region ! ("print", i.toString)   // should recover
  }

  Thread.sleep(1000)
  system.shutdown()
}
