import build._

val msgpack4zCirceJS = msgpack4zCirce.js
val msgpack4zCirceJVM = msgpack4zCirce.jvm

val root = Project("root", file(".")).settings(
  Common.settings
).settings(
  commands += Command.command("testSequential"){
    List(msgpack4zCirceJVM, msgpack4zCirceJS).map(_.id + "/test") ::: _
  },
  PgpKeys.publishLocalSigned := {},
  PgpKeys.publishSigned := {},
  publishLocal := {},
  publish := {},
  publishArtifact in Compile := false,
  scalaSource in Compile := file("dummy"),
  scalaSource in Test := file("dummy")
).aggregate(
  msgpack4zCirceJS, msgpack4zCirceJVM
)
