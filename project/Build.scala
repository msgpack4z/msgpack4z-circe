import sbt._, Keys._
import com.typesafe.sbt.pgp.PgpKeys
import scalaprops.ScalapropsPlugin.autoImport._
import org.scalajs.sbtplugin.cross.CrossProject
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object build extends Build {

  private val msgpack4zCirceName = "msgpack4z-circe"
  val modules = msgpack4zCirceName :: Nil

  lazy val msgpack4zCirece = CrossProject("msgpack4z-circe", file("."), CustomCrossType).settings(
    Common.settings ++ scalapropsCoreSettings : _*
  ).settings(
    name := msgpack4zCirceName,
    libraryDependencies ++= (
      ("io.circe" %%% "circe-core" % "0.3.0") ::
      ("com.github.xuwei-k" %%% "msgpack4z-core" % "0.3.2") ::
      ("com.github.scalaprops" %%% "scalaprops" % "0.3.1" % "test") ::
      ("com.github.xuwei-k" %%% "msgpack4z-native" % "0.3.0" % "test") ::
      Nil
    )
  ).jsSettings(
    scalacOptions += {
      val a = (baseDirectory in LocalRootProject).value.toURI.toString
      val g = "https://raw.githubusercontent.com/msgpack4z/msgpack4z-circe/" + Common.tagOrHash.value
      s"-P:scalajs:mapSourceURI:$a->$g/"
    },
    scalaJSSemantics ~= { _.withStrictFloats(true) },
    scalaJSStage in Test := FastOptStage,
    scalaJSUseRhino in Global := false,
    jsEnv := NodeJSEnv().value
  ).jvmSettings(
    libraryDependencies ++= (
      ("com.github.xuwei-k" % "msgpack4z-java07" % "0.2.0" % "test") ::
      ("com.github.xuwei-k" % "msgpack4z-java06" % "0.2.0" % "test") ::
      Nil
    )
  ).jvmSettings(
    Sxr.subProjectSxr(Compile, "classes.sxr"): _*
  )

  lazy val msgpack4zCireceJS = msgpack4zCirece.js
  lazy val msgpack4zCireceJVM = msgpack4zCirece.jvm

  private val rootId = "root"

  lazy val root = Project(rootId, file(".")).settings(
    Common.settings
  ).settings(
    commands += Command.command("testSequential"){
      projects.map(_.id).filterNot(Set(rootId)).map(_ + "/test").sorted ::: _
    },
    PgpKeys.publishLocalSigned := {},
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publish := {},
    publishArtifact in Compile := false,
    scalaSource in Compile := file("dummy"),
    scalaSource in Test := file("dummy")
  ).aggregate(
    msgpack4zCireceJS, msgpack4zCireceJVM
  )
}
