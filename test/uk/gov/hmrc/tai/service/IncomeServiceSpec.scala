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

package uk.gov.hmrc.tai.service

import controllers.FakeTaiPlayApplication
import org.joda.time.LocalDate
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.responses.{TaiSuccessResponseWithPayload, TaiTaxAccountFailureResponse}
import uk.gov.hmrc.tai.model.EmploymentAmount
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.{Live, OtherBasisOperation, TaxCodeIncome, Week1Month1BasisOperation}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.JourneyCacheConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random


class IncomeServiceSpec extends PlaySpec with MockitoSugar with FakeTaiPlayApplication with JourneyCacheConstants {

  "employmentAmount" must {
    "return employment amount" when {
      "valid inputs are passed" in {
        val sut = createSUT
        val payment = paymentOnDate(LocalDate.now().minusWeeks(5)).copy(payFrequency = Irregular)
        val annualAccount = AnnualAccount("", TaxYear(), Available, List(payment), Nil)
        val employment = employmentWithAccounts(List(annualAccount))
        when(sut.taxAccountService.taxCodeIncomes(any(), any())(any())).
          thenReturn(Future.successful(TaiSuccessResponseWithPayload[Seq[TaxCodeIncome]](taxCodeIncomes)))
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(Some(employment)))

        val result = Await.result(sut.employmentAmount(nino, 1), 5.seconds)

        result mustBe EmploymentAmount(
          "employment",
          "(Current employer)",
          1,
          1111,
          1111,
          None,
          None,
          Some(new LocalDate(2000, 5, 20)),
          None,
          true,
          false)

      }
    }

    "return an error" when {
      "employment details not found" in {
        val sut = createSUT
        val payment = paymentOnDate(LocalDate.now().minusWeeks(5)).copy(payFrequency = Irregular)
        val annualAccount = AnnualAccount("", TaxYear(), Available, List(payment), Nil)
        val employment = employmentWithAccounts(List(annualAccount))
        when(sut.taxAccountService.taxCodeIncomes(any(), any())(any())).
          thenReturn(Future.successful(TaiSuccessResponseWithPayload[Seq[TaxCodeIncome]](Seq.empty[TaxCodeIncome])))
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(Some(employment)))

        val ex = the[RuntimeException] thrownBy Await.result(sut.employmentAmount(nino, 1), 5.seconds)
        ex.getMessage mustBe "Not able to found employment with id 1"
      }

      "income not found" in {
        val sut = createSUT
        val payment = paymentOnDate(LocalDate.now().minusWeeks(5)).copy(payFrequency = Irregular)
        val annualAccount = AnnualAccount("", TaxYear(), Available, List(payment), Nil)
        val employment = employmentWithAccounts(List(annualAccount))
        when(sut.taxAccountService.taxCodeIncomes(any(), any())(any())).
          thenReturn(Future.successful(TaiTaxAccountFailureResponse("Failed")))
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(Some(employment)))

        val ex = the[RuntimeException] thrownBy Await.result(sut.employmentAmount(nino, 1), 5.seconds)
        ex.getMessage mustBe "Exception while reading employment and tax code details"
      }


      "employment not found" in {
        val sut = createSUT
        when(sut.taxAccountService.taxCodeIncomes(any(), any())(any())).
          thenReturn(Future.successful(TaiSuccessResponseWithPayload[Seq[TaxCodeIncome]](Seq.empty[TaxCodeIncome])))
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(None))

        val ex = the[RuntimeException] thrownBy Await.result(sut.employmentAmount(nino, 1), 5.seconds)
        ex.getMessage mustBe "Exception while reading employment and tax code details"
      }
    }
  }

  "latestPayment" must {
    "return latest payment" when {
      "valid inputs are passed" in {
        val sut = createSUT
        val payment = paymentOnDate(LocalDate.now().minusWeeks(5)).copy(payFrequency = Irregular)
        val annualAccount = AnnualAccount("", TaxYear(), Available, List(payment), Nil)
        val employment = employmentWithAccounts(List(annualAccount))
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(Some(employment)))

       Await.result(sut.latestPayment(nino, 1), 5.seconds) mustBe Some(payment)
      }
    }

    "return none" when {
      "employment details are not found" in {
        val sut = createSUT
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(None))

        Await.result(sut.latestPayment(nino, 1), 5.seconds) mustBe None
      }

      "payments details are not present" in {
        val sut = createSUT
        val annualAccount = AnnualAccount("", TaxYear(), Available, Seq.empty[Payment], Nil)
        val employment = employmentWithAccounts(List(annualAccount))
        when(sut.employmentService.employment(any(), any())(any())).
          thenReturn(Future.successful(Some(employment)))

        Await.result(sut.latestPayment(nino, 1), 5.seconds) mustBe None
      }
    }
  }

  "cachePaymentForRegularIncome" must {
    "return cached map data" when {
      "payment is none" in {
        val sut = createSUT
        val expectedCached = Map(UpdateIncome_PayToDateKey -> "0")
        sut.cachePaymentForRegularIncome(None) mustBe expectedCached
      }

      "payment has value" in {
        val sut = createSUT
        val payment = paymentOnDate(new LocalDate(2017, 9, 6))
        val expectedCached = Map(UpdateIncome_PayToDateKey -> "2000", UpdateIncome_DateKey -> payment.date.toString)
        sut.cachePaymentForRegularIncome(Some(payment)) mustBe expectedCached
      }
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  val nino: Nino = new Generator(new Random).nextNino
  val taxCodeIncomes = Seq(
    TaxCodeIncome(EmploymentIncome, Some(1), 1111, "employment1", "1150L", "employment", OtherBasisOperation, Live),
    TaxCodeIncome(PensionIncome, Some(2), 1111, "employment2", "150L", "employment", Week1Month1BasisOperation, Live)
  )

  def employmentWithAccounts(accounts: List[AnnualAccount]) = Employment("ABCD", Some("ABC123"), new LocalDate(2000, 5, 20),
    None, accounts, "", "", 8)

  def paymentOnDate(date: LocalDate) = Payment(
    date = date,
    amountYearToDate = 2000,
    taxAmountYearToDate = 200,
    nationalInsuranceAmountYearToDate = 100,
    amount = 1000,
    taxAmount = 100,
    nationalInsuranceAmount = 50,
    payFrequency = Monthly)

  def createSUT = new SUT

  class SUT extends IncomeService {
    override val taxAccountService: TaxAccountService = mock[TaxAccountService]
    override val employmentService: EmploymentService = mock[EmploymentService]
  }

}