import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.pgp.PgpKeys
import sbtcrossproject.CrossProject

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
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}
val tagOrHash = Def.setting {
  if (isSnapshot.value) gitHash() else tagName.value
}

def gitHash(): String =
  sys.process.Process("git rev-parse HEAD").lineStream_!.head

val unusedWarnings = Seq(
  "-Ywarn-unused:imports",
)

val scala211 = "2.11.12"

val commonSettings = Def.settings(
  ReleasePlugin.extraReleaseCommands,
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
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
        extracted.runAggregated(PgpKeys.publishSigned in Global in extracted.get(thisProjectRef), state)
      },
      enableCrossBuild = true
    ),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    UpdateReadme.updateReadmeProcess,
    pushChanges
  ),
  credentials ++= PartialFunction
    .condOpt(sys.env.get("SONATYPE_USER") -> sys.env.get("SONATYPE_PASS")) {
      case (Some(user), Some(pass)) =>
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
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) =>
        Nil
      case _ =>
        unusedWarnings
    }
  },
  scalaVersion := scala211,
  crossScalaVersions := "2.12.8" :: scala211 :: "2.13.0" :: Nil,
  scalacOptions in (Compile, doc) ++= {
    val tag = tagOrHash.value
    Seq(
      "-sourcepath",
      (baseDirectory in LocalRootProject).value.getAbsolutePath,
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
    def stripIf(f: Node => Boolean) = new RewriteRule {
      override def transform(n: Node) =
        if (f(n)) NodeSeq.Empty else n
    }
    val stripTestScope = stripIf { n =>
      n.label == "dependency" && (n \ "scope").text == "test"
    }
    new RuleTransformer(stripTestScope).transform(node)(0)
  },
  Seq(Compile, Test).flatMap(c => scalacOptions in (c, console) --= unusedWarnings)
)

lazy val circeVersion = settingKey[String]("")

lazy val msgpack4zCirce = CrossProject("msgpack4z-circe", file("."))(JVMPlatform, JSPlatform)
  .crossType(CustomCrossType)
  .settings(
    commonSettings,
    scalapropsCoreSettings,
    name := build.msgpack4zCirceName,
    circeVersion := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v <= 11 =>
          "0.12.0-M3"
        case _ =>
          "0.12.1" // circe 0.12 dropped Scala 2.11 https://github.com/circe/circe/pull/1176
      }
    },
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % circeVersion.value,
      "com.github.xuwei-k" %%% "msgpack4z-core" % "0.3.11",
      "com.github.scalaprops" %%% "scalaprops" % "0.6.1" % "test",
      "com.github.xuwei-k" %%% "msgpack4z-native" % "0.3.5" % "test",
    )
  )
  .jsSettings(
    scalacOptions += {
      val a = (baseDirectory in LocalRootProject).value.toURI.toString
      val g = "https://raw.githubusercontent.com/msgpack4z/msgpack4z-circe/" + tagOrHash.value
      s"-P:scalajs:mapSourceURI:$a->$g/"
    },
    scalaJSSemantics ~= { _.withStrictFloats(true) },
    scalaJSStage in Test := FastOptStage
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.xuwei-k" % "msgpack4z-java" % "0.3.6" % "test",
      "com.github.xuwei-k" % "msgpack4z-java06" % "0.2.0" % "test",
    ),
    Sxr.settings
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
    publishArtifact in Compile := false,
    scalaSource in Compile := file("dummy"),
    scalaSource in Test := file("dummy")
  )
  .aggregate(
    msgpack4zCirceJS,
    msgpack4zCirceJVM
  )
