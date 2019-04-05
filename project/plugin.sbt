addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.github.scalaprops" % "sbt-scalaprops" % "0.3.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.27")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.0")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Yno-adapted-args",
)

fullResolvers ~= { _.filterNot(_.name == "jcenter") }
