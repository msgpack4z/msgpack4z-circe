import sbt._, Keys._
import com.typesafe.sbt.pgp.PgpKeys
import scalaprops.ScalapropsPlugin.autoImport._
import org.scalajs.sbtplugin.cross.CrossProject
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object build {

  private val msgpack4zCirceName = "msgpack4z-circe"
  val modules = msgpack4zCirceName :: Nil

  lazy val msgpack4zCirce = CrossProject("msgpack4z-circe", file("."), CustomCrossType).settings(
    Common.settings ++ scalapropsCoreSettings : _*
  ).settings(
    name := msgpack4zCirceName,
    libraryDependencies ++= (
      ("io.circe" %%% "circe-core" % "0.7.0") ::
      ("com.github.xuwei-k" %%% "msgpack4z-core" % "0.3.5") ::
      ("com.github.scalaprops" %%% "scalaprops" % "0.4.0" % "test") ::
      ("com.github.xuwei-k" %%% "msgpack4z-native" % "0.3.1" % "test") ::
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
    jsEnv := NodeJSEnv().value
  ).jvmSettings(
    libraryDependencies ++= (
      ("com.github.xuwei-k" % "msgpack4z-java" % "0.3.4" % "test") ::
      ("com.github.xuwei-k" % "msgpack4z-java06" % "0.2.0" % "test") ::
      Nil
    )
  ).jvmSettings(
    Sxr.settings
  )

}
