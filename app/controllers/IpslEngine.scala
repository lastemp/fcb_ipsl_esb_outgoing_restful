package controllers

import java.io.{BufferedWriter, FileWriter, IOException, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.sql.{CallableStatement, ResultSet}
import java.text.SimpleDateFormat

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._

import play.api.libs.functional.syntax._
import play.api.db.{Database, NamedDatabase}
import javax.inject.Inject

import oracle.jdbc.OracleTypes
import java.util.{Base64, Date}

import scala.language.postfixOps

import play.api.mvc.{AbstractController, ControllerComponents}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject.AbstractModule
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.Await

import scala.concurrent.Future

import scala.concurrent.{ExecutionContext, Future}
import HttpMethods._
//import com.microsoft.sqlserver.jdbc.{SQLServerCallableStatement, SQLServerDataTable}
import com.microsoft.sqlserver.jdbc.SQLServerDataTable
import play.api.libs.concurrent.CustomExecutionContext
import scala.util.control.Breaks
import scala.util.control.Breaks.break
import scala.util.{Failure, Success}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
/*
trait MyExecutionContext2 extends ExecutionContext

class MyExecutionContextImpl2 @Inject()(system: ActorSystem)
  extends CustomExecutionContext(system, "my-dispatcher") with MyExecutionContext2

class MyExecutionContextModule2 extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MyExecutionContext2])
      .to(classOf[MyExecutionContextImpl2])

  }
}
*/
//, @NamedDatabase("cbsdb") myCbsDB : Database
//(myExecutionContext: MyExecutionContext2,cc: ControllerComponents, @NamedDatabase("ebusiness") myDB : Database)
class IpslEngine @Inject()
(implicit myExecutionContext: ExecutionContext, cc: ControllerComponents, @NamedDatabase("ebusiness") myDB : Database)
  extends AbstractController(cc) {

  //case class MpesaTransactionStatus_Request(mobileno: String, transactioncode: String, amount: Float)
  case class MpesaTransactionStatus_Request(mobileno: JsValue, transactioncode: JsValue, amount: JsValue)
  //case class CMSDeclarations_Request(CertNo: String, DateIssued: String, AgencyName: String, InsuredName: String, PolicyNo: String, InsurancePeriodFrom: String, InsurancePeriodTo: String, VehicleNo: String, SumInsured: Float, PremiumPayable: Float, PremiumPaid: Float, PaymentReference: String)
  //case class CMSDeclarations_Request(CertNo: String, DateIssued: String, AgencyCode: Int, AgencyName: String, InsuredName: String, PolicyNo: String, InsurancePeriodFrom: String, InsurancePeriodTo: String, VehicleNo: String, MobileNo: String, Active : Boolean, SumInsured: Float, PremiumPayable: Float, PremiumPaid: Float, PaymentReference: String)
  //Modified on 20-08-2018: Emmanuel
  //Changed AgencyCode: Int to "AgencyCode: JsValue"
  //case class CMSDeclarations_Request(CertNo: String, DateIssued: String, AgencyCode: JsValue, AgencyName: String, InsuredName: String, PolicyNo: String, InsurancePeriodFrom: String, InsurancePeriodTo: String, VehicleNo: String, MobileNo: String, Active : Boolean, SumInsured: Float, PremiumPayable: Float, PremiumPaid: Float, PaymentReference: String)
  case class CMSDeclarations_Request(CertNo: JsValue, DateIssued: JsValue, AgencyCode: JsValue, AgencyName: JsValue, InsuredName: JsValue, PolicyNo: JsValue, InsurancePeriodFrom: JsValue, InsurancePeriodTo: JsValue, VehicleNo: JsValue, MobileNo: JsValue, Active : JsValue, SumInsured: JsValue, PremiumPayable: JsValue, PremiumPaid: JsValue, PaymentReference: JsValue)
  case class MpesaTransactionStatus_BatchRequest(BatchNo: Option[Int], DeclarationsData: Seq[MpesaTransactionStatus_Request])
  //case class MpesaTransactionStatus_BatchRequest(DeclarationsData: Seq[MpesaTransactionStatus_Request])
  //case class CMSDeclarations_BatchRequest(BatchNo: Int, DeclarationsData: Seq[CMSDeclarations_Request])
  //case class CMSDeclarations_BatchRequest(BatchNo: String, DeclarationsData: Seq[CMSDeclarations_Request])
  case class CMSDeclarations_BatchRequest(BatchNo: JsValue, DeclarationsData: Seq[CMSDeclarations_Request])
  case class MpesaTransactionStatus_Response(statuscode: Int, statusdescription: String)
  case class MpesaTransactionStatus_Batch(transactioncode: String, statuscode: Int, statusdescription: String)
  case class MpesaTransactionStatus_BatchData(DeclarationsData: Seq[MpesaTransactionStatus_Batch])
  case class MpesaTransactionStatus_BatchResponse(myBatchData: MpesaTransactionStatus_BatchData)
  case class CMSDeclarations_Response(statuscode: Int, statusdescription: String, totalMpesa : Float, totalDeclarations : Float)

  //MemberDetails
  case class MemberDetails_Request(memberno: JsValue, rowcount: Option[Int])
  case class MemberDetails_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberDetails_Request])
  case class MemberDetailsResponse_Batch(memberno: Int, fullnames: String, idno: Int, phoneno: String, gender: String, membertype: String, statuscode: Int, statusdescription: String)
  case class MemberDetailsResponse_BatchData(memberdata: Seq[MemberDetailsResponse_Batch])

  //BeneficiaryDetails
  case class BeneficiaryDetails_Request(memberno: JsValue, rowcount: Option[Int])
  case class BeneficiaryDetails_BatchRequest(batchno: Option[Int], beneficiarydata: Seq[BeneficiaryDetails_Request])
  case class BeneficiaryDetailsResponse_Batch(memberno: Int, fullnames: String, relationship: String, gender: String, statuscode: Int, statusdescription: String)
  case class BeneficiaryDetailsResponse_BatchData(beneficiarydata: Seq[BeneficiaryDetailsResponse_Batch])

  //MemberBalanceDetails
  case class MemberBalanceDetails_Request(memberno: JsValue, rowcount: Option[Int])
  case class MemberBalanceDetails_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberBalanceDetails_Request])
  //case class MemberBalanceDetailsResponse_Batch(memberno: Int, fullnames: String, phoneno: String, membertype: String, dcee: BigDecimal, dcer: Float, dcavr: Float, dctotal: Float, dbee: Float, dber: Float, dbtotal: Float, statuscode: Int, statusdescription: String)
  case class MemberBalanceDetailsResponse_Batch(memberno: Int, fullnames: String, phoneno: String, membertype: String, dcee: BigDecimal, dcer: BigDecimal, dcavr: BigDecimal, dctotal: BigDecimal, dbee: BigDecimal, dber: BigDecimal, dbtotal: BigDecimal, statuscode: Int, statusdescription: String)
  case class MemberBalanceDetailsResponse_BatchData(memberdata: Seq[MemberBalanceDetailsResponse_Batch])

  //MemberContributionsDetails
  case class MemberContributionsDetails_Request(memberno: JsValue, rowcount: Option[Int])
  case class MemberContributionsDetails_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberContributionsDetails_Request])
  case class MemberContributionsDetailsResponse_Batch(memberno: Int, fullnames: String, membertype: String, ee: BigDecimal, er: BigDecimal, avr: BigDecimal, total: BigDecimal, regstatus: String, datepaid: String, statuscode: Int, statusdescription: String)
  case class MemberContributionsDetailsResponse_BatchData(memberdata: Seq[MemberContributionsDetailsResponse_Batch])

  //MemberDetailsGeneral
  case class MemberDetailsGeneral_Request(memberno: JsValue, rowcount: Option[Int])
  case class MemberDetailsGeneral_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberDetailsGeneral_Request])
  //case class MemberDetailsGeneralResponse_Batch(memberno: Int, fullnames: String, idno: Int, phoneno: String, membertype: String, statuscode: Int, statusdescription: String)
  case class MemberDetailsGeneralResponse_Batch(memberno: Int, fullnames: String, idno: Int, phoneno: String, gender: String, statuscode: Int, statusdescription: String)
  //case class MemberBalanceDetailsGeneralResponse_Batch(membertype: String, dcee: Float, dcer: Float, dcavr: Float, dctotal: Float, dbee: Float, dber: Float, dbtotal: Float, statuscode: Int, statusdescription: String)
  //case class MemberBalanceDetailsGeneralResponse_Batch(membertype: String, dcee: Float, dcer: Float, dctotal: Float, dbee: Float, dber: Float, dbtotal: Float, statuscode: Int, statusdescription: String)
  //case class MemberBalanceDetailsGeneralResponse_Batch(membertype: String, dcee: Float, dcer: Float, dcavr: Float, dctotal: Float, dbee: Float, dber: Float, dbtotal: Float, statuscode: Int, statusdescription: String)
  case class MemberBalanceDetailsGeneralResponse_Batch(membertype: String, dcee: BigDecimal, dcer: BigDecimal, dcavr: BigDecimal, dctotal: BigDecimal, dbee: BigDecimal, dber: BigDecimal, dbtotal: BigDecimal, statuscode: Int, statusdescription: String)
  case class BeneficiaryDetailsGeneralResponse_Batch(fullnames: String, relationship: String, gender: String, statuscode: Int, statusdescription: String)
  case class MemberDetailsGeneralResponse_BatchData(memberdata: MemberDetailsGeneralResponse_Batch, balancedata: Seq[MemberBalanceDetailsGeneralResponse_Batch], beneficiarydata: Seq[BeneficiaryDetailsGeneralResponse_Batch])
  case class MemberDetailsGeneralResponse(memberdata: Seq[MemberDetailsGeneralResponse_BatchData])

  //MemberDetailsRegistered
  case class MemberDetailsRegistered_Request(memberno: JsValue, idno: Option[JsValue], phoneno: Option[JsValue], channeltype: Option[JsValue])
  case class MemberDetailsRegistered_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberDetailsRegistered_Request])
  case class MemberDetailsRegisteredResponse_Batch(memberno: Int, statuscode: Int, statusdescription: String)
  case class MemberDetailsRegisteredResponse_BatchData(memberdata: Seq[MemberDetailsRegisteredResponse_Batch])

  //MemberDetailsValidate
  case class MemberDetailsValidate_Request(memberno: JsValue, idno: JsValue, phoneno: JsValue)
  case class MemberDetailsValidate_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberDetailsValidate_Request])
  case class MemberDetailsValidateResponse_Batch(memberno: Int, statuscode: Int, statusdescription: String)
  case class MemberDetailsValidateResponse_BatchData(memberdata: Seq[MemberDetailsValidateResponse_Batch])

  //
  /*
  case class CbsMessage_Batch(staffno: JsValue, pensionercode: String, statuscode: JsValue, statusdescription: String, verified_previous_cycle: JsValue, verified_cycle_return_date: String, previous_cycle_id: JsValue)
  case object CbsMessage_Batch extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_BatchFormat = jsonFormat7(CbsMessage_Batch.apply)
  }
  */

  //ProjectionBenefits
  /*
  case class CbsMessage_ProjectionBenefits(id: Option[JsValue], calc_date: Option[JsValue], exit_date: Option[JsValue], scheme_id: Option[JsValue],
                                           member_id: Option[JsValue], exit_reason: Option[JsValue], exit_age: Option[JsValue], years_worked: Option[JsValue],
                                           monthly_pen: Option[JsValue], normalcontr_p_intr: Option[JsValue], avccontr_p_intr: Option[JsValue], accumRegBenefits: Option[JsValue],
                                           accumNonTaxExempt: Option[JsValue], commutedLumpsum: Option[JsValue], commutedLumpsumReg: Option[JsValue], purchasePrice: Option[JsValue],
                                           taxFreeLumpsum: Option[JsValue], taxableAmount: Option[JsValue], witholdingTax: Option[JsValue], commutedLumpsumUnreg: Option[JsValue],
                                           liability: Option[JsValue], nssfOffset: Option[JsValue], lumpsumPayable: Option[JsValue], annualSalary: Option[JsValue],
                                           targetPension: Option[JsValue], annualPension: Option[JsValue], monthlyPension: Option[JsValue], taxOnMonthlyPension: Option[JsValue],
                                           lumpsumFactor: Option[JsValue], annuityRate: Option[JsValue], annuityRateFunction: Option[JsValue], annualPenOneFormula: Option[JsValue],
                                           annualPenTwoFormula: Option[JsValue], lumpsumOneFormula: Option[JsValue], lumpsumTwoFormula: Option[JsValue], projectionType: Option[JsValue],
                                           pensionPerc: Option[JsValue], lumpsumPerc: Option[JsValue], totalBenefitsDb: Option[JsValue])
  */
  /*
  case class CbsMessage_ProjectionBenefits(id: Option[JsValue], calc_date: Option[JsValue], exit_date: Option[JsValue], scheme_id: Option[JsValue],
                                           member_id: Option[JsValue], exit_reason: Option[JsValue], exit_age: Option[JsValue], years_worked: Option[JsValue],
                                           totalBenefits: Option[JsValue], purchasePrice: Option[JsValue], annualPension: Option[JsValue], monthlyPension: Option[JsValue],
                                           taxOnMonthlyPension: Option[JsValue], commutedLumpsum: Option[JsValue], taxFreeLumpsum: Option[JsValue], taxableAmount: Option[JsValue],
                                           witholdingTax: Option[JsValue], liability: Option[JsValue], lumpsumPayable: Option[JsValue])
  case object CbsMessage_ProjectionBenefits extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_ProjectionBenefitsFormat = jsonFormat19(CbsMessage_ProjectionBenefits.apply)
  }

  case class CbsMessage_ProjectionBenefits_Batch(rows: Seq[CbsMessage_ProjectionBenefits])
  case object CbsMessage_ProjectionBenefits_Batch extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_ProjectionBenefits_BatchFormat = jsonFormat1(CbsMessage_ProjectionBenefits_Batch.apply)
  }
  */
  //case class CbsMessage_ProjectionBenefits(id: String, calc_date: String, exit_date: String, scheme_id: String, member_id: String, exit_reason: String, exit_age: String, years_worked: String, totalBenefits: String, purchasePrice: String, annualPension: String, monthlyPension: String, taxOnMonthlyPension: String, commutedLumpsum: String, taxFreeLumpsum: String, taxableAmount: String, witholdingTax: String, liability: String, lumpsumPayable: String)
  //case class CbsMessage_ProjectionBenefits(id: Option[String], calc_date: Option[String], exit_date: Option[String], scheme_id: Option[String], member_id: Option[String], exit_reason: Option[String], exit_age: Option[String], years_worked: Option[String], totalBenefits: Option[String], purchasePrice: Option[String], annualPension: Option[String], monthlyPension: Option[String], taxOnMonthlyPension: Option[String], commutedLumpsum: Option[String], taxFreeLumpsum: Option[String], taxableAmount: Option[String], witholdingTax: Option[String], liability: Option[String], lumpsumPayable: Option[String])
  case class CbsMessage_ProjectionBenefits(id: Option[String], calc_date: Option[String], exit_date: Option[String], scheme_id: Option[String], member_id: Option[String], exit_reason: Option[String], exit_age: Option[String], years_worked: Option[String], totalBenefitsDb: Option[String], purchasePrice: Option[String], annualPension: Option[String], monthlyPension: Option[String], taxOnMonthlyPension: Option[String], commutedLumpsum: Option[String], taxFreeLumpsum: Option[String], taxableAmount: Option[String], witholdingTax: Option[String], liability: Option[String], lumpsumPayable: Option[String])
  case object CbsMessage_ProjectionBenefits extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_ProjectionBenefitsFormat = jsonFormat19(CbsMessage_ProjectionBenefits.apply)
  }

  case class CbsMessage_ProjectionBenefits_Batch(rows: Seq[CbsMessage_ProjectionBenefits])
  case object CbsMessage_ProjectionBenefits_Batch extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_ProjectionBenefits_BatchFormat = jsonFormat1(CbsMessage_ProjectionBenefits_Batch.apply)
  }

  //ProvisionalStatement
  case class CbsMessage_ProvisionalStatement(openEe: Option[String], openEr: Option[String], openAvc: Option[String], openTotal: Option[String], contrEe: Option[String], contrEr: Option[String], contrAvc: Option[String], contrTotal: Option[String], grandTotal: Option[String])
  case object CbsMessage_ProvisionalStatement extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_ProvisionalStatementFormat = jsonFormat9(CbsMessage_ProvisionalStatement.apply)
  }

  case class CbsMessage_ProvisionalStatement_Batch(rows: Seq[CbsMessage_ProvisionalStatement])
  case object CbsMessage_ProvisionalStatement_Batch extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val CbsMessage_ProvisionalStatement_BatchFormat = jsonFormat1(CbsMessage_ProvisionalStatement_Batch.apply)
  }

  //MemberProjectionBenefits
  case class MemberProjectionBenefits_Request(memberno: JsValue, membertype: JsValue, projectiontype: JsValue)
  //case class MemberProjectionBenefits_BatchRequest(batchno: Option[Int], memberdata: Seq[MemberProjectionBenefits_Request])
  case class MemberProjectionBenefitsResponse_Batch(memberno: Int, statuscode: Int, statusdescription: String)
  //case class MemberProjectionBenefitsResponse_BatchData(memberdata: Seq[MemberProjectionBenefitsResponse_Batch])

  case class MemberProjectionBenefitsDetailsResponse_Batch(calc_date: String, exit_date: String, exit_reason: String, exit_age: Int, years_worked: BigDecimal, totalbenefits: BigDecimal, purchaseprice: BigDecimal, annualpension: BigDecimal, monthlypension: BigDecimal, taxonmonthlypension: BigDecimal, netmonthlypension: BigDecimal, commutedlumpsum: BigDecimal, taxfreelumpsum: BigDecimal, taxableamount: BigDecimal, witholdingtax: BigDecimal, liability: BigDecimal, lumpsumpayable: BigDecimal)
  /*
  case object MemberProjectionBenefitsDetailsResponse_Batch extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val MemberProjectionBenefitsDetailsResponse_BatchFormat = jsonFormat17(MemberProjectionBenefitsDetailsResponse_Batch.apply)
  }
  */

  //case class MemberProjectionBenefitsDetailsResponse_BatchData(memberno: Int, statuscode: Int, statusdescription: String, projectionbenefitsdata: Seq[MemberProjectionBenefitsDetailsResponse_Batch])
  case class MemberProjectionBenefitsDetailsResponse_BatchData(memberno: Int, statuscode: Int, statusdescription: String, projectionbenefitsdata: MemberProjectionBenefitsDetailsResponse_Batch)
  /*
  case object MemberProjectionBenefitsDetailsResponse_BatchData extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val MemberProjectionBenefitsDetailsResponse_BatchDataFormat = jsonFormat4(MemberProjectionBenefitsDetailsResponse_BatchData.apply)
  }
  */

  case class response_MemberProjectionBenefits_status(statuscode: Option[Int], statusdescription: Option[String])
  case object response_MemberProjectionBenefits_status extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val response_MemberProjectionBenefits_statusFormat = jsonFormat2(response_MemberProjectionBenefits_status.apply)
  }

  case class ResultOutput_Cbs(statuscode : String, sourceDataTable : SQLServerDataTable)

  case class Result_CbsProvisionalStatement(isSuccessful: Boolean, Ee: BigDecimal, Er: BigDecimal, Avc: BigDecimal, Total: BigDecimal)
  case class ResultOutput_Balances_Cbs(isSuccessful: Boolean, Ee_Db: BigDecimal, Er_Db: BigDecimal, Avc_Db: BigDecimal, Total_Db: BigDecimal, Ee_Dc: BigDecimal, Er_Dc: BigDecimal, Avc_Dc: BigDecimal, Total_Dc: BigDecimal)

  /*** Xml data ***/
  class Stock(var symbol: String, var businessName: String, var price: Double) {

    // (a) convert Stock fields to XML
    def toXml = {
      <stock>
        <symbol>{symbol}</symbol>
        <businessName>{businessName}</businessName>
        <price>{price}</price>
      </stock>
    }

    override def toString =
      s"symbol: $symbol, businessName: $businessName, price: $price"

  }

  object Stock {

    // (b) convert XML to a Stock
    def fromXml(node: scala.xml.Node):Stock = {
      val symbol = (node \ "symbol").text
      val businessName = (node \ "businessName").text
      val price = (node \ "price").text.toDouble
      new Stock(symbol, businessName, price)
    }

  }
  /*
  class AccountVerificationResponse(var messageIdentification: String, var creationDateTime: String, var firstAgentIdentification: String, var assignerAgentIdentification: String, var assigneeAgentIdentification: String, var verificationIdentification: String, var beneficiaryAccountNumber: String, var beneficiaryTransactionMode: String, var beneficiaryAgentIdentification: String) {

    // (a) convert AccountVerification fields to XML
    def toXml = {
      //<?xml version="1.0" encoding="UTF-8"?>
      <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.023.001.02">
        <IdVrfctnReq>
          <Assgnmt>
            <MsgId>{messageIdentification}</MsgId>
            <CreDtTm>{creationDateTime}</CreDtTm>
            <FrstAgt>
              <FinInstnId>
                <Othr>
                  <Id>{firstAgentIdentification}</Id>
                </Othr>
              </FinInstnId>
            </FrstAgt>
            <Assgnr>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{assignerAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </Assgnr>
            <Assgne>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{assigneeAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </Assgne>
          </Assgnmt>
          <Vrfctn>
            <Id>{verificationIdentification}</Id>
            <PtyAndAcctId>
              <Acct>
                <Othr>
                  <Id>{beneficiaryAccountNumber}</Id>
                  <SchmeNm>
                    <Prtry>{beneficiaryTransactionMode}</Prtry>
                  </SchmeNm>
                </Othr>
              </Acct>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{beneficiaryAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </PtyAndAcctId>
          </Vrfctn>
        </IdVrfctnReq>
      </Document>
    }

    override def toString =
      s"messageIdentification: $messageIdentification, creationDateTime: $creationDateTime, firstAgentIdentification: $firstAgentIdentification, assignerAgentIdentification: $assignerAgentIdentification, assigneeAgentIdentification: $assigneeAgentIdentification, verificationIdentification: $verificationIdentification, beneficiaryAccountNumber: $beneficiaryAccountNumber, beneficiaryTransactionMode: $beneficiaryTransactionMode, beneficiaryAgentIdentification: $beneficiaryAgentIdentification"
  }

  object AccountVerification {

    // (b) convert XML to a AccountVerification
    def fromXml(node: scala.xml.Node):AccountVerificationResponse = {
      /*
        val symbol = (node \ "symbol").text
        val businessName = (node \ "businessName").text
        val price = (node \ "price").text.toDouble
      */
      val messageIdentification: String = ""
      val creationDateTime: String = ""
      val firstAgentIdentification: String = ""
      val assignerAgentIdentification: String = ""
      val assigneeAgentIdentification: String = ""
      val verificationIdentification: String = ""
      val beneficiaryAccountNumber: String = ""
      val beneficiaryTransactionMode: String = ""
      val beneficiaryAgentIdentification: String = ""
      new AccountVerificationResponse(messageIdentification, creationDateTime, firstAgentIdentification, assignerAgentIdentification, assigneeAgentIdentification, verificationIdentification, beneficiaryAccountNumber, beneficiaryTransactionMode, beneficiaryAgentIdentification)
    }

  }
  */
  //AccountVerificationResponse

  class AccountVerificationResponse(
                                     var messageIdentification: String,
                                     var creationDateTime: String,
                                     var firstAgentIdentification: String,
                                     var assignerAgentIdentification: String,
                                     var assigneeAgentIdentification: String,
                                     var originalMessageIdentification: String,
                                     var originalCreationDateTime: String,
                                     var originalFirstAgentIdentification: String,
                                     var originalVerificationIdentification: String,
                                     var verificationStatus: String,
                                     var verificationReasonCode: String,
                                     var originalBeneficiaryAccountNumber: String,
                                     var originalBeneficiarySchemeName: String,
                                     var originalBeneficiaryAgentIdentification: String,
                                     var updatedBeneficiaryAccountName: String,
                                     var updatedBeneficiaryAccountNumber: String,
                                     var updatedBeneficiarySchemeName: String,
                                     var updatedBeneficiaryAgentIdentification: String
                                   ) {

    // (a) convert AccountVerificationResponse fields to XML
    def toXml = {
      <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.024.001.02">
        <IdVrfctnRpt>
          <Assgnmt>
            <MsgId>{messageIdentification}</MsgId>
            <CreDtTm>{creationDateTime}</CreDtTm>
            <FrstAgt>
              <FinInstnId>
                <Othr>
                  <Id>{firstAgentIdentification}</Id>
                </Othr>
              </FinInstnId>
            </FrstAgt>
            <Assgnr>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{assignerAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </Assgnr>
            <Assgne>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{assigneeAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </Assgne>
          </Assgnmt>
          <OrgnlAssgnmt>
            <MsgId>{originalMessageIdentification}</MsgId>
            <CreDtTm>{originalCreationDateTime}</CreDtTm>
            <FrstAgt>
              <FinInstnId>
                <Othr>
                  <Id>{originalFirstAgentIdentification}</Id>
                </Othr>
              </FinInstnId>
            </FrstAgt>
          </OrgnlAssgnmt>
          <Rpt>
            <OrgnlId>{originalVerificationIdentification}</OrgnlId>
            <Vrfctn>{verificationStatus}</Vrfctn>
            <Rsn>
              <Cd>{verificationReasonCode}</Cd>
            </Rsn>
            <OrgnlPtyAndAcctId>
              <Acct>
                <Othr>
                  <Id>{originalBeneficiaryAccountNumber}</Id>
                  <SchmeNm>
                    <Prtry>{originalBeneficiarySchemeName}</Prtry>
                  </SchmeNm>
                </Othr>
              </Acct>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{originalBeneficiaryAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </OrgnlPtyAndAcctId>
            <UpdtdPtyAndAcctId>
              <Pty>
                <Nm>{updatedBeneficiaryAccountName}</Nm>
              </Pty>
              <Acct>
                <Othr>
                  <Id>{updatedBeneficiaryAccountNumber}</Id>
                  <SchmeNm>
                    <Prtry>{updatedBeneficiarySchemeName}</Prtry>
                  </SchmeNm>
                </Othr>
              </Acct>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{updatedBeneficiaryAgentIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </UpdtdPtyAndAcctId>
          </Rpt>
        </IdVrfctnRpt>
      </Document>
    }

    override def toString =
      s"messageIdentification: $messageIdentification, creationDateTime: $creationDateTime, firstAgentIdentification: $firstAgentIdentification, assignerAgentIdentification: $assignerAgentIdentification, assigneeAgentIdentification: $assigneeAgentIdentification, originalMessageIdentification: $originalMessageIdentification, originalCreationDateTime: $originalCreationDateTime, originalFirstAgentIdentification: $originalFirstAgentIdentification, originalVerificationIdentification: $originalVerificationIdentification, verificationStatus: $verificationStatus, verificationReasonCode: $verificationReasonCode, originalBeneficiaryAccountNumber: $originalBeneficiaryAccountNumber, originalBeneficiarySchemeName: $originalBeneficiarySchemeName, originalBeneficiaryAgentIdentification: $originalBeneficiaryAgentIdentification, updatedBeneficiaryAccountName: $updatedBeneficiaryAccountName, updatedBeneficiaryAccountNumber: $updatedBeneficiaryAccountNumber, updatedBeneficiarySchemeName: $updatedBeneficiarySchemeName, updatedBeneficiaryAgentIdentification: $updatedBeneficiaryAgentIdentification"
    }

        object AccountVerificationResponse {

        // (b) convert XML to a AccountVerification
        def fromXml(node: scala.xml.Node): AccountVerificationResponse = {
        val messageIdentification: String = (node \ "IdVrfctnRpt" \ "Assgnmt" \ "MsgId").text
        val creationDateTime: String = (node \ "IdVrfctnRpt" \ "Assgnmt" \ "CreDtTm").text
        val firstAgentIdentification: String = (node \ "IdVrfctnRpt" \ "Assgnmt" \ "FrstAgt" \ "FinInstnId" \ "Othr" \ "Id").text
        val assignerAgentIdentification: String = (node \ "IdVrfctnRpt" \ "Assgnmt" \ "Assgnr" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
        val assigneeAgentIdentification: String = (node \ "IdVrfctnRpt" \ "Assgnmt" \ "Assgne" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
        val originalMessageIdentification: String = (node \ "IdVrfctnRpt" \ "OrgnlAssgnmt" \ "MsgId").text
        val originalCreationDateTime: String = (node \ "IdVrfctnRpt" \ "OrgnlAssgnmt" \ "CreDtTm").text
        val originalFirstAgentIdentification: String = (node \ "IdVrfctnRpt" \ "OrgnlAssgnmt" \ "FrstAgt" \ "FinInstnId" \ "Othr" \ "Id").text
        val originalVerificationIdentification: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "OrgnlId").text
        val verificationStatus: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "Vrfctn").text
        val verificationReasonCode: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "Rsn" \ "Cd").text
        val originalBeneficiaryAccountNumber: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "OrgnlPtyAndAcctId" \ "Acct" \ "Othr" \ "Id").text
        val originalBeneficiarySchemeName: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "OrgnlPtyAndAcctId" \ "Acct" \ "Othr" \ "SchmeNm" \ "Prtry").text
        val originalBeneficiaryAgentIdentification: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "OrgnlPtyAndAcctId" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
        val updatedBeneficiaryAccountName: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "UpdtdPtyAndAcctId" \ "Pty" \ "Nm").text
        val updatedBeneficiaryAccountNumber: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "UpdtdPtyAndAcctId" \ "Acct" \ "Othr" \ "Id").text
        val updatedBeneficiarySchemeName: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "UpdtdPtyAndAcctId" \ "Acct" \ "Othr" \ "SchmeNm" \ "Prtry").text
        val updatedBeneficiaryAgentIdentification: String = (node \ "IdVrfctnRpt" \ "Rpt" \ "UpdtdPtyAndAcctId" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
        new AccountVerificationResponse(
        messageIdentification, creationDateTime, firstAgentIdentification,
        assignerAgentIdentification, assigneeAgentIdentification, originalMessageIdentification,
        originalCreationDateTime, originalFirstAgentIdentification, originalVerificationIdentification,
        verificationStatus, verificationReasonCode, originalBeneficiaryAccountNumber,
        originalBeneficiarySchemeName, originalBeneficiaryAgentIdentification, updatedBeneficiaryAccountName,
        updatedBeneficiaryAccountNumber, updatedBeneficiarySchemeName, updatedBeneficiaryAgentIdentification
        )
        }

        }

  case class AccountVerificationDetailsResponse(statuscode: Int, statusdescription: String)
  /*** Xml response test data ***/
  case class xmlResponseData(text: String)
  case class textResponseData(text: String)
  /*** Xml data ***/

  implicit val system = ActorSystem("IpslEngine")
  implicit val materializer = ActorMaterializer()

  //implicit val blockingDispatcher = system.dispatchers.lookup("my-dispatcher")
  implicit val timeout = Timeout(15 seconds)

  val strApplication_path : String = System.getProperty("user.dir")
  var strFileDate  = new SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date)
  val strpath_file : String = strApplication_path + "\\Logs\\" + strFileDate + "\\Logs.txt"
  val strpath_file2 : String = strApplication_path + "\\Logs\\" + strFileDate + "\\Errors.txt"
  var is_Successful : Boolean = create_Folderpaths(strApplication_path)
  var writer_data = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file,true)))
  var writer_errors = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file2,true)))

  def getMemberDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myMemberDetailsResponse_BatchData : Seq[MemberDetailsResponse_Batch] = Seq.empty[MemberDetailsResponse_Batch]
      val strApifunction : String = "getMemberDetails"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberDetails_Request_Reads: Reads[MemberDetails_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "rowcount").readNullable[Int]
              )(MemberDetails_Request.apply _)

            implicit val MemberDetails_BatchRequest_Reads: Reads[MemberDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberDetails_Request]]
              )(MemberDetails_BatchRequest.apply _)

            myjson.validate[MemberDetails_BatchRequest] match {
              case JsSuccess(myMemberDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myMemberDetails_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("OriginalMemberNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)

                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myMemberDetails_BatchRequest.memberdata.foreach(myMemberDetails => {

                      myMemberNo = 0
                      strMemberNo = ""

                      try{
                        //strMemberNo
                        strMemberNo = myMemberDetails.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, strMemberNo, myBatchReference, myBatchSize, strRequestData, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.getMemberDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  val myfullNames = resultSet.getString("fullNames")
                                  val myiDNo = resultSet.getInt("iDNo")
                                  val myphoneNo = resultSet.getString("phoneNo")
                                  val mygender = resultSet.getString("gender")
                                  val mymemberType = resultSet.getString("memberType")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  val myMemberDetailsResponse_Batch = new MemberDetailsResponse_Batch(mymemberNo , myfullNames, myiDNo, myphoneNo, mygender, mymemberType, myresponseCode, myresponseMessage)
                                  myMemberDetailsResponse_BatchData  = myMemberDetailsResponse_BatchData :+ myMemberDetailsResponse_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MemberDetailsResponse_BatchWrites = Json.writes[MemberDetailsResponse_Batch]
      implicit val MemberDetailsResponse_BatchDataWrites = Json.writes[MemberDetailsResponse_BatchData]

      if (myMemberDetailsResponse_BatchData.isEmpty == true || myMemberDetailsResponse_BatchData == true){
        val myMemberDetailsResponse_Batch = new MemberDetailsResponse_Batch(0, "", 0, "", "", "", responseCode, responseMessage)
        myMemberDetailsResponse_BatchData  = myMemberDetailsResponse_BatchData :+ myMemberDetailsResponse_Batch
      }

      val myMemberDetailsResponse = new MemberDetailsResponse_BatchData(myMemberDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myMemberDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def getBeneficiaryDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myBeneficiaryDetailsResponse_BatchData : Seq[BeneficiaryDetailsResponse_Batch] = Seq.empty[BeneficiaryDetailsResponse_Batch]
      val strApifunction : String = "getBeneficiaryDetails"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val BeneficiaryDetails_Request_Reads: Reads[BeneficiaryDetails_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "rowcount").readNullable[Int]
              )(BeneficiaryDetails_Request.apply _)

            implicit val BeneficiaryDetails_BatchRequest_Reads: Reads[BeneficiaryDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "beneficiarydata").read[Seq[BeneficiaryDetails_Request]]
              )(BeneficiaryDetails_BatchRequest.apply _)

            myjson.validate[BeneficiaryDetails_BatchRequest] match {
              case JsSuccess(myBeneficiaryDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myBeneficiaryDetails_BatchRequest.beneficiarydata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("OriginalMemberNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)

                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myBeneficiaryDetails_BatchRequest.beneficiarydata.foreach(myBeneficiaryDetails => {

                      myMemberNo = 0
                      strMemberNo = ""

                      try{
                        //strMemberNo
                        strMemberNo = myBeneficiaryDetails.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, strMemberNo, myBatchReference, myBatchSize, strRequestData, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.getBeneficiaryDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  val myfullNames = resultSet.getString("fullNames")
                                  val myrelationship = resultSet.getString("relationship")
                                  val mygender = resultSet.getString("gender")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  val myBeneficiaryDetailsResponse_Batch = new BeneficiaryDetailsResponse_Batch(mymemberNo , myfullNames, myrelationship, mygender, myresponseCode, myresponseMessage)
                                  myBeneficiaryDetailsResponse_BatchData  = myBeneficiaryDetailsResponse_BatchData :+ myBeneficiaryDetailsResponse_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val BeneficiaryDetailsResponse_BatchWrites = Json.writes[BeneficiaryDetailsResponse_Batch]
      implicit val BeneficiaryDetailsResponse_BatchDataWrites = Json.writes[BeneficiaryDetailsResponse_BatchData]

      if (myBeneficiaryDetailsResponse_BatchData.isEmpty == true || myBeneficiaryDetailsResponse_BatchData == true){
        val myBeneficiaryDetailsResponse_Batch = new BeneficiaryDetailsResponse_Batch(0, "", "", "", responseCode, responseMessage)
        myBeneficiaryDetailsResponse_BatchData  = myBeneficiaryDetailsResponse_BatchData :+ myBeneficiaryDetailsResponse_Batch
      }

      val myBeneficiaryDetailsResponse = new BeneficiaryDetailsResponse_BatchData(myBeneficiaryDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myBeneficiaryDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def getMemberBalanceDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myMemberBalanceDetailsResponse_BatchData : Seq[MemberBalanceDetailsResponse_Batch] = Seq.empty[MemberBalanceDetailsResponse_Batch]
      val strApifunction : String = "getMemberBalanceDetails"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberBalanceDetails_Request_Reads: Reads[MemberBalanceDetails_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "rowcount").readNullable[Int]
              )(MemberBalanceDetails_Request.apply _)

            implicit val MemberBalanceDetails_BatchRequest_Reads: Reads[MemberBalanceDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberBalanceDetails_Request]]
              )(MemberBalanceDetails_BatchRequest.apply _)

            myjson.validate[MemberBalanceDetails_BatchRequest] match {
              case JsSuccess(myMemberBalanceDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myMemberBalanceDetails_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("OriginalMemberNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)

                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myMemberBalanceDetails_BatchRequest.memberdata.foreach(myMemberBalanceDetails => {

                      myMemberNo = 0
                      strMemberNo = ""

                      try{
                        //strMemberNo
                        strMemberNo = myMemberBalanceDetails.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, strMemberNo, myBatchReference, myBatchSize, strRequestData, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.getMemberBalanceDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  val myfullNames = resultSet.getString("fullNames")
                                  val myphoneNo = resultSet.getString("phoneNo")
                                  val mymemberType = resultSet.getString("memberType")
                                  //val mydcee = resultSet.getFloat("dcee")
                                  /*
                                  val mydcee = resultSet.getBigDecimal("dcee")
                                  val mydcer = resultSet.getBigDecimal("dcer")
                                  val mydcavr = resultSet.getBigDecimal("dcavr")
                                  val mydctotal = resultSet.getBigDecimal("dctotal")
                                  val mydbee = resultSet.getBigDecimal("dbee")
                                  val mydber = resultSet.getBigDecimal("dber")
                                  val mydbtotal = resultSet.getBigDecimal("dbtotal")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  */
                                  var mydcee: BigDecimal = 0
                                  var mydcer: BigDecimal = 0
                                  var mydcavr: BigDecimal = 0
                                  var mydctotal: BigDecimal = 0
                                  var mydbee: BigDecimal = 0
                                  var mydber: BigDecimal = 0
                                  var mydbtotal: BigDecimal = 0
                                  var myresponseCode = resultSet.getInt("responseCode")
                                  var myresponseMessage = resultSet.getString("responseMessage")
                                  try{
                                    //If mymemberNo is not found in local DB, then check in CBS
                                    //println("Step 1")
                                    if (myresponseCode == 0 && mymemberNo > 0){
                                      //println("Step 2")
                                      //var respCode: Int = 1
                                      var isSuccessful: Boolean = false
                                      val myResultOutput_Cbs = getMemberBalanceDetailsCbs(mymemberNo)
                                      //println("Step 5 - " + respCode.toString)
                                      if (myResultOutput_Cbs != null){
                                        if (myResultOutput_Cbs.isSuccessful != null){
                                          isSuccessful = myResultOutput_Cbs.isSuccessful
                                        }

                                        if (myResultOutput_Cbs.Ee_Dc != null){
                                          mydcee = myResultOutput_Cbs.Ee_Dc
                                        }

                                        if (myResultOutput_Cbs.Er_Dc != null){
                                          mydcer = myResultOutput_Cbs.Er_Dc
                                        }

                                        if (myResultOutput_Cbs.Avc_Dc != null){
                                          mydcavr = myResultOutput_Cbs.Avc_Dc
                                        }

                                        if (myResultOutput_Cbs.Total_Dc != null){
                                          mydctotal = myResultOutput_Cbs.Total_Dc
                                        }

                                        if (myResultOutput_Cbs.Ee_Db != null){
                                          mydbee = myResultOutput_Cbs.Ee_Db
                                        }

                                        if (myResultOutput_Cbs.Er_Db != null){
                                          mydber = myResultOutput_Cbs.Er_Db
                                        }

                                        if (myResultOutput_Cbs.Total_Db != null){
                                          mydbtotal = myResultOutput_Cbs.Total_Db
                                        }

                                      }
                                      if (isSuccessful == true){
                                        myresponseCode = 0
                                        myresponseMessage = "Successful"
                                      }
                                      else{
                                        myresponseCode = 1
                                        myresponseMessage = "Failed to fetch balances, please try again later"
                                      }
                                    }
                                  }
                                  catch{
                                    case io: Throwable =>
                                      Log_errors(strApifunction + " : " + io.getMessage())
                                    case ex: Exception =>
                                      Log_errors(strApifunction + " : " + ex.getMessage())
                                  }
                                  val myMemberBalanceDetailsResponse_Batch = new MemberBalanceDetailsResponse_Batch(mymemberNo , myfullNames, myphoneNo, mymemberType, mydcee, mydcer, mydcavr, mydctotal, mydbee, mydber, mydbtotal, myresponseCode, myresponseMessage)
                                  //println("mymemberNo: " + mymemberNo + ",mydcee: " + mydcee + ", mydcer: " + mydcer + ", mydcavr: " + mydcavr + ", mydctotal: " + mydctotal + ", mydbee: " + mydbee + ", mydber: " + mydber + ", mydbtotal: " + mydbtotal)
                                  myMemberBalanceDetailsResponse_BatchData  = myMemberBalanceDetailsResponse_BatchData :+ myMemberBalanceDetailsResponse_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MemberBalanceDetailsResponse_BatchWrites = Json.writes[MemberBalanceDetailsResponse_Batch]
      implicit val MemberBalanceDetailsResponse_BatchDataWrites = Json.writes[MemberBalanceDetailsResponse_BatchData]

      if (myMemberBalanceDetailsResponse_BatchData.isEmpty == true || myMemberBalanceDetailsResponse_BatchData == true){
        val myMemberBalanceDetailsResponse_Batch = new MemberBalanceDetailsResponse_Batch(0, "", "", "", 0, 0, 0, 0, 0, 0, 0, responseCode, responseMessage)
        myMemberBalanceDetailsResponse_BatchData  = myMemberBalanceDetailsResponse_BatchData :+ myMemberBalanceDetailsResponse_Batch
      }

      val myMemberBalanceDetailsResponse = new MemberBalanceDetailsResponse_BatchData(myMemberBalanceDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myMemberBalanceDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def getMemberContributionsDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myMemberContributionsDetailsResponse_BatchData : Seq[MemberContributionsDetailsResponse_Batch] = Seq.empty[MemberContributionsDetailsResponse_Batch]
      val strApifunction : String = "getMemberContributionsDetails"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberContributionsDetails_Request_Reads: Reads[MemberContributionsDetails_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "rowcount").readNullable[Int]
              )(MemberContributionsDetails_Request.apply _)

            implicit val MemberContributionsDetails_BatchRequest_Reads: Reads[MemberContributionsDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberContributionsDetails_Request]]
              )(MemberContributionsDetails_BatchRequest.apply _)

            myjson.validate[MemberContributionsDetails_BatchRequest] match {
              case JsSuccess(myMemberContributionsDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myMemberContributionsDetails_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("OriginalMemberNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)

                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myMemberContributionsDetails_BatchRequest.memberdata.foreach(myMemberContributionsDetails => {

                      myMemberNo = 0
                      strMemberNo = ""

                      try{
                        //strMemberNo
                        strMemberNo = myMemberContributionsDetails.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, strMemberNo, myBatchReference, myBatchSize, strRequestData, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.getMemberContributionsDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  val myfullNames = resultSet.getString("fullNames")
                                  val mymemberType = resultSet.getString("memberType")
                                  //val mydcee = resultSet.getFloat("dcee")
                                  val mydcee = resultSet.getBigDecimal("ee")
                                  val mydcer = resultSet.getBigDecimal("er")
                                  val mydcavr = resultSet.getBigDecimal("avr")
                                  val mydctotal = resultSet.getBigDecimal("total")
                                  val myregStatus = resultSet.getString("regStatus")
                                  val mydatePaid = resultSet.getString("datePaid")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  val myMemberContributionsDetailsResponse_Batch = new MemberContributionsDetailsResponse_Batch(mymemberNo , myfullNames, mymemberType, mydcee, mydcer, mydcavr, mydctotal, myregStatus, mydatePaid, myresponseCode, myresponseMessage)
                                  //println("mymemberNo: " + mymemberNo + ",mydcee: " + mydcee + ", mydcer: " + mydcer + ", mydcavr: " + mydcavr + ", mydctotal: " + mydctotal + ", mydbee: " + mydbee + ", mydber: " + mydber + ", mydbtotal: " + mydbtotal)
                                  myMemberContributionsDetailsResponse_BatchData  = myMemberContributionsDetailsResponse_BatchData :+ myMemberContributionsDetailsResponse_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MemberContributionsDetailsResponse_BatchWrites = Json.writes[MemberContributionsDetailsResponse_Batch]
      implicit val MemberContributionsDetailsResponse_BatchDataWrites = Json.writes[MemberContributionsDetailsResponse_BatchData]

      if (myMemberContributionsDetailsResponse_BatchData.isEmpty == true || myMemberContributionsDetailsResponse_BatchData == true){
        val myMemberContributionsDetailsResponse_Batch = new MemberContributionsDetailsResponse_Batch(0, "", "", 0, 0, 0, 0, "", "", responseCode, responseMessage)
        myMemberContributionsDetailsResponse_BatchData  = myMemberContributionsDetailsResponse_BatchData :+ myMemberContributionsDetailsResponse_Batch
      }

      val myMemberContributionsDetailsResponse = new MemberContributionsDetailsResponse_BatchData(myMemberContributionsDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myMemberContributionsDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def getMemberDetailsGeneral = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myMemberDetailsGeneralResponse : Seq[MemberDetailsGeneralResponse_BatchData] = Seq.empty[MemberDetailsGeneralResponse_BatchData]
      val strApifunction : String = "getMemberDetailsGeneral"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true

                if (responseCode == null){
                  responseCode = 1
                }

                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }

              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberDetailsGeneral_Request_Reads: Reads[MemberDetailsGeneral_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "rowcount").readNullable[Int]
              )(MemberDetailsGeneral_Request.apply _)

            implicit val MemberDetailsGeneral_BatchRequest_Reads: Reads[MemberDetailsGeneral_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberDetailsGeneral_Request]]
              )(MemberDetailsGeneral_BatchRequest.apply _)

            myjson.validate[MemberDetailsGeneral_BatchRequest] match {
              case JsSuccess(myMemberDetailsGeneral_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myMemberDetailsGeneral_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("OriginalMemberNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)

                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myMemberDetailsGeneral_BatchRequest.memberdata.foreach(myMemberDetailsGeneral => {

                      myMemberNo = 0
                      strMemberNo = ""

                      try{
                        //strMemberNo
                        strMemberNo = myMemberDetailsGeneral.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, strMemberNo, myBatchReference, myBatchSize, strRequestData, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          //Lets fetch MemberDetails
                          try {
                            var isFetchData: Boolean = false
                            val strSQL = "{ call dbo.getMemberDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  val myfullNames = resultSet.getString("fullNames")
                                  val myiDNo = resultSet.getInt("iDNo")
                                  val myphoneNo = resultSet.getString("phoneNo")
                                  val mygender = resultSet.getString("gender")
                                  //val mymemberType = resultSet.getString("memberType")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  //val myMemberDetailsGeneralResponse_Batch = new MemberDetailsGeneralResponse_Batch(mymemberNo, myfullNames, myiDNo, myphoneNo, mymemberType, myresponseCode, myresponseMessage)
                                  val myMemberDetailsGeneralResponse_Batch = new MemberDetailsGeneralResponse_Batch(mymemberNo, myfullNames, myiDNo, myphoneNo, mygender, myresponseCode, myresponseMessage)
                                  var myMemberBalanceDetailsGeneralResponse_BatchData : Seq[MemberBalanceDetailsGeneralResponse_Batch] = Seq.empty[MemberBalanceDetailsGeneralResponse_Batch]

                                  //println("test 1 - " + mymemberNo.toString + ", myresponseCode" + myresponseCode.toString)
                                  isFetchData = false //Lets intialise this field

                                  //Lets fetch BalanceDetails
                                  try{
                                    if (mymemberNo > 0 && myresponseCode == 0){
                                      //myDB.withConnection { implicit  myconn =>

                                      isFetchData = true

                                      try {
                                        //var myMemberBalanceDetailsGeneralResponse_BatchData : Seq[MemberBalanceDetailsGeneralResponse_Batch] = Seq.empty[MemberBalanceDetailsGeneralResponse_Batch]
                                        val strSQL2 = "{ call dbo.getMemberBalanceDetailsBatchMemberNo(?) }"
                                        val mystmt2 = myconn.prepareCall(strSQL2)
                                        try {
                                          myMemberBalanceDetailsGeneralResponse_BatchData = Seq.empty[MemberBalanceDetailsGeneralResponse_Batch]
                                          var mymemberNo1 : java.math.BigDecimal = new java.math.BigDecimal(mymemberNo)
                                          mystmt2.setBigDecimal(1, mymemberNo1)
                                          val rs = mystmt2.executeQuery()
                                          isProcessed = true
                                          if (rs != null){
                                            while ( rs.next()){
                                              val mymemberType = rs.getString("memberType")
                                              //val mydcee = rs.getFloat("dcee")
                                              /*
                                              val mydcee = rs.getBigDecimal("dcee")
                                              val mydcer = rs.getBigDecimal("dcer")
                                              val mydcavr = rs.getBigDecimal("dcavr")
                                              val mydctotal = rs.getBigDecimal("dctotal")
                                              val mydbee = rs.getBigDecimal("dbee")
                                              val mydber = rs.getBigDecimal("dber")
                                              val mydbtotal = rs.getBigDecimal("dbtotal")
                                              val myrespCode = rs.getInt("responseCode")
                                              val myrespMessage = rs.getString("responseMessage")
                                              */


                                              var mydcee: BigDecimal = 0
                                              var mydcer: BigDecimal = 0
                                              var mydcavr: BigDecimal = 0
                                              var mydctotal: BigDecimal = 0
                                              var mydbee: BigDecimal = 0
                                              var mydber: BigDecimal = 0
                                              var mydbtotal: BigDecimal = 0
                                              var myrespCode = rs.getInt("responseCode")
                                              var myrespMessage = rs.getString("responseMessage")

                                              try{
                                                //If mymemberNo is not found in local DB, then check in CBS
                                                //println("Step 1")
                                                if (myrespCode == 0 && mymemberNo > 0){
                                                  //println("Step 2")
                                                  //var respCode: Int = 1
                                                  var isSuccessful: Boolean = false
                                                  val myResultOutput_Cbs = getMemberBalanceDetailsCbs(mymemberNo)
                                                  //println("Step 5 - " + respCode.toString)
                                                  if (myResultOutput_Cbs != null){
                                                    if (myResultOutput_Cbs.isSuccessful != null){
                                                      isSuccessful = myResultOutput_Cbs.isSuccessful
                                                    }

                                                    if (myResultOutput_Cbs.Ee_Dc != null){
                                                      mydcee = myResultOutput_Cbs.Ee_Dc
                                                    }

                                                    if (myResultOutput_Cbs.Er_Dc != null){
                                                      mydcer = myResultOutput_Cbs.Er_Dc
                                                    }

                                                    if (myResultOutput_Cbs.Avc_Dc != null){
                                                      mydcavr = myResultOutput_Cbs.Avc_Dc
                                                    }

                                                    if (myResultOutput_Cbs.Total_Dc != null){
                                                      mydctotal = myResultOutput_Cbs.Total_Dc
                                                    }

                                                    if (myResultOutput_Cbs.Ee_Db != null){
                                                      mydbee = myResultOutput_Cbs.Ee_Db
                                                    }

                                                    if (myResultOutput_Cbs.Er_Db != null){
                                                      mydber = myResultOutput_Cbs.Er_Db
                                                    }

                                                    if (myResultOutput_Cbs.Total_Db != null){
                                                      mydbtotal = myResultOutput_Cbs.Total_Db
                                                    }

                                                  }
                                                  if (isSuccessful == true){
                                                    myrespCode = 0
                                                    myrespMessage = "Successful"
                                                  }
                                                  else{
                                                    myrespCode = 1
                                                    myrespMessage = "Failed to fetch balances, please try again later"
                                                  }
                                                }
                                              }
                                              catch{
                                                case io: Throwable =>
                                                  Log_errors(strApifunction + " : " + io.getMessage())
                                                case ex: Exception =>
                                                  Log_errors(strApifunction + " : " + ex.getMessage())
                                              }

                                              val myMemberBalanceDetailsGeneralResponse_Batch = new MemberBalanceDetailsGeneralResponse_Batch(mymemberType, mydcee, mydcer, mydcavr, mydctotal, mydbee, mydber, mydbtotal, myrespCode, myrespMessage)
                                              myMemberBalanceDetailsGeneralResponse_BatchData  = myMemberBalanceDetailsGeneralResponse_BatchData :+ myMemberBalanceDetailsGeneralResponse_Batch
                                            }
                                          }
                                        }
                                        catch{
                                          case io: Throwable =>
                                            //io.printStackTrace()
                                            responseMessage = "Error occured during processing, please try again."
                                            //println(io.printStackTrace())
                                            entryID = 2
                                            Log_errors(strApifunction + " : " + io.getMessage())
                                          //strErrorMsg = io.toString
                                          case ex: Exception =>
                                            //ex.printStackTrace()
                                            responseMessage = "Error occured during processing, please try again."
                                            //println(ex.printStackTrace())
                                            entryID = 3
                                            Log_errors(strApifunction + " : " + ex.getMessage())
                                        }

                                        //Lets combine data for MemberDetails, BalanceDetails and BeneficiaryDetails
                                        //val myMemberDetails = new MemberDetailsGeneralResponse_BatchData(myMemberDetailsGeneralResponse_Batch, myMemberBalanceDetailsGeneralResponse_BatchData)
                                        //myMemberDetailsGeneralResponse  = myMemberDetailsGeneralResponse :+ myMemberDetails

                                      }
                                      catch{
                                        case io: IOException =>
                                          //io.printStackTrace()
                                          responseMessage = "Error occured during processing, please try again."
                                          //println(io.printStackTrace())
                                          entryID = 2
                                          Log_errors(strApifunction + " : " + io.getMessage())
                                        //strErrorMsg = io.toString
                                        case ex: Exception =>
                                          //ex.printStackTrace()
                                          responseMessage = "Error occured during processing, please try again."
                                          //println(ex.printStackTrace())
                                          entryID = 3
                                          Log_errors(strApifunction + " : " + ex.getMessage())
                                      }
                                      //}
                                    }
                                    else if (mymemberNo > 0 && myresponseCode != 0){
                                      //println("test 2 - " + mymemberNo.toString + ", myresponseCode" + myresponseCode.toString)
                                    }
                                    else{
                                      responseMessage = "Invalid Input Data length"
                                      /*
                                      if (isValidLength == true){
                                        responseMessage = "Invalid Input Data length"
                                      }
                                      else{
                                        if (isValidDate1 == true && isValidDate2 == true){
                                          responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                                        }
                                        else if (isValidDate1 == false && isValidDate2 == true){
                                          responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                                        }
                                        else if (isValidDate1 == true && isValidDate2 == false){
                                          responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                                        }
                                        else {
                                          responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                                        }
                                      }
                                      */
                                    }
                                  }
                                  catch {
                                    case io: IOException =>
                                      //io.printStackTrace()
                                      responseMessage = "Error occured during processing, please try again."
                                      //println(io.printStackTrace())
                                      entryID = 2
                                      Log_errors(strApifunction + " : " + io.getMessage())
                                    case ex: Exception =>
                                      //ex.printStackTrace()
                                      responseMessage = "Error occured during processing, please try again."
                                      //println(ex.printStackTrace())
                                      entryID = 3
                                      Log_errors(strApifunction + " : " + ex.getMessage())
                                  }
                                  finally{

                                  }

                                  //Lets fetch BeneficiaryDetails
                                  try{
                                    //if (mymemberNo > 0 && myresponseCode == 0){
                                    if (isFetchData == true){
                                      //myDB.withConnection { implicit  myconn =>

                                      try{
                                        sourceDataTable.addRow(myMemberNo, strMemberNo, myBatchReference, myBatchSize, strRequestData)
                                      }
                                      catch {
                                        case io: Throwable =>
                                          Log_errors(strApifunction + " : " + io.getMessage())
                                        case ex: Exception =>
                                          Log_errors(strApifunction + " : " + ex.getMessage())
                                      }

                                      try {
                                        var myBeneficiaryDetailsGeneralResponse_BatchData : Seq[BeneficiaryDetailsGeneralResponse_Batch] = Seq.empty[BeneficiaryDetailsGeneralResponse_Batch]
                                        val strSQL2 = "{ call dbo.getBeneficiaryDetailsBatchMemberNo(?) }"
                                        val mystmt2 = myconn.prepareCall(strSQL2)
                                        //var k: Int = 0
                                        try {
                                          myBeneficiaryDetailsGeneralResponse_BatchData = Seq.empty[BeneficiaryDetailsGeneralResponse_Batch]
                                          var mymemberNo1 : java.math.BigDecimal = new java.math.BigDecimal(mymemberNo)
                                          mystmt2.setBigDecimal(1, mymemberNo1)
                                          val rs = mystmt2.executeQuery()
                                          isProcessed = true
                                          if (rs != null){
                                            while ( rs.next()){
                                              //k = k + 1
                                              val mymemberNo = rs.getInt("memberNo")
                                              val myfullNames = rs.getString("fullNames")
                                              val myrelationship = rs.getString("relationship")
                                              val mygender = rs.getString("gender")
                                              val myresponseCode = rs.getInt("responseCode")
                                              val myresponseMessage = rs.getString("responseMessage")
                                              val myBeneficiaryDetailsGeneralResponse_Batch = new BeneficiaryDetailsGeneralResponse_Batch(myfullNames, myrelationship, mygender, myresponseCode, myresponseMessage)
                                              myBeneficiaryDetailsGeneralResponse_BatchData  = myBeneficiaryDetailsGeneralResponse_BatchData :+ myBeneficiaryDetailsGeneralResponse_Batch
                                            }
                                            /*
                                              if (k > 0){
                                                //val myMemberDetailsGeneralResponse_Batch = new MemberDetailsGeneralResponse_BatchData(myMemberDetailsGeneralResponse_Batch, myBeneficiaryDetailsGeneralResponse_BatchData)
                                              }
                                              */
                                          }
                                        }
                                        catch{
                                          case io: Throwable =>
                                            //io.printStackTrace()
                                            responseMessage = "Error occured during processing, please try again."
                                            //println(io.printStackTrace())
                                            entryID = 2
                                            Log_errors(strApifunction + " : " + io.getMessage())
                                          //strErrorMsg = io.toString
                                          case ex: Exception =>
                                            //ex.printStackTrace()
                                            responseMessage = "Error occured during processing, please try again."
                                            //println(ex.printStackTrace())
                                            entryID = 3
                                            Log_errors(strApifunction + " : " + ex.getMessage())
                                        }

                                        //Lets combine data for MemberDetails, BalanceDetails and BeneficiaryDetails
                                        val myMemberDetails = new MemberDetailsGeneralResponse_BatchData(myMemberDetailsGeneralResponse_Batch, myMemberBalanceDetailsGeneralResponse_BatchData, myBeneficiaryDetailsGeneralResponse_BatchData)
                                        myMemberDetailsGeneralResponse  = myMemberDetailsGeneralResponse :+ myMemberDetails

                                      }
                                      catch{
                                        case io: IOException =>
                                          //io.printStackTrace()
                                          responseMessage = "Error occured during processing, please try again."
                                          //println(io.printStackTrace())
                                          entryID = 2
                                          Log_errors(strApifunction + " : " + io.getMessage())
                                        //strErrorMsg = io.toString
                                        case ex: Exception =>
                                          //ex.printStackTrace()
                                          responseMessage = "Error occured during processing, please try again."
                                          //println(ex.printStackTrace())
                                          entryID = 3
                                          Log_errors(strApifunction + " : " + ex.getMessage())
                                      }
                                      //}
                                    }

                                    else{
                                      responseMessage = "Invalid Input Data length"
                                      if (mymemberNo > 0 && myresponseCode != 0){
                                        val myBeneficiaryDetailsGeneralResponse_BatchData : Seq[BeneficiaryDetailsGeneralResponse_Batch] = Seq.empty[BeneficiaryDetailsGeneralResponse_Batch]
                                        val myMemberDetails = new MemberDetailsGeneralResponse_BatchData(myMemberDetailsGeneralResponse_Batch, myMemberBalanceDetailsGeneralResponse_BatchData, myBeneficiaryDetailsGeneralResponse_BatchData)
                                        myMemberDetailsGeneralResponse  = myMemberDetailsGeneralResponse :+ myMemberDetails
                                        //println("test 3 - " + mymemberNo.toString + ", myresponseCode" + myresponseCode.toString)
                                      }
                                      /*
                                      if (isValidLength == true){
                                        responseMessage = "Invalid Input Data length"
                                      }
                                      else{
                                        if (isValidDate1 == true && isValidDate2 == true){
                                          responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                                        }
                                        else if (isValidDate1 == false && isValidDate2 == true){
                                          responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                                        }
                                        else if (isValidDate1 == true && isValidDate2 == false){
                                          responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                                        }
                                        else {
                                          responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                                        }
                                      }
                                      */
                                    }
                                  }
                                  catch {
                                    case io: IOException =>
                                      //io.printStackTrace()
                                      responseMessage = "Error occured during processing, please try again."
                                      //println(io.printStackTrace())
                                      entryID = 2
                                      Log_errors(strApifunction + " : " + io.getMessage())
                                    case ex: Exception =>
                                      //ex.printStackTrace()
                                      responseMessage = "Error occured during processing, please try again."
                                      //println(ex.printStackTrace())
                                      entryID = 3
                                      Log_errors(strApifunction + " : " + ex.getMessage())
                                  }
                                  finally{

                                  }
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }

                          //start
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MemberDetailsGeneralResponse_BatchWrites = Json.writes[MemberDetailsGeneralResponse_Batch]
      implicit val MemberBalanceDetailsGeneralResponse_BatchWrites = Json.writes[MemberBalanceDetailsGeneralResponse_Batch]
      implicit val BeneficiaryDetailsGeneralResponse_BatchWrites = Json.writes[BeneficiaryDetailsGeneralResponse_Batch]
      implicit val MemberDetailsGeneralResponse_BatchDataWrites = Json.writes[MemberDetailsGeneralResponse_BatchData]
      implicit val MemberDetailsGeneralResponseWrites = Json.writes[MemberDetailsGeneralResponse]

      if (myMemberDetailsGeneralResponse.isEmpty == true || myMemberDetailsGeneralResponse == true){
        //var myMemberDetailsGeneralResponse_Batch = new MemberDetailsGeneralResponse_Batch(0, "", 0, "", "", responseCode, responseMessage)
        val myMemberDetailsGeneralResponse_Batch = new MemberDetailsGeneralResponse_Batch(0, "", 0, "", "", responseCode, responseMessage)
        var myMemberDetailsGeneralResponse_BatchData = new MemberDetailsGeneralResponse_BatchData(myMemberDetailsGeneralResponse_Batch, Seq.empty[MemberBalanceDetailsGeneralResponse_Batch], Seq.empty[BeneficiaryDetailsGeneralResponse_Batch])
        myMemberDetailsGeneralResponse = myMemberDetailsGeneralResponse :+ myMemberDetailsGeneralResponse_BatchData
      }

      val myMemberDetailsResponse = new MemberDetailsGeneralResponse(myMemberDetailsGeneralResponse)

      val jsonResponse = Json.toJson(myMemberDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def validateMemberDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myMemberDetailsValidateResponse_BatchData : Seq[MemberDetailsValidateResponse_Batch] = Seq.empty[MemberDetailsValidateResponse_Batch]
      val strApifunction : String = "validateMemberDetails"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true

                if (responseCode == null){
                  responseCode = 1
                }

                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }

              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberDetailsValidate_Request_Reads: Reads[MemberDetailsValidate_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "idno").read[JsValue] and
                (JsPath \ "phoneno").read[JsValue]
              )(MemberDetailsValidate_Request.apply _)

            implicit val MemberDetailsValidate_BatchRequest_Reads: Reads[MemberDetailsValidate_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberDetailsValidate_Request]]
              )(MemberDetailsValidate_BatchRequest.apply _)

            myjson.validate[MemberDetailsValidate_BatchRequest] match {
              case JsSuccess(myMemberDetailsValidate_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myMemberDetailsValidate_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("OriginalMemberNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("IDNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PhoneNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)


                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""
                    var myIDNo : BigDecimal = 0
                    var strIDNo : String = ""
                    var strPhoneNo : String = ""
                    //var strChannelType : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myMemberDetailsValidate_BatchRequest.memberdata.foreach(myMemberDetails => {

                      myMemberNo = 0
                      myIDNo = 0
                      strMemberNo = ""
                      strIDNo = ""
                      strPhoneNo = ""

                      try{
                        //strMemberNo
                        strMemberNo = myMemberDetails.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }

                        //strIDNo
                        strIDNo = myMemberDetails.idno.toString()
                        if (strIDNo != null && strIDNo != None){
                          strIDNo = strIDNo.trim
                          if (strIDNo.length > 0){
                            strIDNo = strIDNo.replace("'","")//Remove apostrophe
                            strIDNo = strIDNo.replace(" ","")//Remove spaces
                            strIDNo = strIDNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strIDNo = strIDNo.trim
                            val isNumeric : Boolean = strIDNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myIDNo = strIDNo.toDouble
                            }
                          }
                        }


                        //strPhoneNo
                        strPhoneNo = myMemberDetails.phoneno.toString()
                        if (strPhoneNo != null && strPhoneNo != None){
                          strPhoneNo = strPhoneNo.trim
                          if (strPhoneNo.length > 0){
                            strPhoneNo = strPhoneNo.replace("'","")//Remove apostrophe
                            strPhoneNo = strPhoneNo.replace(" ","")//Remove spaces
                            strPhoneNo = strPhoneNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strPhoneNo = strPhoneNo.trim
                          }
                        }


                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, strMemberNo, myIDNo, strPhoneNo, myBatchReference, myBatchSize, strRequestData, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.validateMemberDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  var myresponseCode = resultSet.getInt("responseCode")
                                  var myresponseMessage = resultSet.getString("responseMessage")
                                  try{
                                    //If mymemberNo is not found in local DB, then check in CBS
                                    //println("Step 1")
                                    if (myresponseCode != 0 && mymemberNo > 0){
                                      //println("Step 2")
                                      var iDNo : java.math.BigDecimal =  new java.math.BigDecimal(0)
                                      var phoneNo : String =  ""

                                      iDNo = resultSet.getBigDecimal("iDNo")
                                      phoneNo =  resultSet.getString("phoneNo")
                                      if (phoneNo != null){
                                        phoneNo = phoneNo.trim
                                        if (phoneNo.length > 0){
                                          phoneNo = phoneNo.replace(" ","")
                                          phoneNo = phoneNo.trim
                                        }
                                      }
                                      else{
                                        phoneNo = ""
                                      }
                                      //println("Step 3 - " + mymemberNo.toString + ", iDNo - " + iDNo.toString + ", phoneNo - " + phoneNo.toString)
                                      if (iDNo.signum() > 0 && iDNo.toString.length >= 7 && phoneNo.length >= 10){
                                        //println("Step 4")
                                        var respCode: Int = 1
                                        val memberNo : java.math.BigDecimal =  new java.math.BigDecimal(mymemberNo)
                                        respCode = getMemberDetailsCbs(memberNo, iDNo, phoneNo)
                                        //println("Step 5 - " + respCode.toString)
                                        if (respCode == 0){
                                          myresponseCode = respCode
                                          myresponseMessage = "Successful"
                                        }
                                      }
                                    }
                                  }
                                  catch{
                                    case io: Throwable =>
                                      Log_errors(strApifunction + " : " + io.getMessage())
                                    case ex: Exception =>
                                      Log_errors(strApifunction + " : " + ex.getMessage())
                                  }
                                  val myMemberDetailsValidateResponse_Batch = new MemberDetailsValidateResponse_Batch(mymemberNo, myresponseCode, myresponseMessage)
                                  myMemberDetailsValidateResponse_BatchData  = myMemberDetailsValidateResponse_BatchData :+ myMemberDetailsValidateResponse_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MemberDetailsValidateResponse_BatchWrites = Json.writes[MemberDetailsValidateResponse_Batch]
      implicit val MemberDetailsValidateResponse_BatchDataWrites = Json.writes[MemberDetailsValidateResponse_BatchData]

      if (myMemberDetailsValidateResponse_BatchData.isEmpty == true || myMemberDetailsValidateResponse_BatchData == true){
        val myMemberDetailsValidateResponse_Batch = new MemberDetailsValidateResponse_Batch(0, responseCode, responseMessage)
        myMemberDetailsValidateResponse_BatchData  = myMemberDetailsValidateResponse_BatchData :+ myMemberDetailsValidateResponse_Batch
      }

      val myMemberDetailsResponse = new MemberDetailsValidateResponse_BatchData(myMemberDetailsValidateResponse_BatchData)

      val jsonResponse = Json.toJson(myMemberDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def insertMemberDetailsRegistered = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myMemberDetailsRegisteredResponse_BatchData : Seq[MemberDetailsRegisteredResponse_Batch] = Seq.empty[MemberDetailsRegisteredResponse_Batch]
      val strApifunction : String = "addmemberdetailsregistered"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true

                if (responseCode == null){
                  responseCode = 1
                }

                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }

              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberDetailsRegistered_Request_Reads: Reads[MemberDetailsRegistered_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "idno").readNullable[JsValue] and
                (JsPath \ "phoneno").readNullable[JsValue] and
                (JsPath \ "channeltype").readNullable[JsValue]
              )(MemberDetailsRegistered_Request.apply _)

            implicit val MemberDetailsRegistered_BatchRequest_Reads: Reads[MemberDetailsRegistered_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberDetailsRegistered_Request]]
              )(MemberDetailsRegistered_BatchRequest.apply _)

            myjson.validate[MemberDetailsRegistered_BatchRequest] match {
              case JsSuccess(myMemberDetailsRegistered_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myMemberDetailsRegistered_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("IDNo", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PhoneNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("ChannelType", java.sql.Types.VARCHAR)


                    var myMemberNo : BigDecimal = 0
                    var strMemberNo : String = ""
                    var myIDNo : BigDecimal = 0
                    var strIDNo : String = ""
                    var strPhoneNo : String = ""
                    var strChannelType : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myMemberDetailsRegistered_BatchRequest.memberdata.foreach(myMemberDetails => {

                      myMemberNo = 0
                      myIDNo = 0
                      strMemberNo = ""
                      strIDNo = ""
                      strPhoneNo = ""
                      strChannelType = ""

                      try{
                        //strMemberNo
                        strMemberNo = myMemberDetails.memberno.toString()
                        if (strMemberNo != null && strMemberNo != None){
                          strMemberNo = strMemberNo.trim
                          if (strMemberNo.length > 0){
                            strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                            strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                            strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMemberNo = strMemberNo.trim
                            val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myMemberNo = strMemberNo.toDouble
                            }
                          }
                        }

                        //strIDNo
                        if (myMemberDetails.idno != None) {
                          if (myMemberDetails.idno.get != None) {
                            val myData = myMemberDetails.idno.get
                            strIDNo = myData.toString()
                            if (strIDNo != null && strIDNo != None){
                              strIDNo = strIDNo.trim
                              if (strIDNo.length > 0){
                                strIDNo = strIDNo.replace("'","")//Remove apostrophe
                                strIDNo = strIDNo.replace(" ","")//Remove spaces
                                strIDNo = strIDNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strIDNo = strIDNo.trim
                                val isNumeric : Boolean = strIDNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myIDNo = strIDNo.toDouble
                                }
                              }
                            }
                          }
                        }

                        //strPhoneNo
                        if (myMemberDetails.phoneno != None) {
                          if (myMemberDetails.phoneno.get != None) {
                            val myData = myMemberDetails.phoneno.get
                            strPhoneNo = myData.toString()
                            if (strPhoneNo != null && strPhoneNo != None){
                              strPhoneNo = strPhoneNo.trim
                              if (strPhoneNo.length > 0){
                                strPhoneNo = strPhoneNo.replace("'","")//Remove apostrophe
                                strPhoneNo = strPhoneNo.replace(" ","")//Remove spaces
                                strPhoneNo = strPhoneNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPhoneNo = strPhoneNo.trim
                              }
                            }
                          }
                        }

                        //strChannelType
                        if (myMemberDetails.channeltype != None) {
                          if (myMemberDetails.channeltype.get != None) {
                            val myData = myMemberDetails.channeltype.get
                            strChannelType = myData.toString()
                            if (strChannelType != null && strChannelType != None){
                              strChannelType = strChannelType.trim
                              if (strChannelType.length > 0){
                                strChannelType = strChannelType.replace("'","")//Remove apostrophe
                                strChannelType = strChannelType.replace(" ","")//Remove spaces
                                strChannelType = strChannelType.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strChannelType = strChannelType.trim
                              }
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                      if (isValidInputData == false){
                        if (strMemberNo.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myMemberNo, myIDNo, strPhoneNo, strChannelType)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.insertMemberDetailsChannelRegisteredBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mymemberNo = resultSet.getInt("memberNo")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  val myMemberDetailsRegisteredResponse_Batch = new MemberDetailsRegisteredResponse_Batch(mymemberNo, myresponseCode, myresponseMessage)
                                  myMemberDetailsRegisteredResponse_BatchData  = myMemberDetailsRegisteredResponse_BatchData :+ myMemberDetailsRegisteredResponse_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MemberDetailsRegisteredResponse_BatchWrites = Json.writes[MemberDetailsRegisteredResponse_Batch]
      implicit val MemberDetailsRegisteredResponse_BatchDataWrites = Json.writes[MemberDetailsRegisteredResponse_BatchData]

      if (myMemberDetailsRegisteredResponse_BatchData.isEmpty == true || myMemberDetailsRegisteredResponse_BatchData == true){
        val myMemberDetailsRegisteredResponse_Batch = new MemberDetailsRegisteredResponse_Batch(0, responseCode, responseMessage)
        myMemberDetailsRegisteredResponse_BatchData  = myMemberDetailsRegisteredResponse_BatchData :+ myMemberDetailsRegisteredResponse_Batch
      }

      val myMemberDetailsResponse = new MemberDetailsRegisteredResponse_BatchData(myMemberDetailsRegisteredResponse_BatchData)

      val jsonResponse = Json.toJson(myMemberDetailsResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def getMemberProjectionBenefitsDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var memberNo: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      //var myMemberProjectionBenefitsResponse_BatchData : Seq[MemberProjectionBenefitsResponse_Batch] = Seq.empty[MemberProjectionBenefitsResponse_Batch]
      val strApifunction : String = "getMemberProjectionBenefitsDetails"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strChannelType : String = ""
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          if (request.headers.get("ChannelType") != None){
            val myheaderChannelType = request.headers.get("ChannelType")
            if (myheaderChannelType.get != None){
              strChannelType = myheaderChannelType.get.toString
              if (strChannelType != null){
                strChannelType = strChannelType.trim
              }
              else{
                strChannelType = ""
              }
            }
          }

        }
        else {
          strRequest = "Invalid Request Data"
        }

        //Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        //Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)
        Log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":") == true){
                  val myArray = myAuthToken.toString.split(":")

                  if (myArray.length == 2){
                    strUserName = myArray{0}
                    strPassword = myArray{1}
                    if (strUserName != null && strPassword != null){
                      strUserName = strUserName.trim
                      strPassword = strPassword.trim
                      if (strUserName.length > 0 && strPassword.length > 0){
                        strUserName = strUserName.replace("'","")//Remove apostrophe
                        strUserName = strUserName.replace(" ","")//Remove spaces

                        strPassword = strPassword.replace("'","")//Remove apostrophe
                        strPassword = strPassword.replace(" ","")//Remove spaces

                        isCredentialsFound = true
                        //Lets encrypt the password using base64
                        val strEncryptedPassword: String = Base64.getEncoder.encodeToString(strPassword.getBytes)
                        strPassword = strEncryptedPassword
                      }
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strChannelType)
                mystmt.setString(2,strUserName)
                mystmt.setString(3,strPassword)
                mystmt.setString(4,strClientIP)
                mystmt.setString(5,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                isProcessed = true

                if (responseCode == null){
                  responseCode = 1
                }

                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }

              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MemberProjectionBenefits_Request_Reads: Reads[MemberProjectionBenefits_Request] = (
              (JsPath \ "memberno").read[JsValue] and
                (JsPath \ "membertype").read[JsValue] and
                (JsPath \ "projectiontype").read[JsValue]
              )(MemberProjectionBenefits_Request.apply _)

            /*
            implicit val MemberProjectionBenefits_BatchRequest_Reads: Reads[MemberProjectionBenefits_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "memberdata").read[Seq[MemberProjectionBenefits_Request]]
              )(MemberProjectionBenefits_BatchRequest.apply _)
            */

            //myjson.validate[MemberProjectionBenefits_BatchRequest] match {
            //case JsSuccess(myMemberProjectionBenefits_BatchRequest, _) => {
            myjson.validate[MemberProjectionBenefits_Request] match {
              case JsSuccess(myMemberProjectionBenefits_Request, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  //myBatchSize = myMemberProjectionBenefits_BatchRequest.memberdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    var myMemberNo : Int = 0
                    var strMemberNo : String = ""
                    var strMemberType : String = ""
                    var strProjectionType : String = ""
                    var myProjectionType : Int = 5
                    var strChannelType : String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    //myMemberProjectionBenefits_BatchRequest.memberdata.foreach(myMemberDetails => {

                    myMemberNo = 0
                    myProjectionType = 5
                    strMemberNo = ""
                    strMemberType = ""
                    strProjectionType = ""

                    try{
                      //strMemberNo
                      //strMemberNo = myMemberDetails.memberno.toString()
                      strMemberNo = myMemberProjectionBenefits_Request.memberno.toString()
                      if (strMemberNo != null && strMemberNo != None){
                        strMemberNo = strMemberNo.trim
                        if (strMemberNo.length > 0){
                          strMemberNo = strMemberNo.replace("'","")//Remove apostrophe
                          strMemberNo = strMemberNo.replace(" ","")//Remove spaces
                          strMemberNo = strMemberNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                          strMemberNo = strMemberNo.trim
                          val isNumeric : Boolean = strMemberNo.toString.matches("[0-9]+")//"\\d+", //[0-9]
                          if (isNumeric == true){
                            myMemberNo = strMemberNo.toInt
                            memberNo = myMemberNo //memberNo is a class var
                          }
                        }
                      }

                      //strMemberType
                      //strMemberType = myMemberDetails.membertype.toString()
                      strMemberType = myMemberProjectionBenefits_Request.membertype.toString()
                      if (strMemberType != null && strMemberType != None){
                        strMemberType = strMemberType.trim
                        if (strMemberType.length > 0){
                          strMemberType = strMemberType.replace("'","")//Remove apostrophe
                          strMemberType = strMemberType.replace(" ","")//Remove spaces
                          strMemberType = strMemberType.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                          strMemberType = strMemberType.trim
                        }
                      }

                      //strProjectionType
                      //strProjectionType = myMemberDetails.projectiontype.toString()
                      strProjectionType = myMemberProjectionBenefits_Request.projectiontype.toString()
                      if (strProjectionType != null && strProjectionType != None){
                        strProjectionType = strProjectionType.trim
                        if (strProjectionType.length > 0){
                          strProjectionType = strProjectionType.replace("'","")//Remove apostrophe
                          strProjectionType = strProjectionType.replace(" ","")//Remove spaces
                          strProjectionType = strProjectionType.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                          strProjectionType = strProjectionType.trim
                          val isNumeric : Boolean = strProjectionType.toString.matches("[0-9]+")//"\\d+", //[0-9]
                          if (isNumeric == true){
                            myProjectionType = strProjectionType.toInt
                          }
                        }
                      }


                    }
                    catch {
                      case io: Throwable =>
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }

                    /* Lets set var isValidInputData to true if valid data is received from ECHANNELS */
                    if (isValidInputData == false){
                      if (myMemberNo > 0){
                        isValidInputData = true
                      }
                    }

                    //})

                    try{
                      if (isValidInputData == true){
                        //myDB.withConnection { implicit  myconn =>

                        try {

                          var isValidProjectionType: Boolean = false
                          var isValidMemberType: Boolean = false
                          var isValidMemberId: Boolean = false
                          var myMemberId : Int = 0

                          strMemberType = strMemberType.trim
                          if (strMemberType.length > 0){
                            strMemberType = strMemberType.toUpperCase
                            if (strMemberType.equals("DB") || strMemberType.equals("DC")){
                              isValidMemberType = true
                            }
                          }

                          if (myMemberNo > 0 && isValidMemberType == true){
                            //function to get myMemberId
                            myMemberId = getMemberId(myMemberNo, strMemberType)
                            if (myMemberId > 0){
                              isValidMemberId = true
                            }
                          }

                          if (myProjectionType == 0 || myProjectionType == 1){
                            isValidProjectionType = true
                          }

                          isProcessed = true
                          //myProjectionType. 0: "Retirements Reduced", 1: "Retirements unReduced"
                          if (isValidMemberId == true && isValidMemberType == true && isValidProjectionType == true){
                            responseCode = 0
                            responseMessage = "Successful"
                            //val f = Future {sendProjectionBenefitsRequestsCbs(17274, "DC", "0", "HTTP://")}
                            //val f = Future {sendProjectionBenefitsRequestsCbs(myMemberNo, strMemberType, myProjectionType)}
                            //val strVal : String =  new SimpleDateFormat("ddMMyyyyHHmmssSSS").format(new java.util.Date)
                            val f = Future {sendProjectionBenefitsRequestsCbs(myMemberNo, myMemberId, myProjectionType)}
                          }
                          else{
                            responseCode = 1
                            responseMessage = "Input parameters have invalid values"
                            if (isValidMemberType == false){
                              responseMessage = "member type has an invalid value"
                            }
                            else if (isValidMemberId == false){
                              responseMessage = "Member id does not exist for the given Member no"
                            }
                            else if (isValidProjectionType == false){
                              responseMessage = "projection type has an invalid value"
                            }
                          }

                        }
                        catch{
                          case io: IOException =>
                            //io.printStackTrace() isValidMemberId = true
                            responseMessage = "Error occured during processing, please try again."
                            //println(io.printStackTrace())
                            entryID = 2
                            Log_errors(strApifunction + " : " + io.getMessage())
                          //strErrorMsg = io.toString
                          case ex: Exception =>
                            //ex.printStackTrace()
                            responseMessage = "Error occured during processing, please try again."
                            //println(ex.printStackTrace())
                            entryID = 3
                            Log_errors(strApifunction + " : " + ex.getMessage())
                        }
                        //}
                      }

                      else{

                        responseMessage = "Invalid Input Data length"

                        if (myMemberNo == 0){
                          responseMessage = "Member number has an invalid value"
                        }
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      /*
      implicit val MemberProjectionBenefitsResponse_BatchWrites = Json.writes[MemberProjectionBenefitsResponse_Batch]
      implicit val MemberProjectionBenefitsResponse_BatchDataWrites = Json.writes[MemberProjectionBenefitsResponse_BatchData]

      if (myMemberProjectionBenefitsResponse_BatchData.isEmpty == true || myMemberProjectionBenefitsResponse_BatchData == true){
        val myMemberProjectionBenefitsResponse_Batch = new MemberProjectionBenefitsResponse_Batch(0, responseCode, responseMessage)
        myMemberProjectionBenefitsResponse_BatchData  = myMemberProjectionBenefitsResponse_BatchData :+ myMemberProjectionBenefitsResponse_Batch
      }

      val myMemberDetailsResponse = new MemberProjectionBenefitsResponse_BatchData(myMemberProjectionBenefitsResponse_BatchData)
      */
      implicit val MemberProjectionBenefitsResponse_BatchWrites = Json.writes[MemberProjectionBenefitsResponse_Batch]

      val myProjectionBenefits_Response = new MemberProjectionBenefitsResponse_Batch(memberNo, responseCode, responseMessage)
      val jsonResponse = Json.toJson(myProjectionBenefits_Response)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def ProcessAgentDeclarations = Action.async { request =>
    Future {
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var totalMpesa : Float = 0
      var totalDeclarations : Float = 0
      val strApifunction : String = "ProcessAgentDeclarations"

      try
      {

        var strTitle : String  = ""
        var strInitials : String  = ""
        var strFirst_Name : String  = ""
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }

          }
        }
        else {
          strRequest = "Invalid Request Data"
        }

        Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            //var myAuthToken : String = Base64.getDecoder.decode(strAuthToken).toString
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            Log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              //Log_data("ProcessAgentDeclarations : myAuthToken - " + myAuthToken + " , strAuthToken - " + strAuthToken)

              if (myAuthToken.length > 0){
                val myArray = myAuthToken.toString.split("-")

                //Log_data("ProcessAgentDeclarations : myAuthToken - " + myAuthToken + " , strAuthToken - " + strAuthToken + " , Length - " + myArray.length + " , myArray{0} - " + myArray{0} + " , myArray{1} - " + myArray{1})

                if (myArray.length == 2){
                  strUserName = myArray{0}
                  strPassword = myArray{1}
                  if (strUserName != null && strPassword != null){
                    strUserName = strUserName.trim
                    strPassword = strPassword.trim
                    if (strUserName.length > 0 && strPassword.length > 0){
                      strUserName = strUserName.replace("'","")//Remove apostrophe
                      strUserName = strUserName.replace(" ","")//Remove spaces

                      strPassword = strPassword.replace("'","")//Remove apostrophe
                      strPassword = strPassword.replace(" ","")//Remove spaces

                      isCredentialsFound = true
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : "+ ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              //Log_data("ValidateMpesaTransaction : strUserName - " + strUserName + " , strPassword - " + strPassword + " , strClientIP - " + strClientIP + " , strApifunction - " + strApifunction)
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateCmsAPI(?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strUserName)
                mystmt.setString(2,strPassword)
                mystmt.setString(3,strClientIP)
                mystmt.setString(4,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                isProcessed = true
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")

              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val CMSDeclarations_Request_Reads: Reads[CMSDeclarations_Request] = (
              (JsPath \ "CertNo").read[JsValue] and
                (JsPath \ "DateIssued").read[JsValue] and
                (JsPath \ "AgencyCode").read[JsValue] and
                (JsPath \ "AgencyName").read[JsValue] and
                (JsPath \ "InsuredName").read[JsValue] and
                (JsPath \ "PolicyNo").read[JsValue] and
                (JsPath \ "InsurancePeriodFrom").read[JsValue] and
                (JsPath \ "InsurancePeriodTo").read[JsValue] and
                (JsPath \ "VehicleNo").read[JsValue] and
                (JsPath \ "MobileNo").read[JsValue] and
                (JsPath \ "Active").read[JsValue] and
                (JsPath \ "SumInsured").read[JsValue] and
                (JsPath \ "PremiumPayable").read[JsValue] and
                (JsPath \ "PremiumPaid").read[JsValue] and
                (JsPath \ "PaymentReference").read[JsValue]

              )(CMSDeclarations_Request.apply _)

            implicit val CMSDeclarations_BatchRequest_Reads: Reads[CMSDeclarations_BatchRequest] = (
              (JsPath \ "BatchNo").read[JsValue] and
                (JsPath \ "DeclarationsData").read[Seq[CMSDeclarations_Request]]
              )(CMSDeclarations_BatchRequest.apply _)

            myjson.validate[CMSDeclarations_BatchRequest] match {
              case JsSuccess(myCMSDeclarations_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var is_activated : Boolean = false
                var is_locked : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                //var myBatchReference : Long = 0
                //var myBatchReference : BigDecimal = 0
                //var myBatchRef : BigDecimal = 0
                //val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(0)
                //var myBatchNo : BigDecimal = 0
                //var strBatchNo : String = ""
                //var k : Int = 0
                //var isLastEntry : Boolean = false

                try
                {
                  entryID = 0
                  myBatchSize = myCMSDeclarations_BatchRequest.DeclarationsData.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  //myBatchReference = strBatchReference.toLong String
                  //myBatchReference = strBatchReference.toDouble
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CertNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("DateIssued", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AgencyCode", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("AgencyName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("InsuredName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PolicyNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Insurance_Period_from", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Insurance_Period_to", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("VehicleNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("MobileNo", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Active", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("SumInsured", java.sql.Types.DECIMAL)
                    sourceDataTable.addColumnMetadata("PremiumPayable", java.sql.Types.DECIMAL)
                    sourceDataTable.addColumnMetadata("PremiumPaid", java.sql.Types.DECIMAL)
                    sourceDataTable.addColumnMetadata("PaymentReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("RequestData", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    //sourceDataTable.addColumnMetadata("LastEntry", java.sql.Types.BOOLEAN)

                    var myAgencyCode : BigDecimal = 0
                    var mySumInsured : BigDecimal = 0
                    var myPremiumPayable : BigDecimal = 0
                    var myPremiumPaid : BigDecimal = 0
                    //val isActive : java.lang.Boolean = myCMSDeclarations_Request.Active
                    var isActiveBln : Boolean = true
                    var isActive : java.lang.Integer = 1
                    var strBatchNo: String = ""
                    var strisActive: String = ""
                    var strCertNo: String = ""
                    var strDateIssued: String = ""
                    var strAgencyCode : String = ""
                    var strAgencyName: String = ""
                    var strInsuredName: String = ""
                    var strPolicyNo: String = ""
                    var strInsurancePeriodFrom: String = ""
                    var strInsurancePeriodTo: String = ""
                    var strVehicleNo: String = ""
                    var strMobileNo: String = ""
                    var strPaymentReference: String = ""
                    var strSumInsured: String = ""
                    var strPremiumPayable: String = ""
                    var strPremiumPaid: String = ""

                    strBatchNo = myCMSDeclarations_BatchRequest.BatchNo.toString()
                    if (strBatchNo != null && strBatchNo != None){
                      strBatchNo = strBatchNo.trim
                      if (strBatchNo.length > 0){
                        strBatchNo = strBatchNo.replace("'","")//Remove apostrophe
                        strBatchNo = strBatchNo.replace(" ","")//Remove spaces
                        strBatchNo = strBatchNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                        strBatchNo = strBatchNo.trim
                      }
                    }

                    myCMSDeclarations_BatchRequest.DeclarationsData.foreach(myCMSDeclarations_Request => {
                      /*
                      k = k + 1
                      if (k == myBatchSize){
                        isLastEntry = true
                      }
                      */

                      myAgencyCode = 0
                      mySumInsured = 0
                      myPremiumPayable = 0
                      myPremiumPaid = 0
                      //val isActive : java.lang.Boolean = myCMSDeclarations_Request.Active
                      isActiveBln = true //myCMSDeclarations_Request.Active
                      isActive = 1
                      strisActive = ""
                      strCertNo = ""
                      strDateIssued = ""
                      strAgencyCode = ""
                      strAgencyName = ""
                      strInsuredName = ""
                      strPolicyNo = ""
                      strInsurancePeriodFrom = ""
                      strInsurancePeriodTo = ""
                      strVehicleNo = ""
                      strMobileNo = ""
                      strPaymentReference = ""
                      strSumInsured = ""
                      strPremiumPayable = ""
                      strPremiumPaid = ""

                      try{
                        //strAgencyCode
                        strAgencyCode = myCMSDeclarations_Request.AgencyCode.toString()
                        if (strAgencyCode != null && strAgencyCode != None){
                          strAgencyCode = strAgencyCode.trim
                          if (strAgencyCode.length > 0){
                            strAgencyCode = strAgencyCode.replace("'","")//Remove apostrophe
                            strAgencyCode = strAgencyCode.replace(" ","")//Remove spaces
                            strAgencyCode = strAgencyCode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strAgencyCode = strAgencyCode.trim
                            val isNumeric : Boolean = strAgencyCode.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myAgencyCode = strAgencyCode.toDouble
                            }
                          }
                        }

                        //strisActive
                        strisActive = myCMSDeclarations_Request.Active.toString()
                        if (strisActive != null && strisActive != None){
                          strisActive = strisActive.trim
                          if (strisActive.length > 0){
                            strisActive = strisActive.replace("'","")//Remove apostrophe
                            strisActive = strisActive.replace(" ","")//Remove spaces
                            strisActive = strisActive.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strisActive = strisActive.trim
                            strisActive = strisActive.trim.toLowerCase
                            isActiveBln = strisActive.matches("true")
                            if (isActiveBln == true){
                              isActive = 1
                            }
                            else{
                              isActive = 0
                            }
                          }
                        }

                        //strSumInsured
                        strSumInsured = myCMSDeclarations_Request.SumInsured.toString()
                        if (strSumInsured != null && strSumInsured != None){
                          strSumInsured = strSumInsured.trim
                          if (strSumInsured.length > 0){
                            strSumInsured = strSumInsured.replace("'","")//Remove apostrophe
                            strSumInsured = strSumInsured.replace(" ","")//Remove spaces
                            strSumInsured = strSumInsured.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strSumInsured = strSumInsured.trim
                            val isNumeric : Boolean = strSumInsured.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              mySumInsured = strSumInsured.toDouble
                            }
                          }
                        }

                        //strPremiumPayable
                        strPremiumPayable = myCMSDeclarations_Request.PremiumPayable.toString()
                        if (strPremiumPayable != null && strPremiumPayable != None){
                          strPremiumPayable = strPremiumPayable.trim
                          if (strPremiumPayable.length > 0){
                            strPremiumPayable = strPremiumPayable.replace("'","")//Remove apostrophe
                            strPremiumPayable = strPremiumPayable.replace(" ","")//Remove spaces
                            strPremiumPayable = strPremiumPayable.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strPremiumPayable = strPremiumPayable.trim
                            val isNumeric : Boolean = strPremiumPayable.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myPremiumPayable = strPremiumPayable.toDouble
                            }
                          }
                        }

                        //strPremiumPaid
                        strPremiumPaid = myCMSDeclarations_Request.PremiumPaid.toString()
                        if (strPremiumPaid != null && strPremiumPaid != None){
                          strPremiumPaid = strPremiumPaid.trim
                          if (strPremiumPaid.length > 0){
                            strPremiumPaid = strPremiumPaid.replace("'","")//Remove apostrophe
                            strPremiumPaid = strPremiumPaid.replace(" ","")//Remove spaces
                            strPremiumPaid = strPremiumPaid.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strPremiumPaid = strPremiumPaid.trim
                            val isNumeric : Boolean = strPremiumPaid.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myPremiumPaid = strPremiumPaid.toDouble
                            }
                          }
                        }

                        strCertNo = myCMSDeclarations_Request.CertNo.toString()
                        if (strCertNo != null && strCertNo != None){
                          strCertNo = strCertNo.trim
                          if (strCertNo.length > 0){
                            strCertNo = strCertNo.replace("'","")//Remove apostrophe
                            strCertNo = strCertNo.replace(" ","")//Remove spaces
                            strCertNo = strCertNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strCertNo = strCertNo.trim
                          }
                        }

                        strDateIssued = myCMSDeclarations_Request.DateIssued.toString()
                        if (strDateIssued != null && strDateIssued != None){
                          strDateIssued = strDateIssued.trim
                          if (strDateIssued.length > 0){
                            strDateIssued = strDateIssued.replace("'","")//Remove apostrophe
                            strDateIssued = strDateIssued.replace(" ","")//Remove spaces
                            strDateIssued = strDateIssued.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strDateIssued = strDateIssued.trim
                          }
                        }

                        strAgencyName = myCMSDeclarations_Request.AgencyName.toString()
                        if (strAgencyName != null && strAgencyName != None){
                          strAgencyName = strAgencyName.trim
                          if (strAgencyName.length > 0){
                            strAgencyName = strAgencyName.replace("'","")//Remove apostrophe
                            strAgencyName = strAgencyName.replace("  "," ")//Remove double spaces to just one space
                            strAgencyName = strAgencyName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strAgencyName = strAgencyName.trim
                          }
                        }

                        strInsuredName = myCMSDeclarations_Request.InsuredName.toString()
                        if (strInsuredName != null && strInsuredName != None){
                          strInsuredName = strInsuredName.trim
                          if (strInsuredName.length > 0){
                            strInsuredName = strInsuredName.replace("'","")//Remove apostrophe
                            strInsuredName = strInsuredName.replace("  "," ")//Remove double spaces to just one space
                            strInsuredName = strInsuredName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strInsuredName = strInsuredName.trim
                          }
                        }

                        strPolicyNo = myCMSDeclarations_Request.PolicyNo.toString()
                        if (strPolicyNo != null && strPolicyNo != None){
                          strPolicyNo = strPolicyNo.trim
                          if (strPolicyNo.length > 0){
                            strPolicyNo = strPolicyNo.replace("'","")//Remove apostrophe
                            strPolicyNo = strPolicyNo.replace(" ","")//Remove spaces
                            strPolicyNo = strPolicyNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strPolicyNo = strPolicyNo.trim
                          }
                        }

                        strInsurancePeriodFrom = myCMSDeclarations_Request.InsurancePeriodFrom.toString()
                        if (strInsurancePeriodFrom != null && strInsurancePeriodFrom != None){
                          strInsurancePeriodFrom = strInsurancePeriodFrom.trim
                          if (strInsurancePeriodFrom.length > 0){
                            strInsurancePeriodFrom = strInsurancePeriodFrom.replace("'","")//Remove apostrophe
                            strInsurancePeriodFrom = strInsurancePeriodFrom.replace(" ","")//Remove spaces
                            strInsurancePeriodFrom = strInsurancePeriodFrom.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strInsurancePeriodFrom = strInsurancePeriodFrom.trim
                          }
                        }

                        strInsurancePeriodTo = myCMSDeclarations_Request.InsurancePeriodTo.toString()
                        if (strInsurancePeriodTo != null && strInsurancePeriodTo != None){
                          strInsurancePeriodTo = strInsurancePeriodTo.trim
                          if (strInsurancePeriodTo.length > 0){
                            strInsurancePeriodTo = strInsurancePeriodTo.replace("'","")//Remove apostrophe
                            strInsurancePeriodTo = strInsurancePeriodTo.replace(" ","")//Remove spaces
                            strInsurancePeriodTo = strInsurancePeriodTo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strInsurancePeriodTo = strInsurancePeriodTo.trim
                          }
                        }

                        strVehicleNo = myCMSDeclarations_Request.VehicleNo.toString()
                        if (strVehicleNo != null && strVehicleNo != None){
                          strVehicleNo = strVehicleNo.trim
                          if (strVehicleNo.length > 0){
                            strVehicleNo = strVehicleNo.replace("'","")//Remove apostrophe
                            strVehicleNo = strVehicleNo.replace(" ","")//Remove spaces
                            strVehicleNo = strVehicleNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strVehicleNo = strVehicleNo.trim
                          }
                        }

                        strMobileNo = myCMSDeclarations_Request.MobileNo.toString()
                        if (strMobileNo != null && strMobileNo != None){
                          strMobileNo = strMobileNo.trim
                          if (strMobileNo.length > 0){
                            strMobileNo = strMobileNo.replace("'","")//Remove apostrophe
                            strMobileNo = strMobileNo.replace(" ","")//Remove spaces
                            strMobileNo = strMobileNo.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMobileNo = strMobileNo.trim
                          }
                        }

                        strPaymentReference = myCMSDeclarations_Request.PaymentReference.toString()
                        if (strPaymentReference != null && strPaymentReference != None){
                          strPaymentReference = strPaymentReference.trim
                          if (strPaymentReference.length > 0){
                            strPaymentReference = strPaymentReference.replace("'","")//Remove apostrophe
                            strPaymentReference = strPaymentReference.replace(" ","")//Remove spaces
                            strPaymentReference = strPaymentReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strPaymentReference = strPaymentReference.trim
                          }
                        }

                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /*
                      sourceDataTable.addRow(myCMSDeclarations_BatchRequest.BatchNo,
                        myCMSDeclarations_Request.CertNo,
                        myCMSDeclarations_Request.DateIssued,
                        myAgencyCode,
                        myCMSDeclarations_Request.AgencyName,
                        myCMSDeclarations_Request.InsuredName,
                        myCMSDeclarations_Request.PolicyNo,
                        myCMSDeclarations_Request.InsurancePeriodFrom,
                        myCMSDeclarations_Request.InsurancePeriodTo,
                        myCMSDeclarations_Request.VehicleNo,
                        myCMSDeclarations_Request.MobileNo,
                        //myCMSDeclarations_Request.Active,
                        isActive,
                        mySumInsured,
                        myPremiumPayable,
                        myPremiumPaid,
                        myCMSDeclarations_Request.PaymentReference,
                        strRequest,
                        myBatchSize,
                        myBatchReference)
                        */

                      /* Lets set var isValidInputData to true if valid data is received from CMS */
                      if (isValidInputData == false){
                        if (strBatchNo.length > 0 && strCertNo.length > 0 && myPremiumPaid > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(strBatchNo,
                          strCertNo,
                          strDateIssued,
                          myAgencyCode,
                          strAgencyName,
                          strInsuredName,
                          strPolicyNo,
                          strInsurancePeriodFrom,
                          strInsurancePeriodTo,
                          strVehicleNo,
                          strMobileNo,
                          isActive,
                          mySumInsured,
                          myPremiumPayable,
                          myPremiumPaid,
                          strPaymentReference,
                          strRequest,
                          myBatchSize,
                          myBatchReference)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.InsertIncomingCMSDeclarationsRequests(?,?,?,?,?,?,?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setBigDecimal(1, myBatchReference)
                              mystmt.setObject(2, sourceDataTable)
                              mystmt.setInt(3, myBatchSize)
                              mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                              mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                              mystmt.registerOutParameter("totalDeclarations", java.sql.Types.FLOAT)
                              mystmt.registerOutParameter("totalMpesa", java.sql.Types.FLOAT)
                              mystmt.execute()
                              isProcessed = true
                              responseCode = mystmt.getInt("responseCode")
                              responseMessage = mystmt.getString("responseMessage")
                              totalDeclarations = mystmt.getFloat("totalDeclarations")
                              totalMpesa = mystmt.getFloat("totalMpesa")

                              if (responseCode == null){
                                responseCode = 1
                              }
                              if (responseMessage == null){
                                responseMessage = "Error occured during processing, please try again."
                              }
                              else{
                                if (responseCode !=0 && responseMessage.trim.length == 0){
                                  responseMessage = "Error occured during processing, please try again."
                                }
                              }
                              if (totalDeclarations == null){
                                totalDeclarations = 0
                              }
                              if (totalMpesa == null){
                                totalMpesa = 0
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again." + strBatchNo + " a"
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again." //+ strBatchNo + " b"
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again." //+ strBatchNo + " c"
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again." //+ strBatchNo + " d"
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }

                        }
                      }
                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
        // your scala code here, such as to close a database connection
      }
      /*
      //implicit val requeststatusWrites = Json.writes[requeststatus]
      implicit val CMSDeclarations_ResponseWrites = Json.writes[CMSDeclarations_Response]
      //var myRequeststatus = new requeststatus(1,"failed")
      //var myMpesaTransactionStatus_Response = new MpesaTransactionStatus_Response(1,"failed")
      val myCMSDeclarations_Response = new CMSDeclarations_Response(responseCode,responseMessage,totalMpesa,totalDeclarations)

      val jsonResponse = Json.toJson(myCMSDeclarations_Response)
      val r: Result = Ok(Json.toJson(jsonResponse))
      r
      */

      implicit val CMSDeclarations_ResponseWrites = Json.writes[CMSDeclarations_Response]

      val myCMSDeclarations_Response = new CMSDeclarations_Response(responseCode,responseMessage,totalMpesa,totalDeclarations)
      val jsonResponse = Json.toJson(myCMSDeclarations_Response)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      //val jsonResponse = Json.toJson(myCMSDeclarations_Response)
      //val r: Result = Ok(Json.toJson(jsonResponse))
      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def ValidateBatchMpesaTransactions = Action.async { request =>
    Future {
      var strname : String = ""
      var myage : Int = 0
      var straddress : String = ""
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      //var myMpesaTransactionStatus_BatchResponse : Seq[MpesaTransactionStatus_Batch] = Seq.empty[MpesaTransactionStatus_Batch]
      var myMpesaTransactionStatus_BatchData : Seq[MpesaTransactionStatus_Batch] = Seq.empty[MpesaTransactionStatus_Batch]
      var strApifunction : String = "ValidateBatchMpesaTransactions"

      try
      {

        var strTitle : String  = ""
        var strInitials : String  = ""
        var strFirst_Name : String  = ""
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound : Boolean = false
        var isAuthTokenFound : Boolean = false
        var isCredentialsFound : Boolean = false
        var strUserName : String = ""
        var strPassword : String = ""
        var strClientIP : String = ""

        if (request.body.asJson.isEmpty == false) {
          isDataFound = true
          strRequest = request.body.asJson.get.toString()
          if (request.remoteAddress != null){
            strClientIP = request.remoteAddress
            strClientIP = strClientIP.trim
          }
          if (request.headers.get("Authorization") != None){
            val myheader = request.headers.get("Authorization")
            if (myheader.get != None){
              strRequestHeader = myheader.get.toString
              if (strRequestHeader != null){
                strRequestHeader = strRequestHeader.trim
                if (strRequestHeader.length > 0){
                  if (strRequestHeader.toLowerCase.contains("bearer") == true){
                    val myArray = strRequestHeader.split(" ")

                    if (myArray.length == 2){
                      strAuthToken = myArray{1}
                      if (strAuthToken != null){
                        strAuthToken = strAuthToken.trim
                        if (strAuthToken.length > 0){
                          isAuthTokenFound = true
                        }
                      }
                    }
                  }
                }
              }
            }

          }
        }
        else {
          strRequest = "Invalid Request Data"
        }

        Log_data(strApifunction + " : " + strRequest + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound == true && isAuthTokenFound == true){

          try{
            //var myAuthToken : String = Base64.getDecoder.decode(strAuthToken).toString
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            Log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              //Log_data("ProcessAgentDeclarations : myAuthToken - " + myAuthToken + " , strAuthToken - " + strAuthToken)

              if (myAuthToken.length > 0){
                val myArray = myAuthToken.toString.split("-")

                //Log_data("ProcessAgentDeclarations : myAuthToken - " + myAuthToken + " , strAuthToken - " + strAuthToken + " , Length - " + myArray.length + " , myArray{0} - " + myArray{0} + " , myArray{1} - " + myArray{1})

                if (myArray.length == 2){
                  strUserName = myArray{0}
                  strPassword = myArray{1}
                  if (strUserName != null && strPassword != null){
                    strUserName = strUserName.trim
                    strPassword = strPassword.trim
                    if (strUserName.length > 0 && strPassword.length > 0){
                      strUserName = strUserName.replace("'","")//Remove apostrophe
                      strUserName = strUserName.replace(" ","")//Remove spaces

                      strPassword = strPassword.replace("'","")//Remove apostrophe
                      strPassword = strPassword.replace(" ","")//Remove spaces

                      isCredentialsFound = true
                    }
                  }
                }
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound == true) {
              //Log_data("ValidateMpesaTransaction : strUserName - " + strUserName + " , strPassword - " + strPassword + " , strClientIP - " + strClientIP + " , strApifunction - " + strApifunction)
              myDB.withConnection { implicit  myconn =>

                val strSQL : String = "{ call dbo.ValidateCmsAPI(?,?,?,?,?,?) }"
                val mystmt : CallableStatement = myconn.prepareCall(strSQL)

                mystmt.setString(1,strUserName)
                mystmt.setString(2,strPassword)
                mystmt.setString(3,strClientIP)
                mystmt.setString(4,strApifunction)

                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                isProcessed = true
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
              }
            }
          }
          catch
            {
              case ex: Exception =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                Log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val MpesaTransactionStatus_Reads: Reads[MpesaTransactionStatus_Request] = (
              (JsPath \ "mobileno").read[JsValue] and
                (JsPath \ "transactioncode").read[JsValue] and
                (JsPath \ "amount").read[JsValue]
              )(MpesaTransactionStatus_Request.apply _)

            implicit val MpesaTransactionStatus_BatchRequest_Reads: Reads[MpesaTransactionStatus_BatchRequest] = (
              (JsPath \ "BatchNo").readNullable[Int] and
                (JsPath \ "DeclarationsData").read[Seq[MpesaTransactionStatus_Request]]
              )(MpesaTransactionStatus_BatchRequest.apply _)

            myjson.validate[MpesaTransactionStatus_BatchRequest] match {
              case JsSuccess(myMpesaTransactionStatus_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var is_activated : Boolean = false
                var is_locked : Boolean = false
                //var myBatchSize : Int = 0
                //var strBatchReference : String = ""
                //var myBatchReference : Long = 0
                var k : Int = 0
                var isLastEntry : Boolean = false

                try
                {
                  entryID = 0
                  //myBatchSize = myMpesaTransactionStatus_BatchRequest.DeclarationsData.length
                  //strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  //myBatchReference = strBatchReference.toLong

                  try{

                    //myDB.withConnection { implicit  myconn =>

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("MSISDN", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransID", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransAmount", java.sql.Types.DECIMAL)



                    var strMobileno: String = ""
                    var strTransactioncode: String = ""
                    var strAmnt: String = ""
                    var myAmnt : BigDecimal = 0

                    myMpesaTransactionStatus_BatchRequest.DeclarationsData.foreach(myMpesaTransactionStatus => {

                      strMobileno = ""
                      strTransactioncode = ""
                      strAmnt = ""
                      myAmnt = 0

                      try{
                        //strMobileno
                        strMobileno = myMpesaTransactionStatus.mobileno.toString()
                        if (strMobileno != null && strMobileno != None){
                          strMobileno = strMobileno.trim
                          if (strMobileno.length > 0){
                            strMobileno = strMobileno.replace("'","")//Remove apostrophe
                            strMobileno = strMobileno.replace(" ","")//Remove spaces
                            strMobileno = strMobileno.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strMobileno = strMobileno.trim
                          }
                        }

                        //strTransactioncode
                        strTransactioncode = myMpesaTransactionStatus.transactioncode.toString()
                        if (strTransactioncode != null && strTransactioncode != None){
                          strTransactioncode = strTransactioncode.trim
                          if (strTransactioncode.length > 0){
                            strTransactioncode = strTransactioncode.replace("'","")//Remove apostrophe
                            strTransactioncode = strTransactioncode.replace(" ","")//Remove spaces
                            strTransactioncode = strTransactioncode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strTransactioncode = strTransactioncode.trim
                          }
                        }

                        //strAmnt
                        strAmnt = myMpesaTransactionStatus.amount.toString()
                        if (strAmnt != null && strAmnt != None){
                          strAmnt = strAmnt.trim
                          if (strAmnt.length > 0){
                            strAmnt = strAmnt.replace("'","")//Remove apostrophe
                            strAmnt = strAmnt.replace(" ","")//Remove spaces
                            strAmnt = strAmnt.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                            strAmnt = strAmnt.trim
                            val isNumeric : Boolean = strAmnt.toString.matches("[0-9]+")//"\\d+", //[0-9]
                            if (isNumeric == true){
                              myAmnt = strAmnt.toDouble
                            }
                          }
                        }
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from CMS */
                      if (isValidInputData == false){
                        if (strMobileno.length > 0 && strTransactioncode.length > 0 && myAmnt > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(strMobileno,strTransactioncode,myAmnt)
                      }
                      catch {
                        case io: Throwable =>
                          Log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          Log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.ValidateMpesaTransactionBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            //val cs = myconn.prepareCall("{CALL dbo.ValidateMpesaTransactionBatch (?)}")
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  val mytransactionCode = resultSet.getString("transactionCode")
                                  val myresponseCode = resultSet.getInt("responseCode")
                                  val myresponseMessage = resultSet.getString("responseMessage")
                                  val myMpesaTransactionStatus_Batch = new MpesaTransactionStatus_Batch(mytransactionCode , myresponseCode, myresponseMessage)
                                  myMpesaTransactionStatus_BatchData  = myMpesaTransactionStatus_BatchData :+ myMpesaTransactionStatus_Batch
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                entryID = 2
                                Log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                Log_errors(strApifunction + " : " + ex.getMessage())
                            }
                            //finally if (cs != null) cs.close()
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              Log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        /*
                        if (isValidLength == true){
                          responseMessage = "Invalid Input Data length"
                        }
                        else{
                          if (isValidDate1 == true && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == false && isValidDate2 == true){
                            responseMessage = "Invalid Input Data - wrong start date format. Expected format - " + strDateFormat
                          }
                          else if (isValidDate1 == true && isValidDate2 == false){
                            responseMessage = "Invalid Input Data - wrong stop date format. Expected format - " + strDateFormat
                          }
                          else {
                            responseMessage = "Invalid Input Data - wrong date format. Expected format - " + strDateFormat
                          }
                        }
                        */
                      }
                    }
                    catch {
                      case io: IOException =>
                        //io.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(io.printStackTrace())
                        entryID = 2
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    finally{

                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                      Log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                  // your scala code here, such as to close a database connection
                }
                //strdetail = "Title - " + myIndividualCustomerinfo.Title + " Initials - " +  myIndividualCustomerinfo.Initials + " First_Name - "  + myIndividualCustomerinfo.Gender
                //myconsumer = myIndividualCustomerinfo
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values" //+ myjson.toString()
                Log_errors(strApifunction + " : " + e.toString())
              }
            }
          }

        }
        else {
          if (isDataFound == false) {
            responseMessage = "Invalid Request Data"
          }
          else if (isAuthTokenFound == false) {
            responseMessage = "Invalid Access Token"
          }
          else {
            responseMessage = "Invalid Request Data"
          }
        }

      }
      catch
        {
          case ex: Exception =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false//strname = "no data"//println("Got some other kind of exception")
            responseMessage = "Error occured during processing, please try again."
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val MpesaTransactionStatus_BatchWrites = Json.writes[MpesaTransactionStatus_Batch]
      implicit val MpesaTransactionStatus_BatchDataWrites = Json.writes[MpesaTransactionStatus_BatchData]

      if (myMpesaTransactionStatus_BatchData.isEmpty == true || myMpesaTransactionStatus_BatchData == true){
        val myMpesaTransactionStatus_Batch = new MpesaTransactionStatus_Batch("0" , responseCode,responseMessage)
        myMpesaTransactionStatus_BatchData  = myMpesaTransactionStatus_BatchData :+ myMpesaTransactionStatus_Batch
      }

      val myMpesaTransactionStatus_BatchResponse = new MpesaTransactionStatus_BatchData(myMpesaTransactionStatus_BatchData)

      val jsonResponse = Json.toJson(myMpesaTransactionStatus_BatchResponse)

      try{
        Log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          Log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          Log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def getMemberDetailsCbs(memberNoCbs: java.math.BigDecimal, iDNoCbs: java.math.BigDecimal, phoneNoCbs : String) : Int = {
    /*
    try{
      var isConnected : Boolean = myDataManagement.CheckOracleConnectionStatus

      //Exit processing if Oracle DB is not connected
      if (isConnected == false){
        return
      }
    }catch {
      //case e => e.printStackTrace()
      case t: Throwable => //Log_errors("getIncomingCMSDeclarationsRequests_PostingToGeneSys a : " + t.getMessage + " throwable error occured. ID = " + myID + " , CertNo = " + strCertNo)//strpath_file2
      case ex: Exception => ///Log_errors("getIncomingCMSDeclarationsRequests_PostingToGeneSys a : " + ex.getMessage + " exception error occured. ID = " + myID + " , CertNo = " + strCertNo)//strpath_file2
    }
    */
    val strApifunction : String = "getMemberDetailsCbs"
    var myMemberId : java.math.BigDecimal =  new java.math.BigDecimal(0)
    var responseCode: Int = 1
    try{
      //val sourceDataTable = getMemberDetails_MemberNo(memberNoCbs, iDNoCbs, phoneNoCbs)
      var statuscode : String = ""
      var sourceDataTable : SQLServerDataTable = new SQLServerDataTable
      //println("Step 6 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString)
      /*
      val myResultOutput_Cbs = getMemberDetails_MemberNo(memberNoCbs, iDNoCbs, phoneNoCbs)

      if (myResultOutput_Cbs != null){
        if (myResultOutput_Cbs.statuscode  != null){
          statuscode = myResultOutput_Cbs.statuscode
        }

        if (myResultOutput_Cbs.sourceDataTable  != null){
          sourceDataTable = myResultOutput_Cbs.sourceDataTable
        }

      }
      */
      if (statuscode != null){
        statuscode = statuscode.trim
        if (statuscode.length > 0){
          /*
          if (statuscode.equals("0")){
          }
          */
        }
        else{
          statuscode = "1"
        }
      }
      else{
        statuscode = "1"
      }

      //println("Step 7 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString + ", statuscode - " + statuscode.toString)
      //Lets proceed if both sourceDataTable is not null and the statuscode indicates successful
      if (sourceDataTable != null && statuscode.equals("0")){
        try {
          if (myDB != null){
            myDB.withConnection { implicit  conn =>
              //println("Step 8 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString + ", statuscode - " + statuscode.toString)
              try {
                //var responseCode: Int = 1
                var responseMessage: String = ""
                val strSQL = "{ call dbo.insertCbsMemberDetailsBatch_MemberNo(?,?,?,?) }"
                val mystmt = conn.prepareCall(strSQL)
                mystmt.setObject(1, sourceDataTable)
                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.registerOutParameter("memberId", java.sql.Types.NUMERIC)
                mystmt.execute()
                //isProcessed = true
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")
                myMemberId = mystmt.getBigDecimal("memberId")

                if (responseCode == null){
                  responseCode = 1
                }
                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }
                else{
                  if (responseCode !=0 && responseMessage.trim.length == 0){
                    responseMessage = "Error occured during processing, please try again."
                  }
                }

                //println("Step 9 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString + ", responseCode - " + responseCode.toString)
              }
              catch{
                case ex: Exception =>
                  Log_errors(strApifunction + " : " + ex.getMessage())
                case io: IOException =>
                  Log_errors(strApifunction + " : " + io.getMessage())
                case tr: Throwable =>
                  Log_errors(strApifunction + " : " + tr.getMessage())
              }

              try{
                if (responseCode == 0 && myMemberId.signum() > 0){
                  //Lets run this process asychronously
                  val f = Future {getBeneficiaryDetailsCbs(myMemberId)}
                }
              }
              catch{
                case ex: Exception =>
                  Log_errors(strApifunction + " : " + ex.getMessage())
                case io: IOException =>
                  Log_errors(strApifunction + " : " + io.getMessage())
                case tr: Throwable =>
                  Log_errors(strApifunction + " : " + tr.getMessage())
              }
            }
          }

        }catch {
          case ex: Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage())
          case io: IOException =>
            Log_errors(strApifunction + " : " + io.getMessage())
          case tr: Throwable =>
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      }
    }catch {
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
      case io: IOException =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case tr: Throwable =>
        Log_errors(strApifunction + " : " + tr.getMessage())
    }
    return responseCode
  }
  def getMemberBalanceDetailsCbs(memberNoCbs: Int) : ResultOutput_Balances_Cbs = {

    val strApifunction : String = "getMemberBalanceDetailsCbs"
    var myMemberId_Db : Int =  0
    var myMemberId_Dc : Int =  0
    var responseCode: Int = 1
    var isSuccessful: Boolean = false
    var myEe_Db: BigDecimal = 0
    var myEr_Db: BigDecimal = 0
    var myAvc_Db: BigDecimal = 0
    var myTotal_Db: BigDecimal = 0
    var myEe_Dc: BigDecimal = 0
    var myEr_Dc: BigDecimal = 0
    var myAvc_Dc: BigDecimal = 0
    var myTotal_Dc: BigDecimal = 0
    try{
      if (memberNoCbs > 0){
        try {
          if (myDB != null){
            myDB.withConnection { implicit  conn =>
              //println("Step 8 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString + ", statuscode - " + statuscode.toString)
              try {
                //var responseMessage: String = ""
                val strSQL = "{ call dbo.getCbsMemberId(?,?,?) }"
                val mystmt = conn.prepareCall(strSQL)
                mystmt.setInt(1, memberNoCbs)
                mystmt.registerOutParameter("memberId_Db", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("memberId_Dc", java.sql.Types.INTEGER)
                mystmt.execute()
                myMemberId_Db = mystmt.getInt("memberId_Db")
                myMemberId_Dc = mystmt.getInt("memberId_Dc")
                /*
                if (responseCode == null){
                  responseCode = 1
                }
                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }
                else{
                  if (responseCode !=0 && responseMessage.trim.length == 0){
                    responseMessage = "Error occured during processing, please try again."
                  }
                }
                */
                //println("Step 9 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString + ", responseCode - " + responseCode.toString)
              }
              catch{
                case ex: Exception =>
                  Log_errors(strApifunction + " : " + ex.getMessage())
                case io: IOException =>
                  Log_errors(strApifunction + " : " + io.getMessage())
                case tr: Throwable =>
                  Log_errors(strApifunction + " : " + tr.getMessage())
              }

              try{
                if (myMemberId_Db > 0 || myMemberId_Dc > 0){
                  //println("Step 6 - " + memberNoCbs.toString + ", iDNoCbs - " + iDNoCbs.toString + ", phoneNoCbs - " + phoneNoCbs.toString)
                  if (myMemberId_Db > 0) {
                    /*
                    var myEe: BigDecimal = 0
                    var myEr: BigDecimal = 0
                    var myAvc: BigDecimal = 0
                    var myTotal: BigDecimal = 0
                    */

                    val myResultOutput_Cbs = getProvisionalStatementRequestsCbs(memberNoCbs, myMemberId_Db)

                    if (myResultOutput_Cbs != null){
                      if (myResultOutput_Cbs.isSuccessful  != null){
                        isSuccessful = myResultOutput_Cbs.isSuccessful
                      }

                      if (myResultOutput_Cbs.Ee  != null){
                        myEe_Db = myResultOutput_Cbs.Ee
                      }

                      if (myResultOutput_Cbs.Er  != null){
                        myEr_Db = myResultOutput_Cbs.Er
                      }

                      if (myResultOutput_Cbs.Avc  != null){
                        myAvc_Db = myResultOutput_Cbs.Avc
                      }

                      if (myResultOutput_Cbs.Total  != null){
                        myTotal_Db = myResultOutput_Cbs.Total
                      }

                    }
                  }

                  if (myMemberId_Dc > 0) {
                    /*
                    var myEe: BigDecimal = 0
                    var myEr: BigDecimal = 0
                    var myAvc: BigDecimal = 0
                    var myTotal: BigDecimal = 0
                    */
                    //myEe_Db, myEr_Db, myAvc_Db, myTotal_Db, myEe_Dc, myEr_Dc,  myAvc_Dc, myTotal_Dc
                    val myResultOutput_Cbs = getProvisionalStatementRequestsCbs(memberNoCbs, myMemberId_Dc)

                    if (myResultOutput_Cbs != null){
                      if (myResultOutput_Cbs.isSuccessful  != null){
                        isSuccessful = myResultOutput_Cbs.isSuccessful
                      }

                      if (myResultOutput_Cbs.Ee  != null){
                        myEe_Dc = myResultOutput_Cbs.Ee
                      }

                      if (myResultOutput_Cbs.Er  != null){
                        myEr_Dc = myResultOutput_Cbs.Er
                      }

                      if (myResultOutput_Cbs.Avc  != null){
                        myAvc_Dc = myResultOutput_Cbs.Avc
                      }

                      if (myResultOutput_Cbs.Total  != null){
                        myTotal_Dc = myResultOutput_Cbs.Total
                      }
                    }
                  }


                }
              }
              catch{
                case ex: Exception =>
                  Log_errors(strApifunction + " : " + ex.getMessage())
                case io: IOException =>
                  Log_errors(strApifunction + " : " + io.getMessage())
                case tr: Throwable =>
                  Log_errors(strApifunction + " : " + tr.getMessage())
              }
            }
          }

        }catch {
          case ex: Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage())
          case io: IOException =>
            Log_errors(strApifunction + " : " + io.getMessage())
          case tr: Throwable =>
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      }
    }catch {
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
      case io: IOException =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case tr: Throwable =>
        Log_errors(strApifunction + " : " + tr.getMessage())
    }
    //return responseCode
    val myOutput = ResultOutput_Balances_Cbs(isSuccessful, myEe_Db, myEr_Db, myAvc_Db, myTotal_Db, myEe_Dc, myEr_Dc,  myAvc_Dc, myTotal_Dc)
    return myOutput
  }
  def getBeneficiaryDetailsCbs(memberIdCbs: java.math.BigDecimal) : Unit = {
    /*
    try{
      var isConnected : Boolean = myDataManagement.CheckOracleConnectionStatus

      //Exit processing if Oracle DB is not connected
      if (isConnected == false){
        return
      }
    }catch {
      //case e => e.printStackTrace()
      case t: Throwable => //Log_errors("getIncomingCMSDeclarationsRequests_PostingToGeneSys a : " + t.getMessage + " throwable error occured. ID = " + myID + " , CertNo = " + strCertNo)//strpath_file2
      case ex: Exception => ///Log_errors("getIncomingCMSDeclarationsRequests_PostingToGeneSys a : " + ex.getMessage + " exception error occured. ID = " + myID + " , CertNo = " + strCertNo)//strpath_file2
    }
    */
    val strApifunction : String = "getBeneficiaryDetailsCbs"
    try{
      //val sourceDataTable = getBeneficiaryDetails_MemberId(memberIdCbs)
      //if (sourceDataTable != null){
      val sourceDataTable = null
      if (1 == 1){
        try {

          if (myDB != null){
            myDB.withConnection { implicit  conn =>
              try {
                var responseCode: Int = 1
                var responseMessage: String = ""
                val strSQL = "{ call dbo.insertCbsBeneficiaryDetailsBatch_MemberNo(?,?,?) }"
                val mystmt = conn.prepareCall(strSQL)
                mystmt.setObject(1, sourceDataTable)
                mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
                mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
                mystmt.execute()
                //isProcessed = true
                responseCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")

                if (responseCode == null){
                  responseCode = 1
                }
                if (responseMessage == null){
                  responseMessage = "Error occured during processing, please try again."
                }
                else{
                  if (responseCode !=0 && responseMessage.trim.length == 0){
                    responseMessage = "Error occured during processing, please try again."
                  }
                }
              }
              catch{
                case ex: Exception =>
                  Log_errors(strApifunction + " : " + ex.getMessage())
                case io: IOException =>
                  Log_errors(strApifunction + " : " + io.getMessage())
                case tr: Throwable =>
                  Log_errors(strApifunction + " : " + tr.getMessage())
              }
            }
          }

        }catch {
          case ex: Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage())
          case io: IOException =>
            Log_errors(strApifunction + " : " + io.getMessage())
          case tr: Throwable =>
            Log_errors(strApifunction + " : " + tr.getMessage())
        }
      }
    }catch {
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
      case io: IOException =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case tr: Throwable =>
        Log_errors(strApifunction + " : " + tr.getMessage())
    }
  }
  //def sendProjectionBenefitsRequestsCbs(myMemberNo : Int, strMemberType  : String, myProjectionType  : Int): Unit = {
  def sendProjectionBenefitsRequestsCbs(myMemberNo : Int, myMemberId : Int, myProjectionType  : Int): Unit = {
    val strApifunction : String = "sendProjectionBenefitsRequestsCbs"
    var strProjectionType  : String = "RetirementsReduced"
    var strApiURL  : String = ""


    try{
      myProjectionType match {
        case 0 =>
          strProjectionType = "RetirementsReduced"
        case 1 =>
          strProjectionType = "RetirementsUnreduced"
        case _ =>
          strProjectionType = "RetirementsReduced"
      }
    }
    catch {
      case io: Throwable =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
    }


    try{

      strApiURL = ""
      //strApiURL = "http://172.16.109.253:8088/Xi/api/getProjectionsForMember/283632/60/6973/Retirements Reduced"
      //strApiURL = "http://172.16.109.253:8088/Xi/api/getProjectionsForMember/" + myMemberId + "/60/6973/" + strProjectionType
      strApiURL = getCBSProjectionBenefitsURL(myMemberId, myProjectionType)
      if (strApiURL == null){
        strApiURL = ""
      }

      if (strApiURL.trim.length == 0){
        Log_errors(strApifunction + " : Failure in fetching  Api URL - " + strApiURL + " , application error occured.")
        return
      }

    }
    catch {
      case io: Throwable =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
    }

    val myuri : Uri = strApiURL //"http://172.16.109.253:8088/Xi/api/getProjectionsForMember/283632/60/6973/Retirements Reduced"

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myjsonData : String = ""
    //var strDeveloperId: String = ""//strDeveloperId_Verification


    try
    {
      /*
      if (strDeveloperId == null){
        strDeveloperId = "1"
      }

      if (strMemberType != null && strProjectionType != null && strApiURL != null){
        if (myMemberNo > 0 && strMemberType.length > 0 && strProjectionType.length > 0 && strApiURL.trim.length > 0){
          isValidData = true
        }
      }
      */

      if (myMemberNo > 0 && myMemberId > 0){
        isValidData = true
      }
      else{
        Log_errors(strApifunction + " : Failure in fetching  MemberNo - " + myMemberNo + " , MemberId - " + myMemberId)
        return
      }

    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
        case t: Throwable =>
          isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
      }

    /*
    try
    {
      println("start isValidData 1: " + isValidData)
      if (isValidData == true){
        /*
        val myrequest_verification =  myVerificationMessage_BatchData.toJson
        myjsonData = myrequest_verification.toString()
        */
        }
        }
        catch
        {
        case ex: Exception =>
        isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
        case t: Throwable =>
        isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
        }
        finally
        {
        // your scala code here, such as to close a database connection
        }
        */

    try {
      if (isValidData == true) {

        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        var strUserName: String = ""
        var strPassWord: String = ""
        try {
          strUserName = getCbsApiUserName()
          var strPwd: String = getCbsApiPassword() //n6,e$=p8QK\+c^h~
          var myByteAuthToken = Base64.getDecoder.decode(strPwd)
          var myPwd : String = new String(myByteAuthToken, StandardCharsets.UTF_8)
          strPassWord = myPwd
        }
        catch
          {
            case ex: Exception =>
              isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
            case t: Throwable =>
              isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
          }

        if (strUserName == null){
          strUserName = ""
        }

        if (strPassWord == null){
          strPassWord = ""
        }

        if (strUserName.trim.length == 0){
          Log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strUserName + " , application error occured.")
          return
        }

        if (strPassWord.trim.length == 0){
          Log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strPassWord + " , application error occured.")
          return
        }

        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time: Future[String] = Future(start_time_DB)
        val myMember_No: Future[Int] = Future(myMemberNo)
        //TESTS ONLY
        //println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + res.status.intValue())
              if (res.status != None) {
                if (res.status.intValue() == 200) {
                  var isDataExists: Boolean = false
                  var myCount: Int = 0
                  val oldformatter : SimpleDateFormat = new SimpleDateFormat("MMM dd, yyyy")
                  val newFormatter : SimpleDateFormat = new SimpleDateFormat("dd-MM-yyyy")
                  var strid: String = ""
                  var strCalc_date: String = ""
                  var strExit_date: String = ""
                  var strScheme_id: String = ""
                  var strMember_id: String = ""
                  var strExit_reason: String = ""
                  var strExit_age: String = ""
                  var strYears_worked: String = ""
                  var strTotalBenefits: String = ""
                  var strPurchasePrice: String = ""
                  var strAnnualPension: String = ""
                  var strMonthlyPension: String = ""
                  var strTaxOnMonthlyPension: String = ""
                  //var strNetMonthlyPension: String = ""
                  var strCommutedLumpsum: String = ""
                  var strTaxFreeLumpsum: String = ""
                  var strTaxableAmount: String = ""
                  var strWitholdingTax: String = ""
                  var strLiability: String = ""
                  var strLumpsumPayable: String = ""
                  //Integers
                  var myid: Integer = 0
                  var myScheme_id: Integer = 0
                  var myMember_id: Integer = 0
                  var myExit_age: Integer = 0
                  var myYears_worked: BigDecimal = 0
                  var myTotalBenefits: BigDecimal = 0
                  var myPurchasePrice: BigDecimal = 0
                  var myAnnualPension: BigDecimal = 0
                  var myMonthlyPension: BigDecimal = 0
                  var myTaxOnMonthlyPension: BigDecimal = 0
                  var myNetMonthlyPension: BigDecimal = 0
                  var myCommutedLumpsum: BigDecimal = 0
                  var myTaxFreeLumpsum: BigDecimal = 0
                  var myTaxableAmount: BigDecimal = 0
                  var myWitholdingTax: BigDecimal = 0
                  var myLiability: BigDecimal = 0
                  var myLumpsumPayable: BigDecimal = 0
                  var strResponseData: String = ""
                  val strIntRegex: String = "[0-9]+" //Integers only
                  val strDecimalRegex: String = "^[0-9]*\\.?[0-9]+$" //Decimals
                  val myData = Unmarshal(res.entity).to[CbsMessage_ProjectionBenefits_Batch]

                  if (myData != None) {
                    //val strB = myData.value.getOrElse("requestdata")
                    //println("error occured myData.value.get != None 1 : " + strB.toString)
                    //if (myData.value.get != None) {
                    if (myData.value.getOrElse(None) != None) {
                      val myResultCbsMessage_BatchData = myData.value.get
                      if (myResultCbsMessage_BatchData.get != None) {
                        /*
                        val sourceDataTable = new SQLServerDataTable
                        sourceDataTable.addColumnMetadata("StaffNo", java.sql.Types.INTEGER)
                        sourceDataTable.addColumnMetadata("Pensioner_Identifier", java.sql.Types.VARCHAR)
                        sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                        sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                        sourceDataTable.addColumnMetadata("Verified_Previous_Cycle", java.sql.Types.INTEGER)
                        sourceDataTable.addColumnMetadata("Verified_Cycle_Return_Date", java.sql.Types.VARCHAR)
                        sourceDataTable.addColumnMetadata("Previous_Cycle_id", java.sql.Types.NUMERIC)
                        sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                        sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                        */

                        if (myResultCbsMessage_BatchData.get != None) {
                          strResponseData = myResultCbsMessage_BatchData.toString
                        }

                        var start_time_DB: String = ""
                        //new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var stop_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var memberNo: Int = 0

                        if (myStart_time.value.isEmpty != true) {
                          if (myStart_time.value.get != None) {
                            val myVal = myStart_time.value.get
                            if (myVal.get != None) {
                              start_time_DB = myVal.get
                            }
                          }
                        }

                        if (myMember_No.value.isEmpty != true) {
                          if (myMember_No.value.get != None) {
                            val myVal = myMember_No.value.get
                            if (myVal.get != None) {
                              memberNo = myVal.get
                            }
                          }
                        }

                        if (myResultCbsMessage_BatchData.get.rows != None) {

                          myCount = myResultCbsMessage_BatchData.get.rows.length

                          val myCbsMessageData = myResultCbsMessage_BatchData.get.rows
                          if (myCbsMessageData != None) {
                            myCbsMessageData.foreach(myCbsData => {

                              //strid
                              if (myCbsData.id != None) {
                                if (myCbsData.id.get != None) {
                                  val myData = myCbsData.id.get
                                  strid = myData.toString()
                                  if (strid != null && strid != None){
                                    strid = strid.trim
                                    if (strid.length > 0){
                                      strid = strid.replace("'","")//Remove apostrophe
                                      strid = strid.replace(" ","")//Remove spaces
                                      strid = strid.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strid = strid.trim
                                      //val isNumeric : Boolean = strid.toString.matches("[0-9]+")//"\\d+", //[0-9]
                                      val isNumeric : Boolean = strid.toString.matches(strIntRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myid = strid.toInt
                                      }
                                    }
                                  }
                                }
                              }

                              //strCalc_date
                              if (myCbsData.calc_date != None) {
                                if (myCbsData.calc_date.get != None) {
                                  val myData = myCbsData.calc_date.get
                                  strCalc_date = myData.toString()
                                  if (strCalc_date != null && strCalc_date != None){
                                    strCalc_date = strCalc_date.trim
                                    if (strCalc_date.length > 0){
                                      strCalc_date = strCalc_date.replace("'","")//Remove apostrophe
                                      strCalc_date = strCalc_date.replace("  "," ")//Remove double spaces
                                      strCalc_date = strCalc_date.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strCalc_date = strCalc_date.trim
                                      try{
                                        val myTxnDate : Date = oldformatter.parse(strCalc_date)
                                        //Lets convert var from format "MMM dd, yyyy" to expected date format "dd-MM-yyyy"
                                        strCalc_date = newFormatter.format(myTxnDate)
                                        val strTxnDate: String = newFormatter.format(myTxnDate)
                                        strCalc_date = strTxnDate
                                      }
                                      catch {
                                        case io: Throwable =>
                                          Log_errors(strApifunction + " : " + io.getMessage())
                                        case ex: Exception =>
                                          Log_errors(strApifunction + " : " + ex.getMessage())
                                      }
                                    }
                                  }
                                }
                              }

                              //strExit_date
                              if (myCbsData.exit_date != None) {
                                if (myCbsData.exit_date.get != None) {
                                  val myData = myCbsData.exit_date.get
                                  strExit_date = myData.toString()
                                  if (strExit_date != null && strExit_date != None){
                                    strExit_date = strExit_date.trim
                                    if (strExit_date.length > 0){
                                      strExit_date = strExit_date.replace("'","")//Remove apostrophe
                                      strExit_date = strExit_date.replace("  "," ")//Remove double spaces
                                      strExit_date = strExit_date.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strExit_date = strExit_date.trim
                                      try{
                                        val myTxnDate : Date = oldformatter.parse(strExit_date)
                                        //Lets convert var from format "MMM dd, yyyy" to expected date format "dd-MM-yyyy"
                                        val strTxnDate: String = newFormatter.format(myTxnDate)
                                        strExit_date = strTxnDate
                                      }
                                      catch {
                                        case io: Throwable =>
                                          Log_errors(strApifunction + " : " + io.getMessage())
                                        case ex: Exception =>
                                          Log_errors(strApifunction + " : " + ex.getMessage())
                                      }
                                    }
                                  }
                                }
                              }

                              //strScheme_id
                              if (myCbsData.scheme_id != None) {
                                if (myCbsData.scheme_id.get != None) {
                                  val myData = myCbsData.scheme_id.get
                                  strScheme_id = myData.toString()
                                  if (strScheme_id != null && strScheme_id != None){
                                    strScheme_id = strScheme_id.trim
                                    if (strScheme_id.length > 0){
                                      strScheme_id = strScheme_id.replace("'","")//Remove apostrophe
                                      strScheme_id = strScheme_id.replace(" ","")//Remove spaces
                                      strScheme_id = strScheme_id.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strScheme_id = strScheme_id.trim
                                      //val isNumeric : Boolean = strScheme_id.toString.matches("[0-9]+")//"\\d+", //[0-9]
                                      val isNumeric : Boolean = strScheme_id.toString.matches(strIntRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myScheme_id = strScheme_id.toInt
                                      }
                                    }
                                  }
                                }
                              }

                              //strMember_id
                              if (myCbsData.member_id != None) {
                                if (myCbsData.member_id.get != None) {
                                  val myData = myCbsData.member_id.get
                                  strMember_id = myData.toString()
                                  if (strMember_id != null && strMember_id != None){
                                    strMember_id = strMember_id.trim
                                    if (strMember_id.length > 0){
                                      strMember_id = strMember_id.replace("'","")//Remove apostrophe
                                      strMember_id = strMember_id.replace(" ","")//Remove spaces
                                      strMember_id = strMember_id.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strMember_id = strMember_id.trim
                                      //val isNumeric : Boolean = strMember_id.toString.matches("[0-9]+")//"\\d+", //[0-9]
                                      val isNumeric : Boolean = strMember_id.toString.matches(strIntRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myMember_id = strMember_id.toInt
                                      }
                                    }
                                  }
                                }
                              }

                              //strExit_reason
                              if (myCbsData.exit_reason != None) {
                                if (myCbsData.exit_reason.get != None) {
                                  val myData = myCbsData.exit_reason.get
                                  strExit_reason = myData.toString()
                                  if (strExit_reason != null && strExit_reason != None){
                                    strExit_reason = strExit_reason.trim
                                    if (strExit_reason.length > 0){
                                      strExit_reason = strExit_reason.replace("'","")//Remove apostrophe
                                      strExit_reason = strExit_reason.replace(" ","")//Remove spaces
                                      strExit_reason = strExit_reason.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strExit_reason = strExit_reason.trim
                                    }
                                  }
                                }
                              }

                              //strExit_age
                              if (myCbsData.exit_age != None) {
                                if (myCbsData.exit_age.get != None) {
                                  val myData = myCbsData.exit_age.get
                                  strExit_age = myData.toString()
                                  if (strExit_age != null && strExit_age != None){
                                    strExit_age = strExit_age.trim
                                    if (strExit_age.length > 0){
                                      strExit_age = strExit_age.replace("'","")//Remove apostrophe
                                      strExit_age = strExit_age.replace(" ","")//Remove spaces
                                      strExit_age = strExit_age.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strExit_age = strExit_age.trim
                                      //val isNumeric : Boolean = strExit_age.toString.matches("[0-9]+")//"\\d+", //[0-9]
                                      val isNumeric : Boolean = strExit_age.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        var myExAge: BigDecimal = BigDecimal(strExit_age)
                                        myExAge = myExAge.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                        //myExit_age = strExit_age.toInt
                                        myExit_age = myExAge.toInt
                                      }
                                    }
                                  }
                                }
                              }

                              //strYears_worked
                              if (myCbsData.years_worked != None) {
                                if (myCbsData.years_worked.get != None) {
                                  val myData = myCbsData.years_worked.get
                                  strYears_worked = myData.toString()
                                  if (strYears_worked != null && strYears_worked != None){
                                    strYears_worked = strYears_worked.trim
                                    if (strYears_worked.length > 0){
                                      strYears_worked = strYears_worked.replace("'","")//Remove apostrophe
                                      strYears_worked = strYears_worked.replace(" ","")//Remove spaces
                                      strYears_worked = strYears_worked.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strYears_worked = strYears_worked.trim
                                      //val isNumeric : Boolean = strYears_worked.toString.matches("[0-9]+")//"\\d+", //[0-9]
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myYears_worked = BigDecimal(strYears_worked)
                                        myYears_worked = myYears_worked.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strTotalBenefits
                              if (myCbsData.totalBenefitsDb != None) {
                                if (myCbsData.totalBenefitsDb.get != None) {
                                  val myData = myCbsData.totalBenefitsDb.get
                                  strTotalBenefits = myData.toString()
                                  if (strTotalBenefits != null && strTotalBenefits != None){
                                    strTotalBenefits = strTotalBenefits.trim
                                    if (strTotalBenefits.length > 0){
                                      strTotalBenefits = strTotalBenefits.replace("'","")//Remove apostrophe
                                      strTotalBenefits = strTotalBenefits.replace(" ","")//Remove spaces
                                      strTotalBenefits = strTotalBenefits.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strTotalBenefits = strTotalBenefits.trim
                                      val isNumeric : Boolean = strTotalBenefits.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myTotalBenefits = BigDecimal(strTotalBenefits)
                                        myTotalBenefits = myTotalBenefits.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strPurchasePrice
                              if (myCbsData.purchasePrice != None) {
                                if (myCbsData.purchasePrice.get != None) {
                                  val myData = myCbsData.purchasePrice.get
                                  strPurchasePrice = myData.toString()
                                  if (strPurchasePrice != null && strPurchasePrice != None){
                                    strPurchasePrice = strPurchasePrice.trim
                                    if (strPurchasePrice.length > 0){
                                      strPurchasePrice = strPurchasePrice.replace("'","")//Remove apostrophe
                                      strPurchasePrice = strPurchasePrice.replace(" ","")//Remove spaces
                                      strPurchasePrice = strPurchasePrice.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strPurchasePrice = strPurchasePrice.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myPurchasePrice = BigDecimal(strPurchasePrice)
                                        myPurchasePrice = myPurchasePrice.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strAnnualPension
                              if (myCbsData.annualPension != None) {
                                if (myCbsData.annualPension.get != None) {
                                  val myData = myCbsData.annualPension.get
                                  strAnnualPension = myData.toString()
                                  if (strAnnualPension != null && strAnnualPension != None){
                                    strAnnualPension = strAnnualPension.trim
                                    if (strAnnualPension.length > 0){
                                      strAnnualPension = strAnnualPension.replace("'","")//Remove apostrophe
                                      strAnnualPension = strAnnualPension.replace(" ","")//Remove spaces
                                      strAnnualPension = strAnnualPension.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strAnnualPension = strAnnualPension.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myAnnualPension = BigDecimal(strAnnualPension)
                                        myAnnualPension = myAnnualPension.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strMonthlyPension
                              if (myCbsData.monthlyPension != None) {
                                if (myCbsData.monthlyPension.get != None) {
                                  val myData = myCbsData.monthlyPension.get
                                  strMonthlyPension = myData.toString()
                                  if (strMonthlyPension != null && strMonthlyPension != None){
                                    strMonthlyPension = strMonthlyPension.trim
                                    if (strMonthlyPension.length > 0){
                                      strMonthlyPension = strMonthlyPension.replace("'","")//Remove apostrophe
                                      strMonthlyPension = strMonthlyPension.replace(" ","")//Remove spaces
                                      strMonthlyPension = strMonthlyPension.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strMonthlyPension = strMonthlyPension.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myMonthlyPension = BigDecimal(strMonthlyPension)
                                        myMonthlyPension = myMonthlyPension.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strTaxOnMonthlyPension
                              if (myCbsData.taxOnMonthlyPension != None) {
                                if (myCbsData.taxOnMonthlyPension.get != None) {
                                  val myData = myCbsData.taxOnMonthlyPension.get
                                  strTaxOnMonthlyPension = myData.toString()
                                  if (strTaxOnMonthlyPension != null && strTaxOnMonthlyPension != None){
                                    strTaxOnMonthlyPension = strTaxOnMonthlyPension.trim
                                    if (strTaxOnMonthlyPension.length > 0){
                                      strTaxOnMonthlyPension = strTaxOnMonthlyPension.replace("'","")//Remove apostrophe
                                      strTaxOnMonthlyPension = strTaxOnMonthlyPension.replace(" ","")//Remove spaces
                                      strTaxOnMonthlyPension = strTaxOnMonthlyPension.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strTaxOnMonthlyPension = strTaxOnMonthlyPension.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myTaxOnMonthlyPension = BigDecimal(strTaxOnMonthlyPension)
                                        myTaxOnMonthlyPension = myTaxOnMonthlyPension.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //myNetMonthlyPension
                              myNetMonthlyPension = myMonthlyPension  - myTaxOnMonthlyPension

                              //strCommutedLumpsum
                              if (myCbsData.commutedLumpsum != None) {
                                if (myCbsData.commutedLumpsum.get != None) {
                                  val myData = myCbsData.commutedLumpsum.get
                                  strCommutedLumpsum = myData.toString()
                                  if (strCommutedLumpsum != null && strCommutedLumpsum != None){
                                    strCommutedLumpsum = strCommutedLumpsum.trim
                                    if (strCommutedLumpsum.length > 0){
                                      strCommutedLumpsum = strCommutedLumpsum.replace("'","")//Remove apostrophe
                                      strCommutedLumpsum = strCommutedLumpsum.replace(" ","")//Remove spaces
                                      strCommutedLumpsum = strCommutedLumpsum.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strCommutedLumpsum = strCommutedLumpsum.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myCommutedLumpsum = BigDecimal(strCommutedLumpsum)
                                        myCommutedLumpsum = myCommutedLumpsum.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strTaxFreeLumpsum
                              if (myCbsData.taxFreeLumpsum != None) {
                                if (myCbsData.taxFreeLumpsum.get != None) {
                                  val myData = myCbsData.taxFreeLumpsum.get
                                  strTaxFreeLumpsum = myData.toString()
                                  if (strTaxFreeLumpsum != null && strTaxFreeLumpsum != None){
                                    strTaxFreeLumpsum = strTaxFreeLumpsum.trim
                                    if (strTaxFreeLumpsum.length > 0){
                                      strTaxFreeLumpsum = strTaxFreeLumpsum.replace("'","")//Remove apostrophe
                                      strTaxFreeLumpsum = strTaxFreeLumpsum.replace(" ","")//Remove spaces
                                      strTaxFreeLumpsum = strTaxFreeLumpsum.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strTaxFreeLumpsum = strTaxFreeLumpsum.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myTaxFreeLumpsum = BigDecimal(strTaxFreeLumpsum)
                                        myTaxFreeLumpsum = myTaxFreeLumpsum.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strTaxableAmount
                              if (myCbsData.taxableAmount != None) {
                                if (myCbsData.taxableAmount.get != None) {
                                  val myData = myCbsData.taxableAmount.get
                                  strTaxableAmount = myData.toString()
                                  if (strTaxableAmount != null && strTaxableAmount != None){
                                    strTaxableAmount = strTaxableAmount.trim
                                    if (strTaxableAmount.length > 0){
                                      strTaxableAmount = strTaxableAmount.replace("'","")//Remove apostrophe
                                      strTaxableAmount = strTaxableAmount.replace(" ","")//Remove spaces
                                      strTaxableAmount = strTaxableAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strTaxableAmount = strTaxableAmount.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myTaxableAmount = BigDecimal(strTaxableAmount)
                                        myTaxableAmount = myTaxableAmount.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strWitholdingTax
                              if (myCbsData.witholdingTax != None) {
                                if (myCbsData.witholdingTax.get != None) {
                                  val myData = myCbsData.witholdingTax.get
                                  strWitholdingTax = myData.toString()
                                  if (strWitholdingTax != null && strWitholdingTax != None){
                                    strWitholdingTax = strWitholdingTax.trim
                                    if (strWitholdingTax.length > 0){
                                      strWitholdingTax = strWitholdingTax.replace("'","")//Remove apostrophe
                                      strWitholdingTax = strWitholdingTax.replace(" ","")//Remove spaces
                                      strWitholdingTax = strWitholdingTax.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strWitholdingTax = strWitholdingTax.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myWitholdingTax = BigDecimal(strWitholdingTax)
                                        myWitholdingTax = myWitholdingTax.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strLiability
                              if (myCbsData.liability != None) {
                                if (myCbsData.liability.get != None) {
                                  val myData = myCbsData.liability.get
                                  strLiability = myData.toString()
                                  if (strLiability != null && strLiability != None){
                                    strLiability = strLiability.trim
                                    if (strLiability.length > 0){
                                      strLiability = strLiability.replace("'","")//Remove apostrophe
                                      strLiability = strLiability.replace(" ","")//Remove spaces
                                      strLiability = strLiability.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strLiability = strLiability.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myLiability = BigDecimal(strLiability)
                                        myLiability = myLiability.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strLumpsumPayable
                              if (myCbsData.lumpsumPayable != None) {
                                if (myCbsData.lumpsumPayable.get != None) {
                                  val myData = myCbsData.lumpsumPayable.get
                                  strLumpsumPayable = myData.toString()
                                  if (strLumpsumPayable != null && strLumpsumPayable != None){
                                    strLumpsumPayable = strLumpsumPayable.trim
                                    if (strLumpsumPayable.length > 0){
                                      strLumpsumPayable = strLumpsumPayable.replace("'","")//Remove apostrophe
                                      strLumpsumPayable = strLumpsumPayable.replace(" ","")//Remove spaces
                                      strLumpsumPayable = strLumpsumPayable.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strLumpsumPayable = strLumpsumPayable.trim
                                      val isNumeric : Boolean = strYears_worked.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myLumpsumPayable = BigDecimal(strLumpsumPayable)
                                        myLumpsumPayable = myLumpsumPayable.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //TESTS ONLY
                              val strMessage: String = "myScheme_id - " + myScheme_id + ", myMember_id - " + myMember_id + ", myExit_age - " + myExit_age +
                                ", myYears_worked - " + myYears_worked + ", myTotalBenefits - " + myTotalBenefits + ", myPurchasePrice - " + myPurchasePrice +
                                ", myAnnualPension - " + myAnnualPension + ", myMonthlyPension - " + myMonthlyPension + ", myTaxOnMonthlyPension - " + myTaxOnMonthlyPension +
                                ", myNetMonthlyPension - " + myNetMonthlyPension + ", myCommutedLumpsum - " + myCommutedLumpsum + ", myTaxFreeLumpsum - " + myTaxFreeLumpsum +
                                ", myTaxableAmount - " + myTaxableAmount + ", myWitholdingTax - " + myWitholdingTax + ", myLiability - " + myLiability  +
                                ", myLumpsumPayable - " + myLumpsumPayable
                              Log_data(strApifunction + " : " + strMessage + " - ResponseMessage." + strApifunction)

                              isDataExists = true

                              /*
                              if (myMember_id > 0) {

                                if (isDataExists == false) {
                                  isDataExists = true
                                }

                                sourceDataTable.addRow(myStaffno,
                                  strPensionercode,
                                  myStatuscode,
                                  strStatusmessage,
                                  myVerified_previous_cycle,
                                  strVerified_cycle_return_date,
                                  myPrevious_cycle_id,
                                  start_time_DB,
                                  stop_time_DB
                                )
                              }

                              myStaffno = 0
                              strStaffno  = ""
                              strPensionercode = ""
                              myStatuscode = 1
                              myVerified_previous_cycle = 0
                              strVerified_cycle_return_date = ""
                              myPrevious_cycle_id = 0
                              strStatuscode  = ""
                              strStatusmessage = ""
                              */

                            })
                          }
                        }

                        //val posted_to_Cbs: Boolean = true
                        val posted_to_Cbs: Integer = 1
                        val post_picked_Cbs: Integer = 1
                        val strDate_to_Cbs: String = start_time_DB
                        val strDate_from_Cbs: String = stop_time_DB
                        val myStatusCode_Cbs : Integer = res.status.intValue()
                        val strStatusMessage_Cbs: String = "Successful"
                        //UpdateLogsOutgoingLipaNaMpesaRequests(myTxnID, posted_to_Cbs, strDate_to_Cbs, strDate_from_Cbs, myStatusCode_Cbs, strStatusMessage_Cbs)

                        if (isDataExists == true) {
                          //processUpdatePensionersVerification(sourceDataTable)
                          val myMemberProjectionBenefitsDetailsResponse_Batch = new MemberProjectionBenefitsDetailsResponse_Batch(strCalc_date, strExit_date, strExit_reason, myExit_age, myYears_worked, myTotalBenefits, myPurchasePrice, myAnnualPension, myMonthlyPension, myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum, myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax, myLiability, myLumpsumPayable)
                          //val memberno: Int = 17274
                          val statuscode: Int = 0
                          val statusdescription: String = strStatusMessage_Cbs
                          val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberNo, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                          var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                          try{

                            val sourceDataTable = new SQLServerDataTable
                            sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                            sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
                            sourceDataTable.addColumnMetadata("ProjectionType", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Calc_date", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_date", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_reason", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_age", java.sql.Types.INTEGER)
                            sourceDataTable.addColumnMetadata("Years_worked", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TotalBenefits", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("PurchasePrice", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("AnnualPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("MonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxOnMonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("NetMonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("CommutedLumpsum", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxFreeLumpsum", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxableAmount", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("WitholdingTax", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("Liability", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("LumpsumPayable", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("Posted_to_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                            sourceDataTable.addColumnMetadata("Post_picked_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                            sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                            sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("ResponseData_Cbs", java.sql.Types.VARCHAR)

                            sourceDataTable.addRow(BigDecimal(myMemberNo), BigDecimal(myMemberId), strProjectionType,
                              strCalc_date, strExit_date, strExit_reason,
                              myExit_age, myYears_worked, myTotalBenefits,
                              myPurchasePrice, myAnnualPension, myMonthlyPension,
                              myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum,
                              myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax,
                              myLiability, myLumpsumPayable, posted_to_Cbs,
                              post_picked_Cbs, strDate_to_Cbs, strDate_from_Cbs,
                              myStatusCode_Cbs, strStatusMessage_Cbs, strResponseData
                            )

                            myTxnID = insertEchannelsMemberProjectionBenefitsDetailsRequests(sourceDataTable)
                          }
                          catch {
                            case io: Throwable =>
                              Log_errors(strApifunction + " : " + io.getMessage())
                            case ex: Exception =>
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }

                          //sendProjectionBenefitsResponseEchannel(memberno, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                          //sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData)
                          val f = Future {sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData, myTxnID)}
                        }

                      }
                    }
                    else {
                      //TESTS ONLY
                      //println("error occured myData.value.get != None : " + start_time_DB)
                      //Lets log the status code returned by CBS webservice
                      val myStatusCode : Int = res.status.intValue()
                      val strStatusMessage: String = "Failed"

                      try {

                        //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                        var start_time_DB : String  = ""
                        val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var memberNo: Int = 0
                        /*
                        if (myEntryID.value.isEmpty != true){
                          if (myEntryID.value.get != None){
                            val myVal = myEntryID.value.get
                            if (myVal.get != None){
                              myTxnID = myVal.get
                            }
                          }
                        }
                        */

                        if (myStart_time.value.isEmpty != true){
                          if (myStart_time.value.get != None){
                            val myVal = myStart_time.value.get
                            if (myVal.get != None){
                              start_time_DB = myVal.get
                            }
                          }
                        }

                        if (myMember_No.value.isEmpty != true) {
                          if (myMember_No.value.get != None) {
                            val myVal = myMember_No.value.get
                            if (myVal.get != None) {
                              memberNo = myVal.get
                            }
                          }
                        }

                        val strMessage: String = "member_no - " + myMember_No + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                        Log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None. error occured.")

                        var strCalc_date: String = ""
                        var strExit_date: String = ""
                        var strExit_reason: String = ""

                        //Integers only
                        //var myScheme_id: Integer = 0
                        //var myMember_id: Integer = 0
                        var myExit_age: Integer = 0
                        var myYears_worked: BigDecimal = 0
                        var myTotalBenefits: BigDecimal = 0
                        var myPurchasePrice: BigDecimal = 0
                        var myAnnualPension: BigDecimal = 0
                        var myMonthlyPension: BigDecimal = 0
                        var myTaxOnMonthlyPension: BigDecimal = 0
                        var myNetMonthlyPension: BigDecimal = 0
                        var myCommutedLumpsum: BigDecimal = 0
                        var myTaxFreeLumpsum: BigDecimal = 0
                        var myTaxableAmount: BigDecimal = 0
                        var myWitholdingTax: BigDecimal = 0
                        var myLiability: BigDecimal = 0
                        var myLumpsumPayable: BigDecimal = 0
                        val strResponseData: String = "No Response Data received"

                        //val posted_to_Cbs: Boolean = false
                        val posted_to_Cbs: Integer = 1
                        val post_picked_Cbs: Integer = 1
                        val strDate_to_Cbs: String = start_time_DB
                        val strDate_from_Cbs: String = stop_time_DB
                        val myStatusCode_Cbs : Integer = res.status.intValue()
                        val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"
                        //UpdateLogsOutgoingLipaNaMpesaRequests(myTxnID, posted_to_Mpesa, strDate_to_Mpesa, strDate_from_Mpesa, myStatusCode_Mpesa, strStatusMessage_Mpesa)
                        val myMemberProjectionBenefitsDetailsResponse_Batch = new MemberProjectionBenefitsDetailsResponse_Batch(strCalc_date, strExit_date, strExit_reason, myExit_age, myYears_worked, myTotalBenefits, myPurchasePrice, myAnnualPension, myMonthlyPension, myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum, myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax, myLiability, myLumpsumPayable)
                        val statuscode: Int = 1
                        val statusdescription: String = strStatusMessage_Cbs
                        val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberNo, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                        var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                        //sendProjectionBenefitsResponseEchannel(memberno, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                        //sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData)

                        try{

                          val sourceDataTable = new SQLServerDataTable
                          sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                          sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
                          sourceDataTable.addColumnMetadata("ProjectionType", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("Calc_date", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("Exit_date", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("Exit_reason", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("Exit_age", java.sql.Types.INTEGER)
                          sourceDataTable.addColumnMetadata("Years_worked", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("TotalBenefits", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("PurchasePrice", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("AnnualPension", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("MonthlyPension", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("TaxOnMonthlyPension", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("NetMonthlyPension", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("CommutedLumpsum", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("TaxFreeLumpsum", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("TaxableAmount", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("WitholdingTax", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("Liability", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("LumpsumPayable", java.sql.Types.DECIMAL)
                          sourceDataTable.addColumnMetadata("Posted_to_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                          sourceDataTable.addColumnMetadata("Post_picked_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                          sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                          sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                          sourceDataTable.addColumnMetadata("ResponseData_Cbs", java.sql.Types.VARCHAR)

                          sourceDataTable.addRow(BigDecimal(myMemberNo), BigDecimal(myMemberId), strProjectionType,
                            strCalc_date, strExit_date, strExit_reason,
                            myExit_age, myYears_worked, myTotalBenefits,
                            myPurchasePrice, myAnnualPension, myMonthlyPension,
                            myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum,
                            myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax,
                            myLiability, myLumpsumPayable, posted_to_Cbs,
                            post_picked_Cbs, strDate_to_Cbs, strDate_from_Cbs,
                            myStatusCode_Cbs, strStatusMessage_Cbs, strResponseData
                          )

                          myTxnID = insertEchannelsMemberProjectionBenefitsDetailsRequests(sourceDataTable)
                        }
                        catch {
                          case io: Throwable =>
                            Log_errors(strApifunction + " : " + io.getMessage())
                          case ex: Exception =>
                            Log_errors(strApifunction + " : " + ex.getMessage())
                        }

                        val ftr = Future {sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData, myTxnID)}
                      }
                      catch
                        {
                          case ex: Exception =>
                            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                          case t: Throwable =>
                            Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                        }
                    }
                  }
                }
                else {

                  //Lets log the status code returned by CBS webservice
                  val myStatusCode : Int = res.status.intValue()
                  val strStatusMessage: String = "Failed"

                  try {

                    //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                    var start_time_DB : String  = ""
                    val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                    var memberNo: Int = 0
                    /*
                    if (myEntryID.value.isEmpty != true){
                      if (myEntryID.value.get != None){
                        val myVal = myEntryID.value.get
                        if (myVal.get != None){
                          myTxnID = myVal.get
                        }
                      }
                    }
                    */

                    if (myStart_time.value.isEmpty != true){
                      if (myStart_time.value.get != None){
                        val myVal = myStart_time.value.get
                        if (myVal.get != None){
                          start_time_DB = myVal.get
                        }
                      }
                    }

                    if (myMember_No.value.isEmpty != true) {
                      if (myMember_No.value.get != None) {
                        val myVal = myMember_No.value.get
                        if (myVal.get != None) {
                          memberNo = myVal.get
                        }
                      }
                    }

                    val strMessage: String = "member_no - " + myMember_No + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                    Log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

                    var strCalc_date: String = ""
                    var strExit_date: String = ""
                    var strExit_reason: String = ""

                    //Integers only
                    //var myScheme_id: Integer = 0
                    //var myMember_id: Integer = 0
                    var myExit_age: Integer = 0
                    var myYears_worked: BigDecimal = 0
                    var myTotalBenefits: BigDecimal = 0
                    var myPurchasePrice: BigDecimal = 0
                    var myAnnualPension: BigDecimal = 0
                    var myMonthlyPension: BigDecimal = 0
                    var myTaxOnMonthlyPension: BigDecimal = 0
                    var myNetMonthlyPension: BigDecimal = 0
                    var myCommutedLumpsum: BigDecimal = 0
                    var myTaxFreeLumpsum: BigDecimal = 0
                    var myTaxableAmount: BigDecimal = 0
                    var myWitholdingTax: BigDecimal = 0
                    var myLiability: BigDecimal = 0
                    var myLumpsumPayable: BigDecimal = 0
                    val strResponseData: String = "No Response Data received"

                    //val posted_to_Cbs: Boolean = false
                    val posted_to_Cbs: Integer = 1
                    val post_picked_Cbs: Integer = 1
                    val strDate_to_Cbs: String = start_time_DB
                    val strDate_from_Cbs: String = stop_time_DB
                    val myStatusCode_Cbs : Integer = res.status.intValue()
                    val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"
                    //UpdateLogsOutgoingLipaNaMpesaRequests(myTxnID, posted_to_Mpesa, strDate_to_Mpesa, strDate_from_Mpesa, myStatusCode_Mpesa, strStatusMessage_Mpesa)
                    val myMemberProjectionBenefitsDetailsResponse_Batch = new MemberProjectionBenefitsDetailsResponse_Batch(strCalc_date, strExit_date, strExit_reason, myExit_age, myYears_worked, myTotalBenefits, myPurchasePrice, myAnnualPension, myMonthlyPension, myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum, myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax, myLiability, myLumpsumPayable)
                    val statuscode: Int = 1
                    val statusdescription: String = strStatusMessage_Cbs
                    val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberNo, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                    var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                    //sendProjectionBenefitsResponseEchannel(memberno, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                    //sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData)

                    try{

                      val sourceDataTable = new SQLServerDataTable
                      sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                      sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
                      sourceDataTable.addColumnMetadata("ProjectionType", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("Calc_date", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("Exit_date", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("Exit_reason", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("Exit_age", java.sql.Types.INTEGER)
                      sourceDataTable.addColumnMetadata("Years_worked", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("TotalBenefits", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("PurchasePrice", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("AnnualPension", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("MonthlyPension", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("TaxOnMonthlyPension", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("NetMonthlyPension", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("CommutedLumpsum", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("TaxFreeLumpsum", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("TaxableAmount", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("WitholdingTax", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("Liability", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("LumpsumPayable", java.sql.Types.DECIMAL)
                      sourceDataTable.addColumnMetadata("Posted_to_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                      sourceDataTable.addColumnMetadata("Post_picked_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                      sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                      sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                      sourceDataTable.addColumnMetadata("ResponseData_Cbs", java.sql.Types.VARCHAR)

                      sourceDataTable.addRow(BigDecimal(myMemberNo), BigDecimal(myMemberId), strProjectionType,
                        strCalc_date, strExit_date, strExit_reason,
                        myExit_age, myYears_worked, myTotalBenefits,
                        myPurchasePrice, myAnnualPension, myMonthlyPension,
                        myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum,
                        myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax,
                        myLiability, myLumpsumPayable, posted_to_Cbs,
                        post_picked_Cbs, strDate_to_Cbs, strDate_from_Cbs,
                        myStatusCode_Cbs, strStatusMessage_Cbs, strResponseData
                      )

                      myTxnID = insertEchannelsMemberProjectionBenefitsDetailsRequests(sourceDataTable)
                    }
                    catch {
                      case io: Throwable =>
                        Log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        Log_errors(strApifunction + " : " + ex.getMessage())
                    }

                    val ftr = Future {sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData, myTxnID)}
                  }
                  catch
                    {
                      case ex: Exception =>
                        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                      case t: Throwable =>
                        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                    }
                }
              }
            //println(res)
            //case Failure(_)   => sys.error("something wrong")
            case Failure(f) =>
              //println("start 3: " + f.getMessage)
              //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
              try {

                //Log_errors(strApifunction + " : " + f.getMessage + " - ex exception error occured.")
                Log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

                //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                var start_time_DB : String  = ""
                val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                var memberNo: Int = 0
                /*
                if (myEntryID.value.isEmpty != true){
                  if (myEntryID.value.get != None){
                    val myVal = myEntryID.value.get
                    if (myVal.get != None){
                      myTxnID = myVal.get
                    }
                  }
                }
                */

                if (myStart_time.value.isEmpty != true){
                  if (myStart_time.value.get != None){
                    val myVal = myStart_time.value.get
                    if (myVal.get != None){
                      start_time_DB = myVal.get
                    }
                  }
                }

                if (myMember_No.value.isEmpty != true) {
                  if (myMember_No.value.get != None) {
                    val myVal = myMember_No.value.get
                    if (myVal.get != None) {
                      memberNo = myVal.get
                    }
                  }
                }

                var strCalc_date: String = ""
                var strExit_date: String = ""
                var strExit_reason: String = ""

                //Integers only
                //var myScheme_id: Integer = 0
                //var myMember_id: Integer = 0
                var myExit_age: Integer = 0
                var myYears_worked: BigDecimal = 0
                var myTotalBenefits: BigDecimal = 0
                var myPurchasePrice: BigDecimal = 0
                var myAnnualPension: BigDecimal = 0
                var myMonthlyPension: BigDecimal = 0
                var myTaxOnMonthlyPension: BigDecimal = 0
                var myNetMonthlyPension: BigDecimal = 0
                var myCommutedLumpsum: BigDecimal = 0
                var myTaxFreeLumpsum: BigDecimal = 0
                var myTaxableAmount: BigDecimal = 0
                var myWitholdingTax: BigDecimal = 0
                var myLiability: BigDecimal = 0
                var myLumpsumPayable: BigDecimal = 0
                val strResponseData: String = "No Response Data received"

                //val posted_to_Cbs: Boolean = false
                val posted_to_Cbs: Integer = 1
                val post_picked_Cbs: Integer = 1
                val strDate_to_Cbs: String = start_time_DB
                val strDate_from_Cbs: String = stop_time_DB
                val myStatusCode_Cbs : Integer = 404
                val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"
                //UpdateLogsOutgoingLipaNaMpesaRequests(myTxnID, posted_to_Mpesa, strDate_to_Mpesa, strDate_from_Mpesa, myStatusCode_Mpesa, strStatusMessage_Mpesa)
                val myMemberProjectionBenefitsDetailsResponse_Batch = new MemberProjectionBenefitsDetailsResponse_Batch(strCalc_date, strExit_date, strExit_reason, myExit_age, myYears_worked, myTotalBenefits, myPurchasePrice, myAnnualPension, myMonthlyPension, myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum, myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax, myLiability, myLumpsumPayable)
                val statuscode: Int = 1
                val statusdescription: String = strStatusMessage_Cbs
                val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberNo, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                //sendProjectionBenefitsResponseEchannel(memberno, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                //sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData)

                try{

                  val sourceDataTable = new SQLServerDataTable
                  sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                  sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
                  sourceDataTable.addColumnMetadata("ProjectionType", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("Calc_date", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("Exit_date", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("Exit_reason", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("Exit_age", java.sql.Types.INTEGER)
                  sourceDataTable.addColumnMetadata("Years_worked", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("TotalBenefits", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("PurchasePrice", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("AnnualPension", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("MonthlyPension", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("TaxOnMonthlyPension", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("NetMonthlyPension", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("CommutedLumpsum", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("TaxFreeLumpsum", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("TaxableAmount", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("WitholdingTax", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("Liability", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("LumpsumPayable", java.sql.Types.DECIMAL)
                  sourceDataTable.addColumnMetadata("Posted_to_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                  sourceDataTable.addColumnMetadata("Post_picked_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                  sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                  sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                  sourceDataTable.addColumnMetadata("ResponseData_Cbs", java.sql.Types.VARCHAR)

                  sourceDataTable.addRow(BigDecimal(myMemberNo), BigDecimal(myMemberId), strProjectionType,
                    strCalc_date, strExit_date, strExit_reason,
                    myExit_age, myYears_worked, myTotalBenefits,
                    myPurchasePrice, myAnnualPension, myMonthlyPension,
                    myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum,
                    myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax,
                    myLiability, myLumpsumPayable, posted_to_Cbs,
                    post_picked_Cbs, strDate_to_Cbs, strDate_from_Cbs,
                    myStatusCode_Cbs, strStatusMessage_Cbs, strResponseData
                  )

                  myTxnID = insertEchannelsMemberProjectionBenefitsDetailsRequests(sourceDataTable)
                }
                catch {
                  case io: Throwable =>
                    Log_errors(strApifunction + " : " + io.getMessage())
                  case ex: Exception =>
                    Log_errors(strApifunction + " : " + ex.getMessage())
                }

                val ftr = Future {sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData, myTxnID)}
              }
              catch
                {
                  case ex: Exception =>
                    Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                  case t: Throwable =>
                    Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                }
          }

      }
    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false
          Log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          isSuccessful = false
          Log_errors(strApifunction + " : " + t.getMessage + "t exception error occured.")
      }
    finally
    {
    }

  }
  def getProvisionalStatementRequestsCbs_AsynchronousProcessing(myMemberNo : Int, myMemberId : Int): Unit = {
    val strApifunction : String = "getProvisionalStatementRequestsCbs"
    var strApiURL  : String = ""

    try{

      strApiURL = ""
      //strApiURL = "http://172.16.109.253:8088/Xi/api/getprovisionalstatement/283632"
      strApiURL = getCBSProvisionalStatementURL(myMemberId)
      if (strApiURL == null){
        strApiURL = ""
      }

      if (strApiURL.trim.length == 0){
        Log_errors(strApifunction + " : Failure in fetching  Api URL - " + strApiURL + " , application error occured.")
        return
      }

    }
    catch {
      case io: Throwable =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
    }

    val myuri : Uri = strApiURL //"http://172.16.109.253:8088/Xi/api/getprovisionalstatement/283632"

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myjsonData : String = ""


    try
    {
      if (myMemberNo > 0 && myMemberId > 0){
        isValidData = true
      }
      else{
        Log_errors(strApifunction + " : Failure in fetching  MemberNo - " + myMemberNo + " , MemberId - " + myMemberId)
        return
      }

    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
        case t: Throwable =>
          isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
      }


    try {
      if (isValidData == true) {

        var strUserName: String = ""
        var strPassWord: String = ""
        try {
          strUserName = getCbsApiUserName()
          var strPwd: String = getCbsApiPassword() //n6,e$=p8QK\+c^h~
          var myByteAuthToken = Base64.getDecoder.decode(strPwd)
          var myPwd : String = new String(myByteAuthToken, StandardCharsets.UTF_8)
          strPassWord = myPwd
        }
        catch
          {
            case ex: Exception =>
              isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
            case t: Throwable =>
              isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
          }

        if (strUserName == null){
          strUserName = ""
        }

        if (strPassWord == null){
          strPassWord = ""
        }

        if (strUserName.trim.length == 0){
          Log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strUserName + " , application error occured.")
          return
        }

        if (strPassWord.trim.length == 0){
          Log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strPassWord + " , application error occured.")
          return
        }

        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time: Future[String] = Future(start_time_DB)
        val myMember_No: Future[Int] = Future(myMemberNo)
        //TESTS ONLY
        //println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + res.status.intValue())
              if (res.status != None) {
                if (res.status.intValue() == 200) {
                  var isDataExists: Boolean = false
                  var myCount: Int = 0
                  val oldformatter : SimpleDateFormat = new SimpleDateFormat("MMM dd, yyyy")
                  val newFormatter : SimpleDateFormat = new SimpleDateFormat("dd-MM-yyyy")
                  var strOpenEe: String = ""
                  var strOpenEr: String = ""
                  var strOpenAvc: String = ""
                  var strOpenTotal: String = ""
                  var strContrEe: String = ""
                  var strContrEr: String = ""
                  var strContrAvc: String = ""
                  var strContrTotal: String = ""
                  var strGrandTotal: String = ""
                  //BigDecimal
                  var myOpenEe: BigDecimal = 0
                  var myOpenEr: BigDecimal = 0
                  var myOpenAvc: BigDecimal = 0
                  var myOpenTotal: BigDecimal = 0
                  var myContrEe: BigDecimal = 0
                  var myContrEr: BigDecimal = 0
                  var myContrAvc: BigDecimal = 0
                  var myContrTotal: BigDecimal = 0
                  var myGrandTotal: BigDecimal = 0
                  var myEe: BigDecimal = 0
                  var myEr: BigDecimal = 0
                  var myAvc: BigDecimal = 0
                  var strResponseData: String = ""
                  val strIntRegex: String = "[0-9]+" //Integers only
                  val strDecimalRegex: String = "^[0-9]*\\.?[0-9]+$" //Decimals
                  val myData = Unmarshal(res.entity).to[CbsMessage_ProvisionalStatement_Batch]

                  if (myData != None) {
                    //val strB = myData.value.getOrElse("requestdata")
                    //println("error occured myData.value.get != None 1 : " + strB.toString)
                    //if (myData.value.get != None) {
                    if (myData.value.getOrElse(None) != None) {
                      val myResultCbsMessage_BatchData = myData.value.get
                      if (myResultCbsMessage_BatchData.get != None) {

                        if (myResultCbsMessage_BatchData.get != None) {
                          strResponseData = myResultCbsMessage_BatchData.toString
                        }

                        var start_time_DB: String = ""
                        //new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var stop_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var memberNo: Int = 0

                        if (myStart_time.value.isEmpty != true) {
                          if (myStart_time.value.get != None) {
                            val myVal = myStart_time.value.get
                            if (myVal.get != None) {
                              start_time_DB = myVal.get
                            }
                          }
                        }

                        if (myMember_No.value.isEmpty != true) {
                          if (myMember_No.value.get != None) {
                            val myVal = myMember_No.value.get
                            if (myVal.get != None) {
                              memberNo = myVal.get
                            }
                          }
                        }

                        if (myResultCbsMessage_BatchData.get.rows != None) {

                          myCount = myResultCbsMessage_BatchData.get.rows.length

                          val myCbsMessageData = myResultCbsMessage_BatchData.get.rows
                          if (myCbsMessageData != None) {
                            myCbsMessageData.foreach(myCbsData => {

                              //strOpenEe
                              if (myCbsData.openEe != None) {
                                if (myCbsData.openEe.get != None) {
                                  val myData = myCbsData.openEe.get
                                  strOpenEe = myData.toString()
                                  if (strOpenEe != null && strOpenEe != None){
                                    strOpenEe = strOpenEe.trim
                                    if (strOpenEe.length > 0){
                                      strOpenEe = strOpenEe.replace("'","")//Remove apostrophe
                                      strOpenEe = strOpenEe.replace(" ","")//Remove spaces
                                      strOpenEe = strOpenEe.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenEe = strOpenEe.trim
                                      val isNumeric : Boolean = strOpenEe.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenEe = BigDecimal(strOpenEe)
                                        myOpenEe = myOpenEe.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strOpenEr
                              if (myCbsData.openEr != None) {
                                if (myCbsData.openEr.get != None) {
                                  val myData = myCbsData.openEr.get
                                  strOpenEr = myData.toString()
                                  if (strOpenEr != null && strOpenEr != None){
                                    strOpenEr = strOpenEr.trim
                                    if (strOpenEr.length > 0){
                                      strOpenEr = strOpenEr.replace("'","")//Remove apostrophe
                                      strOpenEr = strOpenEr.replace(" ","")//Remove spaces
                                      strOpenEr = strOpenEr.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenEr = strOpenEr.trim
                                      val isNumeric : Boolean = strOpenEr.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenEr = BigDecimal(strOpenEr)
                                        myOpenEr = myOpenEr.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strOpenAvc
                              if (myCbsData.openAvc != None) {
                                if (myCbsData.openAvc.get != None) {
                                  val myData = myCbsData.openAvc.get
                                  strOpenAvc = myData.toString()
                                  if (strOpenAvc != null && strOpenAvc != None){
                                    strOpenAvc = strOpenAvc.trim
                                    if (strOpenAvc.length > 0){
                                      strOpenAvc = strOpenAvc.replace("'","")//Remove apostrophe
                                      strOpenAvc = strOpenAvc.replace(" ","")//Remove spaces
                                      strOpenAvc = strOpenAvc.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenAvc = strOpenAvc.trim
                                      val isNumeric : Boolean = strOpenAvc.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenAvc = BigDecimal(strOpenAvc)
                                        myOpenAvc = myOpenAvc.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strOpenTotal
                              if (myCbsData.openTotal != None) {
                                if (myCbsData.openTotal.get != None) {
                                  val myData = myCbsData.openTotal.get
                                  strOpenTotal = myData.toString()
                                  if (strOpenTotal != null && strOpenTotal != None){
                                    strOpenTotal = strOpenTotal.trim
                                    if (strOpenTotal.length > 0){
                                      strOpenTotal = strOpenTotal.replace("'","")//Remove apostrophe
                                      strOpenTotal = strOpenTotal.replace(" ","")//Remove spaces
                                      strOpenTotal = strOpenTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenTotal = strOpenTotal.trim
                                      val isNumeric : Boolean = strOpenTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenTotal = BigDecimal(strOpenTotal)
                                        myOpenTotal = myOpenTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrEe
                              if (myCbsData.contrEe != None) {
                                if (myCbsData.contrEe.get != None) {
                                  val myData = myCbsData.contrEe.get
                                  strContrEe = myData.toString()
                                  if (strContrEe != null && strContrEe != None){
                                    strContrEe = strContrEe.trim
                                    if (strContrEe.length > 0){
                                      strContrEe = strContrEe.replace("'","")//Remove apostrophe
                                      strContrEe = strContrEe.replace(" ","")//Remove spaces
                                      strContrEe = strContrEe.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrEe = strContrEe.trim
                                      val isNumeric : Boolean = strContrEe.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrEe = BigDecimal(strContrEe)
                                        myContrEe = myContrEe.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrEr
                              if (myCbsData.contrEr != None) {
                                if (myCbsData.contrEr.get != None) {
                                  val myData = myCbsData.contrEr.get
                                  strContrEr = myData.toString()
                                  if (strContrEr != null && strContrEr != None){
                                    strContrEr = strContrEr.trim
                                    if (strContrEr.length > 0){
                                      strContrEr = strContrEr.replace("'","")//Remove apostrophe
                                      strContrEr = strContrEr.replace(" ","")//Remove spaces
                                      strContrEr = strContrEr.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrEr = strContrEr.trim
                                      val isNumeric : Boolean = strContrEr.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrEr = BigDecimal(strContrEr)
                                        myContrEr = myContrEr.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrAvc
                              if (myCbsData.contrAvc != None) {
                                if (myCbsData.contrAvc.get != None) {
                                  val myData = myCbsData.contrAvc.get
                                  strContrAvc = myData.toString()
                                  if (strContrAvc != null && strContrAvc != None){
                                    strContrAvc = strContrAvc.trim
                                    if (strContrAvc.length > 0){
                                      strContrAvc = strContrAvc.replace("'","")//Remove apostrophe
                                      strContrAvc = strContrAvc.replace(" ","")//Remove spaces
                                      strContrAvc = strContrAvc.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrAvc = strContrAvc.trim
                                      val isNumeric : Boolean = strContrAvc.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrAvc = BigDecimal(strContrAvc)
                                        myContrAvc = myContrAvc.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrTotal
                              if (myCbsData.contrTotal != None) {
                                if (myCbsData.contrTotal.get != None) {
                                  val myData = myCbsData.contrTotal.get
                                  strContrTotal = myData.toString()
                                  if (strContrTotal != null && strContrTotal != None){
                                    strContrTotal = strContrTotal.trim
                                    if (strContrTotal.length > 0){
                                      strContrTotal = strContrTotal.replace("'","")//Remove apostrophe
                                      strContrTotal = strContrTotal.replace(" ","")//Remove spaces
                                      strContrTotal = strContrTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrTotal = strContrTotal.trim
                                      val isNumeric : Boolean = strContrTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrTotal = BigDecimal(strContrTotal)
                                        myContrTotal = myContrTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strGrandTotal
                              if (myCbsData.grandTotal != None) {
                                if (myCbsData.grandTotal.get != None) {
                                  val myData = myCbsData.grandTotal.get
                                  strGrandTotal = myData.toString()
                                  if (strGrandTotal != null && strGrandTotal != None){
                                    strGrandTotal = strGrandTotal.trim
                                    if (strGrandTotal.length > 0){
                                      strGrandTotal = strGrandTotal.replace("'","")//Remove apostrophe
                                      strGrandTotal = strGrandTotal.replace(" ","")//Remove spaces
                                      strGrandTotal = strGrandTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strGrandTotal = strGrandTotal.trim
                                      val isNumeric : Boolean = strGrandTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myGrandTotal = BigDecimal(strGrandTotal)
                                        myGrandTotal = myGrandTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //myEe
                              myEe = myOpenEe  + myContrEe
                              myEr = myOpenEr  + myContrEr
                              myAvc = myOpenAvc  + myContrAvc
                              //TESTS ONLY
                              val strMessage: String = "myOpenEe - " + myOpenEe + ", myOpenEr - " + myOpenEr + ", myOpenAvc - " + myOpenAvc +
                                ", myContrEe - " + myContrEe + ", myContrEr - " + myContrEr + ", myContrAvc - " + myContrAvc +
                                ", myGrandTotal - " + myGrandTotal + ", myEe - " + myEe + ", myEr - " + myEr +
                                ", myAvc - " + myAvc + ", memberNo - " + memberNo + ", myMemberId - " + myMemberId
                              Log_data(strApifunction + " : " + strMessage + " - ResponseMessage." + strApifunction)
                              isDataExists = true

                            })
                          }
                        }
                        /*
                        //val posted_to_Cbs: Boolean = true
                        val posted_to_Cbs: Integer = 1
                        val post_picked_Cbs: Integer = 1
                        val strDate_to_Cbs: String = start_time_DB
                        val strDate_from_Cbs: String = stop_time_DB
                        val myStatusCode_Cbs : Integer = res.status.intValue()
                        val strStatusMessage_Cbs: String = "Successful"
                        //UpdateLogsOutgoingLipaNaMpesaRequests(myTxnID, posted_to_Cbs, strDate_to_Cbs, strDate_from_Cbs, myStatusCode_Cbs, strStatusMessage_Cbs)

                        if (isDataExists == true) {
                          //processUpdatePensionersVerification(sourceDataTable)
                          val myMemberProjectionBenefitsDetailsResponse_Batch = new MemberProjectionBenefitsDetailsResponse_Batch(strCalc_date, strExit_date, strExit_reason, myExit_age, myYears_worked, myTotalBenefits, myPurchasePrice, myAnnualPension, myMonthlyPension, myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum, myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax, myLiability, myLumpsumPayable)
                          //val memberno: Int = 17274
                          val statuscode: Int = 0
                          val statusdescription: String = strStatusMessage_Cbs
                          val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberNo, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                          var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                          try{

                            val sourceDataTable = new SQLServerDataTable
                            sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                            sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
                            sourceDataTable.addColumnMetadata("ProjectionType", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Calc_date", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_date", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_reason", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_age", java.sql.Types.INTEGER)
                            sourceDataTable.addColumnMetadata("Years_worked", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TotalBenefits", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("PurchasePrice", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("AnnualPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("MonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxOnMonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("NetMonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("CommutedLumpsum", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxFreeLumpsum", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxableAmount", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("WitholdingTax", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("Liability", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("LumpsumPayable", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("Posted_to_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                            sourceDataTable.addColumnMetadata("Post_picked_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                            sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                            sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("ResponseData_Cbs", java.sql.Types.VARCHAR)

                            sourceDataTable.addRow(BigDecimal(myMemberNo), BigDecimal(myMemberId), strProjectionType,
                              strCalc_date, strExit_date, strExit_reason,
                              myExit_age, myYears_worked, myTotalBenefits,
                              myPurchasePrice, myAnnualPension, myMonthlyPension,
                              myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum,
                              myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax,
                              myLiability, myLumpsumPayable, posted_to_Cbs,
                              post_picked_Cbs, strDate_to_Cbs, strDate_from_Cbs,
                              myStatusCode_Cbs, strStatusMessage_Cbs, strResponseData
                            )

                            myTxnID = insertEchannelsMemberProjectionBenefitsDetailsRequests(sourceDataTable)
                          }
                          catch {
                            case io: Throwable =>
                              Log_errors(strApifunction + " : " + io.getMessage())
                            case ex: Exception =>
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }

                          //sendProjectionBenefitsResponseEchannel(memberno, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                          //sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData)
                          val f = Future {sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData, myTxnID)}
                        }
                        */

                      }
                    }
                    else {
                      //TESTS ONLY
                      //println("error occured myData.value.get != None : " + start_time_DB)
                      //Lets log the status code returned by CBS webservice
                      val myStatusCode : Int = res.status.intValue()
                      val strStatusMessage: String = "Failed"

                      try {

                        //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                        var start_time_DB : String  = ""
                        val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var memberNo: Int = 0
                        /*
                        if (myEntryID.value.isEmpty != true){
                          if (myEntryID.value.get != None){
                            val myVal = myEntryID.value.get
                            if (myVal.get != None){
                              myTxnID = myVal.get
                            }
                          }
                        }
                        */

                        if (myStart_time.value.isEmpty != true){
                          if (myStart_time.value.get != None){
                            val myVal = myStart_time.value.get
                            if (myVal.get != None){
                              start_time_DB = myVal.get
                            }
                          }
                        }

                        if (myMember_No.value.isEmpty != true) {
                          if (myMember_No.value.get != None) {
                            val myVal = myMember_No.value.get
                            if (myVal.get != None) {
                              memberNo = myVal.get
                            }
                          }
                        }

                        val strMessage: String = "member_no - " + myMember_No + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                        Log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None. error occured.")

                        val strResponseData: String = "No Response Data received"

                        //val posted_to_Cbs: Boolean = false
                        val posted_to_Cbs: Integer = 1
                        val post_picked_Cbs: Integer = 1
                        val strDate_to_Cbs: String = start_time_DB
                        val strDate_from_Cbs: String = stop_time_DB
                        val myStatusCode_Cbs : Integer = res.status.intValue()
                        val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

                        val strMessage1: String = "member_no - " + myMember_No + ", member_Id - " + myMemberId + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                        Log_data(strApifunction + " : " + strResponseData + " - myData.value.getOrElse(None) != None. error occured.")


                      }
                      catch
                        {
                          case ex: Exception =>
                            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                          case t: Throwable =>
                            Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                        }
                    }
                  }
                }
                else {

                  //Lets log the status code returned by CBS webservice
                  val myStatusCode : Int = res.status.intValue()
                  val strStatusMessage: String = "Failed"

                  try {

                    //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                    var start_time_DB : String  = ""
                    val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                    var memberNo: Int = 0
                    /*
                    if (myEntryID.value.isEmpty != true){
                      if (myEntryID.value.get != None){
                        val myVal = myEntryID.value.get
                        if (myVal.get != None){
                          myTxnID = myVal.get
                        }
                      }
                    }
                    */

                    if (myStart_time.value.isEmpty != true){
                      if (myStart_time.value.get != None){
                        val myVal = myStart_time.value.get
                        if (myVal.get != None){
                          start_time_DB = myVal.get
                        }
                      }
                    }

                    if (myMember_No.value.isEmpty != true) {
                      if (myMember_No.value.get != None) {
                        val myVal = myMember_No.value.get
                        if (myVal.get != None) {
                          memberNo = myVal.get
                        }
                      }
                    }

                    val strMessage: String = "member_no - " + myMember_No + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                    Log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

                    val strResponseData: String = "No Response Data received"

                    //val posted_to_Cbs: Boolean = false
                    val posted_to_Cbs: Integer = 1
                    val post_picked_Cbs: Integer = 1
                    val strDate_to_Cbs: String = start_time_DB
                    val strDate_from_Cbs: String = stop_time_DB
                    val myStatusCode_Cbs : Integer = res.status.intValue()
                    val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

                    val strMessage1: String = "member_no - " + myMember_No + ", member_Id - " + myMemberId + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                    Log_data(strApifunction + " : " + strResponseData + " - http != 200 error occured. error occured.")

                  }
                  catch
                    {
                      case ex: Exception =>
                        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                      case t: Throwable =>
                        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                    }
                }
              }
            //println(res)
            //case Failure(_)   => sys.error("something wrong")
            case Failure(f) =>
              //println("start 3: " + f.getMessage)
              //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
              try {

                //Log_errors(strApifunction + " : " + f.getMessage + " - ex exception error occured.")
                Log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

                //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                var start_time_DB : String  = ""
                val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                var memberNo: Int = 0
                /*
                if (myEntryID.value.isEmpty != true){
                  if (myEntryID.value.get != None){
                    val myVal = myEntryID.value.get
                    if (myVal.get != None){
                      myTxnID = myVal.get
                    }
                  }
                }
                */

                if (myStart_time.value.isEmpty != true){
                  if (myStart_time.value.get != None){
                    val myVal = myStart_time.value.get
                    if (myVal.get != None){
                      start_time_DB = myVal.get
                    }
                  }
                }

                if (myMember_No.value.isEmpty != true) {
                  if (myMember_No.value.get != None) {
                    val myVal = myMember_No.value.get
                    if (myVal.get != None) {
                      memberNo = myVal.get
                    }
                  }
                }

                val strResponseData: String = "No Response Data received"

                //val posted_to_Cbs: Boolean = false
                val posted_to_Cbs: Integer = 1
                val post_picked_Cbs: Integer = 1
                val strDate_to_Cbs: String = start_time_DB
                val strDate_from_Cbs: String = stop_time_DB
                val myStatusCode_Cbs : Integer = 404
                val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

                val strMessage1: String = "member_no - " + myMember_No + ", member_Id - " + myMemberId + ", status - " + myStatusCode_Cbs + ", status message - " + strStatusMessage_Cbs
                Log_data(strApifunction + " : " + strResponseData + " - http != 200 error occured. error occured.")
              }
              catch
                {
                  case ex: Exception =>
                    Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                  case t: Throwable =>
                    Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                }
          }

      }
    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false
          Log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          isSuccessful = false
          Log_errors(strApifunction + " : " + t.getMessage + "t exception error occured.")
      }
    finally
    {
    }

  }
  def getProvisionalStatementRequestsCbs(myMemberNo : Int, myMemberId : Int): Result_CbsProvisionalStatement = {
    val strApifunction : String = "getProvisionalStatementRequestsCbs"
    var strApiURL  : String = ""
    var isRequestSuccessful: Boolean = false
    var myEe: BigDecimal = 0
    var myEr: BigDecimal = 0
    var myAvc: BigDecimal = 0
    var myTotal: BigDecimal = 0

    try{

      strApiURL = ""
      //strApiURL = "http://172.16.109.253:8088/Xi/api/getprovisionalstatement/283632"
      strApiURL = getCBSProvisionalStatementURL(myMemberId)
      if (strApiURL == null){
        strApiURL = ""
      }

      if (strApiURL.trim.length == 0){
        Log_errors(strApifunction + " : Failure in fetching  Api URL - " + strApiURL + " , application error occured.")
        val myOutput = Result_CbsProvisionalStatement(isRequestSuccessful, myEe, myEr, myAvc, myTotal)
        return myOutput
      }

    }
    catch {
      case io: Throwable =>
        Log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage())
    }

    val myuri : Uri = strApiURL //"http://172.16.109.253:8088/Xi/api/getprovisionalstatement/283632"

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myjsonData : String = ""


    try
    {
      if (myMemberNo > 0 && myMemberId > 0){
        isValidData = true
      }
      else{
        Log_errors(strApifunction + " : Failure in fetching  MemberNo - " + myMemberNo + " , MemberId - " + myMemberId)
        val myOutput = Result_CbsProvisionalStatement(isRequestSuccessful, myEe, myEr, myAvc, myTotal)
        return myOutput
      }

    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
        case t: Throwable =>
          isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
      }


    try {
      if (isValidData == true) {

        var strUserName: String = ""
        var strPassWord: String = ""
        try {
          strUserName = getCbsApiUserName()
          var strPwd: String = getCbsApiPassword() //n6,e$=p8QK\+c^h~
          var myByteAuthToken = Base64.getDecoder.decode(strPwd)
          var myPwd : String = new String(myByteAuthToken, StandardCharsets.UTF_8)
          strPassWord = myPwd
        }
        catch
          {
            case ex: Exception =>
              isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
            case t: Throwable =>
              isSuccessful = false//strname = "no data"//println("Got some other kind of exception")
          }

        if (strUserName == null){
          strUserName = ""
        }

        if (strPassWord == null){
          strPassWord = ""
        }

        if (strUserName.trim.length == 0){
          Log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strUserName + " , application error occured.")
          val myOutput = Result_CbsProvisionalStatement(isRequestSuccessful, myEe, myEr, myAvc, myTotal)
          return myOutput
        }

        if (strPassWord.trim.length == 0){
          Log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strPassWord + " , application error occured.")
          val myOutput = Result_CbsProvisionalStatement(isRequestSuccessful, myEe, myEr, myAvc, myTotal)
          return myOutput
        }

        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        //val myStart_time: Future[String] = Future(start_time_DB)
        //val myMember_No: Future[Int] = Future(myMemberNo)
        //TESTS ONLY
        //println("start 1: " + start_time_DB)
        val res = Await.result(responseFuture, timeout.duration)

        if (res.status != null) {
          if (res.status.intValue() == 200) {
            var isDataExists: Boolean = false
            var myCount: Int = 0
            val oldformatter : SimpleDateFormat = new SimpleDateFormat("MMM dd, yyyy")
            val newFormatter : SimpleDateFormat = new SimpleDateFormat("dd-MM-yyyy")
            var strOpenEe: String = ""
            var strOpenEr: String = ""
            var strOpenAvc: String = ""
            var strOpenTotal: String = ""
            var strContrEe: String = ""
            var strContrEr: String = ""
            var strContrAvc: String = ""
            var strContrTotal: String = ""
            var strGrandTotal: String = ""
            //BigDecimal
            var myOpenEe: BigDecimal = 0
            var myOpenEr: BigDecimal = 0
            var myOpenAvc: BigDecimal = 0
            var myOpenTotal: BigDecimal = 0
            var myContrEe: BigDecimal = 0
            var myContrEr: BigDecimal = 0
            var myContrAvc: BigDecimal = 0
            var myContrTotal: BigDecimal = 0
            var myGrandTotal: BigDecimal = 0
            /*
            var myEe: BigDecimal = 0
            var myEr: BigDecimal = 0
            var myAvc: BigDecimal = 0
            */
            var strResponseData: String = ""
            val strIntRegex: String = "[0-9]+" //Integers only
            val strDecimalRegex: String = "^[0-9]*\\.?[0-9]+$" //Decimals
            val myData = Unmarshal(res.entity).to[CbsMessage_ProvisionalStatement_Batch]

            if (myData != null) {
              if (myData.value.getOrElse(null) != null) {
                val myResultCbsMessage_BatchData = myData.value.get
                if (myResultCbsMessage_BatchData.get != null) {

                  if (myResultCbsMessage_BatchData.get != null) {
                    strResponseData = myResultCbsMessage_BatchData.toString
                  }

                  var start_time_DB: String = ""
                  //new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                  var stop_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                  /*
                  var memberNo: Int = 0

                  if (myStart_time.value.isEmpty != true) {
                    if (myStart_time.value.get != null) {
                      val myVal = myStart_time.value.get
                      if (myVal.get != null) {
                        start_time_DB = myVal.get
                      }
                    }
                  }

                  if (myMember_No.value.isEmpty != true) {
                    if (myMember_No.value.get != null) {
                      val myVal = myMember_No.value.get
                      if (myVal.get != null) {
                        memberNo = myVal.get
                      }
                    }
                  }
                  */
                  if (myResultCbsMessage_BatchData.get.rows != null) {

                    myCount = myResultCbsMessage_BatchData.get.rows.length

                    val myCbsMessageData = myResultCbsMessage_BatchData.get.rows
                    if (myCbsMessageData != null) {
                      myCbsMessageData.foreach(myCbsData => {

                        //strOpenEe
                        if (myCbsData.openEe != null) {
                          if (myCbsData.openEe.get != null) {
                            val myData = myCbsData.openEe.get
                            strOpenEe = myData.toString()
                            if (strOpenEe != null && strOpenEe != null){
                              strOpenEe = strOpenEe.trim
                              if (strOpenEe.length > 0){
                                strOpenEe = strOpenEe.replace("'","")//Remove apostrophe
                                strOpenEe = strOpenEe.replace(" ","")//Remove spaces
                                strOpenEe = strOpenEe.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strOpenEe = strOpenEe.trim
                                val isNumeric : Boolean = strOpenEe.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myOpenEe = BigDecimal(strOpenEe)
                                  myOpenEe = myOpenEe.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strOpenEr
                        if (myCbsData.openEr != null) {
                          if (myCbsData.openEr.get != null) {
                            val myData = myCbsData.openEr.get
                            strOpenEr = myData.toString()
                            if (strOpenEr != null && strOpenEr != null){
                              strOpenEr = strOpenEr.trim
                              if (strOpenEr.length > 0){
                                strOpenEr = strOpenEr.replace("'","")//Remove apostrophe
                                strOpenEr = strOpenEr.replace(" ","")//Remove spaces
                                strOpenEr = strOpenEr.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strOpenEr = strOpenEr.trim
                                val isNumeric : Boolean = strOpenEr.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myOpenEr = BigDecimal(strOpenEr)
                                  myOpenEr = myOpenEr.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strOpenAvc
                        if (myCbsData.openAvc != null) {
                          if (myCbsData.openAvc.get != null) {
                            val myData = myCbsData.openAvc.get
                            strOpenAvc = myData.toString()
                            if (strOpenAvc != null && strOpenAvc != null){
                              strOpenAvc = strOpenAvc.trim
                              if (strOpenAvc.length > 0){
                                strOpenAvc = strOpenAvc.replace("'","")//Remove apostrophe
                                strOpenAvc = strOpenAvc.replace(" ","")//Remove spaces
                                strOpenAvc = strOpenAvc.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strOpenAvc = strOpenAvc.trim
                                val isNumeric : Boolean = strOpenAvc.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myOpenAvc = BigDecimal(strOpenAvc)
                                  myOpenAvc = myOpenAvc.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strOpenTotal
                        if (myCbsData.openTotal != None) {
                          if (myCbsData.openTotal.get != None) {
                            val myData = myCbsData.openTotal.get
                            strOpenTotal = myData.toString()
                            if (strOpenTotal != null && strOpenTotal != None){
                              strOpenTotal = strOpenTotal.trim
                              if (strOpenTotal.length > 0){
                                strOpenTotal = strOpenTotal.replace("'","")//Remove apostrophe
                                strOpenTotal = strOpenTotal.replace(" ","")//Remove spaces
                                strOpenTotal = strOpenTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strOpenTotal = strOpenTotal.trim
                                val isNumeric : Boolean = strOpenTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myOpenTotal = BigDecimal(strOpenTotal)
                                  myOpenTotal = myOpenTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strContrEe
                        if (myCbsData.contrEe != null) {
                          if (myCbsData.contrEe.get != null) {
                            val myData = myCbsData.contrEe.get
                            strContrEe = myData.toString()
                            if (strContrEe != null && strContrEe != null){
                              strContrEe = strContrEe.trim
                              if (strContrEe.length > 0){
                                strContrEe = strContrEe.replace("'","")//Remove apostrophe
                                strContrEe = strContrEe.replace(" ","")//Remove spaces
                                strContrEe = strContrEe.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strContrEe = strContrEe.trim
                                val isNumeric : Boolean = strContrEe.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myContrEe = BigDecimal(strContrEe)
                                  myContrEe = myContrEe.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strContrEr
                        if (myCbsData.contrEr != null) {
                          if (myCbsData.contrEr.get != null) {
                            val myData = myCbsData.contrEr.get
                            strContrEr = myData.toString()
                            if (strContrEr != null && strContrEr != null){
                              strContrEr = strContrEr.trim
                              if (strContrEr.length > 0){
                                strContrEr = strContrEr.replace("'","")//Remove apostrophe
                                strContrEr = strContrEr.replace(" ","")//Remove spaces
                                strContrEr = strContrEr.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strContrEr = strContrEr.trim
                                val isNumeric : Boolean = strContrEr.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myContrEr = BigDecimal(strContrEr)
                                  myContrEr = myContrEr.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strContrAvc
                        if (myCbsData.contrAvc != null) {
                          if (myCbsData.contrAvc.get != null) {
                            val myData = myCbsData.contrAvc.get
                            strContrAvc = myData.toString()
                            if (strContrAvc != null && strContrAvc != null){
                              strContrAvc = strContrAvc.trim
                              if (strContrAvc.length > 0){
                                strContrAvc = strContrAvc.replace("'","")//Remove apostrophe
                                strContrAvc = strContrAvc.replace(" ","")//Remove spaces
                                strContrAvc = strContrAvc.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strContrAvc = strContrAvc.trim
                                val isNumeric : Boolean = strContrAvc.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myContrAvc = BigDecimal(strContrAvc)
                                  myContrAvc = myContrAvc.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strContrTotal
                        if (myCbsData.contrTotal != None) {
                          if (myCbsData.contrTotal.get != None) {
                            val myData = myCbsData.contrTotal.get
                            strContrTotal = myData.toString()
                            if (strContrTotal != null && strContrTotal != None){
                              strContrTotal = strContrTotal.trim
                              if (strContrTotal.length > 0){
                                strContrTotal = strContrTotal.replace("'","")//Remove apostrophe
                                strContrTotal = strContrTotal.replace(" ","")//Remove spaces
                                strContrTotal = strContrTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strContrTotal = strContrTotal.trim
                                val isNumeric : Boolean = strContrTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myContrTotal = BigDecimal(strContrTotal)
                                  myContrTotal = myContrTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //strGrandTotal
                        if (myCbsData.grandTotal != null) {
                          if (myCbsData.grandTotal.get != null) {
                            val myData = myCbsData.grandTotal.get
                            strGrandTotal = myData.toString()
                            if (strGrandTotal != null && strGrandTotal != null){
                              strGrandTotal = strGrandTotal.trim
                              if (strGrandTotal.length > 0){
                                strGrandTotal = strGrandTotal.replace("'","")//Remove apostrophe
                                strGrandTotal = strGrandTotal.replace(" ","")//Remove spaces
                                strGrandTotal = strGrandTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strGrandTotal = strGrandTotal.trim
                                val isNumeric : Boolean = strGrandTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                if (isNumeric == true){
                                  myGrandTotal = BigDecimal(strGrandTotal)
                                  myGrandTotal = myGrandTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                }
                              }
                            }
                          }
                        }

                        //myEe
                        myEe = myOpenEe  + myContrEe
                        myEr = myOpenEr  + myContrEr
                        myAvc = myOpenAvc  + myContrAvc
                        myTotal = myGrandTotal

                        isRequestSuccessful = true

                        //TESTS ONLY
                        val strMessage: String = "myOpenEe - " + myOpenEe + ", myOpenEr - " + myOpenEr + ", myOpenAvc - " + myOpenAvc +
                          ", myContrEe - " + myContrEe + ", myContrEr - " + myContrEr + ", myContrAvc - " + myContrAvc +
                          ", myGrandTotal - " + myGrandTotal + ", myEe - " + myEe + ", myEr - " + myEr +
                          ", myAvc - " + myAvc + ", memberNo - " + myMemberNo + ", myMemberId - " + myMemberId
                        Log_data(strApifunction + " : " + strMessage + " - ResponseMessage." + strApifunction)
                        isDataExists = true

                      })
                    }
                  }
                }
              }
              else {
                //TESTS ONLY
                //println("error occured myData.value.get != None : " + start_time_DB)
                //Lets log the status code returned by CBS webservice
                val myStatusCode : Int = res.status.intValue()
                val strStatusMessage: String = "Failed"

                try {

                  //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                  var start_time_DB : String  = ""
                  val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                  //var memberNo: Int = 0
                  /*
                  if (myEntryID.value.isEmpty != true){
                    if (myEntryID.value.get != None){
                      val myVal = myEntryID.value.get
                      if (myVal.get != None){
                        myTxnID = myVal.get
                      }
                    }
                  }
                  */
                  /*
                  if (myStart_time.value.isEmpty != true){
                    if (myStart_time.value.get != null){
                      val myVal = myStart_time.value.get
                      if (myVal.get != null){
                        start_time_DB = myVal.get
                      }
                    }
                  }

                  if (myMember_No.value.isEmpty != true) {
                    if (myMember_No.value.get != null) {
                      val myVal = myMember_No.value.get
                      if (myVal.get != null) {
                        memberNo = myVal.get
                      }
                    }
                  }
                  */
                  val strMessage: String = "member_no - " + myMemberNo + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                  Log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None. error occured.")

                  val strResponseData: String = "No Response Data received"

                  //val posted_to_Cbs: Boolean = false
                  val posted_to_Cbs: Integer = 1
                  val post_picked_Cbs: Integer = 1
                  val strDate_to_Cbs: String = start_time_DB
                  val strDate_from_Cbs: String = stop_time_DB
                  val myStatusCode_Cbs : Integer = res.status.intValue()
                  val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

                  val strMessage1: String = "member_no - " + myMemberNo + ", member_Id - " + myMemberId + ", status - " + myStatusCode + ", status message - " + strStatusMessage
                  Log_data(strApifunction + " : " + strResponseData + " - myData.value.getOrElse(None) != None. error occured.")


                }
                catch
                  {
                    case ex: Exception =>
                      Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                    case t: Throwable =>
                      Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                  }
              }
            }
          }
          else {

            //Lets log the status code returned by CBS webservice
            val myStatusCode : Int = res.status.intValue()
            val strStatusMessage: String = "Failed"

            try {

              //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
              var start_time_DB : String  = ""
              val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              //var memberNo: Int = 0
              /*
              if (myEntryID.value.isEmpty != true){
                if (myEntryID.value.get != None){
                  val myVal = myEntryID.value.get
                  if (myVal.get != None){
                    myTxnID = myVal.get
                  }
                }
              }
              */

              /*
              if (myStart_time.value.isEmpty != true){
                if (myStart_time.value.get != null){
                  val myVal = myStart_time.value.get
                  if (myVal.get != null){
                    start_time_DB = myVal.get
                  }
                }
              }

              if (myMember_No.value.isEmpty != true) {
                if (myMember_No.value.get != null) {
                  val myVal = myMember_No.value.get
                  if (myVal.get != None) {
                    memberNo = myVal.get
                  }
                }
              }
              */

              val strMessage: String = "member_no - " + myMemberNo + ", status - " + myStatusCode + ", status message - " + strStatusMessage
              Log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

              val strResponseData: String = "No Response Data received"

              //val posted_to_Cbs: Boolean = false
              val posted_to_Cbs: Integer = 1
              val post_picked_Cbs: Integer = 1
              val strDate_to_Cbs: String = start_time_DB
              val strDate_from_Cbs: String = stop_time_DB
              val myStatusCode_Cbs : Integer = res.status.intValue()
              val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

              val strMessage1: String = "member_no - " + myMemberNo + ", member_Id - " + myMemberId + ", status - " + myStatusCode + ", status message - " + strStatusMessage
              Log_data(strApifunction + " : " + strResponseData + " - http != 200 error occured. error occured.")

            }
            catch
              {
                case ex: Exception =>
                  Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                case t: Throwable =>
                  Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
              }
          }
        }
        else{
          Log_errors(strApifunction + " : " + " - res.status != null && res.status != None. error occured.")
        }
        /*
        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + res.status.intValue())
              if (res.status != None) {
                if (res.status.intValue() == 200) {
                  var isDataExists: Boolean = false
                  var myCount: Int = 0
                  val oldformatter : SimpleDateFormat = new SimpleDateFormat("MMM dd, yyyy")
                  val newFormatter : SimpleDateFormat = new SimpleDateFormat("dd-MM-yyyy")
                  var strOpenEe: String = ""
                  var strOpenEr: String = ""
                  var strOpenAvc: String = ""
                  var strContrEe: String = ""
                  var strContrEr: String = ""
                  var strContrAvc: String = ""
                  var strGrandTotal: String = ""
                  //BigDecimal
                  var myOpenEe: BigDecimal = 0
                  var myOpenEr: BigDecimal = 0
                  var myOpenAvc: BigDecimal = 0
                  var myContrEe: BigDecimal = 0
                  var myContrEr: BigDecimal = 0
                  var myContrAvc: BigDecimal = 0
                  var myGrandTotal: BigDecimal = 0
                  var myEe: BigDecimal = 0
                  var myEr: BigDecimal = 0
                  var myAvc: BigDecimal = 0
                  var strResponseData: String = ""
                  val strIntRegex: String = "[0-9]+" //Integers only
                  val strDecimalRegex: String = "^[0-9]*\\.?[0-9]+$" //Decimals
                  val myData = Unmarshal(res.entity).to[CbsMessage_ProvisionalStatement_Batch]

                  if (myData != None) {
                    //val strB = myData.value.getOrElse("requestdata")
                    //println("error occured myData.value.get != None 1 : " + strB.toString)
                    //if (myData.value.get != None) {
                    if (myData.value.getOrElse(None) != None) {
                      val myResultCbsMessage_BatchData = myData.value.get
                      if (myResultCbsMessage_BatchData.get != None) {

                        if (myResultCbsMessage_BatchData.get != None) {
                          strResponseData = myResultCbsMessage_BatchData.toString
                        }

                        var start_time_DB: String = ""
                        //new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var stop_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                        var memberNo: Int = 0

                        if (myStart_time.value.isEmpty != true) {
                          if (myStart_time.value.get != None) {
                            val myVal = myStart_time.value.get
                            if (myVal.get != None) {
                              start_time_DB = myVal.get
                            }
                          }
                        }

                        if (myMember_No.value.isEmpty != true) {
                          if (myMember_No.value.get != None) {
                            val myVal = myMember_No.value.get
                            if (myVal.get != None) {
                              memberNo = myVal.get
                            }
                          }
                        }

                        if (myResultCbsMessage_BatchData.get.rows != None) {

                          myCount = myResultCbsMessage_BatchData.get.rows.length

                          val myCbsMessageData = myResultCbsMessage_BatchData.get.rows
                          if (myCbsMessageData != None) {
                            myCbsMessageData.foreach(myCbsData => {

                              //strOpenEe
                              if (myCbsData.openEe != None) {
                                if (myCbsData.openEe.get != None) {
                                  val myData = myCbsData.openEe.get
                                  strOpenEe = myData.toString()
                                  if (strOpenEe != null && strOpenEe != None){
                                    strOpenEe = strOpenEe.trim
                                    if (strOpenEe.length > 0){
                                      strOpenEe = strOpenEe.replace("'","")//Remove apostrophe
                                      strOpenEe = strOpenEe.replace(" ","")//Remove spaces
                                      strOpenEe = strOpenEe.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenEe = strOpenEe.trim
                                      val isNumeric : Boolean = strOpenEe.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenEe = BigDecimal(strOpenEe)
                                        myOpenEe = myOpenEe.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strOpenEr
                              if (myCbsData.openEr != None) {
                                if (myCbsData.openEr.get != None) {
                                  val myData = myCbsData.openEr.get
                                  strOpenEr = myData.toString()
                                  if (strOpenEr != null && strOpenEr != None){
                                    strOpenEr = strOpenEr.trim
                                    if (strOpenEr.length > 0){
                                      strOpenEr = strOpenEr.replace("'","")//Remove apostrophe
                                      strOpenEr = strOpenEr.replace(" ","")//Remove spaces
                                      strOpenEr = strOpenEr.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenEr = strOpenEr.trim
                                      val isNumeric : Boolean = strOpenEr.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenEr = BigDecimal(strOpenEr)
                                        myOpenEr = myOpenEr.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strOpenAvc
                              if (myCbsData.openAvc != None) {
                                if (myCbsData.openAvc.get != None) {
                                  val myData = myCbsData.openAvc.get
                                  strOpenAvc = myData.toString()
                                  if (strOpenAvc != null && strOpenAvc != None){
                                    strOpenAvc = strOpenAvc.trim
                                    if (strOpenAvc.length > 0){
                                      strOpenAvc = strOpenAvc.replace("'","")//Remove apostrophe
                                      strOpenAvc = strOpenAvc.replace(" ","")//Remove spaces
                                      strOpenAvc = strOpenAvc.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strOpenAvc = strOpenAvc.trim
                                      val isNumeric : Boolean = strOpenAvc.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myOpenAvc = BigDecimal(strOpenAvc)
                                        myOpenAvc = myOpenAvc.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrEe
                              if (myCbsData.contrEe != None) {
                                if (myCbsData.contrEe.get != None) {
                                  val myData = myCbsData.contrEe.get
                                  strContrEe = myData.toString()
                                  if (strContrEe != null && strContrEe != None){
                                    strContrEe = strContrEe.trim
                                    if (strContrEe.length > 0){
                                      strContrEe = strContrEe.replace("'","")//Remove apostrophe
                                      strContrEe = strContrEe.replace(" ","")//Remove spaces
                                      strContrEe = strContrEe.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrEe = strContrEe.trim
                                      val isNumeric : Boolean = strContrEe.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrEe = BigDecimal(strContrEe)
                                        myContrEe = myContrEe.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrEr
                              if (myCbsData.contrEr != None) {
                                if (myCbsData.contrEr.get != None) {
                                  val myData = myCbsData.contrEr.get
                                  strContrEr = myData.toString()
                                  if (strContrEr != null && strContrEr != None){
                                    strContrEr = strContrEr.trim
                                    if (strContrEr.length > 0){
                                      strContrEr = strContrEr.replace("'","")//Remove apostrophe
                                      strContrEr = strContrEr.replace(" ","")//Remove spaces
                                      strContrEr = strContrEr.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrEr = strContrEr.trim
                                      val isNumeric : Boolean = strContrEr.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrEr = BigDecimal(strContrEr)
                                        myContrEr = myContrEr.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strContrAvc
                              if (myCbsData.contrAvc != None) {
                                if (myCbsData.contrAvc.get != None) {
                                  val myData = myCbsData.contrAvc.get
                                  strContrAvc = myData.toString()
                                  if (strContrAvc != null && strContrAvc != None){
                                    strContrAvc = strContrAvc.trim
                                    if (strContrAvc.length > 0){
                                      strContrAvc = strContrAvc.replace("'","")//Remove apostrophe
                                      strContrAvc = strContrAvc.replace(" ","")//Remove spaces
                                      strContrAvc = strContrAvc.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strContrAvc = strContrAvc.trim
                                      val isNumeric : Boolean = strContrAvc.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myContrAvc = BigDecimal(strContrAvc)
                                        myContrAvc = myContrAvc.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //strGrandTotal
                              if (myCbsData.grandTotal != None) {
                                if (myCbsData.grandTotal.get != None) {
                                  val myData = myCbsData.grandTotal.get
                                  strGrandTotal = myData.toString()
                                  if (strGrandTotal != null && strGrandTotal != None){
                                    strGrandTotal = strGrandTotal.trim
                                    if (strGrandTotal.length > 0){
                                      strGrandTotal = strGrandTotal.replace("'","")//Remove apostrophe
                                      strGrandTotal = strGrandTotal.replace(" ","")//Remove spaces
                                      strGrandTotal = strGrandTotal.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                      strGrandTotal = strGrandTotal.trim
                                      val isNumeric : Boolean = strGrandTotal.toString.matches(strDecimalRegex)//"\\d+", //[0-9]
                                      if (isNumeric == true){
                                        myGrandTotal = BigDecimal(strGrandTotal)
                                        myGrandTotal = myGrandTotal.setScale(2,mode = BigDecimal.RoundingMode.HALF_EVEN);
                                      }
                                    }
                                  }
                                }
                              }

                              //myEe
                              myEe = myOpenEe  + myContrEe
                              myEr = myOpenEr  + myContrEr
                              myAvc = myOpenAvc  + myContrAvc
                              //TESTS ONLY
                              val strMessage: String = "myOpenEe - " + myOpenEe + ", myOpenEr - " + myOpenEr + ", myOpenAvc - " + myOpenAvc +
                                ", myContrEe - " + myContrEe + ", myContrEr - " + myContrEr + ", myContrAvc - " + myContrAvc +
                                ", myGrandTotal - " + myGrandTotal + ", myEe - " + myEe + ", myEr - " + myEr +
                                ", myAvc - " + myAvc + ", memberNo - " + memberNo + ", myMemberId - " + myMemberId
                              Log_data(strApifunction + " : " + strMessage + " - ResponseMessage." + strApifunction)
                              isDataExists = true

                            })
                          }
                        }
                        /*
                        //val posted_to_Cbs: Boolean = true
                        val posted_to_Cbs: Integer = 1
                        val post_picked_Cbs: Integer = 1
                        val strDate_to_Cbs: String = start_time_DB
                        val strDate_from_Cbs: String = stop_time_DB
                        val myStatusCode_Cbs : Integer = res.status.intValue()
                        val strStatusMessage_Cbs: String = "Successful"
                        //UpdateLogsOutgoingLipaNaMpesaRequests(myTxnID, posted_to_Cbs, strDate_to_Cbs, strDate_from_Cbs, myStatusCode_Cbs, strStatusMessage_Cbs)

                        if (isDataExists == true) {
                          //processUpdatePensionersVerification(sourceDataTable)
                          val myMemberProjectionBenefitsDetailsResponse_Batch = new MemberProjectionBenefitsDetailsResponse_Batch(strCalc_date, strExit_date, strExit_reason, myExit_age, myYears_worked, myTotalBenefits, myPurchasePrice, myAnnualPension, myMonthlyPension, myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum, myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax, myLiability, myLumpsumPayable)
                          //val memberno: Int = 17274
                          val statuscode: Int = 0
                          val statusdescription: String = strStatusMessage_Cbs
                          val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberNo, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                          var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                          try{

                            val sourceDataTable = new SQLServerDataTable
                            sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
                            sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
                            sourceDataTable.addColumnMetadata("ProjectionType", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Calc_date", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_date", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_reason", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Exit_age", java.sql.Types.INTEGER)
                            sourceDataTable.addColumnMetadata("Years_worked", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TotalBenefits", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("PurchasePrice", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("AnnualPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("MonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxOnMonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("NetMonthlyPension", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("CommutedLumpsum", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxFreeLumpsum", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("TaxableAmount", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("WitholdingTax", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("Liability", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("LumpsumPayable", java.sql.Types.DECIMAL)
                            sourceDataTable.addColumnMetadata("Posted_to_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                            sourceDataTable.addColumnMetadata("Post_picked_Cbs", java.sql.Types.INTEGER)//BOOLEAN
                            sourceDataTable.addColumnMetadata("Date_to_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("Date_from_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("StatusCode_Cbs", java.sql.Types.INTEGER)
                            sourceDataTable.addColumnMetadata("StatusMessage_Cbs", java.sql.Types.VARCHAR)
                            sourceDataTable.addColumnMetadata("ResponseData_Cbs", java.sql.Types.VARCHAR)

                            sourceDataTable.addRow(BigDecimal(myMemberNo), BigDecimal(myMemberId), strProjectionType,
                              strCalc_date, strExit_date, strExit_reason,
                              myExit_age, myYears_worked, myTotalBenefits,
                              myPurchasePrice, myAnnualPension, myMonthlyPension,
                              myTaxOnMonthlyPension, myNetMonthlyPension, myCommutedLumpsum,
                              myTaxFreeLumpsum, myTaxableAmount, myWitholdingTax,
                              myLiability, myLumpsumPayable, posted_to_Cbs,
                              post_picked_Cbs, strDate_to_Cbs, strDate_from_Cbs,
                              myStatusCode_Cbs, strStatusMessage_Cbs, strResponseData
                            )

                            myTxnID = insertEchannelsMemberProjectionBenefitsDetailsRequests(sourceDataTable)
                          }
                          catch {
                            case io: Throwable =>
                              Log_errors(strApifunction + " : " + io.getMessage())
                            case ex: Exception =>
                              Log_errors(strApifunction + " : " + ex.getMessage())
                          }

                          //sendProjectionBenefitsResponseEchannel(memberno, statuscode, statusdescription, myMemberProjectionBenefitsDetailsResponse_Batch)
                          //sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData)
                          val f = Future {sendProjectionBenefitsResponseEchannel(myresponse_MemberProjectionBenefitsData, myTxnID)}
                        }
                        */

        }
        }
        else {
        //TESTS ONLY
        //println("error occured myData.value.get != None : " + start_time_DB)
        //Lets log the status code returned by CBS webservice
        val myStatusCode : Int = res.status.intValue()
        val strStatusMessage: String = "Failed"

        try {

        //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
        var start_time_DB : String  = ""
        val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        var memberNo: Int = 0
                        /*
                        if (myEntryID.value.isEmpty != true){
                          if (myEntryID.value.get != None){
                            val myVal = myEntryID.value.get
                            if (myVal.get != None){
                              myTxnID = myVal.get
                            }
                          }
                        }
                        */

        if (myStart_time.value.isEmpty != true){
        if (myStart_time.value.get != None){
        val myVal = myStart_time.value.get
        if (myVal.get != None){
        start_time_DB = myVal.get
        }
        }
        }

        if (myMember_No.value.isEmpty != true) {
        if (myMember_No.value.get != None) {
        val myVal = myMember_No.value.get
        if (myVal.get != None) {
        memberNo = myVal.get
        }
        }
        }

        val strMessage: String = "member_no - " + myMember_No + ", status - " + myStatusCode + ", status message - " + strStatusMessage
        Log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None. error occured.")

        val strResponseData: String = "No Response Data received"

        //val posted_to_Cbs: Boolean = false
        val posted_to_Cbs: Integer = 1
        val post_picked_Cbs: Integer = 1
        val strDate_to_Cbs: String = start_time_DB
        val strDate_from_Cbs: String = stop_time_DB
        val myStatusCode_Cbs : Integer = res.status.intValue()
        val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

        val strMessage1: String = "member_no - " + myMember_No + ", member_Id - " + myMemberId + ", status - " + myStatusCode + ", status message - " + strStatusMessage
        Log_data(strApifunction + " : " + strResponseData + " - myData.value.getOrElse(None) != None. error occured.")


        }
        catch
        {
        case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
        }
        }
        }
        }
        else {

        //Lets log the status code returned by CBS webservice
        val myStatusCode : Int = res.status.intValue()
        val strStatusMessage: String = "Failed"

        try {

        //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
        var start_time_DB : String  = ""
        val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        var memberNo: Int = 0
                    /*
                    if (myEntryID.value.isEmpty != true){
                      if (myEntryID.value.get != None){
                        val myVal = myEntryID.value.get
                        if (myVal.get != None){
                          myTxnID = myVal.get
                        }
                      }
                    }
                    */

        if (myStart_time.value.isEmpty != true){
        if (myStart_time.value.get != None){
        val myVal = myStart_time.value.get
        if (myVal.get != None){
        start_time_DB = myVal.get
        }
        }
        }

        if (myMember_No.value.isEmpty != true) {
        if (myMember_No.value.get != None) {
        val myVal = myMember_No.value.get
        if (myVal.get != None) {
        memberNo = myVal.get
        }
        }
        }

        val strMessage: String = "member_no - " + myMember_No + ", status - " + myStatusCode + ", status message - " + strStatusMessage
        Log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

        val strResponseData: String = "No Response Data received"

        //val posted_to_Cbs: Boolean = false
        val posted_to_Cbs: Integer = 1
        val post_picked_Cbs: Integer = 1
        val strDate_to_Cbs: String = start_time_DB
        val strDate_from_Cbs: String = stop_time_DB
        val myStatusCode_Cbs : Integer = res.status.intValue()
        val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

        val strMessage1: String = "member_no - " + myMember_No + ", member_Id - " + myMemberId + ", status - " + myStatusCode + ", status message - " + strStatusMessage
        Log_data(strApifunction + " : " + strResponseData + " - http != 200 error occured. error occured.")

        }
        catch
        {
        case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
        }
        }
        }
        //println(res)
        //case Failure(_)   => sys.error("something wrong")
        case Failure(f) =>
        //println("start 3: " + f.getMessage)
        //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
        try {

        //Log_errors(strApifunction + " : " + f.getMessage + " - ex exception error occured.")
        Log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

        //var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
        var start_time_DB : String  = ""
        val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        var memberNo: Int = 0
                /*
                if (myEntryID.value.isEmpty != true){
                  if (myEntryID.value.get != None){
                    val myVal = myEntryID.value.get
                    if (myVal.get != None){
                      myTxnID = myVal.get
                    }
                  }
                }
                */

        if (myStart_time.value.isEmpty != true){
        if (myStart_time.value.get != None){
        val myVal = myStart_time.value.get
        if (myVal.get != None){
        start_time_DB = myVal.get
        }
        }
        }

        if (myMember_No.value.isEmpty != true) {
        if (myMember_No.value.get != None) {
        val myVal = myMember_No.value.get
        if (myVal.get != None) {
        memberNo = myVal.get
        }
        }
        }

        val strResponseData: String = "No Response Data received"

        //val posted_to_Cbs: Boolean = false
        val posted_to_Cbs: Integer = 1
        val post_picked_Cbs: Integer = 1
        val strDate_to_Cbs: String = start_time_DB
        val strDate_from_Cbs: String = stop_time_DB
        val myStatusCode_Cbs : Integer = 404
        val strStatusMessage_Cbs: String = "Failure occured when sending the request to API"

        val strMessage1: String = "member_no - " + myMember_No + ", member_Id - " + myMemberId + ", status - " + myStatusCode_Cbs + ", status message - " + strStatusMessage_Cbs
        Log_data(strApifunction + " : " + strResponseData + " - http != 200 error occured. error occured.")
        }
        catch
        {
        case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
        }
        }
        */

      }
    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false
          Log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          isSuccessful = false
          Log_errors(strApifunction + " : " + t.getMessage + "t exception error occured.")
      }
    finally
    {
    }

    val myOutput = Result_CbsProvisionalStatement(isRequestSuccessful, myEe, myEr, myAvc, myTotal)
    return myOutput
  }
  //def getMemberDetails_MemberNo(memberNoCbs: java.math.BigDecimal, iDNoCbs: java.math.BigDecimal, phoneNoCbs : String): SQLServerDataTable = {
  /*
  def getMemberDetails_MemberNo(memberNoCbs: java.math.BigDecimal, iDNoCbs: java.math.BigDecimal, phoneNoCbs : String): ResultOutput_Cbs = {
    //var is_Successful : Boolean = false
    var isValidEntry : Boolean = false
    val sourceDataTable = new SQLServerDataTable
    val errormessage : String = "A runtime error occured on Application during Processing"
    val strApifunction : String = "getMemberDetails_MemberNo"
    var myResultOutput_Cbs = new ResultOutput_Cbs("1", sourceDataTable)

    try{

      if (memberNoCbs.signum() > 0){
        isValidEntry = true
      }

      if (isValidEntry == true){

        if (myCbsDB != null){
          myCbsDB.withConnection { implicit myconn =>
            if (myconn != null){
              val statement = myconn.prepareCall("{ call fm.P_GET_MEMBER_DETAILS_ONE_API(?,?) }")
              var myNCUSTNUM : Int = 0
              var myCCOVERTYPE  : String = ""
              var myCREGNUM  : String = ""
              var myNSUMINSURED  : Int = 0
              var myNNETPREM  : Int = 0
              var myPERIODFROM  :  java.util.Date = new java.util.Date
              var myPERIODTO  : java.util.Date = new java.util.Date
              var strDetail : String = ""
              var strData: String = ""
              var mymemberId: BigDecimal = 0
              var mymemberNo: BigDecimal = 0
              var myfullNames  : String = ""
              var myiDNo: BigDecimal = 0
              var striDNo: String = ""
              var mygender  : String = ""
              var isNumeric : Boolean = false
              var myphoneNo: String = ""
              val mymemberType: String = "DB/DC"
              var responseCode: Int = 1
              var responseMessage: String = ""
              val strResultSet_FetchSize: String = "1"

              //val sourceDataTable = new SQLServerDataTable
              sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
              sourceDataTable.addColumnMetadata("MemberNo", java.sql.Types.NUMERIC)
              sourceDataTable.addColumnMetadata("FullNames", java.sql.Types.VARCHAR)
              sourceDataTable.addColumnMetadata("IDNo", java.sql.Types.NUMERIC)
              sourceDataTable.addColumnMetadata("PhoneNo", java.sql.Types.VARCHAR)
              sourceDataTable.addColumnMetadata("Gender", java.sql.Types.VARCHAR)
              sourceDataTable.addColumnMetadata("MemberType", java.sql.Types.VARCHAR)

              statement.setFetchSize(strResultSet_FetchSize.toInt)

              statement.setBigDecimal(1, memberNoCbs)
              statement.registerOutParameter(2, OracleTypes.CURSOR)

              statement.execute()

              val rs = statement.getObject(2).asInstanceOf[ResultSet]
              if (rs != null){
                var k: Int = 0
                while (rs.next ()){
                  k = k + 1
                  mymemberId = 0
                  mymemberNo = 0
                  myiDNo = 0
                  myfullNames = ""
                  striDNo = ""
                  myphoneNo = ""
                  mygender = ""

                  try{
                    mymemberId = rs.getInt("MEMBER_ID")
                    mymemberNo = rs.getInt("MEMBER_NO")
                    myfullNames = rs.getString("FULLNAMES")
                    striDNo = rs.getString("IDNO")
                    myphoneNo = rs.getString("PHONENO")
                    mygender = rs.getString("GENDER")
                  }
                  catch {
                    case io: Throwable =>
                      Log_errors(strApifunction + " : " + io.getMessage() + "mymemberId - " + mymemberId.toString() + "mymemberNo - " + mymemberNo.toString() + "myfullNames - " + myfullNames.toString() + "striDNo - " + striDNo.toString())
                    case ex: Exception =>
                      Log_errors(strApifunction + " : " + ex.getMessage() + "mymemberId - " + mymemberId.toString() + "mymemberNo - " + mymemberNo.toString() + "myfullNames - " + myfullNames.toString() + "striDNo - " + striDNo.toString())
                  }

                  if (myfullNames != null){
                    myfullNames = myfullNames.trim
                    if (myfullNames.length > 0){
                      myfullNames = myfullNames.replace("  "," ")
                      myfullNames = myfullNames.trim
                    }
                  }
                  else{
                    myfullNames = ""
                  }

                  if (striDNo != null){
                    striDNo = striDNo.trim
                    if (striDNo.length > 0){
                      striDNo = striDNo.replace(" ","")
                      striDNo = striDNo.trim
                    }
                  }
                  else{
                    striDNo = ""
                  }

                  if (myphoneNo != null){
                    myphoneNo = myphoneNo.trim
                    if (myphoneNo.length > 0){
                      myphoneNo = myphoneNo.replace(" ","")
                      myphoneNo = myphoneNo.trim
                    }
                  }
                  else{
                    myphoneNo = ""
                  }

                  if (mygender != null){
                    mygender = mygender.trim
                    if (mygender.length > 0){
                      mygender = mygender.replace(" ","")
                      mygender = mygender.trim
                    }
                  }
                  else{
                    mygender = ""
                  }

                  isNumeric = false

                  striDNo = striDNo.trim

                  if (striDNo.length > 0){
                    if (striDNo.contains("/") == true){
                      val myindex = striDNo.indexOf("/")
                      val strID = striDNo.substring(0,myindex)
                      striDNo = strID.trim
                    }
                    isNumeric = striDNo.matches("[0-9]+")
                  }

                  if (isNumeric == true){
                    myiDNo = striDNo.toDouble
                  }

                  //val mymemberType = rs.getString("memberType")
                  //val myresponseCode = rs.getInt("responseCode")
                  //val myresponseMessage = rs.getString("responseMessage")

                  //strData = "MEMBERNO - " + mystaffNo + ", FULLNAMES - " + myfullNames + ", IDNO - " + myiDNo  + ", PHONENO - " + myphoneNo + ", MEMBERTYPE - " + mymemberType
                  //strDetail = strDetail + System.lineSeparator() + strData

                  try{
                    sourceDataTable.addRow(mymemberId, mymemberNo, myfullNames, myiDNo, myphoneNo, mygender, mymemberType)
                  }
                  catch {
                    case io: Throwable =>
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }

                  var myStatusCode: String  = "1"
                  try{
                    if (memberNoCbs.signum() > 0 && iDNoCbs.signum() > 0 && iDNoCbs.toString.length >= 7 && phoneNoCbs.length >= 10){
                      if (mymemberNo > 0 && myiDNo > 0  && myiDNo.toString.length >= 7 && myphoneNo.length >= 10){
                        if (BigDecimal(memberNoCbs) == mymemberNo && BigDecimal(iDNoCbs) == myiDNo && phoneNoCbs.replace("2547","07").equals(myphoneNo.replace("2547","07"))){
                          myStatusCode  = "0" //Successful
                        }
                      }
                    }
                  }
                  catch {
                    case io: Throwable =>
                      Log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      Log_errors(strApifunction + " : " + ex.getMessage())
                  }

                  myResultOutput_Cbs = new ResultOutput_Cbs(myStatusCode, sourceDataTable)

                }
              }
            }
          }
        }
      }
    }
    catch {
      case io: IOException =>
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured. ")
      //Log_errors("getMemberDetails : " + io.getMessage + " - io exception error occured. MotorRegNo - " + strMotorRegNo)
      case ex : Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      //Log_errors("getMemberDetails : " + ex.getMessage + " - ex exception error occured. MotorRegNo - " + strMotorRegNo)
      case t: Throwable =>
        //Log_errors("getMemberDetails : " + t.getMessage + " - t exception error occured. MotorRegNo - " + strMotorRegNo)
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }
    return  myResultOutput_Cbs
  }
  */
  /*
  def getBeneficiaryDetails_MemberId(memberIdCbs: java.math.BigDecimal): SQLServerDataTable = {
    //var is_Successful : Boolean = false
    var isValidEntry : Boolean = false
    val sourceDataTable = new SQLServerDataTable
    val errormessage : String = "A runtime error occured on Application during Processing"
    val strApifunction : String = "getBeneficiaryDetails_MemberId"

    try{

      if (memberIdCbs.signum() > 0){
        isValidEntry = true
      }

      if (isValidEntry == true){
        if (myCbsDB != null){
          myCbsDB.withConnection { implicit myconn =>
            if (myconn != null){
              val statement = myconn.prepareCall("{ call fm.P_GET_BENEFICI_DETAILS_ONE_API(?,?) }")
              var myNCUSTNUM : Int = 0
              var myCCOVERTYPE  : String = ""
              var myCREGNUM  : String = ""
              var myNSUMINSURED  : Int = 0
              var myNNETPREM  : Int = 0
              var myPERIODFROM  :  java.util.Date = new java.util.Date
              var myPERIODTO  : java.util.Date = new java.util.Date
              var strDetail : String = ""
              var strData: String = ""
              var mybeneficiaryId: BigDecimal = 0
              var mymemberId: BigDecimal = 0
              var myfullNames  : String = ""
              var myrelationship: String = ""
              var mygender: String = ""
              var responseCode: Int = 1
              var responseMessage: String = ""
              val strResultSet_FetchSize: String = "10"

              //val sourceDataTable = new SQLServerDataTable
              sourceDataTable.addColumnMetadata("BeneficiaryId", java.sql.Types.NUMERIC)
              sourceDataTable.addColumnMetadata("MemberId", java.sql.Types.NUMERIC)
              sourceDataTable.addColumnMetadata("FullNames", java.sql.Types.VARCHAR)
              sourceDataTable.addColumnMetadata("Relationship", java.sql.Types.VARCHAR)
              sourceDataTable.addColumnMetadata("Gender", java.sql.Types.VARCHAR)

              statement.setFetchSize(strResultSet_FetchSize.toInt)

              statement.setBigDecimal(1, memberIdCbs)
              statement.registerOutParameter(2, OracleTypes.CURSOR)

              statement.execute()

              val rs = statement.getObject(2).asInstanceOf[ResultSet]
              if (rs != null){
                var k: Int = 0
                while (rs.next ()){
                  k = k + 1
                  mybeneficiaryId = 0
                  mymemberId = 0
                  myfullNames = ""
                  myrelationship = ""
                  mygender = ""
                  mybeneficiaryId = rs.getInt("BEN_ID")
                  mymemberId = rs.getInt("MEMBER_ID")
                  myfullNames = rs.getString("FULLNAMES")
                  myrelationship = rs.getString("RELATIONSHIP")
                  mygender = rs.getString("GENDER")
                  //val mymemberType = rs.getString("memberType")
                  //val myresponseCode = rs.getInt("responseCode")
                  //val myresponseMessage = rs.getString("responseMessage")

                  try{
                    if (myfullNames != null){
                      myfullNames = myfullNames.trim
                      if (myfullNames.length > 0){
                        myfullNames = myfullNames.replace("  "," ")
                        myfullNames = myfullNames.trim
                      }
                    }
                    else{
                      myfullNames = ""
                    }

                    if (myrelationship != null){
                      myrelationship = myrelationship.trim
                      if (myrelationship.length > 0){
                        myrelationship = myrelationship.replace(" ","")
                        myrelationship = myrelationship.trim
                      }
                    }
                    else{
                      myrelationship = ""
                    }

                    if (mygender != null){
                      mygender = mygender.trim
                      if (mygender.length > 0){
                        mygender = mygender.replace(" ","")
                        mygender = mygender.trim
                      }
                    }
                    else{
                      mygender = ""
                    }
                    sourceDataTable.addRow(mybeneficiaryId, mymemberId, myfullNames, myrelationship, mygender)
                  }
                  catch {
                    case io: Throwable =>
                      //Log_errors(strApifunction + " : " + io.getMessage())
                      Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured. ")
                    case ex: Exception =>
                      //Log_errors(strApifunction + " : " + ex.getMessage())
                      Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                  }

                }
              }
            }
          }
        }
      }
    }
    catch {
      case io: IOException =>
        //Log_errors("getMemberDetails : " + io.getMessage + " - io exception error occured. MotorRegNo - " + strMotorRegNo)
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured. ")
      case ex : Exception =>
        //Log_errors("getMemberDetails : " + ex.getMessage + " - ex exception error occured. MotorRegNo - " + strMotorRegNo)
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        //Log_errors("getMemberDetails : " + t.getMessage + " - t exception error occured. MotorRegNo - " + strMotorRegNo)
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }
    return  sourceDataTable
  }
  */
  //def sendProjectionBenefitsResponseEchannel(memberno: Int, statuscode: Int, statusdescription: String, myProjectionbenefitsdata: Seq[MemberProjectionBenefitsDetailsResponse_Batch]): Unit = {
  //def sendProjectionBenefitsResponseEchannel(memberno: Int, statuscode: Int, statusdescription: String, myProjectionbenefitsdata: MemberProjectionBenefitsDetailsResponse_Batch): Unit = {
  def sendProjectionBenefitsResponseEchannel(myProjectionbenefitsdata: MemberProjectionBenefitsDetailsResponse_BatchData, entryID: java.math.BigDecimal): Unit = {

    //val myuri : Uri = strESBLipaNaMpesaURL//"http://localhost:9000/processlipanampesarequests" //Maintain in DB
    //val myuri : Uri = "http://localhost:9000/getBeneficiaryDetails" //Maintain in DB
    //val myuri : Uri = "http://localhost:8067/sendProjectionBenefitsDetails" //Maintain in DB

    var strApiURL: String = ""
    var isSuccessful : Boolean = false
    var isValidData : Boolean = false
    var myPerson2 : String = ""
    var myjsonData : String = ""
    val strApifunction : String = "sendProjectionBenefitsResponseEchannel"
    //var entryID : java.math.BigDecimal = new java.math.BigDecimal(0)

    try{
      if (myProjectionbenefitsdata != null){// && myProjectionbenefitsdata != None
        isValidData = true
      }
      //Lets insert the entries for reference
      //entryID = InsertLogsOutgoingLipaNaMpesaRequests(myTotalAmount, isMultiPayment, myAmountOne, myAmountTwo, strPhoneNo, strSessionId, strAccountReferenceOne, strAccountReferenceTwo, myGroupPolicyPayment, myGroupPolicyType)
      strApiURL = getEchannelsProjectionBenefitsURL()
      if (strApiURL == null){
        strApiURL = ""
      }

      if (strApiURL.trim.length == 0){
        Log_errors(strApifunction + " : Failure in fetching  Api URL - " + strApiURL + " , application error occured.")
      }
    }
    catch{
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
    }

    val myuri : Uri = strApiURL //Maintain in DB

    try
    {
      if (isValidData == true){
        //val strCurrentTime : String = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)

        //val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberno, statuscode, statusdescription, myProjectionbenefitsdata).toJson
        //val myresponse_MemberProjectionBenefitsData =  MemberProjectionBenefitsDetailsResponse_BatchData(memberno, statuscode, statusdescription, myProjectionbenefitsdata)
        //val jsonResponse = Json.toJson(myresponse_MemberProjectionBenefitsData)

        //val jsonResponse = Json.toJson(myresponse_MemberProjectionBenefitsData)
        //myjsonData = myresponse_MemberProjectionBenefitsData.toString()
        implicit val MemberProjectionBenefitsDetailsResponse_BatchWrites = Json.writes[MemberProjectionBenefitsDetailsResponse_Batch]
        implicit val MemberProjectionBenefitsDetailsResponse_BatchDataWrites = Json.writes[MemberProjectionBenefitsDetailsResponse_BatchData]

        val jsonResponse = Json.toJson(myProjectionbenefitsdata)

        myjsonData = jsonResponse.toString()
      }
    }
    catch
      {
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
          Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
      }
    finally
    {
    }

    try
    {
      if (isValidData == true){
        val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val authorization = headers.Authorization(BasicHttpCredentials("Authorization","Bearer " + accessToken))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization","Bearer " + accessToken)))
        val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        val requestData : Future[String] = Future(myjsonData)
        var start_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time : Future[String] = Future(start_time_DB)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data))

        //TESTS ONLY
        //println("start 1: " + strApifunction + " " + start_time_DB+ " " + myjsonData)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + strApifunction + " " + res.status.intValue())
              if (res.status != None){
                if (res.status.intValue() == 200){
                  var mystatuscode: Int = 0
                  var strstatusdescription: String = ""
                  var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                  var strRequestData: String = ""
                  val myData = Unmarshal(res.entity).to[response_MemberProjectionBenefits_status]
                  var start_time_DB : String  = ""
                  val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                  if (myData != None){
                    //if (myData.value.get != None){
                    if (myData.value.getOrElse(None) != None){
                      val myResponse_MemberProjectionBenefits_status =  myData.value.get
                      if (myResponse_MemberProjectionBenefits_status.get != None){
                        //mystatuscode = myResponse_MemberProjectionBenefits_status.get.statuscode
                        //strstatusdescription = myResponse_MemberProjectionBenefits_status.get.statusdescription.toString
                        if (myResponse_MemberProjectionBenefits_status.get.statuscode != None){
                          val myData = myResponse_MemberProjectionBenefits_status.get.statuscode
                          mystatuscode = myData.get
                        }

                        if (myResponse_MemberProjectionBenefits_status.get.statusdescription != None){
                          val myData = myResponse_MemberProjectionBenefits_status.get.statusdescription
                          strstatusdescription = myData.get.toString
                        }
                      }

                      var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                      var strRequestData: String = ""
                      var start_time_DB : String  = ""
                      val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                      if (myEntryID.value.isEmpty != true){
                        if (myEntryID.value.get != None){
                          val myVal = myEntryID.value.get
                          if (myVal.get != None){
                            myTxnID = myVal.get
                          }
                        }
                      }

                      if (requestData.value.isEmpty != true){
                        if (requestData.value.get != None){
                          val myVal = requestData.value.get
                          if (myVal.get != None){
                            strRequestData = myVal.get
                          }
                        }
                      }

                      if (myStart_time.value.isEmpty != true){
                        if (myStart_time.value.get != None){
                          val myVal = myStart_time.value.get
                          if (myVal.get != None){
                            start_time_DB = myVal.get
                          }
                        }
                      }

                      val posted_to_Echannels: Boolean = true
                      val post_picked_Echannels: Boolean = true
                      val strDate_to_Echannels: String = start_time_DB
                      val strDate_from_Echannels: String = stop_time_DB
                      val myStatusCode_Echannels : Int = res.status.intValue()
                      val strStatusMessage_Echannels: String = "Successful"
                      updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)
                    }
                    else {
                      //Lets log the status code returned by CBS webservice
                      val myStatusCode_Cbs : Int = res.status.intValue()
                      val strStatusMessage_Cbs: String = "Failed"
                      //val strMessage: String = "status - " + myStatusCode_Cbs + ", status message - " + strStatusMessage_Cbs
                      //Log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None error occured.")

                      var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                      var strRequestData: String = ""
                      var start_time_DB : String  = ""
                      val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                      if (myEntryID.value.isEmpty != true){
                        if (myEntryID.value.get != None){
                          val myVal = myEntryID.value.get
                          if (myVal.get != None){
                            myTxnID = myVal.get
                          }
                        }
                      }

                      if (requestData.value.isEmpty != true){
                        if (requestData.value.get != None){
                          val myVal = requestData.value.get
                          if (myVal.get != None){
                            strRequestData = myVal.get
                          }
                        }
                      }

                      if (myStart_time.value.isEmpty != true){
                        if (myStart_time.value.get != None){
                          val myVal = myStart_time.value.get
                          if (myVal.get != None){
                            start_time_DB = myVal.get
                          }
                        }
                      }

                      val strMessage: String = "txnid - " + myTxnID + ", status - " + myStatusCode_Cbs + ", status message - " + strStatusMessage_Cbs
                      Log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None error occured.")

                      val posted_to_Echannels: Boolean = false
                      val post_picked_Echannels: Boolean = false
                      val strDate_to_Echannels: String = start_time_DB
                      val strDate_from_Echannels: String = stop_time_DB
                      val myStatusCode_Echannels : Int = res.status.intValue()
                      val strStatusMessage_Echannels: String = "Failure occured. No request was received the from API"

                      updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)

                    }
                  }

                  if (myEntryID.value.isEmpty != true){
                    if (myEntryID.value.get != None){
                      val myVal = myEntryID.value.get
                      if (myVal.get != None){
                        myTxnID = myVal.get
                      }
                    }
                  }

                  if (requestData.value.isEmpty != true){
                    if (requestData.value.get != None){
                      val myVal = requestData.value.get
                      if (myVal.get != None){
                        strRequestData = myVal.get
                      }
                    }
                  }

                  if (myStart_time.value.isEmpty != true){
                    if (myStart_time.value.get != None){
                      val myVal = myStart_time.value.get
                      if (myVal.get != None){
                        start_time_DB = myVal.get
                      }
                    }
                  }

                  val posted_to_Echannels: Boolean = true
                  val post_picked_Echannels: Boolean = true
                  val strDate_to_Echannels: String = start_time_DB
                  val strDate_from_Echannels: String = stop_time_DB
                  val myStatusCode_Echannels : Int = res.status.intValue()
                  val strStatusMessage_Echannels: String = "Successful"

                  updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)

                }
                else{
                  var mystatuscode: Int = 0
                  var strstatusdescription: String = ""
                  var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                  var strRequestData: String = ""

                  val myData = Unmarshal(res.entity).to[response_MemberProjectionBenefits_status]
                  var start_time_DB : String  = ""
                  val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                  if (myData != None){
                    if (myData.value.get != None){
                      val myResponse_MemberProjectionBenefits_status =  myData.value.get
                      if (myResponse_MemberProjectionBenefits_status.get != None){
                        val myresponse_MemberProjectionBenefits_status =  myData.value.get
                        if (myresponse_MemberProjectionBenefits_status.get != None){
                          //mystatuscode = myresponse_MemberProjectionBenefits_status.get.statuscode
                          //strstatusdescription = myresponse_MemberProjectionBenefits_status.get.statusdescription.toString
                          if (myResponse_MemberProjectionBenefits_status.get.statuscode != None){
                            val myData = myResponse_MemberProjectionBenefits_status.get.statuscode
                            mystatuscode = myData.get
                          }

                          if (myResponse_MemberProjectionBenefits_status.get.statusdescription != None){
                            val myData = myResponse_MemberProjectionBenefits_status.get.statusdescription
                            strstatusdescription = myData.get.toString
                          }

                        }
                      }
                    }
                  }

                  if (myEntryID.value.isEmpty != true){
                    if (myEntryID.value.get != None){
                      val myVal = myEntryID.value.get
                      if (myVal.get != None){
                        myTxnID = myVal.get
                      }
                    }
                  }

                  if (requestData.value.isEmpty != true){
                    if (requestData.value.get != None){
                      val myVal = requestData.value.get
                      if (myVal.get != None){
                        strRequestData = myVal.get
                      }
                    }
                  }

                  if (myStart_time.value.isEmpty != true){
                    if (myStart_time.value.get != None){
                      val myVal = myStart_time.value.get
                      if (myVal.get != None){
                        start_time_DB = myVal.get
                      }
                    }
                  }

                  val posted_to_Echannels: Boolean = true
                  val post_picked_Echannels: Boolean = true
                  val strDate_to_Echannels: String = start_time_DB
                  val strDate_from_Echannels: String = stop_time_DB
                  val myStatusCode_Echannels : Int = res.status.intValue()
                  val strStatusMessage_Echannels: String = "Failed processing"

                  updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)

                }
              }
            //println(res)
            case Failure(f)   =>
              //sys.error("something wrong")
              //println("start 3: " + strApifunction + " " + f.getMessage)
              try {

                Log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

                var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                var strRequestData: String = ""
                var start_time_DB : String  = ""
                val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                if (myEntryID.value.isEmpty != true){
                  if (myEntryID.value.get != None){
                    val myVal = myEntryID.value.get
                    if (myVal.get != None){
                      myTxnID = myVal.get
                    }
                  }
                }

                if (requestData.value.isEmpty != true){
                  if (requestData.value.get != None){
                    val myVal = requestData.value.get
                    if (myVal.get != None){
                      strRequestData = myVal.get
                    }
                  }
                }

                if (myStart_time.value.isEmpty != true){
                  if (myStart_time.value.get != None){
                    val myVal = myStart_time.value.get
                    if (myVal.get != None){
                      start_time_DB = myVal.get
                    }
                  }
                }

                val posted_to_Echannels: Boolean = false
                val post_picked_Echannels: Boolean = false
                val strDate_to_Echannels: String = start_time_DB
                val strDate_from_Echannels: String = stop_time_DB
                val myStatusCode_Echannels : Int = 404
                val strStatusMessage_Echannels: String = "Failure occured when sending the request to API"

                updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)
              }
              catch
                {
                  case ex: Exception =>
                    Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                  case t: Throwable =>
                    Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                }

          }
      }
    }
    catch
      {
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
          Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
      }
    finally
    {
      // your scala code here, such as to close a database connection
    }

  }
  def processUpdatePensionersVerification(sourceDataTable : SQLServerDataTable) : Unit = {
    val strApifunction : String = "processUpdatePensionersVerification"
    try {

      try {

        myDB.withConnection { implicit  myconn =>

          val strSQL : String = "{ call dbo.Process_Update_Pensioners_Verification_API(?) }"
          val mystmt : CallableStatement = myconn.prepareCall(strSQL)

          mystmt.setObject(1, sourceDataTable)
          mystmt.execute()
        }
      }catch {
        case ex: Exception =>
          Log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          Log_errors(strApifunction + " : " + t.getMessage + "exception error occured.")
      }finally {
        /*
        if (statement != null) {
          statement.close()
          statement = null
        }
        */
      }

    }catch {
      case ex: Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + "exception error occured.")
    }finally {
      /*
      if (conn != null) {
          conn.close()
          conn = null
        }
        */
      /*
      if (conn != null){
        if (conn.isClosed != true){
          conn.close()
        }
      }
      */
    }
    //}
    //return  myID
  }
  def getMemberId(myMemberNo: Int, strMemberType: String) : Int = {

    val strApifunction : String = "getMemberId"
    //var myMemberId: java.math.BigDecimal = new java.math.BigDecimal(0)
    var myMemberId: Int = 0

    val strSQL : String = "{ call dbo.GetMemberId(?,?,?) }"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.setInt(1,myMemberNo)
          mystmt.setString(2,strMemberType)
          mystmt.registerOutParameter("MemberId", java.sql.Types.INTEGER)
          mystmt.execute()
          myMemberId = mystmt.getInt("MemberId")
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " MemberNo - " + myMemberNo.toString()  + " MemberId - " + myMemberId.toString())
          case t: Throwable =>
            Log_errors(strApifunction + " : " + t.getMessage + " t exception error occured." + " MemberNo - " + myMemberNo.toString()  + " MemberId - " + myMemberId.toString())
        }

      }
    }catch {
      case ex : Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " MemberNo - " + myMemberNo.toString()  + " MemberId - " + myMemberId.toString())
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " t exception error occured." + " MemberNo - " + myMemberNo.toString()  + " MemberId - " + myMemberId.toString())
    }finally {

    }

    return  myMemberId
  }
  def insertEchannelsMemberProjectionBenefitsDetailsRequests(mySQLServerDataTable: SQLServerDataTable) : java.math.BigDecimal = {

    val strApifunction : String = "insertEchannelsMemberProjectionBenefitsDetailsRequests"
    var myTxnID: java.math.BigDecimal = new java.math.BigDecimal(0)

    val strSQL : String = "{ call dbo.insertEchannelsMemberProjectionBenefitsDetailsRequests(?,?) }"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.setObject(1, mySQLServerDataTable)
          mystmt.registerOutParameter("TxnID", java.sql.Types.NUMERIC)
          mystmt.execute()
          myTxnID = mystmt.getBigDecimal("TxnID")
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " TxnID - " + myTxnID.toString())
          case t: Throwable =>
            Log_errors(strApifunction + " : " + t.getMessage + " t exception error occured." + " TxnID - " + myTxnID.toString())
        }

      }
    }catch {
      case ex : Exception =>
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " TxnID - " + myTxnID.toString())
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " t exception error occured." + " TxnID - " + myTxnID.toString())
    }finally {

    }

    return  myTxnID
  }
  def updateMemberProjectionBenefitsDetailsRequests(myID: java.math.BigDecimal, Posted_to_Echannels: Boolean, Post_picked_Echannels: Boolean, strDate_to_Echannels: String, strDate_from_Echannels: String, myStatusCode_Echannels : Int, strStatusMessage_Echannels: String, strRequestData: String) : Boolean = {
    //var isPhoneUserRegistered: Boolean = false
    //var strDescription: String = "Error occured during processing, please try again."
    var isValidEntry : Boolean = false
    var isSuccessful : Boolean = false
    try{
      /*
      if (strDate_to_Mpesa != null && strDate_from_Mpesa != null){
        if (strDate_to_Mpesa.trim.length > 0 && strDate_from_Mpesa.trim.length > 0){
          isValidEntry = true
        }
        else {
          isValidEntry = false
        }
      }
      else {
        isValidEntry = false
      }
      */
      isValidEntry = true
      if (isValidEntry == true){
        myDB.withConnection { implicit  myconn =>

          val strSQL : String = "{ call dbo.UpdateEchannelsMemberProjectionBenefitsDetailsRequests(?,?,?,?,?,?,?,?) }"
          val mystmt : CallableStatement = myconn.prepareCall(strSQL)

          mystmt.setBigDecimal(1,myID)
          mystmt.setBoolean(2,Posted_to_Echannels)
          mystmt.setBoolean(3,Post_picked_Echannels)
          mystmt.setString(4,strDate_to_Echannels)
          mystmt.setString(5,strDate_from_Echannels)
          mystmt.setInt(6,myStatusCode_Echannels)
          mystmt.setString(7,strStatusMessage_Echannels)
          mystmt.setString(8,strRequestData)

          mystmt.executeUpdate()
          isSuccessful = true
        }
      }

    }
    catch
      {
        case ex: Exception =>
          Log_errors("UpdateLogsOutgoingLipaNaMpesaRequests : ID - " + myID + " , Error - " + ex.getMessage())
        case tr: Throwable =>
          Log_errors("UpdateLogsOutgoingLipaNaMpesaRequests : ID - " + myID + " , Error - " + tr.getMessage())
      }

    isSuccessful
  }
  def getCBSProjectionBenefitsURL(myMemberId: Int, myProjectionType: Int) : String = {

    var strCBSProjectionBenefitsURL : String = ""
    val strSQL : String = "{ call dbo.GetCBSProjectionBenefitsURL(?,?,?) }"
    val strApifunction : String = "GetCBSProjectionBenefitsURL"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("CBSProjectionBenefitsURL", java.sql.Types.VARCHAR)
          mystmt.setInt(1, myMemberId)
          mystmt.setInt(2, myProjectionType)
          mystmt.execute()
          strCBSProjectionBenefitsURL = mystmt.getString("CBSProjectionBenefitsURL")
          //println("strCBSProjectionBenefitsURL: " + strCBSProjectionBenefitsURL)
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        }
      }

    }catch {
      case io: IOException =>
        //io.printStackTrace()
        //strErrorMsg = io.toString
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        //ex.printStackTrace()
        //strErrorMsg = ex.toString
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }finally {
    }

    return  strCBSProjectionBenefitsURL
  }
  def getCBSProvisionalStatementURL(myMemberId: Int) : String = {

    var strCBSProvisionalStatementURL : String = ""
    val strSQL : String = "{ call dbo.GetCBSProvisionalStatementURL(?,?) }"
    val strApifunction : String = "GetCBSProvisionalStatementURL"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("CBSProvisionalStatementURL", java.sql.Types.VARCHAR)
          mystmt.setInt(1, myMemberId)
          mystmt.execute()
          strCBSProvisionalStatementURL = mystmt.getString("CBSProvisionalStatementURL")
          //println("strCBSProjectionBenefitsURL: " + strCBSProvisionalStatementURL)
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        }
      }

    }catch {
      case io: IOException =>
        //io.printStackTrace()
        //strErrorMsg = io.toString
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        //ex.printStackTrace()
        //strErrorMsg = ex.toString
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }finally {
    }

    return  strCBSProvisionalStatementURL
  }
  def getEchannelsProjectionBenefitsURL() : String = {

    var strEchannelsProjectionBenefitsURL : String = ""
    val strSQL : String = "{ call dbo.GetEchannelsProjectionBenefitsURL(?) }"
    val strApifunction : String = "GetEchannelsProjectionBenefitsURL"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("EchannelsProjectionBenefitsURL", java.sql.Types.VARCHAR)
          mystmt.execute()
          strEchannelsProjectionBenefitsURL = mystmt.getString("EchannelsProjectionBenefitsURL")
          //println("strEchannelsProjectionBenefitsURL: " + strEchannelsProjectionBenefitsURL)
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        }

      }

    }catch {
      case io: IOException =>
        //io.printStackTrace()
        //strErrorMsg = io.toString
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        //ex.printStackTrace()
        //strErrorMsg = ex.toString
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }finally {
    }


    return  strEchannelsProjectionBenefitsURL
  }
  def getCbsApiUserName() : String = {

    var strApiUserName : String = ""
    val strSQL : String = "{ call dbo.GetCbsApiUserName(?) }"
    val strApifunction : String = "getCbsApiUserName"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("ApiUserName", java.sql.Types.VARCHAR)
          mystmt.execute()
          strApiUserName = mystmt.getString("ApiUserName")
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        }

      }

    }catch {
      case io: IOException =>
        //io.printStackTrace()
        //strErrorMsg = io.toString
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        //ex.printStackTrace()
        //strErrorMsg = ex.toString
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }finally {
    }


    return  strApiUserName
  }
  def getCbsApiPassword() : String = {

    var strApiPassword : String = ""
    val strSQL : String = "{ call dbo.GetCbsApiPassword(?) }"
    val strApifunction : String = "getCbsApiUserName"

    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("ApiPassword", java.sql.Types.VARCHAR)
          mystmt.execute()
          strApiPassword = mystmt.getString("ApiPassword")
        }
        catch{
          case ex : Exception =>
            Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        }

      }

    }catch {
      case io: IOException =>
        //io.printStackTrace()
        //strErrorMsg = io.toString
        Log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        //ex.printStackTrace()
        //strErrorMsg = ex.toString
        Log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        Log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }finally {
    }


    return  strApiPassword
  }
  def Log_data(mydetail : String) : Unit = {
    //var strpath_file2 : String = "C:\\Program Files\\Biometric_System\\mps1\\Logs.txt"
    try{
      var strdetail = ""//println(new java.util.Date)
      val str_Date  = new SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date)
      //Lets create a new date-folder when date changes
      if (strFileDate.equals(str_Date)== false){
        //Initialise these two fields
        /*
        strpath_file = strApplication_path + "\\Logs"+ "\\Logs.txt"
        strpath_file2 = strApplication_path + "\\Logs" + "\\Errors.txt"
        var is_Successful : Boolean = create_Folderpaths
        writer_data = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file,true)))
        */
        var is_Successful : Boolean = create_Folderpaths(strApplication_path)
        strFileDate = str_Date
      }
      //var writer_data = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file2,true)))
      //writer.println(strdetail)
      //val requestDate : String =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      val requestDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)

      //strdetail  =  mydetail + " - " + new java.util.Date
      strdetail  =  mydetail + " - " + requestDate
      writer_data.append(strdetail)
      writer_data.append(System.lineSeparator())
      writer_data.append("======================================================================")
      writer_data.append(System.lineSeparator())
      if (writer_data != null) {
        //writer_data.close()
        //writer_data = null
        writer_data.flush()
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
      //strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
      //strErrorMsg = ex.toString
    }
    finally {
    }
  }
  def Log_errors(mydetail : String) : Unit = {
    //, strpath_file2 : String
    try{
      var strdetail = ""//println(new java.util.Date)
      val str_Date  = new SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date)
      //Lets create a new date-folder when date changes
      if (strFileDate.equals(str_Date)== false){
        //Initialise these two fields
        /*
        strpath_file = strApplication_path + "\\Logs"+ "\\Logs.txt"
        strpath_file2 = strApplication_path + "\\Logs" + "\\Errors.txt"
        var is_Successful : Boolean = create_Folderpaths
        writer_errors = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file2,true)))
        */
        var is_Successful : Boolean = create_Folderpaths(strApplication_path)
        strFileDate = str_Date
      }
      //var writer_errors = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file2,true)))
      //writer.println(strdetail)
      strdetail  =  mydetail + " - " + new java.util.Date
      writer_errors.append(strdetail)
      writer_errors.append(System.lineSeparator())
      writer_errors.append("======================================================================")
      writer_errors.append(System.lineSeparator())
      if (writer_errors != null) {
        writer_errors.flush()
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
      //strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
      //strErrorMsg = ex.toString
    }
    finally {
    }
  }
  def create_Folderpaths(strApplication_path : String): Boolean = {
    var is_Successful : Boolean = false
    var strpath_file : String = strApplication_path + "\\Logs"+ "\\Logs.txt"
    var strpath_file2 : String = strApplication_path + "\\Logs" + "\\Errors.txt"
    try{
      val str_Date  = new SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date)
      var m : Int = 0
      var n : Int = 0
      var strFileName : String = ""
      //if directory exists?
      //F:\my_Systems_2\Scala\Email\Doc\Logs.txt
      //we use "lastIndexOf" to remove "Logs.txt" get path as "F:\my_Systems_2\Scala\Email\Doc\"
      m = strpath_file.lastIndexOf("\\")
      n = m + 1
      strFileName =  strpath_file.substring(n)
      strpath_file = strpath_file.substring(0,m) + "\\" + str_Date

      if (!Files.exists(Paths.get(strpath_file))) {
        Files.createDirectories(Paths.get(strpath_file))
        is_Successful = true
      }
      else {
        is_Successful = true
      }
      strpath_file = strpath_file + "\\" + strFileName
      //F:\my_Systems_2\Scala\Email\Doc\Errors.txt
      //we use "lastIndexOf" to remove "Errors.txt" and get path as "F:\my_Systems_2\Scala\Email\Doc\"
      m = 0
      n = 0
      strFileName = ""
      m = strpath_file2.lastIndexOf("\\")
      n = m + 1
      strFileName =  strpath_file2.substring(n)
      strpath_file2 = strpath_file2.substring(0,m) + "\\" + str_Date

      if (!Files.exists(Paths.get(strpath_file2))) {
        Files.createDirectories(Paths.get(strpath_file2))
        is_Successful = true
      }
      else {
        is_Successful = true
      }
      strpath_file2 = strpath_file2 + "\\" + strFileName

      if (writer_data != null){
        writer_data = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file,true)))
      }
      if (writer_errors != null){
        writer_errors = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file2,true)))
      }

    }
    catch {
      case io: IOException => Log_errors("create_Folderpaths : " + io.getMessage + "exception error occured")
      //io.printStackTrace()
      case t: Throwable => Log_errors("create_Folderpaths : " + t.getMessage + "exception error occured")
      //strErrorMsg = io.toString
      case ex : Exception => Log_errors("create_Folderpaths : " + ex.getMessage + "exception error occured")
      //ex.printStackTrace()
    }
    finally {
    }
    return  is_Successful
  }
  def getAccountVerificationResponseDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "getaccountVerificationDetails"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 getAccountVerificationResponseDetails: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          //println("start 1 getAccountVerificationResponseDetails: Request data - " + strRequest)
        }
      }
      catch{
        case ex: Exception =>
          //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val messageIdentification: String = "001"
      val creationDateTime: String = "2021-03-22"
      val firstAgentIdentification: String = "2031"
      val assignerAgentIdentification: String = "2031"
      val assigneeAgentIdentification: String = "009"
      val originalMessageIdentification: String = "001"
      val originalCreationDateTime: String = "2021-03-22"
      val originalFirstAgentIdentification: String = "2031"
      val originalVerificationIdentification: String = "0002"
      val verificationStatus: String =  "true"//"false"
      val verificationReasonCode: String = ""//"Account does not exist"
      val originalBeneficiaryAccountNumber: String = "219277372626"
      val originalBeneficiarySchemeName: String = "PHNE"
      val originalBeneficiaryAgentIdentification: String = "2010"
      val updatedBeneficiaryAccountName: String = "Paul Wakimani"
      val updatedBeneficiaryAccountNumber: String = "219277372626"
      val updatedBeneficiarySchemeName: String = "PHNE"
      val updatedBeneficiaryAgentIdentification: String = "2010"
      val accountVerification = new AccountVerificationResponse(
        messageIdentification, creationDateTime, firstAgentIdentification,
        assignerAgentIdentification, assigneeAgentIdentification, originalMessageIdentification,
        originalCreationDateTime, originalFirstAgentIdentification, originalVerificationIdentification,
        verificationStatus, verificationReasonCode, originalBeneficiaryAccountNumber,
        originalBeneficiarySchemeName, originalBeneficiaryAgentIdentification, updatedBeneficiaryAccountName,
        updatedBeneficiaryAccountNumber, updatedBeneficiarySchemeName, updatedBeneficiaryAgentIdentification
        )
      val myData = accountVerification.toXml

      val myXmlResponseData = new xmlResponseData(myData.toString())

      val textResponse = Ok(myXmlResponseData.text.toString).as("text/xml")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def getSingleCreditTransferResponseDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "getSingleCreditTransferResponseDetails"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 getSingleCreditTransferResponseDetails: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          //println("start 2 getSingleCreditTransferResponseDetails: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def getBulkCreditTransferResponseDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "getBulkCreditTransferResponseDetails"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 getBulkCreditTransferResponseDetails: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          println("start 2 getBulkCreditTransferResponseDetails: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def getPaymentCancellationResponseDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "getPaymentCancellationResponseDetails"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 getPaymentCancellationResponseDetails: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          println("start 2 getPaymentCancellationResponseDetails: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def getPaymentStatusResponseDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "getPaymentStatusResponseDetails"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 getPaymentStatusResponseDetails: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          //println("start 2 getSingleCreditTransferResponseDetails: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def addAccountVerificationRequestEsbCbs = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "addAccountVerificationRequestEsbCbs"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 addAccountVerificationRequestEsbCbs: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          println("start 2 addAccountVerificationRequestEsbCbs: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def addCreditTransferRequestEsbCbs = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "addCreditTransferRequestEsbCbs"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 addCreditTransferRequestEsbCbs: Request received")
        if (!request.body.asXml.isEmpty){
          strRequest = request.body.asXml.get.toString()
          println("start 2 addCreditTransferRequestEsbCbs: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def addAccountVerificationResponseEsbCbs = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      val strApifunction : String = "addAccountVerificationResponseEsbCbs"
      var strRequest: String = ""

      implicit val xmlResponseData_Writes = Json.writes[xmlResponseData]

      try{
        println("start 1 addAccountVerificationResponseEsbCbs: Request received")
        if (!request.body.asJson.isEmpty){
          strRequest = request.body.asJson.get.toString()
          println("start 2 addAccountVerificationResponseEsbCbs: Request data - " + System.lineSeparator() + strRequest)
        }
      }
      catch{
        case ex: Exception =>
        //log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
        //log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
        //log_errors(strApifunction + " : " + tr.getMessage())
      }

      implicit val  AccountVerificationDetailsResponse_BatchWrites = Json.writes[AccountVerificationDetailsResponse]

      val myAccountVerificationResponse =  AccountVerificationDetailsResponse(0, "successful")
      val jsonResponse = Json.toJson(myAccountVerificationResponse)

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
}