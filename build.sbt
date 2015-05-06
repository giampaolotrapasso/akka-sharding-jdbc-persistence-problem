name := """akka-sample-persistence-scala"""

version := "2.3.10"

scalaVersion := "2.10.4"

resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.10",
  "mysql" % "mysql-connector-java" % "5.1.25",
  "org.slf4j" % "slf4j-simple" % "1.6.4",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.10",
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.1.0",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.0.0"
)



fork in run := true