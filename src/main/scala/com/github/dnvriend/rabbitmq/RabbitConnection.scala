package com.github.dnvriend.rabbitmq

import akka.actor.{Extension, ExtendedActorSystem, ExtensionIdProvider, ExtensionId}
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.Source
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import io.scalac.amqp.{Queue, Direct, Exchange, Connection}

object RabbitConnection extends ExtensionId[RabbitConnectionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): RabbitConnectionImpl = new RabbitConnectionImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = RabbitConnection
}

class RabbitConnectionImpl()(implicit val system: ExtendedActorSystem) extends Extension {
  implicit val flowMaterializer: FlowMaterializer = ActorFlowMaterializer()
  implicit val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val ec = system.dispatcher

  lazy val connection = Connection()

  Source.single(1)
    // declare and bind to the orders queue
    .mapAsync(1)(_ => connection.exchangeDeclare(RabbitRegistry.outboundNumbersExchange))
    .mapAsync(1)(_ => connection.queueDeclare(RabbitRegistry.inboundNumbersQueue))
    .mapAsync(1)(_ => connection.queueBind(RabbitRegistry.inboundNumbersQueue.name, RabbitRegistry.outboundNumbersExchange.name, routingKey = ""))
    .runForeach(_ => ())
}

object RabbitRegistry {
  val outboundNumbersExchange = Exchange(name = "numbers.outbound.exchange", `type` = Direct, durable = true)

  val inboundNumbersQueue = Queue(name = "numbers.inbound.queue", durable = true)
}
