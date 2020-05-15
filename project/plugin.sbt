addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.2")
addSbtPlugin("com.github.scalaprops" % "sbt-scalaprops" % "0.3.2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.1.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Yno-adapted-args",
)

fullResolvers ~= { _.filterNot(_.name == "jcenter") }
