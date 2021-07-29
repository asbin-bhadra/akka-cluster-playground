package clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp}
import com.typesafe.config.ConfigFactory

object ChatDomain{

  case class ChatMessage(nickName:String, contents:String)
  case class UserMessage(contents:String)
  case class EnterRoom(fullAddress:String, nickName:String)
}

object ChatActor{
  def props(nickName:String, port:Int) = Props(new ChatActor(nickName, port))
}

class ChatActor(nickName:String, port:Int) extends Actor with ActorLogging{

  import ChatDomain._
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent]
    )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = online(Map())

  def online(chatRoom:Map[String, String]): Receive = {
    case MemberUp(member)=>
      val remoteChatActorSelection = getChatActor(member.address.toString)
      remoteChatActorSelection ! EnterRoom(s"${self.path.address}@localhost:$port", nickName)
    case MemberRemoved(member, _) =>
      val remoteNickName = chatRoom(member.address.toString)
      log.info(s"$remoteNickName left hte room")
      context.become(online(chatRoom - member.address.toString))
    case EnterRoom(remoteAddress, remoteNickName) =>
      if(remoteNickName != nickName) {
        log.info(s"$remoteNickName enter the room")
        context.become(online(chatRoom + (remoteAddress -> remoteNickName)))
      }

    case UserMessage(contents)=>
      chatRoom.keys.foreach{remoteAddressAsString =>
        getChatActor(remoteAddressAsString) ! ChatMessage(nickName, contents)

      }
    case ChatMessage(remoteNickName, contents) =>
      log.info(s"${remoteNickName} $contents")




  }

  def getChatActor(memberAddress: String) ={
    context.actorSelection(s"${memberAddress}/user/chatActor")

  }
}

class ChatApp(nickName:String, port:Int) extends App{
  import ChatDomain._

  val config = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = $port
      |""".stripMargin)
    .withFallback(ConfigFactory.load("clustering/clusterChat.conf"))

  val system = ActorSystem("asbin", config)

  val chatActor = system.actorOf(ChatActor.props(nickName,port), "chatActor")

  scala.io.Source.stdin.getLines().foreach{line =>
    chatActor ! UserMessage(line)
  }

}

object Alice extends ChatApp("Alice", 2551)
object Bob extends ChatApp("Bob", 2552)
object Charlie extends ChatApp("Charlie", 2553)