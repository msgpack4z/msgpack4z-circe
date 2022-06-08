import sbtrelease.ReleaseStateTransformations._
import sbtcrossproject.CrossProject

Global / onChangedBuildSource := ReloadOnSourceChanges

val CustomCrossType = new sbtcrossproject.CrossType {
  override def projectDir(crossBase: File, projectType: String) =
    crossBase / projectType

  override def projectDir(crossBase: File, projectType: sbtcrossproject.Platform) = {
    val dir = projectType match {
      case JVMPlatform => "jvm"
      case JSPlatform => "js"
    }
    crossBase / dir
  }

  def shared(projectBase: File, conf: String) =
    projectBase.getParentFile / "src" / conf / "scala"

  override def sharedSrcDir(projectBase: File, conf: String) =
    Some(shared(projectBase, conf))
}

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

val scala212 = "2.12.16"

val commonSettings = Def.settings(
  ReleasePlugin.extraReleaseCommands,
  publishTo := sonatypePublishToBundle.value,
  fullResolvers ~= { _.filterNot(_.name == "jcenter") },
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
    ReleaseStep(
      action = { state =>
        val extracted = Project extract state
        extracted.runAggregated(extracted.get(thisProjectRef) / (Global / PgpKeys.publishSigned), state)
      },
      enableCrossBuild = true
    ),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    UpdateReadme.updateReadmeProcess,
    pushChanges
  ),
  credentials ++= PartialFunction
    .condOpt(sys.env.get("SONATYPE_USER") -> sys.env.get("SONATYPE_PASS")) { case (Some(user), Some(pass)) =>
      Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    }
    .toList,
  organization := "com.github.xuwei-k",
  homepage := Some(url("https://github.com/msgpack4z")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-unchecked",
    "-Xlint",
    "-language:existentials",
    "-language:higherKinds",
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
  scalacOptions ++= unusedWarnings,
  scalaVersion := scala212,
  crossScalaVersions := scala212 :: "2.13.8" :: "3.1.2" :: Nil,
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

lazy val msgpack4zCirce = CrossProject("msgpack4z-circe", file("."))(JVMPlatform, JSPlatform)
  .crossType(CustomCrossType)
  .settings(
    commonSettings,
    scalapropsCoreSettings,
    name := build.msgpack4zCirceName,
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.2",
      "com.github.xuwei-k" %%% "msgpack4z-core" % "0.6.0",
      "com.github.scalaprops" %%% "scalaprops" % "0.9.0" % "test",
      "com.github.xuwei-k" %%% "msgpack4z-native" % "0.3.8" % "test",
    )
  )
  .jsSettings(
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
    scalaJSLinkerConfig ~= { _.withSemantics(_.withStrictFloats(true)) },
    Test / scalaJSStage := FastOptStage
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.xuwei-k" % "msgpack4z-java" % "0.4.0" % "test",
      "com.github.xuwei-k" % "msgpack4z-java06" % "0.2.0" % "test",
    ),
  )

val msgpack4zCirceJS = msgpack4zCirce.js
val msgpack4zCirceJVM = msgpack4zCirce.jvm

val root = Project("root", file("."))
  .settings(
    commonSettings,
    commands += Command.command("testSequential") {
      List(msgpack4zCirceJVM, msgpack4zCirceJS).map(_.id + "/test") ::: _
    },
    PgpKeys.publishLocalSigned := {},
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publish := {},
    Compile / publishArtifact := false,
    Compile / scalaSource := (LocalRootProject / baseDirectory).value / "dummy",
    Test / scalaSource := (LocalRootProject / baseDirectory).value / "dummy"
  )
  .aggregate(
    msgpack4zCirceJS,
    msgpack4zCirceJVM
  )
