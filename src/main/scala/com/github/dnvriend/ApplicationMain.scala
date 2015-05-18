package com.github.dnvriend

import akka.actor._
import akka.camel.{CamelMessage, Consumer, Oneway, Producer}
import akka.event.{Logging, LoggingAdapter, LoggingReceive}
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor._
import akka.stream.scaladsl._
import akka.stream.{ActorFlowMaterializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import com.github.dnvriend.activemq.{ActiveMqComponent, CamelHelper}
import com.github.dnvriend.rabbitmq.{RabbitConnection, RabbitRegistry}
import io.scalac.amqp._

import scala.annotation.tailrec
import scala.concurrent.duration._

object ApplicationMain extends App {
  implicit val system = ActorSystem("MyActorSystem")
  implicit val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val ec = system.dispatcher
  implicit val mat = ActorFlowMaterializer()
  implicit val timeout = Timeout(10.seconds)

  CamelHelper.addComponent(new ActiveMqComponent("broker1"))

  val queueName = "testQueue"

  // generate numbers every 1 second
  val getallenSource: Source[Long, Unit] =
    Source(() => Iterator from 0)
      .buffer(1, OverflowStrategy.backpressure)
      .map(x => { Thread.sleep(1000); x } )

  // 1st RunnableFlow: send to AMQ
  getallenSource.log("send to amq").runWith(Sink.actorSubscriber(Props(new JmsProducer(queueName))))

  // 2nd RunnableFlow: read from AMQ print on console
//  Source.actorPublisher(Props(new JmsConsumer(queueName)))
//    .runForeach(println)

  // 3nd RunnableFlow: read from AMQ, write to RMQ
  val connection = RabbitConnection(system).connection
  val mapToRouted = Flow[Long].map (number => Routed(routingKey = "", Message(body = ByteString(number.toString))))
  val mapToMessage = Flow[Long].map (number => Message(body = ByteString(number)))
  val rabbitSink: Sink[Routed, Unit] = Sink(connection.publish(RabbitRegistry.outboundNumbersExchange.name))
  Source.actorPublisher(Props(new JmsConsumer[Long](queueName)))
    .via(mapToRouted)
    .runWith(rabbitSink)

  // 4th RunnableFlow: read from RMQ, print on console
  val decodeMessage = Flow[Delivery].map (delivery => delivery.message.body.decodeString("UTF-8"))
  Source(connection.consume(RabbitRegistry.inboundNumbersQueue.name))
    .via(decodeMessage)
    .log("Decoded")
    .runForeach(_ => ())

  system.awaitTermination()
}

class JmsConsumer[T](queueName: String) extends Consumer with ActorPublisher[T] with ActorLogging {
  override def endpointUri: String = "activemq:" + queueName

  var buf = Vector.empty[T]

  override def receive: Receive = LoggingReceive {
    case msg @ CamelMessage(body, headers) =>
      buf = buf :+ body.asInstanceOf[T]
      log.info("Buffer size: {}", buf.size)
      if(totalDemand > 0) deliverBuf()

    case Request(_) => deliverBuf()

    case Cancel => stop()

    case m => log.info("[Dropping]: {}", m)
  }

  @tailrec final def deliverBuf(): Unit =
    if (totalDemand > 0) {
      /*
       * totalDemand is a Long and could be larger than
       * what buf.splitAt can accept
       */
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliverBuf()
      }
    }

  def stop(): Unit = {
    context.stop(self)
  }

  override def preStart(): Unit = {
    log.info("Starting")
    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("Stopping")
    super.postStop()
  }
}

class JmsProducer(queueName: String) extends Producer with Oneway with ActorLogging with ActorSubscriber {
  override def endpointUri: String = "activemq:" + queueName

  override protected def requestStrategy: RequestStrategy = ZeroRequestStrategy



  request(1)

  override protected def produce: Receive = {
    case OnNext(msg: Long)                      => super.produce(msg); request(1)
    case msg: NoSerializationVerificationNeeded => super.produce(msg)
    case OnComplete                             => //stop(); // when we stop the actor, buffered messages will not be sent to the broker
    case OnError(t)                             => stop()
    case m                                      => log.info("[Dropping]: {}", m)
  }

  def stop(): Unit = {
    context.stop(self)
  }

  override def preStart(): Unit = {
    log.info("Starting")
    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("Stopping")
    super.postStop()
  }
}

