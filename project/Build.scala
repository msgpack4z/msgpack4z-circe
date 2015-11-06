import sbt._, Keys._
import scalaprops.ScalapropsPlugin.autoImport._

object build extends Build {

  private val msgpack4zCirceName = "msgpack4z-circe"
  val modules = msgpack4zCirceName :: Nil

  lazy val msgpack4z = Project("msgpack4z-circe", file(".")).settings(
    Common.settings ++ scalapropsSettings
  ).settings(
    name := msgpack4zCirceName,
    scalapropsVersion := "0.1.16",
    libraryDependencies ++= (
      ("io.circe" %% "circe-core" % "0.2.0") ::
      ("com.github.xuwei-k" %% "msgpack4z-core" % "0.1.4") ::
      ("com.github.xuwei-k" % "msgpack4z-java07" % "0.1.5" % "test") ::
      ("com.github.xuwei-k" % "msgpack4z-java06" % "0.1.1" % "test") ::
      ("com.github.xuwei-k" %% "msgpack4z-native" % "0.1.1" % "test") ::
      Nil
    )
  ).settings(
    Sxr.subProjectSxr(Compile, "classes.sxr"): _*
  )

}
