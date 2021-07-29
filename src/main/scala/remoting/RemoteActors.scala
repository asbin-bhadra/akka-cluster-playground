package remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorSystem, Identify, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

object RemoteActors extends App{

  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("remoting/remoteActors.conf"))
  val localSimpleActor = localSystem.actorOf(Props[SimpleActor], "LocaleSimpleActor")
  localSimpleActor ! "Hello local actor"

  // Method1 :
  val remoteActorSelection = localSystem.actorSelection("akka://remoteSystem@localhost:2552/user/RemoteSimpleActor")
  remoteActorSelection ! "hello from the \"local\" JVM"

  // Method2 :
  import localSystem.dispatcher
  implicit val timeout = Timeout(3 seconds)
  val remoteActorRefFuture = remoteActorSelection.resolveOne()

  remoteActorRefFuture.onComplete{
      case Success(actorRef) => actorRef ! "i have resolved you in  a future!"
      case Failure(exception) => println(s"Failure : $exception")
    }

  class ActorResolver extends Actor with ActorLogging{
    override def preStart(): Unit = {
      val selection = context.actorSelection("akka://remoteSystem@localhost:2552/user/RemoteSimpleActor")
      selection ! Identify(42)
    }

    override def receive: Receive = {
      case ActorIdentity(42, Some(actorRef)) =>
        actorRef ! "thank you for identifying yourself"
    }
  }

  localSystem.actorOf(Props[ActorResolver],"localActorResolver")
}


object RemoteActors_Remote extends App{

  val remoteSystem = ActorSystem("remoteSystem", ConfigFactory.load("remoting/remoteActors.conf").getConfig("remoteSystem"))


  val remoteSimpleActor = remoteSystem.actorOf(Props[SimpleActor], "RemoteSimpleActor")

  remoteSimpleActor ! "Hello remote Actor"
}
