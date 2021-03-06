//
// Copyright 2016 Commonwealth Bank of Australia
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//        http://www.apache.org/licenses/LICENSE-2.0
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//

package commbank.coppersmith.examples

import commbank.coppersmith.Feature._
import commbank.coppersmith.{lift => _, _}
import commbank.coppersmith.examples.thrift.Customer
import au.com.cba.omnia.maestro.core.codec.{DecodeError, DecodeOk, DecodeResult}


import au.com.cba.omnia.maestro.scalding.JobStatus
import au.com.cba.omnia.maestro.api._, Maestro._

import com.twitter.scalding._

import org.joda.time.DateTime


import scalaz.{Value => _, _}, Scalaz._

import PivotMacro._
import scalding.lift.scalding._

object Example1 {
  val pivoted = pivotThrift[Customer]("namespace", _.id)
  val pivotedAsFeatureSet:PivotFeatureSet[Customer] = pivoted
  val acct: Feature[Customer, Value.Str] = pivoted.acct
  val cat: Feature[Customer, Value.Str] = pivoted.cat
  val balance: Feature[Customer, Value.Integral] = pivoted.balance

  case class ExampleConfig(config:Config) {
    val args          = config.getArgs
    val hdfsInputPath = args("input-dir")
    val queryDate     = args.optional("query-date").cata(new DateTime(_), DateTime.now().minusMonths(1))
    val yearMonth     = queryDate.toString("yyyyMM")
    val env           = args("hdfs-root")
    val hivePath      = s"${env}/view/warehouse/features/customers"
    val year          = queryDate.toString("yyyy")
    val month         = queryDate.toString("MM")
  }



  def accountFeatureJob: Execution[JobStatus] = {
    for {
      conf                    <- Execution.getConfig.map(ExampleConfig)
      (inputPipe, _)          <- Execution.from(Util.decodeHive[Customer](
                                  MultipleTextLineFiles(s"${conf.hdfsInputPath}/efft_yr_month=${conf.yearMonth}")))
      outputPipe              = lift(acct)(inputPipe)
      _                       <- outputPipe.writeExecution(TypedPsv(s"${conf.hivePath}/year=${conf.year}/month=${conf.month}"))
    } yield (JobFinished)
  }

  def allFeaturesJob: Execution[JobStatus] = {
    for {
      conf                    <- Execution.getConfig.map(ExampleConfig)
      (inputPipe, _)          <- Execution.from(Util.decodeHive[Customer](
                                MultipleTextLineFiles(s"${conf.hdfsInputPath}/efft_yr_month=${conf.yearMonth}")))
      outputPipe             = lift(pivotedAsFeatureSet)(inputPipe)
      _                       <- outputPipe.writeExecution(TypedPsv(s"${conf.hivePath}/year=${conf.year}/month=${conf.month}"))
    } yield (JobFinished)
  }
}
