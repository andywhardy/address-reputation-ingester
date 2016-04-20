/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import play.PlayImport.PlayKeys
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.{AssemblyKeys, MergeStrategy, PathList}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import TestPhases._
  import uk.gov.hmrc.SbtBuildInfo

  val appName: String

  lazy val appDependencies: Seq[ModuleID] = ???
  lazy val plugins: Seq[Plugins] = Seq(play.PlayScala)
  lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(plugins: _*)
    .settings(playSettings: _*)
    .settings(scalaSettings: _*)
    .settings(scalaVersion := "2.11.8")
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.8",
      libraryDependencies ++= appDependencies,
      parallelExecution in Test := false,
      fork in Test := false,
      retrieveManaged := true
    )
    .settings(Provenance.setting)
    .settings(Repositories.playPublishingSettings: _*)

    .configs(Test)
    .settings(
      unmanagedSourceDirectories in Test <<= (baseDirectory in Test) (base => Seq(base / "test" / "unit")),
      addTestReportOption(Test, "test-reports"))

    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in Test) (base => Seq(base / "test" / "it")),
      unmanagedResourceDirectories in IntegrationTest <<= (baseDirectory in Test) (base => Seq(base / "test" / "resources")),
      addTestReportOption(IntegrationTest, "int-test-reports"),
      testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)

    .settings(SbtBuildInfo(): _*)
    .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)

    .settings(resolvers += Resolver.bintrayRepo("hmrc", "releases"))
    .settings(resolvers += Resolver.bintrayRepo("hmrc", "release-candidates"))
    .settings(mainClass in assembly := Some("play.core.server.NettyServer"))
    .settings(assemblyJarName in assembly := s"${name.value}-${version.value}.tgz")
    .settings(fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value))
    .settings(assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs@_*) => MergeStrategy.first
      case PathList("com", "codahale", "metrics", xs@_*) => MergeStrategy.first
      case PathList("org", "apache", "commons", "logging", xs@_*) => MergeStrategy.first
      case PathList("play", "core", "server", xs@_*) => MergeStrategy.first
      case PathList("org", "slf4j", "impl", xs@_*) => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith "BuildInfo$.class" => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith "BuildInfo.class" => MergeStrategy.first
      case "application.conf" => MergeStrategy.concat
      case "unwanted.txt" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    })
    .enablePlugins(SbtDistributablesPlugin, SbtGitVersioning)
}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}

private object Repositories {

  import uk.gov.hmrc._
  import PublishingSettings._

  lazy val playPublishingSettings: Seq[sbt.Setting[_]] = sbtrelease.ReleasePlugin.releaseSettings ++ Seq(

    credentials += SbtCredentials,

    publishArtifact in(Compile, packageDoc) := false,
    publishArtifact in(Compile, packageSrc) := false
  ) ++
    publishAllArtefacts
}
