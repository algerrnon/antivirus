organization := "com.altuera.open_ru"
name := "gms_antivirus_service"
version := "0.0.4"
webappWebInfClasses := true

scalaVersion := "2.12.8"

mainClass in Compile := Some("com.altuera.gms_antivirus_service.UploadServlet")

scalacOptions ++= Seq(
  "-Ypartial-unification", // 2.11.9+
  "-feature")

scapegoatVersion in ThisBuild := "1.3.8"

libraryDependencies ++= Seq(

  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe" % "config" % "1.3.3",
  "io.spray" %% "spray-json" % "1.3.5",
  "commons-fileupload" % "commons-fileupload" % "1.3.3",
  "commons-io" % "commons-io" % "2.2",
  "com.softwaremill.sttp" %% "core" % "1.5.11",
  "com.softwaremill.retry" %% "retry" % "0.3.2"
)

//если хотите версию томката которая не совпадает с версией по умолчанию
//containerLibs in Tomcat := Seq("com.github.jsimone" % "webapp-runner" % "8.5.35" intransitive())

enablePlugins(JettyPlugin, TomcatPlugin)


fork in run := true
