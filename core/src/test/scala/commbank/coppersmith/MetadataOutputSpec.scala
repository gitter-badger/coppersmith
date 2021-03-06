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

package commbank.coppersmith

import commbank.coppersmith.Feature.Type._
import commbank.coppersmith.Feature.Value.{Str, Decimal, Integral}
import commbank.coppersmith.Feature._
import commbank.coppersmith.test.thrift.Customer
import org.scalacheck.Prop._
import org.specs2.matcher.JsonMatchers
import org.specs2.{ScalaCheck, Specification}


import Arbitraries._

object MetadataOutputSpec extends Specification with ScalaCheck with JsonMatchers { def is = s2"""
  Psv creates expected metadata $psv
  Json creates expected metadata $json
"""

  def psv = forAll { (namespace: Namespace, name: Name, desc: Description, fType: Type, value: Value) => {
    val (metadata, expectedValueType) = value match {
      case Integral(_) => (Metadata[Customer, Integral](namespace, name, desc, fType), "int")
      case Decimal(_)  => (Metadata[Customer, Decimal] (namespace, name, desc, fType), "double")
      case Str(_)      => (Metadata[Customer, Str]     (namespace, name, desc, fType), "string")
    }

    val expectedFeatureType = fType match {
      case n : Numeric    => "continuous"
      case c: Categorical => "categorical"
    }

    val psvMetadata = MetadataOutput.Psv.fn(metadata, None)

    psvMetadata must_==
      s"${namespace.toLowerCase}.${name.toLowerCase}|$expectedValueType|$expectedFeatureType"
  }}

  def json = forAll { (namespace: Namespace, name: Name, desc: Description, fType: Type, value: Value) => {
    val (metadata, expectedValueType) = value match {
      case Integral(_) => (Metadata[Customer, Integral](namespace, name, desc, fType), "integral")
      case Decimal(_)  => (Metadata[Customer, Decimal] (namespace, name, desc, fType), "decimal")
      case Str(_)      => (Metadata[Customer, Str]     (namespace, name, desc, fType), "string")
    }

    val expectedFeatureType = fType.toString.toLowerCase
    val oConforms = (fType, value) match {
      case (Nominal,    Str(_))      => Some(NominalStr)
      case (Ordinal,    Decimal(_))  => Some(OrdinalDecimal)
      case (Continuous, Decimal(_))  => Some(ContinuousDecimal)
      case (Ordinal,    Integral(_)) => Some(OrdinalIntegral)
      case (Continuous, Integral(_)) => Some(ContinuousIntegral)
      case (Discrete,   Integral(_)) => Some(DiscreteIntegral)
      case _                         => None
    }
    val expectedTypesConform = oConforms.isDefined

    val jsonOutput = MetadataOutput.JsonObject.fn(metadata, oConforms)
    Seq(
      jsonOutput must /("name" -> metadata.name),
      jsonOutput must /("namespace" -> metadata.namespace),
      jsonOutput must /("description" -> metadata.description),
      jsonOutput must /("source" -> metadata.sourceType.toString),
      jsonOutput must /("featureType" -> expectedFeatureType),
      jsonOutput must /("valueType" -> expectedValueType),
      jsonOutput must /("typesConform" -> expectedTypesConform)
    )
  }}
}
