import play.PlayImport.PlayKeys
import sbt.Keys._
import sbt.Tests.{SubProcess, Group}
import sbt._
import sbtassembly.{PathList, MergeStrategy, AssemblyKeys}
import sbtassembly.AssemblyPlugin.defaultShellScript
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning


import AssemblyKeys._

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt}

  import TestPhases._

import sbtassembly.AssemblyPlugin.defaultShellScript

  val appName: String
//  val appVersion: String

  lazy val appDependencies : Seq[ModuleID] = ???
  lazy val plugins : Seq[Plugins] = Seq(play.PlayScala)
  lazy val playSettings : Seq[Setting[_]] = Seq.empty

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(plugins : _*)
    .settings(playSettings : _*)
//    .settings(version := appVersion)
    .settings(scalaSettings: _*)
    .settings(scalaVersion := "2.11.7")
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.8",
//      shellPrompt := ShellPrompt(appVersion),
      libraryDependencies ++= appDependencies,
      parallelExecution in Test := false,
      fork in Test := false,
      retrieveManaged := true
    )
    .settings(Repositories.playPublishingSettings : _*)
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest)(base => Seq(base / "it")),
      addTestReportOption(IntegrationTest, "int-test-reports"),
      testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)
    .settings(SbtBuildInfo(): _*)
    .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
    .settings(resolvers += Resolver.bintrayRepo("hmrc", "releases"))
    .settings(mainClass in assembly := Some("play.core.server.NettyServer"))
    .settings(assemblyJarName in assembly := s"${name.value}-${version.value}.tgz")
    .settings(fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value))
    .settings(assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case PathList("com", "codahale", "metrics",  xs @ _*)         => MergeStrategy.first
      case PathList("org", "apache", "commons", "logging",  xs @ _*)         => MergeStrategy.first
      case PathList("play", "core", "server",   xs @ _*)         => MergeStrategy.first
      case PathList("org", "slf4j", "impl",   xs @ _*)         => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith "BuildInfo$.class" => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith "BuildInfo.class" => MergeStrategy.first
      case "application.conf"                            => MergeStrategy.concat
      case "unwanted.txt"                                => MergeStrategy.discard
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

  lazy val playPublishingSettings : Seq[sbt.Setting[_]] = sbtrelease.ReleasePlugin.releaseSettings ++ Seq(

    credentials += SbtCredentials,

    publishArtifact in(Compile, packageDoc) := false,
    publishArtifact in(Compile, packageSrc) := false
  ) ++
    publishAllArtefacts 
}
