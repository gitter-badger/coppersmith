package au.com.cba.omnia.dataproducts.features.examples

import au.com.cba.omnia.dataproducts.features.Feature._
import au.com.cba.omnia.dataproducts.features._
import au.com.cba.omnia.dataproducts.features.example.thrift._
import au.com.cba.omnia.dataproducts.features.scalding._

import au.com.cba.omnia.etl.util.{ParseUtils, SimpleMaestroJob}

import au.com.cba.omnia.maestro.scalding.JobStatus
import au.com.cba.omnia.maestro.api._, Maestro._

import com.twitter.scalding.{TypedPsv, Config, MultipleTextLineFiles, Execution}

import org.joda.time.DateTime


import scalaz.{Value => _, _}, Scalaz._

object Example2 {

  val customerJoinAccount = Join[Customer].to[Account].on(_.acct, _.id)

  val feature = Patterns.general[(Customer, Account), Value.Decimal, Value.Decimal]("ns", "name", Type.Continuous, {case (c, a) => c._1}, {case (c,a) => Some(a.balance)}, {case (c,a) => 0})

  case class ExampleConfig(config:Config) {
    val args          = config.getArgs
    val hdfsInputPath    = args("input-dir")
    val queryDate        = args.optional("query-date").cata(new DateTime(_), DateTime.now().minusMonths(1))
    val yearMonth        = queryDate.toString("yyyyMM")
    val env              = args("hdfs-root")
    val hivePath         = s"${env}/view/warehouse/features/customers"
    val year             = queryDate.toString("yyyy")
    val month            = queryDate.toString("MM")
  }

  def featureJob: Execution[JobStatus] = {
    for {
      conf          <- Execution.getConfig.map(ExampleConfig)
      customers     <- Execution.from(ParseUtils.decodeHiveTextTable[Customer](MultipleTextLineFiles(s"${conf.hdfsInputPath}/cust/efft_yr_month=${conf.yearMonth}")))
      accounts      <- Execution.from(ParseUtils.decodeHiveTextTable[Account](MultipleTextLineFiles(s"${conf.hdfsInputPath}/acct/efft_yr_month=${conf.yearMonth}")))
      _             <- materialiseJoinFeature(customerJoinAccount, feature)(customers.rows, accounts.rows, TypedPsv(s"${conf.hivePath}/year=${conf.year}/month=${conf.month}"))
    } yield (JobFinished)
  }
}