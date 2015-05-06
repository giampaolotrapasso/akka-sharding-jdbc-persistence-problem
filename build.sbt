import akka.sbt.AkkaKernelPlugin

name := """akka-sample-persistence-scala"""

version := "2.3.10"

scalaVersion := "2.10.4"

resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies ++= {
  val akkaVersion = "2.3.10"
  Seq(
    "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-kernel" % akkaVersion,
    "mysql" % "mysql-connector-java" % "5.1.33",
    "org.slf4j" % "slf4j-simple" % "1.6.4",
    "com.typesafe.akka" %% "akka-contrib" % "2.3.10",
    "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.1.0"
  )
}

fork in run := true

AkkaKernelPlugin.distSettings

distMainClass in Dist := "akka.kernel.Main sample.persistence.ExampleKernel"