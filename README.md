reactive-amq-rmq-bridge
=======================
This is a proof of concept for a bridge between ActiveMQ and RabbitMQ using Akka-Camel and Akka-Stream

# Flows
There are three flows:

1. An Akka Source that produces numbers and puts it on activemq:testQueue,
2. An Akka Source that is an Camel Consumer and consumes CamelMessage(s) and produces numbers. Those numbers will be sent to the RabbitMQ exchange, controlled by the totalDemand,
3. An Akka Source that is a RabbitMQ Producer that consumes numbers from the RabbitMQ queue. Those numbers will be sent to the LoggingAdapter.

![Three Flows](https://github.com/dnvriend/reactive-amq-rmq-bridge/blob/master/img/three_flows.png "Three Flows")

