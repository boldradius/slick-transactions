import sbtrelease.ReleasePlugin.ReleaseKeys._

import sbtrelease.ReleasePlugin._

name := """slick-transactions"""

useGlobalVersion := false

organization := "com.boldradius"

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.4", "2.11.6")

publishMavenStyle := true

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalaz" %% "scalaz-effect" % "7.1.0",
  "com.typesafe.slick" %% "slick" % "3.0.0"
)

bintraySettings

releaseSettings

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("boldradiussolutions")

com.typesafe.sbt.SbtGit.versionWithGit
