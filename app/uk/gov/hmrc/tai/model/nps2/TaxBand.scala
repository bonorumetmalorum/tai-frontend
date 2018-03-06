/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hmrc.nps2

case class TaxBand (
  /**
    * Part of the tax code. Rarely used by itself.
    */
  bandType: Option[String],
  code: Option[String],
  income: BigDecimal,
  tax: BigDecimal,
  lowerBand: Option[BigDecimal],
  upperBand: Option[BigDecimal],
  rate: BigDecimal
)