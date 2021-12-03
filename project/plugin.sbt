addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10")
addSbtPlugin("com.github.scalaprops" % "sbt-scalaprops" % "0.4.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.7.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.5")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Yno-adapted-args",
)

fullResolvers ~= { _.filterNot(_.name == "jcenter") }
