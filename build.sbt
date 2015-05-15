import spray.revolver.RevolverPlugin.Revolver

name := "reactive-amq-rmq-bridge"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= {
  val akkaVersion   = "2.3.11"
  val streamVersion   = "1.0-RC2"
  val activemqVersion = "5.10.0"
  Seq(
    "com.typesafe.akka"  %% "akka-actor"                       % akkaVersion,
    "com.typesafe.akka"  %% "akka-camel"                       % akkaVersion,
    "org.apache.activemq" % "activemq-camel"                   % activemqVersion,
    "com.typesafe.akka"  %% "akka-stream-experimental"         % streamVersion,
    "io.scalac"          %% "reactive-rabbit"                  % "1.0.0",
    "com.typesafe.akka"  %% "akka-testkit"                     % akkaVersion   % "test",
    "com.typesafe.akka"  %% "akka-stream-testkit-experimental" % streamVersion % "test",
    "org.scalatest"      %% "scalatest" % "2.2.4" % "test"
  )
}

Revolver.settings

Revolver.enableDebugging(port = 5050, suspend = false)

mainClass in Revolver.reStart := Some("com.github.dnvriend.ApplicationMain")