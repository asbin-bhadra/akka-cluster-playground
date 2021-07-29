package remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSystem, Identify, PoisonPill, Props}
import com.typesafe.config.ConfigFactory

object WordCountDomain {
  case class Initialize(nWorkers: Int)
  case class WordCountTask(text:String)
  case class WordCountResult(count:Int)
  case object EndWordCount
}

class WordCountWorker extends Actor with ActorLogging{
  import WordCountDomain._

  override def receive: Receive = {
    case WordCountTask(text) =>
      log.info(s"I am processing $text")
      sender() ! WordCountResult(text.split(" ").length)
  }
}

class WordCountMaster extends Actor with ActorLogging{
  import  WordCountDomain._

  override def receive: Receive = {
    case Initialize(nWorkers)=>
      log.info("master Initializing...")
      val workerSelection = (1 to nWorkers).map(id => context.actorSelection(s"akka://WorkersSystem@localhost:2552/user/WordCountWorker$id"))
      workerSelection.foreach(_ ! Identify("asbin"))
      context.become(initializing(List(), nWorkers))
  }

  def initializing(workers: List[ActorRef], remainingWorkers: Int): Receive={

    case ActorIdentity("asbin", Some(workerRef)) =>
      log.info(s"Worker Identified : $workerRef")
      if(remainingWorkers == 1){
        context.become(online(workerRef :: workers, 0, 0))
      }
      else{
        context.become(initializing(workerRef :: workers, remainingWorkers -1))
      }
  }

  def online(workers: List[ActorRef], remainingTask:Int, totalCount:Int): Receive={
    case text:String =>
      val sentences = text.split("\\. ")
      Iterator.continually(workers).flatten.zip(sentences.iterator).foreach{ pair=>
        val (worker, sentence) = pair
        worker ! WordCountTask(sentence)
      }
      context.become(online(workers, remainingTask + sentences.length, totalCount))
    case WordCountResult(count) =>
      if(remainingTask == 1){
        log.info(s"TOTAL RESULT: ${totalCount + count}")
        workers.foreach(_ ! PoisonPill)
        context.stop(self)
      }
      else{
        context.become(online(workers, remainingTask - 1, totalCount + count))
      }
  }
}

object MasterApp extends App {

  import WordCountDomain._

  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2551
      |""".stripMargin)
    .withFallback(ConfigFactory.load("remoting/remoteActorExcercise.conf"))

  val system = ActorSystem("MasterSystem", config)

  val master = system.actorOf(Props[WordCountMaster], "WordCountMaster")

  master ! Initialize(5)
  Thread.sleep(1000)

  scala.io.Source.fromFile("src/main/resources/txt/lipsum.txt").getLines().foreach { line =>
    master ! line
  }
}

object WorkersApp extends App{
  import WordCountDomain._
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2552
      |""".stripMargin)
    .withFallback(ConfigFactory.load("remoting/remoteActorExcercise.conf"))

  val system = ActorSystem("WorkersSystem", config)

  (1 to 5).map{i=>
    system.actorOf(Props[WordCountWorker], s"WordCountWorker${i}")

  }
}
