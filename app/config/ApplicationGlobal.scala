
package config

import play.api._
import services.ingester.Task
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.microservice.bootstrap.JsonErrorHandling
import uk.gov.hmrc.play.microservice.bootstrap.Routing.RemovingOfTrailingSlashes

object ApplicationGlobal extends GlobalSettings with GraphiteConfig with RemovingOfTrailingSlashes with JsonErrorHandling with RunMode {

  override def onStart(app: Application) {
    val config = app.configuration
    val appName = config.getString("appName").getOrElse("APP NAME NOT SET")
    Logger.info(s"Starting microservice : $appName : in mode : ${app.mode}")
    Logger.info(s"address-reputation-ingestor config: ${config.underlying.toString}")
    // TODO log provenance
    super.onStart(app)
  }

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"$env.metrics")

  override def onStop(app: Application): Unit = {
    if (Task.currentlyExecuting.get()) Task.cancelTask.set(true)
    //TODO: need a configurable timeout to avoid spinning forever in case things go wrong
    while (Task.currentlyExecuting.get) {
      Logger.info("Waiting for task to finish")
      Thread.sleep(1000)
    }
  }
}
