import sbtrelease.ReleaseStateTransformations._

Global / onChangedBuildSource := ReloadOnSourceChanges

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
}
val tagOrHash = Def.setting {
  if (isSnapshot.value) gitHash() else tagName.value
}

def gitHash(): String =
  sys.process.Process("git rev-parse HEAD").lineStream_!.head

val unusedWarnings = Seq(
  "-Ywarn-unused:imports",
)

val scalaVersions = Seq("2.12.21", "2.13.18", "3.3.7")

val commonSettings = Def.settings(
  ReleasePlugin.extraReleaseCommands,
  publishTo := (if (isSnapshot.value) None else localStaging.value),
  commands += Command.command("updateReadme")(UpdateReadme.updateReadmeTask),
  releaseTagName := tagName.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    UpdateReadme.updateReadmeProcess,
    tagRelease,
    releaseStepCommandAndRemaining("publishSigned"),
    releaseStepCommandAndRemaining("sonaRelease"),
    setNextVersion,
    commitNextVersion,
    UpdateReadme.updateReadmeProcess,
    pushChanges
  ),
  organization := "com.github.xuwei-k",
  homepage := Some(url("https://github.com/msgpack4z")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Xlint",
    "-language:existentials",
    "-language:implicitConversions",
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11 | 12)) =>
        Seq("-Xfuture")
      case _ =>
        Nil
    }
  },
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) =>
        Seq("-Xsource:3-cross")
      case Some((2, _)) =>
        Seq("-Xsource:3")
      case _ =>
        Nil
    }
  },
  scalacOptions ++= unusedWarnings,
  (Compile / doc / scalacOptions) ++= {
    val tag = tagOrHash.value
    Seq(
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/msgpack4z/msgpack4z-circe/tree/${tag}€{FILE_PATH}.scala"
    )
  },
  pomExtra :=
    <developers>
      <developer>
        <id>xuwei-k</id>
        <name>Kenji Yoshida</name>
        <url>https://github.com/xuwei-k</url>
      </developer>
    </developers>
    <scm>
      <url>git@github.com:msgpack4z/msgpack4z-circe.git</url>
      <connection>scm:git:git@github.com:msgpack4z/msgpack4z-circe.git</connection>
      <tag>{tagOrHash.value}</tag>
    </scm>,
  description := "msgpack4z circe binding",
  pomPostProcess := { node =>
    import scala.xml._
    import scala.xml.transform._
    def stripIf(f: Node => Boolean) =
      new RewriteRule {
        override def transform(n: Node) =
          if (f(n)) NodeSeq.Empty else n
      }
    val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
    new RuleTransformer(stripTestScope).transform(node)(0)
  },
  Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings)
)

lazy val msgpack4zCirce = projectMatrix
  .defaultAxes()
  .in(file("."))
  .withId("msgpack4z-circe")
  .settings(
    commonSettings,
    scalapropsCoreSettings,
    name := build.msgpack4zCirceName,
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.15",
      "com.github.xuwei-k" %%% "msgpack4z-core" % "0.6.2",
      "com.github.scalaprops" %%% "scalaprops" % "0.10.1" % "test",
      "com.github.xuwei-k" %%% "msgpack4z-native" % "0.4.0" % "test",
    )
  )
  .jsPlatform(
    scalaVersions,
    Def.settings(
      scalacOptions += {
        val a = (LocalRootProject / baseDirectory).value.toURI.toString
        val g = "https://raw.githubusercontent.com/msgpack4z/msgpack4z-circe/" + tagOrHash.value
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, _)) =>
            s"-P:scalajs:mapSourceURI:$a->$g/"
          case _ =>
            s"-scalajs-mapSourceURI:$a->$g/"
        }
      },
      Test / scalaJSStage := FastOptStage
    )
  )
  .jvmPlatform(
    scalaVersions,
    Def.settings(
      libraryDependencies ++= Seq(
        "com.github.xuwei-k" % "msgpack4z-java" % "0.4.0" % "test",
        "com.github.xuwei-k" % "msgpack4z-java06" % "0.2.0" % "test",
      ),
    )
  )
  .nativePlatform(
    scalaVersions,
    Def.settings(
      scalapropsNativeSettings,
    )
  )

commonSettings
autoScalaLibrary := false
PgpKeys.publishLocalSigned := {}
PgpKeys.publishSigned := {}
publishLocal := {}
publish := {}
Compile / publishArtifact := false
Compile / scalaSource := (LocalRootProject / baseDirectory).value / "dummy"
Test / scalaSource := (LocalRootProject / baseDirectory).value / "dummy"
TaskKey[Unit]("testSequential") := Def
  .sequential(
    msgpack4zCirce
      .allProjects()
      .map(_._1)
      .sortBy(_.id)
      .flatMap(p =>
        Seq(
          Def.task(streams.value.log.info(s"start ${p.id} test")),
          p / Test / test
        )
      )
  )
  .value
