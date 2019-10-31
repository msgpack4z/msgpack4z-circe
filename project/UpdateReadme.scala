import sbt._, Keys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import sbtrelease.Git

object UpdateReadme {
  private val sonatypeURL = "https://oss.sonatype.org/service/local/repositories/"

  val updateReadmeTask = { state: State =>
    val extracted = Project.extract(state)
    val scalaV = "2.12"
    val v = extracted get version
    val org = extracted get organization
    val modules = build.modules
    val snapshotOrRelease = if (extracted get isSnapshot) "snapshots" else "releases"
    val readme = "README.md"
    val readmeFile = file(readme)
    val newReadme = Predef
      .augmentString(IO.read(readmeFile))
      .lines
      .map { line =>
        val matchReleaseOrSnapshot = line.contains("SNAPSHOT") == v.contains("SNAPSHOT")
        def n = modules(modules.indexWhere(line.contains))
        if (line.startsWith("libraryDependencies") && matchReleaseOrSnapshot) {
          s"""libraryDependencies += "${org}" %% "$n" % "$v""""
        } else if (line.contains(sonatypeURL) && matchReleaseOrSnapshot) {
          val sxrIndexHtml = "-sxr.jar/!/index.html"
          val baseURL = s"${sonatypeURL}${snapshotOrRelease}/archive/${org.replace('.', '/')}/${n}_${scalaV}/${v}/${n}_${scalaV}-${v}"
          if (line.contains(sxrIndexHtml)) {
            s"- [sxr](${baseURL}${sxrIndexHtml})"
          } else line
        } else line
      }
      .mkString("", "\n", "\n")
    IO.write(readmeFile, newReadme)
    val git = new Git(extracted get baseDirectory)
    git.add(readme) ! state.log
    git.commit(message = "update " + readme, sign = false, signOff = false) ! state.log
    sys.process.Process("git diff HEAD^") ! state.log
    state
  }

  val updateReadmeProcess: ReleaseStep = updateReadmeTask
}
