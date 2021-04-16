package controllers

import java.io.{BufferedWriter, FileWriter, IOException, PrintWriter}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Paths}
import java.sql.{CallableStatement, ResultSet}
import java.text.SimpleDateFormat

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.db.{Database, NamedDatabase}
import javax.inject.Inject
import java.util.{Base64, Date}

import play.api.mvc.{AbstractController, ControllerComponents}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject.AbstractModule
import com.microsoft.sqlserver.jdbc.SQLServerDataTable
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

//import com.microsoft.sqlserver.jdbc.{SQLServerCallableStatement, SQLServerDataTable}
//import com.microsoft.sqlserver.jdbc.SQLServerDataTable
import play.api.libs.concurrent.CustomExecutionContext
//import scala.util.control.Breaks
//import scala.util.control.Breaks.break
import oracle.jdbc.OracleTypes
//import com.microsoft.sqlserver.jdbc.SQLServerDataTable
//(cc: ControllerComponents,myDB : Database,myExecutionContext: MyExecutionContext)
trait MyExecutionContext extends ExecutionContext

class MyExecutionContextImpl @Inject()(system: ActorSystem)
  extends CustomExecutionContext(system, "my-dispatcher") with MyExecutionContext

class MyExecutionContextModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MyExecutionContext])
      .to(classOf[MyExecutionContextImpl])

  }
}

class CbsEngine @Inject()
 //(myExecutionContext: MyExecutionContext,cc: ControllerComponents, @NamedDatabase("ebusiness") myDB : Database, @NamedDatabase("cbsdb") myCbsDB : Database)
(myExecutionContext: MyExecutionContext,cc: ControllerComponents, @NamedDatabase("ebusiness") myDB : Database)
  extends AbstractController(cc) {

  //case class PolicyDetails_Request(custnum : Int, dataformattype: Option[Int])
  //case class PolicyDetails_Request(custnum : String, dataformattype: Option[Int])
  case class MemberDetails_Request(memberno : String, rowcount: Option[Int])
  case class RegNumDetails_Request(custnum : String, dataformattype: Option[Int])
  case class MemberDetailsResponse_Batch(memberno: Int, fullnames: String, idno: Int, phoneno: String, membertype: String, statuscode: Int, statusdescription: String)
  case class MemberDetailsResponse_BatchData(memberdata: Seq[MemberDetailsResponse_Batch])
  //case class PolicyDetails_Batch(vehicleregno: String, covertype: String, suminsured: Int, premium: Int, periodfrom: java.util.Date, periodto: java.util.Date)
  case class PolicyDetails_Batch(vehicleregno: String, covertype: String, suminsured: Int, premium: Int, periodfrom: String, periodto: String)
  case class PolicyDetails_BatchData(PolicyDetailsData: Seq[PolicyDetails_Batch])

  //case class MiniStatement_Request(custnum : Int, dataformattype: Option[Int])
  case class Beneficiary_Request(memberno : String, rowcount: Option[Int])
  case class BeneficiaryDetailsResponse_Batch(memberno: Int, fullnames: String, relationship: String, gender: String, statuscode: Int, statusdescription: String)
  case class BeneficiaryDetailsResponse_BatchData(beneficiarydata: Seq[BeneficiaryDetailsResponse_Batch])
  //case class MiniStatement_Batch(vehicleregno: String, amount: Int, paymode: Int, trandate: String)
  //case class MiniStatement_Batch(vehicleregno: String, amount: Int, paymode: String, trandate: String)
  case class MiniStatement_Batch(vehicleregno: String, amount: Int, paymode: String, receiptnum : Int, trandate: String)
  case class MiniStatement_BatchData(MiniStatementData: Seq[MiniStatement_Batch])

  //case class ClaimDetailsReg_Request(custnum : Int, dataformattype: Option[Int])
  case class Balance_Request(memberno : String, rowcount: Option[Int])
  case class ClaimDetailsReg_Batch(claimnum : Int, vehicleregno: String, amount: Int, regdate: String)
  case class ClaimDetailsReg_BatchData(ClaimDetailsData: Seq[ClaimDetailsReg_Batch])

  //case class ClaimDetailsPaid_Request(custnum : Int, dataformattype: Option[Int])
  case class ProjectionBenefits_Request(memberno : String, rowcount: Option[Int])
  case class ClaimDetailsPaid_Batch(claimnum : Int, vehicleregno: String, amount: Int, paiddate: String)
  case class ClaimDetailsPaid_BatchData(ClaimDetailsData: Seq[ClaimDetailsPaid_Batch])

  case class MemberDetailsValidate_Request(mobileno : String, idno : String)
  //case class PolicyDetails_Batch(vehicleregno: String, covertype: String, suminsured: Int, premium: Int, periodfrom: String, periodto: String)
  //case class PolicyDetails_BatchData(PolicyDetailsData: Seq[PolicyDetails_Batch])

  case class RegNumDetails_Batch(vehicleregno: String)
  case class RegNumDetails_BatchData(RegNumDetailsData: Seq[RegNumDetails_Batch])

  //PensionerDetailsVerification
  case class PensionerDetailsVerification_Request(staffno: JsValue, pensionercode: JsValue, verificationdate: JsValue)
  case class PensionerDetailsVerification_BatchRequest(batchno: Option[Int], pensionersdata: Seq[PensionerDetailsVerification_Request])
  case class PensionerDetailsVerificationResponse_Batch(staffno: Int, pensionercode: String, statuscode: Int, statusdescription: String, verified_previous_cycle: Int, verified_cycle_return_date: String, previous_cycle_id: BigDecimal)
  case class PensionerDetailsVerificationResponse_BatchData(pensionersdata: Seq[PensionerDetailsVerificationResponse_Batch])

  //S2B_PaymentDetails i.e Straight to Bank Payments
  //case class S2B_PaymentDetails_Request(memberno: JsValue, idno: Option[JsValue], phoneno: Option[JsValue], channeltype: Option[JsValue])
  //case class S2B_PaymentDetails_Request(debitaccountnumber: Option[JsValue], accountname: Option[JsValue], customerreference: Option[JsValue], accountnumber: Option[JsValue], bankcode: Option[JsValue], branchcode: Option[JsValue], amount: Option[JsValue], description: Option[JsValue], paymenttype: Option[JsValue], emailaddress: Option[JsValue])
  case class S2B_PaymentDetails_Request(debitaccountnumber: Option[JsValue], accountname: Option[JsValue], customerreference: Option[JsValue], accountnumber: Option[JsValue], bankcode: Option[JsValue], localbankcode: Option[JsValue], branchcode: Option[JsValue], amount: Option[JsValue], description: Option[JsValue], paymenttype: Option[JsValue], purposeofpayment: Option[JsValue], emailaddress: Option[JsValue])
  case class S2B_PaymentDetails_BatchRequest(batchno: Option[Int], paymentdata: Seq[S2B_PaymentDetails_Request])
  case class S2B_PaymentDetailsResponse_Batch(accountnumber: String, bankcode: String, branchcode: String, customerreference: String, statuscode: Int, statusdescription: String)
  case class S2B_PaymentDetailsResponse_BatchData(paymentdata: Seq[S2B_PaymentDetailsResponse_Batch])

  //S2B_ForexPaymentDetails i.e Straight to Bank Forex Payments
  case class S2B_ForexPaymentDetails_Request(accountname: Option[JsValue], customerreference: Option[JsValue], accountnumber: Option[JsValue], bankcode: Option[JsValue], branchcode: Option[JsValue],
    paymentamount: Option[JsValue], paymentdetails: Option[JsValue], paymenttype: Option[JsValue], paymentcurrency: Option[JsValue], debitaccountnumber: Option[JsValue],
    emailaddress: Option[JsValue], fxtype: Option[JsValue], appliedamount: Option[JsValue], fxrate: Option[JsValue], dealnumber: Option[JsValue],
    dealername: Option[JsValue], directinverse: Option[JsValue], maturitydate: Option[JsValue], intermediarybankcode: Option[JsValue])
  case class S2B_ForexPaymentDetails_BatchRequest(batchno: Option[Int], paymentdata: Seq[S2B_ForexPaymentDetails_Request])
  case class S2B_ForexPaymentDetailsResponse_Batch(accountnumber: String, bankcode: String, branchcode: String, customerreference: String, statuscode: Int, statusdescription: String)
  case class S2B_ForexPaymentDetailsResponse_BatchData(paymentdata: Seq[S2B_ForexPaymentDetailsResponse_Batch])

  //Tax_PaymentDetails i.e Tax Payments to KRA through Bank
  case class Tax_PaymentDetails_Request(debitaccountnumber: Option[JsValue], prn: Option[JsValue], customerreference: Option[JsValue], amount: Option[JsValue])
  case class Tax_PaymentDetails_BatchRequest(batchno: Option[Int], paymentdata: Seq[Tax_PaymentDetails_Request])
  case class Tax_PaymentDetailsResponse_Batch(prn: String, customerreference: String, statuscode: Int, statusdescription: String)
  case class Tax_PaymentDetailsResponse_BatchData(paymentdata: Seq[Tax_PaymentDetailsResponse_Batch])

  //Coop_InternalTransfer_PaymentDetails i.e Coop Bank InternalTransfer Payments
  case class Coop_InternalTransfer_PaymentDetails_Request(debitaccountnumber: Option[JsValue], transactioncurrency_debitaccount: Option[JsValue], description_debitaccount: Option[JsValue], accountnumber: Option[JsValue], accountname: Option[JsValue], customerreference: Option[JsValue], amount: Option[JsValue], transactioncurrency_creditaccount: Option[JsValue], purposeofpayment: Option[JsValue], description_creditaccount: Option[JsValue], emailaddress: Option[JsValue])
  case class Coop_InternalTransfer_PaymentDetails_BatchRequest(batchno: Option[Int], paymentdata: Seq[Coop_InternalTransfer_PaymentDetails_Request])
  case class Coop_InternalTransfer_PaymentDetailsResponse_Batch(accountnumber: String, customerreference: String, statuscode: Int, statusdescription: String)
  case class Coop_InternalTransfer_PaymentDetailsResponse_BatchData(batchno: BigDecimal, paymentdata: Seq[Coop_InternalTransfer_PaymentDetailsResponse_Batch])

  //Coop_AcctoPesalink_PaymentDetails i.e Coop Bank Account-to-Pesalink Transfer Payments
  case class Coop_AcctoPesalink_PaymentDetails_Request(debitaccountnumber: Option[JsValue], transactioncurrency_debitaccount: Option[JsValue], description_debitaccount: Option[JsValue], accountnumber: Option[JsValue], accountname: Option[JsValue], bankcode: Option[JsValue],customerreference: Option[JsValue], amount: Option[JsValue], transactioncurrency_creditaccount: Option[JsValue], purposeofpayment: Option[JsValue], description_creditaccount: Option[JsValue], emailaddress: Option[JsValue])
  case class Coop_AcctoPesalink_PaymentDetails_BatchRequest(batchno: Option[Int], paymentdata: Seq[Coop_AcctoPesalink_PaymentDetails_Request])
  case class Coop_AcctoPesalink_PaymentDetailsResponse_Batch(accountnumber: String, customerreference: String, statuscode: Int, statusdescription: String)
  case class Coop_AcctoPesalink_PaymentDetailsResponse_BatchData(batchno: BigDecimal, paymentdata: Seq[Coop_AcctoPesalink_PaymentDetailsResponse_Batch])

  //Coop_AcctoMpesa_PaymentDetails i.e Coop Bank Account-to-Mpesa Transfer Payments
  case class Coop_AcctoMpesa_PaymentDetails_Request(debitaccountnumber: Option[JsValue], transactioncurrency_debitaccount: Option[JsValue], description_debitaccount: Option[JsValue], mobilenumber: Option[JsValue], customerreference: Option[JsValue], amount: Option[JsValue], purposeofpayment: Option[JsValue], description_creditaccount: Option[JsValue], emailaddress: Option[JsValue])
  case class Coop_AcctoMpesa_PaymentDetails_BatchRequest(batchno: Option[Int], paymentdata: Seq[Coop_AcctoMpesa_PaymentDetails_Request])
  case class Coop_AcctoMpesa_PaymentDetailsResponse_Batch(mobilenumber: String, customerreference: String, statuscode: Int, statusdescription: String)
  case class Coop_AcctoMpesa_PaymentDetailsResponse_BatchData(batchno: BigDecimal, paymentdata: Seq[Coop_AcctoMpesa_PaymentDetailsResponse_Batch])

  /* IPSL-ESB integration */
  case class TransactionResponse(statuscode: Option[spray.json.JsValue], statusdescription: Option[spray.json.JsValue])
  case object TransactionResponse extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val TransactionResponseFormat = jsonFormat2(TransactionResponse.apply)
  }

  object HttpStatusCode extends Enumeration {
    type HttpStatusCode = Value
    val Accepted, BadRequest, Ok, Unauthorized = Value
  }
  /*
  object SchemeMode extends Enumeration {
    type SchemeMode = Value
    val ACC, PHNE = Value
  }
  */
  object SchemeName extends Enumeration {
    type SchemeName = Value
    val ACC, PHNE = Value
  }

  //AccountVerification Details i.e AccountVerification request to IPSL through ESB
  //The request is initiated from ESB-CBS/Other CHannels
  case class AccountVerificationDetails_Request(transactionreference: Option[JsValue], accountnumber: Option[JsValue], schememode: Option[JsValue], bankcode: Option[JsValue])
  case class AccountVerificationDetails_BatchRequest(messagereference: Option[JsValue], accountdata: AccountVerificationDetails_Request)
  case class AccountVerificationDetailsResponse_Batch(transactionreference: String, accountnumber: String, accountname: String, bankcode: String)
  case class AccountVerificationDetailsResponse_BatchData(messagereference: String, statuscode: Int, statusdescription: String, accountdata: AccountVerificationDetailsResponse_Batch)
  case class AccountVerificationDetailsResponse(statuscode: Int, statusdescription: String)
  //AccountVerification Details i.e AccountVerification request from ESB
  case class AccountVerificationDetails(messagereference: String, creationdatetime: String, firstagentidentification: String, assigneragentidentification: String, assigneeagentidentification: String, transactionreference: String, accountnumber: String, schemename: String, bankcode: String)

  //SingleCreditTransfer Details i.e SingleCreditTransfer request to IPSL through ESB
  //The request is initiated from ESB-CBS/Other CHannels
  case class ContactInformation(phonenumber: Option[JsValue], emailaddress: Option[JsValue])
  case class DebitAccountInformation(debitaccountnumber: Option[JsValue], debitaccountname: Option[JsValue], debitcontactinformation: ContactInformation)
  case class CreditAccountInformation(creditaccountnumber: Option[JsValue], creditaccountname: Option[JsValue], schemename: Option[JsValue], bankcode: Option[JsValue], creditcontactinformation: ContactInformation)
  case class TransferPurposeInformation(purposecode: Option[JsValue], purposedescription: Option[JsValue])
  case class TransferRemittanceInformation(unstructured: Option[JsValue], taxremittancereferencenumber: Option[JsValue])
  case class TransferMandateInformation(mandateidentification: Option[JsValue], mandatedescription: Option[JsValue])
  case class CreditTransferPaymentInformation(transactionreference: Option[JsValue], amount: Option[JsValue], debitaccountinformation: DebitAccountInformation, creditaccountinformation: CreditAccountInformation, mandateinformation: TransferMandateInformation, remittanceinformation: TransferRemittanceInformation, purposeinformation: TransferPurposeInformation)
  case class SingleCreditTransferPaymentDetails_Request(messagereference: Option[JsValue], paymentdata: CreditTransferPaymentInformation)
  case class SingleCreditTransferPaymentDetailsResponse(statuscode: Int, statusdescription: String)
  //Below classes are used for mapping/holding data internally
  case class TransferDefaultInfo(firstagentidentification: String, assigneeagentidentification: String, chargebearer: String, settlementmethod: String, clearingsystem: String, servicelevel: String, localinstrumentcode: String, categorypurpose: String)
  case class ContactInfo(phonenumber: String)
  case class DebitAccountInfo(debitaccountnumber: String, debitaccountname: String, debitcontactinformation: ContactInfo, schemename: String)
  case class CreditAccountInfo(creditaccountnumber: String, creditaccountname: String, schemename: String, bankcode: String, creditcontactinformation: ContactInfo)
  case class TransferPurposeInfo(purposecode: String)
  case class TransferRemittanceInfo(unstructured: String, taxremittancereferencenumber: String)
  case class TransferMandateInfo(mandateidentification: String)
  case class CreditTransferPaymentInfo(transactionreference: String, amount: BigDecimal, debitaccountinformation: DebitAccountInfo, creditaccountinformation: CreditAccountInfo, mandateinformation: TransferMandateInfo, remittanceinformation: TransferRemittanceInfo, purposeinformation: TransferPurposeInfo, transferdefaultinformation: TransferDefaultInfo)
  case class SingleCreditTransferPaymentInfo(messagereference: String, creationdatetime: String, numberoftransactions: Int, totalinterbanksettlementamount: BigDecimal, paymentdata: CreditTransferPaymentInfo)

  /*** Xml data ***/
  //val prettyPrinter = new scala.xml.PrettyPrinter(80, 2)
  val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)
  /* AccountVerification */
  case class FirstAgentInformation(financialInstitutionIdentification: String)
  case class AssignerAgentInformation(financialInstitutionIdentification: String)
  case class AssigneeAgentInformation(financialInstitutionIdentification: String)
  case class AssignmentInformation(messageIdentification: String, creationDateTime: String, firstAgentInformation: FirstAgentInformation, assignerAgentInformation: AssignerAgentInformation, assigneeAgentInformation: AssigneeAgentInformation)

  case class AccountInformation(accountIdentification: String, schemeName: String)
  case class AgentInformation(financialInstitutionIdentification: String)
  case class PartyAndAccountIdentificationInformation(accountInformation: AccountInformation, agentInformation: AgentInformation)
  case class VerificationInformation(identification: String, partyAndAccountIdentificationInformation: PartyAndAccountIdentificationInformation)
  /* AccountVerificationResponse */
  case class OriginalAssignmentInformation(messageIdentification: String, creationDateTime: String, firstAgentInformation: FirstAgentInformation)

  case class UpdatedAccountInformation(accountIdentificationName: String, accountIdentification: String, schemeName: String)
  case class OriginalPartyAndAccountIdentificationInformation(accountInformation: AccountInformation, agentInformation: AgentInformation)
  case class UpdatedPartyAndAccountIdentificationInformation(updatedAccountInformation: UpdatedAccountInformation, agentInformation: AgentInformation)
  case class VerificationReportInformation(originalidentification: String, verificationstatus: String, verificationreasoncode: String, originalpartyandaccountidentificationinformation: OriginalPartyAndAccountIdentificationInformation, updatedpartyandaccountidentificationinformation: UpdatedPartyAndAccountIdentificationInformation)
  /* SingleCreditTransfer/BulkCreditTransfer */
  case class SettlementInformation(settlementmethod: String, clearingSystem: String)
  case class PaymentTypeInformation(servicelevel: String, localinstrumentcode: String, categorypurpose: String)
  case class InstructingAgentInformation(financialInstitutionIdentification: String)
  case class InstructedAgentInformation(financialInstitutionIdentification: String)
  case class GroupHeaderInformation(messageidentification: String, creationdatetime: String, numberoftransactions: Int, totalinterbanksettlementamount: BigDecimal,
                                    settlementinformation: SettlementInformation, paymenttypeinformation: PaymentTypeInformation,
                                    instructingagentinformation: InstructingAgentInformation, instructedagentinformation: InstructedAgentInformation)
  case class MandateRelatedInformation(mandateidentification: String)
  case class UltimateDebtorInformation(debtorname: String, debtororganisationidentification: String, debtorcontactphonenumber: String)
  case class InitiatingPartyInformation(organisationidentification: String)
  case class DebtorInformation(debtorname: String, debtororganisationidentification: String, debtorcontactphonenumber: String)
  case class DebtorAccountInformation(debtoraccountidentification: String, debtoraccountschemename: String, debtoraccountname: String)
  case class DebtorAgentInformation(financialInstitutionIdentification: String)
  case class CreditorAgentInformation(financialInstitutionIdentification: String)
  case class CreditorInformation(creditorname: String, creditororganisationidentification: String, creditorcontactphonenumber: String)
  case class CreditorAccountInformation(creditoraccountidentification: String, creditoraccountschemename: String, creditoraccountname: String)
  case class UltimateCreditorInformation(creditorname: String, creditororganisationidentification: String, creditorcontactphonenumber: String)
  case class PurposeInformation(purposecode: String)
  case class RemittanceInformation(unstructured: String, taxremittancereferencenumber: String)
  case class CreditTransferTransactionInformation(paymentendtoendidentification: String, interbanksettlementamount: BigDecimal, acceptancedatetime: String, chargebearer: String,
                                                  mandaterelatedinformation: MandateRelatedInformation, ultimatedebtorinformation: UltimateDebtorInformation, initiatingpartyinformation: InitiatingPartyInformation,
                                                  debtorinformation: DebtorInformation, debtoraccountinformation: DebtorAccountInformation, debtoragentinformation: DebtorAgentInformation,
                                                  creditoragentinformation: CreditorAgentInformation, creditorinformation: CreditorInformation, creditoraccountinformation: CreditorAccountInformation,
                                                  ultimatecreditorinformation: UltimateCreditorInformation, purposeinformation: PurposeInformation, remittanceinformation: RemittanceInformation)
  case class CreditTransferTransactionInformationBatch(creditTransfertransactioninformation: Seq[CreditTransferTransactionInformation])
  case class response_processUssdActions(text: String)
  case class textResponseData(text: String)
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
  //AccountVerification
  //class AccountVerification(var assignmentInformation: AssignmentInformation, var verificationInformation: VerificationInformation) {
  class AccountVerification(val assignmentInformation: AssignmentInformation, val verificationInformation: VerificationInformation) {

    // (a) convert AccountVerification fields to XML
    def toXml = {
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.023.001.02">
          <IdVrfctnReq>
            <Assgnmt>
              <MsgId>{assignmentInformation.messageIdentification}</MsgId>
              <CreDtTm>{assignmentInformation.creationDateTime}</CreDtTm>
              <FrstAgt>
                <FinInstnId>
                  <Othr>
                    <Id>{assignmentInformation.firstAgentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </FrstAgt>
              <Assgnr>
                <Agt>
                  <FinInstnId>
                    <Othr>
                      <Id>{assignmentInformation.assignerAgentInformation.financialInstitutionIdentification}</Id>
                    </Othr>
                  </FinInstnId>
                </Agt>
              </Assgnr>
              <Assgne>
                <Agt>
                  <FinInstnId>
                    <Othr>
                      <Id>{assignmentInformation.assigneeAgentInformation.financialInstitutionIdentification}</Id>
                    </Othr>
                  </FinInstnId>
                </Agt>
              </Assgne>
            </Assgnmt>
            <Vrfctn>
              <Id>{verificationInformation.identification}</Id>
              <PtyAndAcctId>
                <Acct>
                  <Othr>
                    <Id>{verificationInformation.partyAndAccountIdentificationInformation.accountInformation.accountIdentification}</Id>
                    <SchmeNm>
                      <Prtry>{verificationInformation.partyAndAccountIdentificationInformation.accountInformation.schemeName}</Prtry>
                    </SchmeNm>
                  </Othr>
                </Acct>
                <Agt>
                  <FinInstnId>
                    <Othr>
                      <Id>{verificationInformation.partyAndAccountIdentificationInformation.agentInformation.financialInstitutionIdentification}</Id>
                    </Othr>
                  </FinInstnId>
                </Agt>
              </PtyAndAcctId>
            </Vrfctn>
          </IdVrfctnReq>
        </Document>
    }

    override def toString =
      s"assignmentInformation: $assignmentInformation, verificationInformation: $verificationInformation"
  }

  object AccountVerification {

    // (b) convert XML to a AccountVerification
    def fromXml(node: scala.xml.Node): AccountVerification = {
      /*
        val symbol = (node \ "symbol").text
        val businessName = (node \ "businessName").text
        val price = (node \ "price").text.toDouble
      */
      val messageIdentification: String = (node \ "IdVrfctnReq" \ "Assgnmt" \ "MsgId").text
      val creationDateTime: String = (node \ "IdVrfctnReq" \ "Assgnmt" \ "CreDtTm").text
      val firstAgentIdentification: String = (node \ "IdVrfctnReq" \ "Assgnmt" \ "FrstAgt" \ "FinInstnId" \ "Othr" \ "Id").text
      val assignerAgentIdentification: String = (node \ "IdVrfctnReq" \ "Assgnmt" \ "Assgnr" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
      val assigneeAgentIdentification: String = (node \ "IdVrfctnReq" \ "Assgnmt" \ "Assgne" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
      val identification: String = (node \ "IdVrfctnReq" \ "Vrfctn" \ "Id").text
      val accountNumber: String = (node \ "IdVrfctnReq" \ "Vrfctn" \ "PtyAndAcctId" \ "Acct" \ "Othr" \ "Id").text
      val schemeName: String = (node \ "IdVrfctnReq" \ "Vrfctn" \ "PtyAndAcctId" \ "Acct" \ "Othr" \ "SchmeNm" \ "Prtry").text
      val agentIdentification: String = (node \ "IdVrfctnReq" \ "Vrfctn" \ "PtyAndAcctId" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
      val firstAgentInformation: FirstAgentInformation = FirstAgentInformation(firstAgentIdentification)
      val assignerAgentInformation: AssignerAgentInformation = AssignerAgentInformation(assignerAgentIdentification)
      val assigneeAgentInformation: AssigneeAgentInformation = AssigneeAgentInformation(assigneeAgentIdentification)
      val assignmentInformation: AssignmentInformation = AssignmentInformation(messageIdentification, creationDateTime, firstAgentInformation, assignerAgentInformation, assigneeAgentInformation)
      val accountInformation: AccountInformation = AccountInformation(accountNumber, schemeName)
      val agentInformation: AgentInformation = AgentInformation(agentIdentification)
      val partyAndAccountIdentificationInformation: PartyAndAccountIdentificationInformation = PartyAndAccountIdentificationInformation(accountInformation, agentInformation)
      val verificationInformation: VerificationInformation = VerificationInformation(identification, partyAndAccountIdentificationInformation)
      new AccountVerification(assignmentInformation, verificationInformation)
    }

  }

  //AccountVerificationResponse

  class AccountVerificationResponse(val assignmentInformation: AssignmentInformation, val originalassignmentinformation: OriginalAssignmentInformation, val verificationreportinformation: VerificationReportInformation) {

    // (a) convert AccountVerificationResponse fields to XML
    def toXml = {
      <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.024.001.02">
        <IdVrfctnRpt>
          <Assgnmt>
            <MsgId>{assignmentInformation.messageIdentification}</MsgId>
            <CreDtTm>{assignmentInformation.creationDateTime}</CreDtTm>
            <FrstAgt>
              <FinInstnId>
                <Othr>
                  <Id>{assignmentInformation.firstAgentInformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </FrstAgt>
            <Assgnr>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{assignmentInformation.assignerAgentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </Assgnr>
            <Assgne>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{assignmentInformation.assigneeAgentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </Assgne>
          </Assgnmt>
          <OrgnlAssgnmt>
            <MsgId>{originalassignmentinformation.messageIdentification}</MsgId>
            <CreDtTm>{originalassignmentinformation.creationDateTime}</CreDtTm>
            <FrstAgt>
              <FinInstnId>
                <Othr>
                  <Id>{originalassignmentinformation.firstAgentInformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </FrstAgt>
          </OrgnlAssgnmt>
          <Rpt>
            <OrgnlId>{verificationreportinformation.originalidentification}</OrgnlId>
            <Vrfctn>{verificationreportinformation.verificationstatus}</Vrfctn>
            <Rsn>
              <Cd>{verificationreportinformation.verificationreasoncode}</Cd>
            </Rsn>
            <OrgnlPtyAndAcctId>
              <Acct>
                <Othr>
                  <Id>{verificationreportinformation.originalpartyandaccountidentificationinformation.accountInformation.accountIdentification}</Id>
                  <SchmeNm>
                    <Prtry>{verificationreportinformation.originalpartyandaccountidentificationinformation.accountInformation.schemeName}</Prtry>
                  </SchmeNm>
                </Othr>
              </Acct>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{verificationreportinformation.originalpartyandaccountidentificationinformation.agentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </OrgnlPtyAndAcctId>
            <UpdtdPtyAndAcctId>
              <Pty>
                <Nm>{verificationreportinformation.updatedpartyandaccountidentificationinformation.updatedAccountInformation.accountIdentificationName}</Nm>
              </Pty>
              <Acct>
                <Othr>
                  <Id>{verificationreportinformation.updatedpartyandaccountidentificationinformation.updatedAccountInformation.accountIdentification}</Id>
                  <SchmeNm>
                    <Prtry>{verificationreportinformation.updatedpartyandaccountidentificationinformation.updatedAccountInformation.schemeName}</Prtry>
                  </SchmeNm>
                </Othr>
              </Acct>
              <Agt>
                <FinInstnId>
                  <Othr>
                    <Id>{verificationreportinformation.updatedpartyandaccountidentificationinformation.agentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </Agt>
            </UpdtdPtyAndAcctId>
          </Rpt>
        </IdVrfctnRpt>
      </Document>
    }

    override def toString =
      s"assignmentInformation: $assignmentInformation, originalassignmentinformation: $originalassignmentinformation, verificationreportinformation: $verificationreportinformation"
    }

  object AccountVerificationResponse {

    // (b) convert XML to a AccountVerificationResponse
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

      val firstAgentInformation: FirstAgentInformation = FirstAgentInformation(firstAgentIdentification)
      val assignerAgentInformation: AssignerAgentInformation = AssignerAgentInformation(assignerAgentIdentification)
      val assigneeAgentInformation: AssigneeAgentInformation = AssigneeAgentInformation(assigneeAgentIdentification)
      val assignmentInformation: AssignmentInformation = AssignmentInformation(messageIdentification, creationDateTime, firstAgentInformation, assignerAgentInformation, assigneeAgentInformation)

      val originalFirstAgentInformation: FirstAgentInformation = FirstAgentInformation(originalFirstAgentIdentification)
      val originalAssignmentInformation: OriginalAssignmentInformation = OriginalAssignmentInformation(originalMessageIdentification, originalCreationDateTime, originalFirstAgentInformation)

      val originalPartyAccountInformation: AccountInformation = AccountInformation(originalBeneficiaryAccountNumber, originalBeneficiarySchemeName)
      val originalPartyAgentInformation: AgentInformation = AgentInformation(originalBeneficiaryAgentIdentification)
      val originalPartyAndAccountIdentificationInformation: OriginalPartyAndAccountIdentificationInformation = OriginalPartyAndAccountIdentificationInformation(originalPartyAccountInformation, originalPartyAgentInformation)

      val updatedAccountInformation: UpdatedAccountInformation = UpdatedAccountInformation(updatedBeneficiaryAccountName, updatedBeneficiaryAccountNumber, updatedBeneficiarySchemeName)
      val updatedAgentInformation: AgentInformation = AgentInformation(updatedBeneficiaryAgentIdentification)
      val updatedPartyAndAccountIdentificationInformation: UpdatedPartyAndAccountIdentificationInformation = UpdatedPartyAndAccountIdentificationInformation(updatedAccountInformation, updatedAgentInformation)

      val verificationReportInformation: VerificationReportInformation = VerificationReportInformation(originalVerificationIdentification, verificationStatus, verificationReasonCode, originalPartyAndAccountIdentificationInformation, updatedPartyAndAccountIdentificationInformation)

      new AccountVerificationResponse(assignmentInformation, originalAssignmentInformation, verificationReportInformation)
    }

  }

  //SingleCreditTransfer
  class SingleCreditTransfer(val groupHeaderInformation: GroupHeaderInformation, val creditTransferTransactionInformation: CreditTransferTransactionInformation) {

    // (a) convert SingleCreditTransfer fields to XML
    def toXml = {
      <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09">
        <FIToFICstmrCdtTrf>
          <GrpHdr>
            <MsgId>{groupHeaderInformation.messageidentification}</MsgId>
            <CreDtTm>{groupHeaderInformation.creationdatetime}</CreDtTm>
            <NbOfTxs>{groupHeaderInformation.numberoftransactions}</NbOfTxs>
            <SttlmInf>
              <SttlmMtd>{groupHeaderInformation.settlementinformation.settlementmethod}</SttlmMtd>
              <ClrSys>
                <Prtry>{groupHeaderInformation.settlementinformation.clearingSystem}</Prtry>
              </ClrSys>
            </SttlmInf>
            <PmtTpInf>
              <SvcLvl>
                <Prtry>{groupHeaderInformation.paymenttypeinformation.servicelevel}</Prtry>
              </SvcLvl>
              <LclInstrm>
                <Cd>{groupHeaderInformation.paymenttypeinformation.localinstrumentcode}</Cd>
              </LclInstrm>
              <CtgyPurp>
                <Prtry>{groupHeaderInformation.paymenttypeinformation.categorypurpose}</Prtry>
              </CtgyPurp>
            </PmtTpInf>
            <InstgAgt>
              <FinInstnId>
                <Othr>
                  <Id>{groupHeaderInformation.instructingagentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </InstgAgt>
            <InstdAgt>
              <FinInstnId>
                <Othr>
                  <Id>{groupHeaderInformation.instructedagentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </InstdAgt>
          </GrpHdr>
          <CdtTrfTxInf>
            <PmtId>
              <EndToEndId>{creditTransferTransactionInformation.paymentendtoendidentification}</EndToEndId>
            </PmtId>
            <IntrBkSttlmAmt>{creditTransferTransactionInformation.interbanksettlementamount}</IntrBkSttlmAmt>
            <AccptncDtTm>{creditTransferTransactionInformation.acceptancedatetime}</AccptncDtTm>
            <ChrgBr>{creditTransferTransactionInformation.chargebearer}</ChrgBr>
            <MndtRltdInf>
              <MndtId>{creditTransferTransactionInformation.mandaterelatedinformation.mandateidentification}</MndtId>
            </MndtRltdInf>
            <UltmtDbtr>
              <Nm>{creditTransferTransactionInformation.ultimatedebtorinformation.debtorname}</Nm>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{creditTransferTransactionInformation.ultimatedebtorinformation.debtororganisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
              <CtctDtls>
                <PhneNb>{creditTransferTransactionInformation.ultimatedebtorinformation.debtorcontactphonenumber}</PhneNb>
              </CtctDtls>
            </UltmtDbtr>
            <InitgPty>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{creditTransferTransactionInformation.initiatingpartyinformation.organisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
            </InitgPty>
            <Dbtr>
              <Nm>{creditTransferTransactionInformation.debtorinformation.debtorname}</Nm>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{creditTransferTransactionInformation.debtorinformation.debtororganisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
              <CtctDtls>
                <PhneNb>{creditTransferTransactionInformation.debtorinformation.debtorcontactphonenumber}</PhneNb>
              </CtctDtls>
            </Dbtr>
            <DbtrAcct>
              <Id>
                <Othr>
                  <Id>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountidentification}</Id>
                  <SchmeNm>
                    <Prtry>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountschemename}</Prtry>
                  </SchmeNm>
                </Othr>
              </Id>
              <Nm>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountname}</Nm>
            </DbtrAcct>
            <DbtrAgt>
              <FinInstnId>
                <Othr>
                  <Id>{creditTransferTransactionInformation.debtoragentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </DbtrAgt>
            <CdtrAgt>
              <FinInstnId>
                <Othr>
                  <Id>{creditTransferTransactionInformation.creditoragentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </CdtrAgt>
            <Cdtr>
              <Nm>{creditTransferTransactionInformation.creditorinformation.creditorname}</Nm>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{creditTransferTransactionInformation.creditorinformation.creditororganisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
              <CtctDtls>
                <PhneNb>{creditTransferTransactionInformation.creditorinformation.creditorcontactphonenumber}</PhneNb>
              </CtctDtls>
            </Cdtr>
            <CdtrAcct>
              <Id>
                <Othr>
                  <Id>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountidentification}</Id>
                  <SchmeNm>
                    <Prtry>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountschemename}</Prtry>
                  </SchmeNm>
                </Othr>
              </Id>
              <Nm>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountname}</Nm>
            </CdtrAcct>
            <UltmtCdtr>
              <Nm>{creditTransferTransactionInformation.ultimatecreditorinformation.creditorname}</Nm>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{creditTransferTransactionInformation.ultimatecreditorinformation.creditororganisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
              <CtctDtls>
                <PhneNb>{creditTransferTransactionInformation.ultimatecreditorinformation.creditorcontactphonenumber}</PhneNb>
              </CtctDtls>
            </UltmtCdtr>
            <Purp>
              <Prtry>{creditTransferTransactionInformation.purposeinformation.purposecode}</Prtry>
            </Purp>
            <RmtInf>
              <Ustrd>{creditTransferTransactionInformation.remittanceinformation.unstructured}</Ustrd>
              <Strd>
                <TaxRmt>
                  <RefNb>{creditTransferTransactionInformation.remittanceinformation.taxremittancereferencenumber}</RefNb>
                </TaxRmt>
              </Strd>
            </RmtInf>
          </CdtTrfTxInf>
        </FIToFICstmrCdtTrf>
      </Document>
    }

    override def toString =
    s"groupHeaderInformation: $groupHeaderInformation, creditTransferTransactionInformation: $creditTransferTransactionInformation"
  }

  object SingleCreditTransfer {

    // (b) convert XML to a SingleCreditTransfer
    def fromXml(node: scala.xml.Node):SingleCreditTransfer = {
      //Group Header Information
      val messageidentification: String = ""
      val creationdatetime: String = ""
      val numberoftransactions: Int = 0
      val totalinterbanksettlementamount: BigDecimal = 0
      val settlementinformation: SettlementInformation = SettlementInformation("","")
      val paymenttypeinformation: PaymentTypeInformation = PaymentTypeInformation("","","")
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation("")
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation("")
      val groupHeaderInformation = GroupHeaderInformation(
        messageidentification, creationdatetime, numberoftransactions, totalinterbanksettlementamount,
        settlementinformation, paymenttypeinformation,
        instructingagentinformation, instructedagentinformation
      )
      //Credit Transfer Transaction Information
      val paymentendtoendidentification: String = ""
      val interbanksettlementamount: BigDecimal = 0
      val acceptancedatetime: String = ""
      val chargebearer: String = ""
      val mandaterelatedinformation: MandateRelatedInformation = MandateRelatedInformation("")
      val ultimatedebtorinformation: UltimateDebtorInformation = UltimateDebtorInformation("","","")
      val initiatingpartyinformation: InitiatingPartyInformation = InitiatingPartyInformation("")
      val debtorinformation: DebtorInformation = DebtorInformation("","","")
      val debtoraccountinformation: DebtorAccountInformation = DebtorAccountInformation("","","")
      val debtoragentinformation: DebtorAgentInformation = DebtorAgentInformation("")
      val creditoragentinformation: CreditorAgentInformation = CreditorAgentInformation("")
      val creditorinformation: CreditorInformation = CreditorInformation("","","")
      val creditoraccountinformation: CreditorAccountInformation = CreditorAccountInformation("","","")
      val ultimatecreditorinformation: UltimateCreditorInformation = UltimateCreditorInformation("","","")
      val purposeinformation: PurposeInformation = PurposeInformation("")
      val remittanceinformation: RemittanceInformation = RemittanceInformation("","")
      val creditTransferTransactionInformation = CreditTransferTransactionInformation(
        paymentendtoendidentification, interbanksettlementamount, acceptancedatetime, chargebearer,
        mandaterelatedinformation, ultimatedebtorinformation, initiatingpartyinformation,
        debtorinformation, debtoraccountinformation, debtoragentinformation,
        creditoragentinformation, creditorinformation, creditoraccountinformation,
        ultimatecreditorinformation, purposeinformation, remittanceinformation
      )

      new SingleCreditTransfer(groupHeaderInformation, creditTransferTransactionInformation)
    }

  }

  //BulkCreditTransfer
  class BulkCreditTransfer(var groupHeaderInformation: GroupHeaderInformation, var creditTransferTransactionInformationBatch: Seq[CreditTransferTransactionInformation]) {

    // (a) convert BulkCreditTransfer fields to XML
    def toXml = {
      /*
      <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09">
        <FIToFICstmrCdtTrf>
        </FIToFICstmrCdtTrf>
      </Document>
      */
      val x = toXmlGroupHeaderInformation
      val groupHeaderInfo: String = x.toString
      val y = toXmlCreditTransferTransactionInformation
      val creditTransferTransactionInfo = y.toString
      /*
      val z = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">\n  <FIToFICstmrCdtTrf>\n" + groupHeaderInfo + System.lineSeparator() + creditTransferTransactionInfo + System.lineSeparator() + "</FIToFICstmrCdtTrf>\n      </Document>"
      }
      */
      /*
      val z = {
        //"<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">\n        <FIToFICstmrCdtTrf>\n        </FIToFICstmrCdtTrf>\n      </Document>"
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">\n        <FIToFICstmrCdtTrf>\n        " +
          "        " + groupHeaderInfo + System.lineSeparator() +
          "        " + creditTransferTransactionInfo + System.lineSeparator() +
          "</FIToFICstmrCdtTrf>\n      </Document>"
      }
      */
      val z = {
        //"<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">\n        <FIToFICstmrCdtTrf>\n        </FIToFICstmrCdtTrf>\n      </Document>"
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\"><FIToFICstmrCdtTrf>" +
          groupHeaderInfo + System.lineSeparator() +
          creditTransferTransactionInfo + System.lineSeparator() +
          "</FIToFICstmrCdtTrf></Document>"
      }

      val t: scala.xml.Node = scala.xml.XML.loadString(z)
      val bulkCreditTransfer = prettyPrinter.format(t)
      bulkCreditTransfer
    }
    def toXmlGroupHeaderInformation = {
          <GrpHdr>
            <MsgId>{groupHeaderInformation.messageidentification}</MsgId>
            <CreDtTm>{groupHeaderInformation.creationdatetime}</CreDtTm>
            <NbOfTxs>{groupHeaderInformation.numberoftransactions}</NbOfTxs>
            <TtlIntrBkSttlmAmt>{groupHeaderInformation.totalinterbanksettlementamount}</TtlIntrBkSttlmAmt>
            <SttlmInf>
              <SttlmMtd>{groupHeaderInformation.settlementinformation.settlementmethod}</SttlmMtd>
              <ClrSys>
                <Prtry>{groupHeaderInformation.settlementinformation.clearingSystem}</Prtry>
              </ClrSys>
            </SttlmInf>
            <PmtTpInf>
              <SvcLvl>
                <Prtry>{groupHeaderInformation.paymenttypeinformation.servicelevel}</Prtry>
              </SvcLvl>
              <LclInstrm>
                <Cd>{groupHeaderInformation.paymenttypeinformation.localinstrumentcode}</Cd>
              </LclInstrm>
              <CtgyPurp>
                <Prtry>{groupHeaderInformation.paymenttypeinformation.categorypurpose}</Prtry>
              </CtgyPurp>
            </PmtTpInf>
            <InstgAgt>
              <FinInstnId>
                <Othr>
                  <Id>{groupHeaderInformation.instructingagentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </InstgAgt>
            <InstdAgt>
              <FinInstnId>
                <Othr>
                  <Id>{groupHeaderInformation.instructedagentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </InstdAgt>
          </GrpHdr>
    }
    def toXmlCreditTransferTransactionInformation = {
      //var creditTransferTransactionBatch = Seq[xml.NodeSeq]()
      var creditTransferTransactionBatch: String = ""
      try{
        //println("start 1 toXmlCreditTransferTransactionInformation: Request received")
        if (!creditTransferTransactionInformationBatch.isEmpty){
          //println("start 2 toXmlCreditTransferTransactionInformation: Request received")
          if (creditTransferTransactionInformationBatch.length > 0){
            //println("start 3 toXmlCreditTransferTransactionInformation: Request received")
            creditTransferTransactionInformationBatch.foreach(creditTransferTransactionInformation => {
              //println("start 4 toXmlCreditTransferTransactionInformation: Request received")
              val creditTransferTransaction: xml.NodeSeq = {
                <CdtTrfTxInf>
                  <PmtId>
                    <EndToEndId>{creditTransferTransactionInformation.paymentendtoendidentification}</EndToEndId>
                  </PmtId>
                  <IntrBkSttlmAmt>{creditTransferTransactionInformation.interbanksettlementamount}</IntrBkSttlmAmt>
                  <AccptncDtTm>{creditTransferTransactionInformation.acceptancedatetime}</AccptncDtTm>
                  <ChrgBr>{creditTransferTransactionInformation.chargebearer}</ChrgBr>
                  <MndtRltdInf>
                    <MndtId>{creditTransferTransactionInformation.mandaterelatedinformation.mandateidentification}</MndtId>
                  </MndtRltdInf>
                  <UltmtDbtr>
                    <Nm>{creditTransferTransactionInformation.ultimatedebtorinformation.debtorname}</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>{creditTransferTransactionInformation.ultimatedebtorinformation.debtororganisationidentification}</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                    <CtctDtls>
                      <PhneNb>{creditTransferTransactionInformation.ultimatedebtorinformation.debtorcontactphonenumber}</PhneNb>
                    </CtctDtls>
                  </UltmtDbtr>
                  <InitgPty>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>{creditTransferTransactionInformation.initiatingpartyinformation.organisationidentification}</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                  </InitgPty>
                  <Dbtr>
                    <Nm>{creditTransferTransactionInformation.debtorinformation.debtorname}</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>{creditTransferTransactionInformation.debtorinformation.debtororganisationidentification}</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                    <CtctDtls>
                      <PhneNb>{creditTransferTransactionInformation.debtorinformation.debtorcontactphonenumber}</PhneNb>
                    </CtctDtls>
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <Othr>
                        <Id>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountidentification}</Id>
                        <SchmeNm>
                          <Prtry>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountschemename}</Prtry>
                        </SchmeNm>
                      </Othr>
                    </Id>
                    <Nm>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountname}</Nm>
                  </DbtrAcct>
                  <DbtrAgt>
                    <FinInstnId>
                      <Othr>
                        <Id>{creditTransferTransactionInformation.debtoragentinformation.financialInstitutionIdentification}</Id>
                      </Othr>
                    </FinInstnId>
                  </DbtrAgt>
                  <CdtrAgt>
                    <FinInstnId>
                      <Othr>
                        <Id>{creditTransferTransactionInformation.creditoragentinformation.financialInstitutionIdentification}</Id>
                      </Othr>
                    </FinInstnId>
                  </CdtrAgt>
                  <Cdtr>
                    <Nm>{creditTransferTransactionInformation.creditorinformation.creditorname}</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>{creditTransferTransactionInformation.creditorinformation.creditororganisationidentification}</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                    <CtctDtls>
                      <PhneNb>{creditTransferTransactionInformation.creditorinformation.creditorcontactphonenumber}</PhneNb>
                    </CtctDtls>
                  </Cdtr>
                  <CdtrAcct>
                    <Id>
                      <Othr>
                        <Id>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountidentification}</Id>
                        <SchmeNm>
                          <Prtry>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountschemename}</Prtry>
                        </SchmeNm>
                      </Othr>
                    </Id>
                    <Nm>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountname}</Nm>
                  </CdtrAcct>
                  <UltmtCdtr>
                    <Nm>{creditTransferTransactionInformation.ultimatecreditorinformation.creditorname}</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>{creditTransferTransactionInformation.ultimatecreditorinformation.creditororganisationidentification}</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                    <CtctDtls>
                      <PhneNb>{creditTransferTransactionInformation.ultimatecreditorinformation.creditorcontactphonenumber}</PhneNb>
                    </CtctDtls>
                  </UltmtCdtr>
                  <Purp>
                    <Prtry>{creditTransferTransactionInformation.purposeinformation.purposecode}</Prtry>
                  </Purp>
                  <RmtInf>
                    <Ustrd>{creditTransferTransactionInformation.remittanceinformation.unstructured}</Ustrd>
                    <Strd>
                      <TaxRmt>
                        <RefNb>{creditTransferTransactionInformation.remittanceinformation.taxremittancereferencenumber}</RefNb>
                      </TaxRmt>
                    </Strd>
                  </RmtInf>
                </CdtTrfTxInf>
              }
              //println("toXmlCreditTransferTransactionInformation: creditTransferTransaction - " + creditTransferTransactionBatch.toString())
              //creditTransferTransactionBatch = creditTransferTransactionBatch :+ creditTransferTransaction
              if (creditTransferTransactionBatch.length == 0){
                creditTransferTransactionBatch = creditTransferTransactionBatch + creditTransferTransaction.toString()
              }
              else{
                creditTransferTransactionBatch = creditTransferTransactionBatch + System.lineSeparator() + creditTransferTransaction.toString()
              }
            })
          }
        }
      }
      catch {
        case io: Throwable =>
          //log_errors(strApifunction + " : " + io.getMessage())
        case ex: Exception =>
          //log_errors(strApifunction + " : " + ex.getMessage())
      }
      //println("toXmlCreditTransferTransactionInformation:creditTransferTransactionBatch - " + creditTransferTransactionBatch.toString())
      creditTransferTransactionBatch
    }
    override def toString =
      s"groupHeaderInformation: $groupHeaderInformation, creditTransferTransactionInformation: $creditTransferTransactionInformationBatch"
  }

  object BulkCreditTransfer {

    // (b) convert XML to a BulkCreditTransfer
    def fromXml(node: scala.xml.Node):BulkCreditTransfer = {
      //Group Header Information
      val messageidentification: String = ""
      val creationdatetime: String = ""
      val numberoftransactions: Int = 0
      val totalinterbanksettlementamount: BigDecimal = 0
      val settlementinformation: SettlementInformation = SettlementInformation("","")
      val paymenttypeinformation: PaymentTypeInformation = PaymentTypeInformation("","","")
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation("")
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation("")
      val groupHeaderInformation = GroupHeaderInformation(
        messageidentification, creationdatetime, numberoftransactions, totalinterbanksettlementamount,
        settlementinformation, paymenttypeinformation,
        instructingagentinformation, instructedagentinformation
      )
      //Credit Transfer Transaction Information
      val paymentendtoendidentification: String = ""
      val interbanksettlementamount: BigDecimal = 0
      val acceptancedatetime: String = ""
      val chargebearer: String = ""
      val mandaterelatedinformation: MandateRelatedInformation = MandateRelatedInformation("")
      val ultimatedebtorinformation: UltimateDebtorInformation = UltimateDebtorInformation("","","")
      val initiatingpartyinformation: InitiatingPartyInformation = InitiatingPartyInformation("")
      val debtorinformation: DebtorInformation = DebtorInformation("","","")
      val debtoraccountinformation: DebtorAccountInformation = DebtorAccountInformation("","","")
      val debtoragentinformation: DebtorAgentInformation = DebtorAgentInformation("")
      val creditoragentinformation: CreditorAgentInformation = CreditorAgentInformation("")
      val creditorinformation: CreditorInformation = CreditorInformation("","","")
      val creditoraccountinformation: CreditorAccountInformation = CreditorAccountInformation("","","")
      val ultimatecreditorinformation: UltimateCreditorInformation = UltimateCreditorInformation("","","")
      val purposeinformation: PurposeInformation = PurposeInformation("")
      val remittanceinformation: RemittanceInformation = RemittanceInformation("","")
      val creditTransferTransactionInformation = CreditTransferTransactionInformation(
        paymentendtoendidentification, interbanksettlementamount, acceptancedatetime, chargebearer,
        mandaterelatedinformation, ultimatedebtorinformation, initiatingpartyinformation,
        debtorinformation, debtoraccountinformation, debtoragentinformation,
        creditoragentinformation, creditorinformation, creditoraccountinformation,
        ultimatecreditorinformation, purposeinformation, remittanceinformation
      )
      var creditTransfertransactioninformationbatch = Seq[CreditTransferTransactionInformation]()
      creditTransfertransactioninformationbatch = creditTransfertransactioninformationbatch :+ creditTransferTransactionInformation
      new BulkCreditTransfer(groupHeaderInformation, creditTransfertransactioninformationbatch)
    }
  }

  /*** Xml data ***/

  implicit val system = ActorSystem("CbsEngine")
  implicit val materializer = ActorMaterializer()

  implicit val blockingDispatcher = system.dispatchers.lookup("my-dispatcher")
  //implicit val timeout = Timeout(15 seconds)

  val myCodeESBmemberDetails : Int = 1
  val myCodeESBbeneficiaryDetails : Int = 2
  val myCodeESBbalanceDetails : Int = 3
  val myCodeESBprojectionBenefitsDetails : Int = 4
  val myCodeESBvalidateMemberDetails : Int = 5
  val myCodeESBupdatePensionersVerification : Int = 6
  val myCodeESBRegnumDetails : Int = 0

  val strApplication_path : String = System.getProperty("user.dir")
  var strFileDate  = new SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date)
  val strpath_file : String = strApplication_path + "\\Logs\\" + strFileDate + "\\Logs.txt"
  val strpath_file2 : String = strApplication_path + "\\Logs\\" + strFileDate + "\\Errors.txt"
  var is_Successful : Boolean = create_Folderpaths(strApplication_path)
  var writer_data = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file,true)))
  var writer_errors = new PrintWriter(new BufferedWriter(new FileWriter(strpath_file2,true)))
  val strDateRegex: String = "^((19|2[0-9])[0-9]{2})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$" //"yyyy-mm-dd"
  val strNumbersOnlyRegex: String = "[0-9]+" //validate numbers only
  //
  val firstAgentIdentification: String = "2031" //Request creator
  val assignerAgentIdentification: String = "2031" //party that deliveres request to IPSL
  val assigneeAgentIdentification: String = "009" //party that processes request i.e IPSL
  //
  val chargeBearer: String = "SLEV"
  val settlementMethod: String = "CLRG"
  val clearingSystem: String = "IPS"
  val serviceLevel: String = "P2PT"
  val localInstrumentCode: String = "INST"
  val categoryPurpose: String = "IBNK"

  def addSingleCreditTransferPaymentDetails = Action.async { request =>
    Future {
      val startDate: String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var entryID: Int = 0
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      var myS2B_PaymentDetailsResponse_BatchData: Seq[S2B_PaymentDetailsResponse_Batch] = Seq.empty[S2B_PaymentDetailsResponse_Batch]
      val strApifunction: String = "addsinglecredittransferpaymentdetails"
      var myHttpStatusCode = HttpStatusCode.BadRequest

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

        if (!request.body.asJson.isEmpty) {
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
                  if (strRequestHeader.toLowerCase.contains("bearer")){
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
        log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound && isAuthTokenFound){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":")){
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound) {
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
                //responseCode = mystmt.getInt("responseCode")
                val respCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")

                if (respCode != null){
                  responseCode = respCode
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val ContactInformation_Reads: Reads[ContactInformation] = (
              (JsPath \ "phonenumber").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(ContactInformation.apply _)

            implicit val DebitAccountInformation_Reads: Reads[DebitAccountInformation] = (
              (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "debitaccountname").readNullable[JsValue] and
                (JsPath \ "debitcontactinformation").read[ContactInformation]
              )(DebitAccountInformation.apply _)

            implicit val CreditAccountInformation_Reads: Reads[CreditAccountInformation] = (
              (JsPath \ "creditaccountnumber").readNullable[JsValue] and
                (JsPath \ "creditaccountname").readNullable[JsValue] and
                (JsPath \ "schemename").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue] and
                (JsPath \ "creditcontactinformation").read[ContactInformation]
              )(CreditAccountInformation.apply _)

            implicit val TransferPurposeInformation_Reads: Reads[TransferPurposeInformation] = (
              (JsPath \ "purposecode").readNullable[JsValue] and
                (JsPath \ "purposedescription").readNullable[JsValue]
              )(TransferPurposeInformation.apply _)

            implicit val TransferRemittanceInformation_Reads: Reads[TransferRemittanceInformation] = (
              (JsPath \ "unstructured").readNullable[JsValue] and
                (JsPath \ "taxremittancereferencenumber").readNullable[JsValue]
              )(TransferRemittanceInformation.apply _)

            implicit val TransferMandateInformation_Reads: Reads[TransferMandateInformation] = (
              (JsPath \ "mandateidentification").readNullable[JsValue] and
                (JsPath \ "mandatedescription").readNullable[JsValue]
              )(TransferMandateInformation.apply _)

            implicit val CreditTransferPaymentInformation_Reads: Reads[CreditTransferPaymentInformation] = (
              (JsPath \ "transactionreference").readNullable[JsValue] and
                (JsPath \ "amount").readNullable[JsValue] and
                (JsPath \ "debitaccountinformation").read[DebitAccountInformation] and
                (JsPath \ "creditaccountinformation").read[CreditAccountInformation] and
                (JsPath \ "mandateinformation").read[TransferMandateInformation] and
                (JsPath \ "remittanceinformation").read[TransferRemittanceInformation] and
                (JsPath \ "purposeinformation").read[TransferPurposeInformation]
              )(CreditTransferPaymentInformation.apply _)

            implicit val SingleCreditTransferPaymentDetails_Request_Reads: Reads[SingleCreditTransferPaymentDetails_Request] = (
              (JsPath \ "messagereference").readNullable[JsValue] and
                (JsPath \ "paymentdata").read[CreditTransferPaymentInformation]
              )(SingleCreditTransferPaymentDetails_Request.apply _)

            myjson.validate[SingleCreditTransferPaymentDetails_Request] match {
              case JsSuccess(myPaymentDetails, _) => {

                var isValidInputData : Boolean = false
                //var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  //myBatchSize = myS2B_PaymentDetails_BatchRequest.paymentdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("LocalBankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BranchCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Amount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PaymentType", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PurposeofPayment", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("EmailAddress", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)


                    var myAmount: BigDecimal = 0
                    var strAmount: String = ""

                    var messageidentification: String = ""

                    var paymentendtoendidentification: String = ""
                    var interbanksettlementamount: BigDecimal = 0
                    var totalinterbanksettlementamount: BigDecimal = 0
                    var mandateidentification: String = ""
                    //debtor
                    var debtorinformationdebtorname: String = ""
                    val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
                    var debtorinformationdebtorcontactphonenumber: String = ""
                    var debtoraccountinformationdebtoraccountidentification: String = ""
                    val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
                    var debtoraccountinformationdebtoraccountname: String = ""
                    val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
                    //creditor
                    var creditoragentinformationfinancialInstitutionIdentification: String = ""
                    var creditorinformationcreditorname: String = ""
                    var creditorinformationcreditororganisationidentification: String = ""
                    var creditorinformationcreditorcontactphonenumber: String = ""
                    var creditoraccountinformationcreditoraccountidentification: String = ""
                    var creditoraccountinformationcreditoraccountschemename: String = ""
                    var creditoraccountinformationcreditoraccountname: String = ""
                    //other details
                    var purposeinformationpurposecode: String = ""
                    var remittanceinformationunstructured: String = ""
                    var remittanceinformationtaxremittancereferencenumber: String = ""
                    //default values
                    val instructingagentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
                    val instructedagentinformationfinancialInstitutionIdentification: String = assigneeAgentIdentification //i.e IPSL
                    val initiatingpartyinformationorganisationidentification: String = firstAgentIdentification
                    val chargebearer: String = chargeBearer
                    val settlementmethod: String = settlementMethod
                    val clearingsystem: String = clearingSystem
                    val servicelevel: String = serviceLevel
                    val localinstrumentcode: String = localInstrumentCode
                    val categorypurpose: String = categoryPurpose

                    val creationDateTime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    /*
                    val schemeName: String = {
                      mySchemeMode match {
                        case 0 =>
                          SchemeName.ACC.toString.toUpperCase
                        case 1 =>
                          SchemeName.PHNE.toString.toUpperCase
                        case _ =>
                          SchemeName.ACC.toString.toUpperCase
                      }
                    }
                    */
                    val numberoftransactions: Int = 1
                    val acceptancedatetime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)

                    val accSchemeName: String = SchemeName.ACC.toString.toUpperCase
                    val phneSchemeName: String = SchemeName.PHNE.toString.toUpperCase

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    //myS2B_PaymentDetails_BatchRequest.paymentdata.foreach(myPaymentDetails => {

                    myAmount = 0
                    strAmount = ""
                    messageidentification = ""

                    paymentendtoendidentification = ""
                    interbanksettlementamount = 0
                    totalinterbanksettlementamount = 0
                    mandateidentification = ""
                    //debtor
                    debtorinformationdebtorname = ""
                    debtorinformationdebtorcontactphonenumber = ""
                    debtoraccountinformationdebtoraccountidentification = ""
                    debtoraccountinformationdebtoraccountname = ""
                    //creditor
                    creditoragentinformationfinancialInstitutionIdentification = ""
                    creditorinformationcreditorname = ""
                    creditorinformationcreditororganisationidentification = ""
                    creditorinformationcreditorcontactphonenumber = ""
                    creditoraccountinformationcreditoraccountidentification = ""
                    creditoraccountinformationcreditoraccountschemename = ""
                    creditoraccountinformationcreditoraccountname = ""
                    //other details
                    purposeinformationpurposecode = ""
                    remittanceinformationunstructured = ""
                    remittanceinformationtaxremittancereferencenumber = ""

                    try{
                      //messageidentification
                      if (myPaymentDetails.messagereference != None) {
                        if (myPaymentDetails.messagereference.get != None) {
                          val myData = myPaymentDetails.messagereference.get
                          messageidentification = myData.toString()
                          if (messageidentification != null && messageidentification != None){
                            messageidentification = messageidentification.trim
                            if (messageidentification.length > 0){
                              messageidentification = messageidentification.replace("'","")//Remove apostrophe
                              messageidentification = messageidentification.replace(" ","")//Remove spaces
                              messageidentification = messageidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              messageidentification = messageidentification.trim
                            }
                          }
                        }
                      }

                      //paymentendtoendidentification
                      if (myPaymentDetails.paymentdata.transactionreference != None) {
                        if (myPaymentDetails.paymentdata.transactionreference.get != None) {
                          val myData = myPaymentDetails.paymentdata.transactionreference.get
                          paymentendtoendidentification = myData.toString()
                          if (paymentendtoendidentification != null && paymentendtoendidentification != None){
                            paymentendtoendidentification = paymentendtoendidentification.trim
                            if (paymentendtoendidentification.length > 0){
                              paymentendtoendidentification = paymentendtoendidentification.replace("'","")//Remove apostrophe
                              paymentendtoendidentification = paymentendtoendidentification.replace(" ","")//Remove spaces
                              paymentendtoendidentification = paymentendtoendidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              paymentendtoendidentification = paymentendtoendidentification.trim
                            }
                          }
                        }
                      }

                      //strAmount
                      if (myPaymentDetails.paymentdata.amount != None) {
                        if (myPaymentDetails.paymentdata.amount.get != None) {
                          val myData = myPaymentDetails.paymentdata.amount.get
                          strAmount = myData.toString()
                          if (strAmount != null && strAmount != None){
                            strAmount = strAmount.trim
                            if (strAmount.length > 0){
                              strAmount = strAmount.replace("'","")//Remove apostrophe
                              strAmount = strAmount.replace(" ","")//Remove spaces
                              strAmount = strAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strAmount = strAmount.trim
                              if (strAmount.length > 0){
                                //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                val isNumeric : Boolean = strAmount.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                if (isNumeric){
                                  myAmount = BigDecimal(strAmount)
                                  interbanksettlementamount = myAmount
                                  totalinterbanksettlementamount = myAmount
                                }
                              }
                            }
                          }
                        }
                      }

                      //mandateidentification
                      if (myPaymentDetails.paymentdata.mandateinformation.mandateidentification != None) {
                        if (myPaymentDetails.paymentdata.mandateinformation.mandateidentification.get != None) {
                          val myData = myPaymentDetails.paymentdata.mandateinformation.mandateidentification.get
                          mandateidentification = myData.toString()
                          if (mandateidentification != null && mandateidentification != None){
                            mandateidentification = mandateidentification.trim
                            if (mandateidentification.length > 0){
                              mandateidentification = mandateidentification.replace("'","")//Remove apostrophe
                              mandateidentification = mandateidentification.replace(" ","")//Remove spaces
                              mandateidentification = mandateidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              mandateidentification = mandateidentification.trim
                            }
                          }
                        }
                      }

                      //debtorinformationdebtorname
                      if (myPaymentDetails.paymentdata.debitaccountinformation.debitaccountname != None) {
                        if (myPaymentDetails.paymentdata.debitaccountinformation.debitaccountname.get != None) {
                          val myData = myPaymentDetails.paymentdata.debitaccountinformation.debitaccountname.get
                          debtorinformationdebtorname = myData.toString()
                          if (debtorinformationdebtorname != null && debtorinformationdebtorname != None){
                            debtorinformationdebtorname = debtorinformationdebtorname.trim
                            if (debtorinformationdebtorname.length > 0){
                              debtorinformationdebtorname = debtorinformationdebtorname.replace("'","")//Remove apostrophe
                              debtorinformationdebtorname = debtorinformationdebtorname.replace("  "," ")//Remove double spaces
                              debtorinformationdebtorname = debtorinformationdebtorname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              debtorinformationdebtorname = debtorinformationdebtorname.trim
                            }
                          }
                        }
                      }

                      //debtorinformationdebtorcontactphonenumber
                      if (myPaymentDetails.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber != None) {
                        if (myPaymentDetails.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber.get != None) {
                          val myData = myPaymentDetails.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber.get
                          debtorinformationdebtorcontactphonenumber = myData.toString()
                          if (debtorinformationdebtorcontactphonenumber != null && debtorinformationdebtorcontactphonenumber != None){
                            debtorinformationdebtorcontactphonenumber = debtorinformationdebtorcontactphonenumber.trim
                            if (debtorinformationdebtorcontactphonenumber.length > 0){
                              debtorinformationdebtorcontactphonenumber = debtorinformationdebtorcontactphonenumber.replace("'","")//Remove apostrophe
                              debtorinformationdebtorcontactphonenumber = debtorinformationdebtorcontactphonenumber.replace(" ","")//Remove spaces
                              debtorinformationdebtorcontactphonenumber = debtorinformationdebtorcontactphonenumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              debtorinformationdebtorcontactphonenumber = debtorinformationdebtorcontactphonenumber.trim
                            }
                          }
                        }
                      }

                      //debtoraccountinformationdebtoraccountidentification
                      if (myPaymentDetails.paymentdata.debitaccountinformation.debitaccountnumber != None) {
                        if (myPaymentDetails.paymentdata.debitaccountinformation.debitaccountnumber.get != None) {
                          val myData = myPaymentDetails.paymentdata.debitaccountinformation.debitaccountnumber.get
                          debtoraccountinformationdebtoraccountidentification = myData.toString()
                          if (debtoraccountinformationdebtoraccountidentification != null && debtoraccountinformationdebtoraccountidentification != None){
                            debtoraccountinformationdebtoraccountidentification = debtoraccountinformationdebtoraccountidentification.trim
                            if (debtoraccountinformationdebtoraccountidentification.length > 0){
                              debtoraccountinformationdebtoraccountidentification = debtoraccountinformationdebtoraccountidentification.replace("'","")//Remove apostrophe
                              debtoraccountinformationdebtoraccountidentification = debtoraccountinformationdebtoraccountidentification.replace(" ","")//Remove spaces
                              debtoraccountinformationdebtoraccountidentification = debtoraccountinformationdebtoraccountidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              debtoraccountinformationdebtoraccountidentification = debtoraccountinformationdebtoraccountidentification.trim
                            }
                          }
                        }
                      }

                      //debtoraccountinformationdebtoraccountname
                      if (myPaymentDetails.paymentdata.debitaccountinformation.debitaccountname != None) {
                        if (myPaymentDetails.paymentdata.debitaccountinformation.debitaccountname.get != None) {
                          val myData = myPaymentDetails.paymentdata.debitaccountinformation.debitaccountname.get
                          debtoraccountinformationdebtoraccountname = myData.toString()
                          if (debtoraccountinformationdebtoraccountname != null && debtoraccountinformationdebtoraccountname != None){
                            debtoraccountinformationdebtoraccountname = debtoraccountinformationdebtoraccountname.trim
                            if (debtoraccountinformationdebtoraccountname.length > 0){
                              debtoraccountinformationdebtoraccountname = debtoraccountinformationdebtoraccountname.replace("'","")//Remove apostrophe
                              debtoraccountinformationdebtoraccountname = debtoraccountinformationdebtoraccountname.replace("  "," ")//Remove double spaces
                              debtoraccountinformationdebtoraccountname = debtoraccountinformationdebtoraccountname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              debtoraccountinformationdebtoraccountname = debtoraccountinformationdebtoraccountname.trim
                            }
                          }
                        }
                      }

                      //creditorinformationcreditorname
                      if (myPaymentDetails.paymentdata.creditaccountinformation.creditaccountname != None) {
                        if (myPaymentDetails.paymentdata.creditaccountinformation.creditaccountname.get != None) {
                          val myData = myPaymentDetails.paymentdata.creditaccountinformation.creditaccountname.get
                          creditorinformationcreditorname = myData.toString()
                          if (creditorinformationcreditorname != null && creditorinformationcreditorname != None){
                            creditorinformationcreditorname = creditorinformationcreditorname.trim
                            if (creditorinformationcreditorname.length > 0){
                              creditorinformationcreditorname = creditorinformationcreditorname.replace("'","")//Remove apostrophe
                              creditorinformationcreditorname = creditorinformationcreditorname.replace("  "," ")//Remove double spaces
                              creditorinformationcreditorname = creditorinformationcreditorname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditorinformationcreditorname = creditorinformationcreditorname.trim
                            }
                          }
                        }
                      }

                      //creditorinformationcreditorcontactphonenumber
                      if (myPaymentDetails.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber != None) {
                        if (myPaymentDetails.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber.get != None) {
                          val myData = myPaymentDetails.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber.get
                          creditorinformationcreditorcontactphonenumber = myData.toString()
                          if (creditorinformationcreditorcontactphonenumber != null && creditorinformationcreditorcontactphonenumber != None){
                            creditorinformationcreditorcontactphonenumber = creditorinformationcreditorcontactphonenumber.trim
                            if (creditorinformationcreditorcontactphonenumber.length > 0){
                              creditorinformationcreditorcontactphonenumber = creditorinformationcreditorcontactphonenumber.replace("'","")//Remove apostrophe
                              creditorinformationcreditorcontactphonenumber = creditorinformationcreditorcontactphonenumber.replace(" ","")//Remove spaces
                              creditorinformationcreditorcontactphonenumber = creditorinformationcreditorcontactphonenumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditorinformationcreditorcontactphonenumber = creditorinformationcreditorcontactphonenumber.trim
                            }
                          }
                        }
                      }

                      //creditoraccountinformationcreditoraccountidentification
                      if (myPaymentDetails.paymentdata.creditaccountinformation.creditaccountnumber != None) {
                        if (myPaymentDetails.paymentdata.creditaccountinformation.creditaccountnumber.get != None) {
                          val myData = myPaymentDetails.paymentdata.creditaccountinformation.creditaccountnumber.get
                          creditoraccountinformationcreditoraccountidentification = myData.toString()
                          if (creditoraccountinformationcreditoraccountidentification != null && creditoraccountinformationcreditoraccountidentification != None){
                            creditoraccountinformationcreditoraccountidentification = creditoraccountinformationcreditoraccountidentification.trim
                            if (creditoraccountinformationcreditoraccountidentification.length > 0){
                              creditoraccountinformationcreditoraccountidentification = creditoraccountinformationcreditoraccountidentification.replace("'","")//Remove apostrophe
                              creditoraccountinformationcreditoraccountidentification = creditoraccountinformationcreditoraccountidentification.replace(" ","")//Remove spaces
                              creditoraccountinformationcreditoraccountidentification = creditoraccountinformationcreditoraccountidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditoraccountinformationcreditoraccountidentification = creditoraccountinformationcreditoraccountidentification.trim
                            }
                          }
                        }
                      }

                      //creditoraccountinformationcreditoraccountschemename
                      if (myPaymentDetails.paymentdata.creditaccountinformation.schemename != None) {
                        if (myPaymentDetails.paymentdata.creditaccountinformation.schemename.get != None) {
                          val myData = myPaymentDetails.paymentdata.creditaccountinformation.schemename.get
                          creditoraccountinformationcreditoraccountschemename = myData.toString()
                          if (creditoraccountinformationcreditoraccountschemename != null && creditoraccountinformationcreditoraccountschemename != None){
                            creditoraccountinformationcreditoraccountschemename = creditoraccountinformationcreditoraccountschemename.trim
                            if (creditoraccountinformationcreditoraccountschemename.length > 0){
                              creditoraccountinformationcreditoraccountschemename = creditoraccountinformationcreditoraccountschemename.replace("'","")//Remove apostrophe
                              creditoraccountinformationcreditoraccountschemename = creditoraccountinformationcreditoraccountschemename.replace(" ","")//Remove spaces
                              creditoraccountinformationcreditoraccountschemename = creditoraccountinformationcreditoraccountschemename.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditoraccountinformationcreditoraccountschemename = creditoraccountinformationcreditoraccountschemename.trim
                            }
                          }
                        }
                      }

                      //creditoraccountinformationcreditoraccountname
                      if (myPaymentDetails.paymentdata.creditaccountinformation.creditaccountname != None) {
                        if (myPaymentDetails.paymentdata.creditaccountinformation.creditaccountname.get != None) {
                          val myData = myPaymentDetails.paymentdata.creditaccountinformation.creditaccountname.get
                          creditoraccountinformationcreditoraccountname = myData.toString()
                          if (creditoraccountinformationcreditoraccountname != null && creditoraccountinformationcreditoraccountname != None){
                            creditoraccountinformationcreditoraccountname = creditoraccountinformationcreditoraccountname.trim
                            if (creditoraccountinformationcreditoraccountname.length > 0){
                              creditoraccountinformationcreditoraccountname = creditoraccountinformationcreditoraccountname.replace("'","")//Remove apostrophe
                              creditoraccountinformationcreditoraccountname = creditoraccountinformationcreditoraccountname.replace("  "," ")//Remove double spaces
                              creditoraccountinformationcreditoraccountname = creditoraccountinformationcreditoraccountname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditoraccountinformationcreditoraccountname = creditoraccountinformationcreditoraccountname.trim
                            }
                          }
                        }
                      }

                      //creditoragentinformationfinancialInstitutionIdentification
                      if (myPaymentDetails.paymentdata.creditaccountinformation.bankcode != None) {
                        if (myPaymentDetails.paymentdata.creditaccountinformation.bankcode.get != None) {
                          val myData = myPaymentDetails.paymentdata.creditaccountinformation.bankcode.get
                          creditoragentinformationfinancialInstitutionIdentification = myData.toString()
                          if (creditoragentinformationfinancialInstitutionIdentification != null && creditoragentinformationfinancialInstitutionIdentification != None){
                            creditoragentinformationfinancialInstitutionIdentification = creditoragentinformationfinancialInstitutionIdentification.trim
                            if (creditoragentinformationfinancialInstitutionIdentification.length > 0){
                              creditoragentinformationfinancialInstitutionIdentification = creditoragentinformationfinancialInstitutionIdentification.replace("'","")//Remove apostrophe
                              creditoragentinformationfinancialInstitutionIdentification = creditoragentinformationfinancialInstitutionIdentification.replace(" ","")//Remove spaces
                              creditoragentinformationfinancialInstitutionIdentification = creditoragentinformationfinancialInstitutionIdentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditoragentinformationfinancialInstitutionIdentification = creditoragentinformationfinancialInstitutionIdentification.trim
                            }
                          }
                        }
                      }

                      //creditorinformationcreditororganisationidentification
                      creditorinformationcreditororganisationidentification = creditoragentinformationfinancialInstitutionIdentification


                      //purposeinformationpurposecode
                      if (myPaymentDetails.paymentdata.purposeinformation.purposecode != None) {
                        if (myPaymentDetails.paymentdata.purposeinformation.purposecode.get != None) {
                          val myData = myPaymentDetails.paymentdata.purposeinformation.purposecode.get
                          purposeinformationpurposecode = myData.toString()
                          if (purposeinformationpurposecode != null && purposeinformationpurposecode != None){
                            purposeinformationpurposecode = purposeinformationpurposecode.trim
                            if (purposeinformationpurposecode.length > 0){
                              purposeinformationpurposecode = purposeinformationpurposecode.replace("'","")//Remove apostrophe
                              purposeinformationpurposecode = purposeinformationpurposecode.replace(" ","")//Remove spaces
                              purposeinformationpurposecode = purposeinformationpurposecode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              purposeinformationpurposecode = purposeinformationpurposecode.trim
                            }
                          }
                        }
                      }

                      //remittanceinformationunstructured
                      if (myPaymentDetails.paymentdata.remittanceinformation.unstructured != None) {
                        if (myPaymentDetails.paymentdata.remittanceinformation.unstructured.get != None) {
                          val myData = myPaymentDetails.paymentdata.remittanceinformation.unstructured.get
                          remittanceinformationunstructured = myData.toString()
                          if (remittanceinformationunstructured != null && remittanceinformationunstructured != None){
                            remittanceinformationunstructured = remittanceinformationunstructured.trim
                            if (remittanceinformationunstructured.length > 0){
                              remittanceinformationunstructured = remittanceinformationunstructured.replace("'","")//Remove apostrophe
                              remittanceinformationunstructured = remittanceinformationunstructured.replace(" ","")//Remove spaces
                              remittanceinformationunstructured = remittanceinformationunstructured.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              remittanceinformationunstructured = remittanceinformationunstructured.trim
                            }
                          }
                        }
                      }

                      //remittanceinformationtaxremittancereferencenumber
                      if (myPaymentDetails.paymentdata.remittanceinformation.taxremittancereferencenumber != None) {
                        if (myPaymentDetails.paymentdata.remittanceinformation.taxremittancereferencenumber.get != None) {
                          val myData = myPaymentDetails.paymentdata.remittanceinformation.taxremittancereferencenumber.get
                          remittanceinformationtaxremittancereferencenumber = myData.toString()
                          if (remittanceinformationtaxremittancereferencenumber != null && remittanceinformationtaxremittancereferencenumber != None){
                            remittanceinformationtaxremittancereferencenumber = remittanceinformationtaxremittancereferencenumber.trim
                            if (remittanceinformationtaxremittancereferencenumber.length > 0){
                              remittanceinformationtaxremittancereferencenumber = remittanceinformationtaxremittancereferencenumber.replace("'","")//Remove apostrophe
                              remittanceinformationtaxremittancereferencenumber = remittanceinformationtaxremittancereferencenumber.replace(" ","")//Remove spaces
                              remittanceinformationtaxremittancereferencenumber = remittanceinformationtaxremittancereferencenumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              remittanceinformationtaxremittancereferencenumber = remittanceinformationtaxremittancereferencenumber.trim
                            }
                          }
                        }
                      }

                    }
                    catch {
                      case io: Throwable =>
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }

                    /* Lets set var isValidInputData to true if valid data is received from e-Channels/ESB-CBS System */
                    /*
                    val isValidSchemeName: Boolean = {
                      creditoraccountinformationcreditoraccountschemename match {
                        case accSchemeName =>
                          true
                        case phneSchemeName =>
                          true
                        case _ =>
                          false
                      }
                    }
                    */
                    val isValidSchemeName: Boolean = {
                      var isValid: Boolean = false
                      if (creditoraccountinformationcreditoraccountschemename.length == 0 || accSchemeName.length == 0){
                        isValid = false
                      }
                      else if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(accSchemeName)){
                        isValid = true
                      }
                      else if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(phneSchemeName)){
                        isValid = true
                      }
                      else {
                        isValid = false
                      }
                      isValid
                    }

                    if (debtoraccountinformationdebtoraccountidentification.length > 0 && creditoraccountinformationcreditoraccountidentification.length > 0 && isValidSchemeName){
                      isValidInputData = true
                      myHttpStatusCode = HttpStatusCode.Accepted //TESTS ONLY
                      responseCode = 0
                      responseMessage = "Message accepted for processing."
                      println("messageidentification - " + messageidentification + ", paymentendtoendidentification - " + paymentendtoendidentification + ", totalinterbanksettlementamount - " + totalinterbanksettlementamount +
                        ", debtoraccountinformationdebtoraccountidentification - " + debtoraccountinformationdebtoraccountidentification + ", creditoraccountinformationcreditoraccountidentification - " + creditoraccountinformationcreditoraccountidentification)
                    }

                    try{
                      //sourceDataTable.addRow(myBatchReference, strDebitAccountNumber, strAccountNumber, strAccountName, strCustomerReference, strBankCode, strLocalBankCode, strBranchCode, myAmount, strPaymentType, strPurposeofPayment, strDescription, strEmailAddress, myBatchSize)
                      //validate creditoraccountinformationcreditoraccountschemename to ensure it has the right input value
                      /*
                      val schemeName: String = {
                        creditoraccountinformationcreditoraccountschemename match {
                          case accSchemeName =>
                            SchemeName.ACC.toString.toUpperCase
                          case phneSchemeName =>
                            SchemeName.PHNE.toString.toUpperCase
                          case _ =>
                            SchemeName.ACC.toString.toUpperCase
                        }
                      }
                      */
                      val schemeName: String = {
                        var scheme: String = ""
                        if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(accSchemeName)){
                          scheme = accSchemeName
                        }
                        else if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(phneSchemeName)){
                          scheme = phneSchemeName
                        }
                        scheme
                      }

                      creditoraccountinformationcreditoraccountschemename = schemeName

                      val transferDefaultInfo = TransferDefaultInfo(firstAgentIdentification, assigneeAgentIdentification, chargeBearer, settlementMethod, clearingSystem, serviceLevel, localInstrumentCode, categoryPurpose)
                      val debitcontactinformation = ContactInfo(debtorinformationdebtorcontactphonenumber)
                      val debitAccountInfo = DebitAccountInfo(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, debitcontactinformation, SchemeName.ACC.toString.toUpperCase)
                      val creditcontactinformation = ContactInfo(creditorinformationcreditorcontactphonenumber)
                      val creditAccountInfo = CreditAccountInfo(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoraccountinformationcreditoraccountschemename, creditoragentinformationfinancialInstitutionIdentification, creditcontactinformation)
                      val purposeInfo = TransferPurposeInfo(purposeinformationpurposecode)
                      val remittanceInfo = TransferRemittanceInfo(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
                      val mandateInfo = TransferMandateInfo(mandateidentification)
                      val paymentdata = CreditTransferPaymentInfo(paymentendtoendidentification, interbanksettlementamount, debitAccountInfo, creditAccountInfo, mandateInfo, remittanceInfo, purposeInfo, transferDefaultInfo)
                      val singleCreditTransferPaymentInfo = SingleCreditTransferPaymentInfo(messageidentification, creationDateTime, numberoftransactions, totalinterbanksettlementamount, paymentdata)

                      val f = Future {
                        println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                        val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo)
                        sendSingleCreditTransferRequestsIpsl(myRespData)
                      }

                    }
                    catch {
                      case io: Throwable =>
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }
                    //})

                    try{
                      if (isValidInputData){
                        /*
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.insertOutgoingS2bPaymentDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  try{
                                    val myaccountnumber = resultSet.getString("accountnumber")
                                    val mybankcode = resultSet.getString("bankcode")
                                    val mybranchcode = resultSet.getString("branchcode")
                                    val mycustomerreference = resultSet.getString("customerreference")
                                    val myresponseCode = resultSet.getInt("responseCode")
                                    val myresponseMessage = resultSet.getString("responseMessage")
                                    val myS2B_PaymentDetailsResponse_Batch = new S2B_PaymentDetailsResponse_Batch(myaccountnumber, mybankcode, mybranchcode, mycustomerreference, myresponseCode, myresponseMessage)
                                    myS2B_PaymentDetailsResponse_BatchData  = myS2B_PaymentDetailsResponse_BatchData :+ myS2B_PaymentDetailsResponse_Batch
                                  }
                                  catch{
                                    case io: Throwable =>
                                      log_errors(strApifunction + " : resultSet.next - " + io.getMessage())
                                    case ex: Exception =>
                                      log_errors(strApifunction + " : resultSet.next - " + ex.getMessage())
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
                                log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                log_errors(strApifunction + " : " + ex.getMessage())
                            }

                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              log_errors(strApifunction + " : " + ex.getMessage())
                          }
                        }
                        */
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
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }

                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      log_errors(strApifunction + " : " + ex.getMessage())
                  }
                }
                catch
                  {
                    case ex: Exception =>
                      log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      log_errors(strApifunction + " : " + tr.getMessage())
                  }

              }
              case JsError(e) => {
                // do something
                responseMessage = "Error occured when unpacking Json values"
                log_errors(strApifunction + " : " + e.toString())
              }
            }
          }
          else{
            myHttpStatusCode = HttpStatusCode.Unauthorized
          }

        }
        else {
          if (!isDataFound) {
            responseMessage = "Invalid Request Data"
          }
          else if (!isAuthTokenFound) {
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
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + tr.getMessage())
        }
      /*
      implicit val S2B_PaymentDetailsResponse_BatchWrites = Json.writes[S2B_PaymentDetailsResponse_Batch]
      implicit val S2B_PaymentDetailsResponse_BatchDataWrites = Json.writes[S2B_PaymentDetailsResponse_BatchData]

      if (myS2B_PaymentDetailsResponse_BatchData.isEmpty || myS2B_PaymentDetailsResponse_BatchData){
        val myS2B_PaymentDetailsResponse_Batch = new S2B_PaymentDetailsResponse_Batch("", "", "", "", responseCode, responseMessage)
        myS2B_PaymentDetailsResponse_BatchData  = myS2B_PaymentDetailsResponse_BatchData :+ myS2B_PaymentDetailsResponse_Batch
      }

      val myPaymentDetailsResponse = new S2B_PaymentDetailsResponse_BatchData(myS2B_PaymentDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myPaymentDetailsResponse)
      */
      /*
      try{
        log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
      */
      /*
      val myRespData: String = getSingleCreditTransferDetails

      val f = Future {sendSingleCreditTransferRequestsIpsl(myRespData)}
      */

      //Log_data("processWhatsAppActions - " + "ResponseCode - " + responseCode.toString + " , ResponseMessage - " + responseMessage + " - Request Message : " + strRequest)
      /*
      val textResponse = Ok(myresponse_processUssdActions.text.toString).as("text/xml")
      val r: Result = textResponse
      r
      */
      implicit val  SingleCreditTransferPaymentDetailsResponse_Writes = Json.writes[SingleCreditTransferPaymentDetailsResponse]

      val mySingleCreditTransferPaymentResponse =  SingleCreditTransferPaymentDetailsResponse(responseCode, responseMessage)
      val jsonResponse = Json.toJson(mySingleCreditTransferPaymentResponse)

      val r: Result = {
        myHttpStatusCode match {
          case HttpStatusCode.Accepted =>
            Accepted(jsonResponse)
          case HttpStatusCode.BadRequest =>
            BadRequest(jsonResponse)
          case HttpStatusCode.Unauthorized =>
            Unauthorized(jsonResponse)
          case _ =>
            BadRequest(jsonResponse)
        }
      }

      r
    }(myExecutionContext)
  }
  def addBulkCreditTransferPaymentDetails = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myS2B_ForexPaymentDetailsResponse_BatchData : Seq[S2B_ForexPaymentDetailsResponse_Batch] = Seq.empty[S2B_ForexPaymentDetailsResponse_Batch]
      val strApifunction : String = "addbulkcredittransferpaymentdetails"

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
        log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val S2B_ForexPaymentDetails_Request_Reads: Reads[S2B_ForexPaymentDetails_Request] = (
              (JsPath \ "accountname").readNullable[JsValue] and
                (JsPath \ "customerreference").readNullable[JsValue] and
                (JsPath \ "accountnumber").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue] and
                (JsPath \ "branchcode").readNullable[JsValue] and
                (JsPath \ "paymentamount").readNullable[JsValue] and
                (JsPath \ "paymentdetails").readNullable[JsValue] and
                (JsPath \ "paymenttype").readNullable[JsValue] and
                (JsPath \ "paymentcurrency").readNullable[JsValue] and
                (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue] and
                (JsPath \ "fxtype").readNullable[JsValue] and
                (JsPath \ "appliedamount").readNullable[JsValue] and
                (JsPath \ "fxrate").readNullable[JsValue] and
                (JsPath \ "dealnumber").readNullable[JsValue] and
                (JsPath \ "dealername").readNullable[JsValue] and
                (JsPath \ "directinverse").readNullable[JsValue] and
                (JsPath \ "maturitydate").readNullable[JsValue] and
                (JsPath \ "intermediarybankcode").readNullable[JsValue]
              )(S2B_ForexPaymentDetails_Request.apply _)

            implicit val S2B_ForexPaymentDetails_BatchRequest_Reads: Reads[S2B_ForexPaymentDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "paymentdata").read[Seq[S2B_ForexPaymentDetails_Request]]
              )(S2B_ForexPaymentDetails_BatchRequest.apply _)

            myjson.validate[S2B_ForexPaymentDetails_BatchRequest] match {
              case JsSuccess(myS2B_ForexPaymentDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myS2B_ForexPaymentDetails_BatchRequest.paymentdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("AccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BranchCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PaymentAmount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PaymentType", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PaymentDetails", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PaymentCurrency", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("EmailAddress", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("FXType", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AppliedAmount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("FXRate", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DealNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("DealerName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Direct_Inverse", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("MaturityDate", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("IntermediaryBankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("IsValidMaturityDate", java.sql.Types.INTEGER)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)


                    var myPaymentAmount : BigDecimal = 0
                    var myAppliedAmount : BigDecimal = 0
                    var myFxrate : BigDecimal = 0
                    var strAccountName : String = ""
                    var strCustomerReference : String = ""
                    var strAccountNumber : String = ""
                    var strBankCode : String = ""
                    var strBranchCode : String = ""
                    var strPaymentAmount : String = ""
                    var strAppliedAmount : String = ""
                    var strFxrate : String = ""
                    var strPaymentDetails : String = ""
                    var strPaymentType : String = ""
                    var strPaymentCurrency : String = ""
                    var strDebitAccountNumber : String = ""
                    var strEmailAddress : String = ""
                    var strFxtype : String = ""
                    var strDealNumber : String = ""
                    var strDealerName : String = ""
                    var strDirectInverse : String = ""
                    var strMaturityDate : String = ""
                    var strIntermediaryBankCode : String = ""
                    var isValidDate: Boolean = false
                    var myValidDate: Integer = 0

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myS2B_ForexPaymentDetails_BatchRequest.paymentdata.foreach(myPaymentDetails => {

                      myPaymentAmount = 0
                      myAppliedAmount = 0
                      myFxrate = 0
                      strAccountName = ""
                      strCustomerReference = ""
                      strAccountNumber = ""
                      strBankCode = ""
                      strBranchCode = ""
                      strPaymentAmount = ""
                      strAppliedAmount = ""
                      strFxrate = ""
                      strPaymentDetails = ""
                      strPaymentType = ""
                      strPaymentCurrency = ""
                      strDebitAccountNumber = ""
                      strEmailAddress = ""
                      strFxtype = ""
                      strDealNumber = ""
                      strDealerName = ""
                      strDirectInverse = ""
                      strMaturityDate = ""
                      strIntermediaryBankCode = ""
                      isValidDate = false
                      myValidDate = 0

                      try{
                        //strAccountName
                        if (myPaymentDetails.accountname != None) {
                          if (myPaymentDetails.accountname.get != None) {
                            val myData = myPaymentDetails.accountname.get
                            strAccountName = myData.toString()
                            if (strAccountName != null && strAccountName != None){
                              strAccountName = strAccountName.trim
                              if (strAccountName.length > 0){
                                strAccountName = strAccountName.replace("'","")//Remove apostrophe
                                strAccountName = strAccountName.replace("  "," ")//Remove double spaces
                                strAccountName = strAccountName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAccountName = strAccountName.trim
                              }
                            }
                          }
                        }

                        //strCustomerReference
                        if (myPaymentDetails.customerreference != None) {
                          if (myPaymentDetails.customerreference.get != None) {
                            val myData = myPaymentDetails.customerreference.get
                            strCustomerReference = myData.toString()
                            if (strCustomerReference != null && strCustomerReference != None){
                              strCustomerReference = strCustomerReference.trim
                              if (strCustomerReference.length > 0){
                                strCustomerReference = strCustomerReference.replace("'","")//Remove apostrophe
                                strCustomerReference = strCustomerReference.replace(" ","")//Remove spaces
                                strCustomerReference = strCustomerReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strCustomerReference = strCustomerReference.trim
                              }
                            }
                          }
                        }

                        //strAccountNumber
                        if (myPaymentDetails.accountnumber != None) {
                          if (myPaymentDetails.accountnumber.get != None) {
                            val myData = myPaymentDetails.accountnumber.get
                            strAccountNumber = myData.toString()
                            if (strAccountNumber != null && strAccountNumber != None){
                              strAccountNumber = strAccountNumber.trim
                              if (strAccountNumber.length > 0){
                                strAccountNumber = strAccountNumber.replace("'","")//Remove apostrophe
                                strAccountNumber = strAccountNumber.replace(" ","")//Remove spaces
                                strAccountNumber = strAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAccountNumber = strAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strBankCode
                        if (myPaymentDetails.bankcode != None) {
                          if (myPaymentDetails.bankcode.get != None) {
                            val myData = myPaymentDetails.bankcode.get
                            strBankCode = myData.toString()
                            if (strBankCode != null && strBankCode != None){
                              strBankCode = strBankCode.trim
                              if (strBankCode.length > 0){
                                strBankCode = strBankCode.replace("'","")//Remove apostrophe
                                strBankCode = strBankCode.replace(" ","")//Remove spaces
                                strBankCode = strBankCode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strBankCode = strBankCode.trim
                              }
                            }
                          }
                        }

                        //strBranchCode
                        if (myPaymentDetails.branchcode != None) {
                          if (myPaymentDetails.branchcode.get != None) {
                            val myData = myPaymentDetails.branchcode.get
                            strBranchCode = myData.toString()
                            if (strBranchCode != null && strBranchCode != None){
                              strBranchCode = strBranchCode.trim
                              if (strBranchCode.length > 0){
                                strBranchCode = strBranchCode.replace("'","")//Remove apostrophe
                                strBranchCode = strBranchCode.replace(" ","")//Remove spaces
                                strBranchCode = strBranchCode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strBranchCode = strBranchCode.trim
                              }
                            }
                          }
                        }

                        //strPaymentAmount
                        if (myPaymentDetails.paymentamount != None) {
                          if (myPaymentDetails.paymentamount.get != None) {
                            val myData = myPaymentDetails.paymentamount.get
                            strPaymentAmount = myData.toString()
                            if (strPaymentAmount != null && strPaymentAmount != None){
                              strPaymentAmount = strPaymentAmount.trim
                              if (strPaymentAmount.length > 0){
                                strPaymentAmount = strPaymentAmount.replace("'","")//Remove apostrophe
                                strPaymentAmount = strPaymentAmount.replace(" ","")//Remove spaces
                                strPaymentAmount = strPaymentAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPaymentAmount = strPaymentAmount.trim
                                if (strPaymentAmount.length > 0){
                                  //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                  val isNumeric : Boolean = strPaymentAmount.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                  if (isNumeric == true){
                                    myPaymentAmount = BigDecimal(strPaymentAmount)
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strPaymentDetails
                        if (myPaymentDetails.paymentdetails != None) {
                          if (myPaymentDetails.paymentdetails.get != None) {
                            val myData = myPaymentDetails.paymentdetails.get
                            strPaymentDetails = myData.toString()
                            if (strPaymentDetails != null && strPaymentDetails != None){
                              strPaymentDetails = strPaymentDetails.trim
                              if (strPaymentDetails.length > 0){
                                strPaymentDetails = strPaymentDetails.replace("'","")//Remove apostrophe
                                strPaymentDetails = strPaymentDetails.replace("  "," ")//Remove double spaces
                                strPaymentDetails = strPaymentDetails.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPaymentDetails = strPaymentDetails.trim
                              }
                            }
                          }
                        }

                        //strPaymentType
                        if (myPaymentDetails.paymenttype != None) {
                          if (myPaymentDetails.paymenttype.get != None) {
                            val myData = myPaymentDetails.paymenttype.get
                            strPaymentType = myData.toString()
                            if (strPaymentType != null && strPaymentType != None){
                              strPaymentType = strPaymentType.trim
                              if (strPaymentType.length > 0){
                                strPaymentType = strPaymentType.replace("'","")//Remove apostrophe
                                strPaymentType = strPaymentType.replace(" ","")//Remove spaces
                                strPaymentType = strPaymentType.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPaymentType = strPaymentType.trim
                              }
                            }
                          }
                        }

                        //strPaymentCurrency
                        if (myPaymentDetails.paymentcurrency != None) {
                          if (myPaymentDetails.paymentcurrency.get != None) {
                            val myData = myPaymentDetails.paymentcurrency.get
                            strPaymentCurrency = myData.toString()
                            if (strPaymentCurrency != null && strPaymentCurrency != None){
                              strPaymentCurrency = strPaymentCurrency.trim
                              if (strPaymentCurrency.length > 0){
                                strPaymentCurrency = strPaymentCurrency.replace("'","")//Remove apostrophe
                                strPaymentCurrency = strPaymentCurrency.replace(" ","")//Remove spaces
                                strPaymentCurrency = strPaymentCurrency.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPaymentCurrency = strPaymentCurrency.trim
                              }
                            }
                          }
                        }

                        //strDebitAccountNumber
                        if (myPaymentDetails.debitaccountnumber != None) {
                          if (myPaymentDetails.debitaccountnumber.get != None) {
                            val myData = myPaymentDetails.debitaccountnumber.get
                            strDebitAccountNumber = myData.toString()
                            if (strDebitAccountNumber != null && strDebitAccountNumber != None){
                              strDebitAccountNumber = strDebitAccountNumber.trim
                              if (strDebitAccountNumber.length > 0){
                                strDebitAccountNumber = strDebitAccountNumber.replace("'","")//Remove apostrophe
                                strDebitAccountNumber = strDebitAccountNumber.replace(" ","")//Remove spaces
                                strDebitAccountNumber = strDebitAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDebitAccountNumber = strDebitAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strEmailAddress
                        if (myPaymentDetails.emailaddress != None) {
                          if (myPaymentDetails.emailaddress.get != None) {
                            val myData = myPaymentDetails.emailaddress.get
                            strEmailAddress = myData.toString()
                            if (strEmailAddress != null && strEmailAddress != None){
                              strEmailAddress = strEmailAddress.trim
                              if (strEmailAddress.length > 0){
                                strEmailAddress = strEmailAddress.replace("'","")//Remove apostrophe
                                strEmailAddress = strEmailAddress.replace(" ","")//Remove spaces
                                strEmailAddress = strEmailAddress.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strEmailAddress = strEmailAddress.trim
                              }
                            }
                          }
                        }

                        //strFxtype
                        if (myPaymentDetails.fxtype != None) {
                          if (myPaymentDetails.fxtype.get != None) {
                            val myData = myPaymentDetails.fxtype.get
                            strFxtype = myData.toString()
                            if (strFxtype != null && strFxtype != None){
                              strFxtype = strFxtype.trim
                              if (strFxtype.length > 0){
                                strFxtype = strFxtype.replace("'","")//Remove apostrophe
                                strFxtype = strFxtype.replace(" ","")//Remove spaces
                                strFxtype = strFxtype.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strFxtype = strFxtype.trim
                              }
                            }
                          }
                        }

                        //strAppliedAmount
                        if (myPaymentDetails.appliedamount != None) {
                          if (myPaymentDetails.appliedamount.get != None) {
                            val myData = myPaymentDetails.appliedamount.get
                            strAppliedAmount = myData.toString()
                            if (strAppliedAmount != null && strAppliedAmount != None){
                              strAppliedAmount = strAppliedAmount.trim
                              if (strAppliedAmount.length > 0){
                                strAppliedAmount = strAppliedAmount.replace("'","")//Remove apostrophe
                                strAppliedAmount = strAppliedAmount.replace(" ","")//Remove spaces
                                strAppliedAmount = strAppliedAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAppliedAmount = strAppliedAmount.trim
                                if (strAppliedAmount.length > 0){
                                  //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                  val isNumeric : Boolean = strAppliedAmount.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                  if (isNumeric == true){
                                    myAppliedAmount = BigDecimal(strAppliedAmount)
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strFxrate
                        if (myPaymentDetails.fxrate != None) {
                          if (myPaymentDetails.fxrate.get != None) {
                            val myData = myPaymentDetails.fxrate.get
                            strFxrate = myData.toString()
                            if (strFxrate != null && strFxrate != None){
                              strFxrate = strFxrate.trim
                              if (strFxrate.length > 0){
                                strFxrate = strFxrate.replace("'","")//Remove apostrophe
                                strFxrate = strFxrate.replace(" ","")//Remove spaces
                                strFxrate = strFxrate.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strFxrate = strFxrate.trim
                                if (strFxrate.length > 0){
                                  //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                  val isNumeric : Boolean = strFxrate.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                  if (isNumeric == true){
                                    myFxrate = BigDecimal(strFxrate)
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strDealNumber
                        if (myPaymentDetails.dealnumber != None) {
                          if (myPaymentDetails.dealnumber.get != None) {
                            val myData = myPaymentDetails.dealnumber.get
                            strDealNumber = myData.toString()
                            if (strDealNumber != null && strDealNumber != None){
                              strDealNumber = strDealNumber.trim
                              if (strDealNumber.length > 0){
                                strDealNumber = strDealNumber.replace("'","")//Remove apostrophe
                                strDealNumber = strDealNumber.replace(" ","")//Remove spaces
                                strDealNumber = strDealNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDealNumber = strDealNumber.trim
                              }
                            }
                          }
                        }

                        //strDealerName
                        if (myPaymentDetails.dealername != None) {
                          if (myPaymentDetails.dealername.get != None) {
                            val myData = myPaymentDetails.dealername.get
                            strDealerName = myData.toString()
                            if (strDealerName != null && strDealerName != None){
                              strDealerName = strDealerName.trim
                              if (strDealerName.length > 0){
                                strDealerName = strDealerName.replace("'","")//Remove apostrophe
                                strDealerName = strDealerName.replace("  "," ")//Remove double spaces
                                strDealerName = strDealerName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDealerName = strDealerName.trim
                              }
                            }
                          }
                        }

                        //strDirectInverse
                        if (myPaymentDetails.directinverse != None) {
                          if (myPaymentDetails.directinverse.get != None) {
                            val myData = myPaymentDetails.directinverse.get
                            strDirectInverse = myData.toString()
                            if (strDirectInverse != null && strDirectInverse != None){
                              strDirectInverse = strDirectInverse.trim
                              if (strDirectInverse.length > 0){
                                strDirectInverse = strDirectInverse.replace("'","")//Remove apostrophe
                                strDirectInverse = strDirectInverse.replace("  "," ")//Remove double spaces
                                strDirectInverse = strDirectInverse.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDirectInverse = strDirectInverse.trim
                              }
                            }
                          }
                        }

                        //strMaturityDate
                        if (myPaymentDetails.maturitydate != None) {
                          if (myPaymentDetails.maturitydate.get != None) {
                            val myData = myPaymentDetails.maturitydate.get
                            strMaturityDate = myData.toString()
                            if (strMaturityDate != null && strMaturityDate != None){
                              strMaturityDate = strMaturityDate.trim
                              if (strMaturityDate.length > 0){
                                strMaturityDate = strMaturityDate.replace("'","")//Remove apostrophe
                                strMaturityDate = strMaturityDate.replace("  "," ")//Remove double spaces
                                strMaturityDate = strMaturityDate.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strMaturityDate = strMaturityDate.trim
                                if (strMaturityDate.length == 10){
                                  //Lets check if the data is in valid format i.e "yyyy-mm-dd"
                                  isValidDate = strMaturityDate.toString.matches(strDateRegex)
                                  if (isValidDate == true){
                                    myValidDate = 1
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strIntermediaryBankCode
                        if (myPaymentDetails.intermediarybankcode != None) {
                          if (myPaymentDetails.intermediarybankcode.get != None) {
                            val myData = myPaymentDetails.intermediarybankcode.get
                            strIntermediaryBankCode = myData.toString()
                            if (strIntermediaryBankCode != null && strIntermediaryBankCode != None){
                              strIntermediaryBankCode = strIntermediaryBankCode.trim
                              if (strIntermediaryBankCode.length > 0){
                                strIntermediaryBankCode = strIntermediaryBankCode.replace("'","")//Remove apostrophe
                                strIntermediaryBankCode = strIntermediaryBankCode.replace(" ","")//Remove spaces
                                strIntermediaryBankCode = strIntermediaryBankCode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strIntermediaryBankCode = strIntermediaryBankCode.trim
                              }
                            }
                          }
                        }

                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from FundMaster Core System */
                      if (isValidInputData == false){
                        if (strAccountNumber.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myBatchReference, strAccountNumber, strAccountName, strCustomerReference, strBankCode, strBranchCode,
                          myPaymentAmount, strPaymentType, strPaymentDetails, strPaymentCurrency, strDebitAccountNumber, strEmailAddress,
                          strFxtype, myAppliedAmount, myFxrate, strDealNumber, strDealerName, strDirectInverse,
                          strMaturityDate, strIntermediaryBankCode, myValidDate, myBatchSize)
                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData == true){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.insertOutgoingS2bForexPaymentDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  try{
                                    val myaccountnumber = resultSet.getString("accountnumber")
                                    val mybankcode = resultSet.getString("bankcode")
                                    val mybranchcode = resultSet.getString("branchcode")
                                    val mycustomerreference = resultSet.getString("customerreference")
                                    val myresponseCode = resultSet.getInt("responseCode")
                                    val myresponseMessage = resultSet.getString("responseMessage")
                                    val myS2B_ForexPaymentDetailsResponse_Batch = new S2B_ForexPaymentDetailsResponse_Batch(myaccountnumber, mybankcode, mybranchcode, mycustomerreference, myresponseCode, myresponseMessage)
                                    myS2B_ForexPaymentDetailsResponse_BatchData  = myS2B_ForexPaymentDetailsResponse_BatchData :+ myS2B_ForexPaymentDetailsResponse_Batch
                                  }
                                  catch{
                                    case io: Throwable =>
                                      log_errors(strApifunction + " : resultSet.next - " + io.getMessage())
                                    case ex: Exception =>
                                      log_errors(strApifunction + " : resultSet.next - " + ex.getMessage())
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
                                log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                log_errors(strApifunction + " : " + ex.getMessage())
                            }

                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              log_errors(strApifunction + " : " + ex.getMessage())
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
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        log_errors(strApifunction + " : " + ex.getMessage())
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
                      log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      log_errors(strApifunction + " : " + ex.getMessage())
                  }
                  finally{

                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + tr.getMessage())
                  }
                finally
                {
                }

              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values"
                log_errors(strApifunction + " : " + e.toString())
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
            isProcessed = false
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + tr.getMessage())
        }
      finally
      {
      }

      implicit val S2B_ForexPaymentDetailsResponse_BatchWrites = Json.writes[S2B_ForexPaymentDetailsResponse_Batch]
      implicit val S2B_ForexPaymentDetailsResponse_BatchDataWrites = Json.writes[S2B_ForexPaymentDetailsResponse_BatchData]

      if (myS2B_ForexPaymentDetailsResponse_BatchData.isEmpty == true || myS2B_ForexPaymentDetailsResponse_BatchData == true){
        val myS2B_ForexPaymentDetailsResponse_Batch = new S2B_ForexPaymentDetailsResponse_Batch("", "", "", "", responseCode, responseMessage)
        myS2B_ForexPaymentDetailsResponse_BatchData  = myS2B_ForexPaymentDetailsResponse_BatchData :+ myS2B_ForexPaymentDetailsResponse_Batch
      }

      val myPaymentDetailsResponse = new S2B_ForexPaymentDetailsResponse_BatchData(myS2B_ForexPaymentDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myPaymentDetailsResponse)
      /*
      try{
        log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
      */
      //implicit val response_processUssdActions_Writes = Json.writes[response_processUssdActions]

      val myRespData: String = getBulkCreditTransferDetails
      //val myresponse_processUssdActions = new response_processUssdActions(myData)

      //TESTS ONLY
      //sendBulkCreditTransferRequestsIpsl(myData.toString())
      val f = Future {sendBulkCreditTransferRequestsIpsl(myRespData)}

      //Log_data("processWhatsAppActions - " + "ResponseCode - " + responseCode.toString + " , ResponseMessage - " + responseMessage + " - Request Message : " + strRequest)
      /*
      val textResponse = Ok(myresponse_processUssdActions.text.toString).as("text/xml")
      val r: Result = textResponse
      r
      */
      implicit val textResponseData_Writes = Json.writes[textResponseData]

      val myData: String = "Message accepted for processing."

      val myResponseData = new textResponseData(myData)

      val textResponse = Accepted(myResponseData.text.toString).as("text/plain")
      val r: Result = textResponse
      r
    }(myExecutionContext)
  }
  def addAccountVerificationDetails = Action.async { request =>
    Future {
      val startDate: String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      var myHttpStatusCode = HttpStatusCode.BadRequest
      val strApifunction: String = "addaccountverificationdetails"

      var myBankCode: Int = 0
      var mySchemeMode: Int = 0
      var strMessageReference: String = ""
      var strTransactionReference: String = ""
      var strAccountNumber: String = ""
      var strSchemeMode: String = ""
      var strBankCode : String = ""
      //var strAccountname: String = ""
      var isValidSchemeMode: Boolean = false

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

        if (!request.body.asJson.isEmpty) {
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
                  if (strRequestHeader.toLowerCase.contains("bearer")){
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
        log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

        if (isDataFound && isAuthTokenFound){

          try{
            var myByteAuthToken = Base64.getDecoder.decode(strAuthToken)
            var myAuthToken : String = new String(myByteAuthToken, StandardCharsets.UTF_8)

            //log_data(strApifunction + " : myAuthToken - " + "**********" + " , strAuthToken - " + strAuthToken)

            if (myAuthToken != null){
              myAuthToken = myAuthToken.trim

              if (myAuthToken.length > 0){
                if (myAuthToken.contains(":")){
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          try{
            if (isCredentialsFound) {
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
                val respCode = mystmt.getInt("responseCode")
                responseMessage = mystmt.getString("responseMessage")

                if (respCode != null){
                  responseCode = respCode
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val AccountVerificationDetails_Request_Reads: Reads[AccountVerificationDetails_Request] = (
              (JsPath \ "transactionreference").readNullable[JsValue] and
                (JsPath \ "accountnumber").readNullable[JsValue] and
                (JsPath \ "schememode").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue]
              )(AccountVerificationDetails_Request.apply _)

            implicit val AccountVerificationDetails_BatchRequest_Reads: Reads[AccountVerificationDetails_BatchRequest] = (
              (JsPath \ "messagereference").readNullable[JsValue] and
                (JsPath \ "accountdata").read[AccountVerificationDetails_Request]
              )(AccountVerificationDetails_BatchRequest.apply _)

            myjson.validate[AccountVerificationDetails_BatchRequest] match {
              case JsSuccess(myPaymentDetails, _) => {

                var isValidInputData : Boolean = false
                //val myBatchSize : Integer = 1
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  //myBatchSize = myAccountVerificationDetails_BatchRequest.accountdata.length
                  //strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  //val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Prn", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Amount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    /*
                    var myBankCode: Int = 0
                    var mySchemeMode: Int = 0
                    var strMessageReference: String = ""
                    var strTransactionReference: String = ""
                    var strAccountNumber: String = ""
                    var strSchemeMode: String = ""
                    var strBankCode : String = ""
                    */

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }


                    myBankCode = 0
                    mySchemeMode = 0
                    strMessageReference = ""
                    strTransactionReference = ""
                    strAccountNumber = ""
                    strSchemeMode = ""
                    strBankCode = ""

                    try{
                      //strMessageReference
                      if (myPaymentDetails.messagereference != None) {
                        if (myPaymentDetails.messagereference.get != None) {
                          val myData = myPaymentDetails.messagereference.get
                          strMessageReference = myData.toString()
                          if (strMessageReference != null && strMessageReference != None){
                            strMessageReference = strMessageReference.trim
                            if (strMessageReference.length > 0){
                              strMessageReference = strMessageReference.replace("'","")//Remove apostrophe
                              strMessageReference = strMessageReference.replace(" ","")//Remove spaces
                              strMessageReference = strMessageReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strMessageReference = strMessageReference.trim
                            }
                          }
                        }
                      }

                      //strTransactionReference
                      if (myPaymentDetails.accountdata.transactionreference != None) {
                        if (myPaymentDetails.accountdata.transactionreference.get != None) {
                          val myData = myPaymentDetails.accountdata.transactionreference.get
                          strTransactionReference = myData.toString()
                          if (strTransactionReference != null && strTransactionReference != None){
                            strTransactionReference = strTransactionReference.trim
                            if (strTransactionReference.length > 0){
                              strTransactionReference = strTransactionReference.replace("'","")//Remove apostrophe
                              strTransactionReference = strTransactionReference.replace(" ","")//Remove spaces
                              strTransactionReference = strTransactionReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strTransactionReference = strTransactionReference.trim
                            }
                          }
                        }
                      }

                      //strAccountNumber
                      if (myPaymentDetails.accountdata.accountnumber != None) {
                        if (myPaymentDetails.accountdata.accountnumber.get != None) {
                          val myData = myPaymentDetails.accountdata.accountnumber.get
                          strAccountNumber = myData.toString()
                          if (strAccountNumber != null && strAccountNumber != None){
                            strAccountNumber = strAccountNumber.trim
                            if (strAccountNumber.length > 0){
                              strAccountNumber = strAccountNumber.replace("'","")//Remove apostrophe
                              strAccountNumber = strAccountNumber.replace(" ","")//Remove spaces
                              strAccountNumber = strAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strAccountNumber = strAccountNumber.trim
                            }
                          }
                        }
                      }

                      //strSchemeMode
                      if (myPaymentDetails.accountdata.schememode != None) {
                        if (myPaymentDetails.accountdata.schememode.get != None) {
                          val myData = myPaymentDetails.accountdata.schememode.get
                          strSchemeMode = myData.toString()
                          if (strSchemeMode != null && strSchemeMode != None){
                            strSchemeMode = strSchemeMode.trim
                            if (strSchemeMode.length > 0){
                              strSchemeMode = strSchemeMode.replace("'","")//Remove apostrophe
                              strSchemeMode = strSchemeMode.replace(" ","")//Remove spaces
                              strSchemeMode = strSchemeMode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strSchemeMode = strSchemeMode.trim
                              if (strSchemeMode.length > 0){
                                val isNumeric: Boolean = strSchemeMode.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                                if (isNumeric){
                                  mySchemeMode = strSchemeMode.toInt
                                }
                              }
                            }
                          }
                        }
                      }

                      //strBankCode
                      if (myPaymentDetails.accountdata.bankcode != None) {
                        if (myPaymentDetails.accountdata.bankcode.get != None) {
                          val myData = myPaymentDetails.accountdata.bankcode.get
                          strBankCode = myData.toString()
                          if (strBankCode != null && strBankCode != None){
                            strBankCode = strBankCode.trim
                            if (strBankCode.length > 0){
                              strBankCode = strBankCode.replace("'","")//Remove apostrophe
                              strBankCode = strBankCode.replace(" ","")//Remove spaces
                              strBankCode = strBankCode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strBankCode = strBankCode.trim
                              if (strBankCode.length > 0){
                                val isNumeric: Boolean = strBankCode.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                                if (isNumeric){
                                  myBankCode = strBankCode.toInt
                                }
                              }
                            }
                          }
                        }
                      }

                    }
                    catch {
                      case io: Throwable =>
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }

                     isValidSchemeMode = {
                      if (mySchemeMode == 0 || mySchemeMode == 1){true}
                      else {false}
                    }

                    /* Lets set var isValidInputData to true if valid data is received from e-Channels/ESB-CBS System */
                    if (strMessageReference.length > 0 && strTransactionReference.length > 0 && strAccountNumber.length > 0 && isValidSchemeMode && myBankCode > 0){
                      isValidInputData = true
                      myHttpStatusCode = HttpStatusCode.Accepted //TESTS ONLY
                      responseCode = 0
                      responseMessage = "Message accepted for processing."
                    }

                    try{
                      //sourceDataTable.addRow(myBatchReference, strDebitAccountNumber, strPrn, strCustomerReference, myAmount, myBatchSize)
                      //println("strMessageReference - " + strMessageReference + ", strTransactionReference - " + strTransactionReference + ", strAccountNumber - " + strAccountNumber)
                      //println("myHttpStatusCode - " + myHttpStatusCode)
                    }
                    catch {
                      case io: Throwable =>
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }

                    try{
                      if (isValidInputData){
                        myDB.withConnection { implicit  myconn =>
                          /*
                          try {
                            val strSQL = "{ call dbo.insertOutgoingTaxPaymentDetailsBatch(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  try{
                                    val myprn = resultSet.getString("prn")
                                    val mycustomerReference = resultSet.getString("customerReference")
                                    val myresponseCode = resultSet.getInt("responseCode")
                                    val myresponseMessage = resultSet.getString("responseMessage")
                                    val myTax_PaymentDetailsResponse_Batch = new Tax_PaymentDetailsResponse_Batch(myprn, mycustomerReference, myresponseCode, myresponseMessage)
                                    myTax_PaymentDetailsResponse_BatchData  = myTax_PaymentDetailsResponse_BatchData :+ myTax_PaymentDetailsResponse_Batch
                                  }
                                  catch{
                                    case io: Throwable =>
                                      log_errors(strApifunction + " : resultSet.next - " + io.getMessage())
                                    case ex: Exception =>
                                      log_errors(strApifunction + " : resultSet.next - " + ex.getMessage())
                                  }
                                }
                              }
                            }
                            catch{
                              case io: Throwable =>
                                //io.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(io.printStackTrace())
                                log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                log_errors(strApifunction + " : " + ex.getMessage())
                            }
                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              log_errors(strApifunction + " : " + ex.getMessage())
                          }
                          */
                        }
                      }

                      else{
                        responseMessage = "Invalid Input Data length"
                        if (strMessageReference.length == 0){
                          responseMessage = "Invalid Input Data. messagereference"
                        }
                        else if (strTransactionReference.length == 0){
                          responseMessage = "Invalid Input Data. transactionreference"
                        }
                        else if (strAccountNumber.length == 0){
                          responseMessage = "Invalid Input Data. accountnumber"
                        }
                        else if (!isValidSchemeMode){
                          responseMessage = "Invalid Input Data. schememode"
                        }
                        else if (myBankCode == 0){
                          responseMessage = "Invalid Input Data. bankcode"
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
                        responseMessage = "Error occured during processing, please try again."
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        responseMessage = "Error occured during processing, please try again."
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }

                  }
                  catch {
                    case io: IOException =>
                      responseMessage = "Error occured during processing, please try again."
                      log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      responseMessage = "Error occured during processing, please try again."
                      log_errors(strApifunction + " : " + ex.getMessage())
                  }
                }
                catch
                  {
                    case ex: Exception =>
                      log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      log_errors(strApifunction + " : " + tr.getMessage())
                  }

              }
              case JsError(e) => {
                responseMessage = "Error occured when unpacking Json values"
                log_errors(strApifunction + " : " + e.toString())
              }
            }
          }
          else{
            myHttpStatusCode = HttpStatusCode.Unauthorized
          }
        }
        else {
          if (!isDataFound) {
            responseMessage = "Invalid Request Data"
          }
          else if (!isAuthTokenFound) {
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
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + tr.getMessage())
        }
      /*
      implicit val response_processUssdActions_Writes = Json.writes[response_processUssdActions]

      val myRespData: String = getAccountVerificationDetails
      val f = Future {sendAccountVerificationRequestsIpsl(myRespData)}

      implicit val textResponseData_Writes = Json.writes[textResponseData]

      val myResponseData = new textResponseData(responseMessage)

      val textResponse = {
        myHttpStatusCode match {
          case HttpStatusCode.Accepted =>
            Accepted(myResponseData.text.toString).as("text/plain")
          case HttpStatusCode.BadRequest =>
            BadRequest(myResponseData.text.toString).as("text/plain")
          case HttpStatusCode.Unauthorized =>
            Unauthorized(myResponseData.text.toString).as("text/plain")
          case _ =>
            BadRequest(myResponseData.text.toString).as("text/plain")
        }
      }
      val r: Result = textResponse
      r
      */
      /*
      implicit val AccountVerificationDetailsResponse_BatchWrites = Json.writes[AccountVerificationDetailsResponse_Batch]
      implicit val AccountVerificationDetailsResponse_BatchDataWrites = Json.writes[AccountVerificationDetailsResponse_BatchData]

      val myAccountVerificationDetailsResponse_Batch = AccountVerificationDetailsResponse_Batch(strTransactionReference, strAccountNumber, strAccountname, myBankCode.toString)
      val myAccountVerificationResponse = AccountVerificationDetailsResponse_BatchData(strMessageReference, responseCode, responseMessage, myAccountVerificationDetailsResponse_Batch)
      */
      implicit val  AccountVerificationDetailsResponse_Writes = Json.writes[AccountVerificationDetailsResponse]

      val myAccountVerificationResponse =  AccountVerificationDetailsResponse(responseCode, responseMessage)
      val jsonResponse = Json.toJson(myAccountVerificationResponse)

      try{
        val creationDateTime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
        val schemeName: String = {
          mySchemeMode match {
            case 0 =>
              SchemeName.ACC.toString.toUpperCase
            case 1 =>
              SchemeName.PHNE.toString.toUpperCase
            case _ =>
              SchemeName.ACC.toString.toUpperCase
          }
        }
        val accountVerificationDetails = AccountVerificationDetails(strMessageReference, creationDateTime, firstAgentIdentification, assignerAgentIdentification, assigneeAgentIdentification: String, strTransactionReference, strAccountNumber, schemeName, myBankCode.toString)
        //println("schemeName - " + schemeName.toString)
        //println("accountVerificationDetails - " + accountVerificationDetails.toString)
        val f = Future {
          val myRespData: String = getAccountVerificationDetails(accountVerificationDetails)
          sendAccountVerificationRequestsIpsl(myRespData)
        }
        log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = {
        myHttpStatusCode match {
          case HttpStatusCode.Accepted =>
            Accepted(jsonResponse)
          case HttpStatusCode.BadRequest =>
            BadRequest(jsonResponse)
          case HttpStatusCode.Unauthorized =>
            Unauthorized(jsonResponse)
          case _ =>
            BadRequest(jsonResponse)
        }
      }

      r
    }(myExecutionContext)
  }
  def addInternalTransferPaymentDetailsCoopBank = Action.async { request =>
      Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myCoop_InternalTransfer_PaymentDetailsResponse_BatchData : Seq[Coop_InternalTransfer_PaymentDetailsResponse_Batch] = Seq.empty[Coop_InternalTransfer_PaymentDetailsResponse_Batch]
      var myBatchNo: BigDecimal = 0
      val strApifunction : String = "addInternalTransferPaymentDetailsCoopBank"

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

        log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val Coop_InternalTransfer_PaymentDetails_Request_Reads: Reads[Coop_InternalTransfer_PaymentDetails_Request] = (
                (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "transactioncurrency_debitaccount").readNullable[JsValue] and
                (JsPath \ "description_debitaccount").readNullable[JsValue] and
                (JsPath \ "accountnumber").readNullable[JsValue] and
                (JsPath \ "accountname").readNullable[JsValue] and
                (JsPath \ "customerreference").readNullable[JsValue] and
                (JsPath \ "amount").readNullable[JsValue] and
                (JsPath \ "transactioncurrency_creditaccount").readNullable[JsValue] and
                (JsPath \ "purposeofpayment").readNullable[JsValue] and
                (JsPath \ "description_creditaccount").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(Coop_InternalTransfer_PaymentDetails_Request.apply _)

            implicit val Coop_InternalTransfer_PaymentDetails_BatchRequest_Reads: Reads[Coop_InternalTransfer_PaymentDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "paymentdata").read[Seq[Coop_InternalTransfer_PaymentDetails_Request]]
              )(Coop_InternalTransfer_PaymentDetails_BatchRequest.apply _)

            myjson.validate[Coop_InternalTransfer_PaymentDetails_BatchRequest] match {
              case JsSuccess(myCoop_InternalTransfer_PaymentDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myCoop_InternalTransfer_PaymentDetails_BatchRequest.paymentdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                  myBatchNo =  BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("LocalBankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Amount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PaymentType", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PurposeofPayment", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("MobileNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransactionCurrency_DebitAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransactionCurrency_CreditAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description_DebitAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description_CreditAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("EmailAddress", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)

                    var myAmount : BigDecimal = 0
                    var strDebitAccountNumber: String = ""
                    var strTransactionCurrency_DebitAccount: String = ""
                    var strDescription_DebitAccount: String = ""
                    var strAccountNumber: String = ""
                    var strAccountName: String = ""
                    var strCustomerReference: String = ""
                    val strLocalBankCode: String = ""
                    var strAmount: String = ""
                    val strPaymentType: String = "internaltransfer"
                    var strTransactionCurrency_CreditAccount: String = ""
                    var strPurposeofPayment: String = ""
                    val strMobileNumber: String = ""
                    var strDescription_CreditAccount: String = ""
                    var strEmailAddress: String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myCoop_InternalTransfer_PaymentDetails_BatchRequest.paymentdata.foreach(myPaymentDetails => {

                      myAmount = 0
                      strDebitAccountNumber = ""
                      strTransactionCurrency_DebitAccount = ""
                      strDescription_DebitAccount = ""
                      strAccountNumber = ""
                      strAccountName = ""
                      strCustomerReference = ""
                      strAmount = ""
                      strTransactionCurrency_CreditAccount = ""
                      strPurposeofPayment = ""
                      strDescription_CreditAccount = ""
                      strEmailAddress = ""

                      try{
                        //strDebitAccountNumber
                        if (myPaymentDetails.debitaccountnumber != None) {
                          if (myPaymentDetails.debitaccountnumber.get != None) {
                            val myData = myPaymentDetails.debitaccountnumber.get
                            strDebitAccountNumber = myData.toString()
                            if (strDebitAccountNumber != null && strDebitAccountNumber != None){
                              strDebitAccountNumber = strDebitAccountNumber.trim
                              if (strDebitAccountNumber.length > 0){
                                strDebitAccountNumber = strDebitAccountNumber.replace("'","")//Remove apostrophe
                                strDebitAccountNumber = strDebitAccountNumber.replace(" ","")//Remove spaces
                                strDebitAccountNumber = strDebitAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDebitAccountNumber = strDebitAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strTransactionCurrency_DebitAccount
                        if (myPaymentDetails.transactioncurrency_debitaccount != None) {
                          if (myPaymentDetails.transactioncurrency_debitaccount.get != None) {
                            val myData = myPaymentDetails.transactioncurrency_debitaccount.get
                            strTransactionCurrency_DebitAccount = myData.toString()
                            if (strTransactionCurrency_DebitAccount != null && strTransactionCurrency_DebitAccount != None){
                              strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.trim
                              if (strTransactionCurrency_DebitAccount.length > 0){
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replace("'","")//Remove apostrophe
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replace(" ","")//Remove spaces
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.trim
                              }
                            }
                          }
                        }

                        //strDescription_DebitAccount
                        if (myPaymentDetails.description_debitaccount != None) {
                          if (myPaymentDetails.description_debitaccount.get != None) {
                            val myData = myPaymentDetails.description_debitaccount.get
                            strDescription_DebitAccount = myData.toString()
                            if (strDescription_DebitAccount != null && strDescription_DebitAccount != None){
                              strDescription_DebitAccount = strDescription_DebitAccount.trim
                              if (strDescription_DebitAccount.length > 0){
                                strDescription_DebitAccount = strDescription_DebitAccount.replace("'","")//Remove apostrophe
                                strDescription_DebitAccount = strDescription_DebitAccount.replace("  "," ")//Remove double spaces
                                strDescription_DebitAccount = strDescription_DebitAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDescription_DebitAccount = strDescription_DebitAccount.trim
                              }
                            }
                          }
                        }

                        //strAccountNumber
                        if (myPaymentDetails.accountnumber != None) {
                          if (myPaymentDetails.accountnumber.get != None) {
                            val myData = myPaymentDetails.accountnumber.get
                            strAccountNumber = myData.toString()
                            if (strAccountNumber != null && strAccountNumber != None){
                              strAccountNumber = strAccountNumber.trim
                              if (strAccountNumber.length > 0){
                                strAccountNumber = strAccountNumber.replace("'","")//Remove apostrophe
                                strAccountNumber = strAccountNumber.replace(" ","")//Remove spaces
                                strAccountNumber = strAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAccountNumber = strAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strAccountName
                        if (myPaymentDetails.accountname != None) {
                          if (myPaymentDetails.accountname.get != None) {
                            val myData = myPaymentDetails.accountname.get
                            strAccountName = myData.toString()
                            if (strAccountName != null && strAccountName != None){
                              strAccountName = strAccountName.trim
                              if (strAccountName.length > 0){
                                strAccountName = strAccountName.replace("'","")//Remove apostrophe
                                strAccountName = strAccountName.replace("  "," ")//Remove double spaces
                                strAccountName = strAccountName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAccountName = strAccountName.trim
                              }
                            }
                          }
                        }

                        //strCustomerReference
                        if (myPaymentDetails.customerreference != None) {
                          if (myPaymentDetails.customerreference.get != None) {
                            val myData = myPaymentDetails.customerreference.get
                            strCustomerReference = myData.toString()
                            if (strCustomerReference != null && strCustomerReference != None){
                              strCustomerReference = strCustomerReference.trim
                              if (strCustomerReference.length > 0){
                                strCustomerReference = strCustomerReference.replace("'","")//Remove apostrophe
                                strCustomerReference = strCustomerReference.replace(" ","")//Remove spaces
                                strCustomerReference = strCustomerReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strCustomerReference = strCustomerReference.trim
                              }
                            }
                          }
                        }

                        //strAmount
                        if (myPaymentDetails.amount != None) {
                          if (myPaymentDetails.amount.get != None) {
                            val myData = myPaymentDetails.amount.get
                            strAmount = myData.toString()
                            if (strAmount != null && strAmount != None){
                              strAmount = strAmount.trim
                              if (strAmount.length > 0){
                                strAmount = strAmount.replace("'","")//Remove apostrophe
                                strAmount = strAmount.replace(" ","")//Remove spaces
                                strAmount = strAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAmount = strAmount.trim
                                if (strAmount.length > 0){
                                  //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                  val isNumeric : Boolean = strAmount.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                  if (isNumeric == true){
                                    myAmount = BigDecimal(strAmount)
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strTransactionCurrency_CreditAccount
                        if (myPaymentDetails.transactioncurrency_creditaccount != None) {
                          if (myPaymentDetails.transactioncurrency_creditaccount.get != None) {
                            val myData = myPaymentDetails.transactioncurrency_creditaccount.get
                            strTransactionCurrency_CreditAccount = myData.toString()
                            if (strTransactionCurrency_CreditAccount != null && strTransactionCurrency_CreditAccount != None){
                              strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.trim
                              if (strTransactionCurrency_CreditAccount.length > 0){
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.replace("'","")//Remove apostrophe
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.replace(" ","")//Remove spaces
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.trim
                              }
                            }
                          }
                        }


                        //strPurposeofPayment
                        if (myPaymentDetails.purposeofpayment != None) {
                          if (myPaymentDetails.purposeofpayment.get != None) {
                            val myData = myPaymentDetails.purposeofpayment.get
                            strPurposeofPayment = myData.toString()
                            if (strPurposeofPayment != null && strPurposeofPayment != None){
                              strPurposeofPayment = strPurposeofPayment.trim
                              if (strPurposeofPayment.length > 0){
                                strPurposeofPayment = strPurposeofPayment.replace("'","")//Remove apostrophe
                                strPurposeofPayment = strPurposeofPayment.replace(" ","")//Remove spaces
                                strPurposeofPayment = strPurposeofPayment.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPurposeofPayment = strPurposeofPayment.trim
                              }
                            }
                          }
                        }

                        //strDescription_CreditAccount
                        if (myPaymentDetails.description_creditaccount != None) {
                          if (myPaymentDetails.description_creditaccount.get != None) {
                            val myData = myPaymentDetails.description_creditaccount.get
                            strDescription_CreditAccount = myData.toString()
                            if (strDescription_CreditAccount != null && strDescription_CreditAccount != None){
                              strDescription_CreditAccount = strDescription_CreditAccount.trim
                              if (strDescription_CreditAccount.length > 0){
                                strDescription_CreditAccount = strDescription_CreditAccount.replace("'","")//Remove apostrophe
                                strDescription_CreditAccount = strDescription_CreditAccount.replace("  "," ")//Remove double spaces
                                strDescription_CreditAccount = strDescription_CreditAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDescription_CreditAccount = strDescription_CreditAccount.trim
                              }
                            }
                          }
                        }

                        //strEmailAddress
                        if (myPaymentDetails.emailaddress != None) {
                          if (myPaymentDetails.emailaddress.get != None) {
                            val myData = myPaymentDetails.emailaddress.get
                            strEmailAddress = myData.toString()
                            if (strEmailAddress != null && strEmailAddress != None){
                              strEmailAddress = strEmailAddress.trim
                              if (strEmailAddress.length > 0){
                                strEmailAddress = strEmailAddress.replace("'","")//Remove apostrophe
                                strEmailAddress = strEmailAddress.replace(" ","")//Remove spaces
                                strEmailAddress = strEmailAddress.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strEmailAddress = strEmailAddress.trim
                              }
                            }
                          }
                        }

                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ERP Core System */
                      if (!isValidInputData){
                        if (strDebitAccountNumber.length > 0 && strAccountNumber.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myBatchReference,strDebitAccountNumber,strAccountNumber,strAccountName,strCustomerReference,strLocalBankCode,myAmount,strPaymentType,strPurposeofPayment,strMobileNumber,strTransactionCurrency_DebitAccount,strTransactionCurrency_CreditAccount,strDescription_DebitAccount,strDescription_CreditAccount,strEmailAddress,myBatchSize)
                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.insertOutgoingCoopPaymentDetailsBatch_InternalTransfer(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  try{
                                    val myaccountnumber = resultSet.getString("accountnumber")
                                    val mycustomerreference = resultSet.getString("customerreference")
                                    val myresponseCode = resultSet.getInt("responseCode")
                                    val myresponseMessage = resultSet.getString("responseMessage")
                                    val myCoop_InternalTransfer_PaymentDetailsResponse_Batch = new Coop_InternalTransfer_PaymentDetailsResponse_Batch(myaccountnumber, mycustomerreference, myresponseCode, myresponseMessage)
                                    myCoop_InternalTransfer_PaymentDetailsResponse_BatchData  = myCoop_InternalTransfer_PaymentDetailsResponse_BatchData :+ myCoop_InternalTransfer_PaymentDetailsResponse_Batch
                                  }
                                  catch{
                                    case io: Throwable =>
                                      log_errors(strApifunction + " : resultSet.next - " + io.getMessage())
                                    case ex: Exception =>
                                      log_errors(strApifunction + " : resultSet.next - " + ex.getMessage())
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
                                log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                log_errors(strApifunction + " : " + ex.getMessage())
                            }

                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              log_errors(strApifunction + " : " + ex.getMessage())
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
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        log_errors(strApifunction + " : " + ex.getMessage())
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
                      log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      log_errors(strApifunction + " : " + ex.getMessage())
                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + tr.getMessage())
                  }
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values"
                log_errors(strApifunction + " : " + e.toString())
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
          isProcessed = false
          responseMessage = "Error occured during processing, please try again."
          log_errors(strApifunction + " : " + ex.getMessage())
        case tr: Throwable =>
          isProcessed = false
          responseMessage = "Error occured during processing, please try again."
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      implicit val Coop_InternalTransfer_PaymentDetailsResponse_BatchWrites = Json.writes[Coop_InternalTransfer_PaymentDetailsResponse_Batch]
      implicit val Coop_InternalTransfer_PaymentDetailsResponse_BatchDataWrites = Json.writes[Coop_InternalTransfer_PaymentDetailsResponse_BatchData]

      if (myCoop_InternalTransfer_PaymentDetailsResponse_BatchData.isEmpty == true || myCoop_InternalTransfer_PaymentDetailsResponse_BatchData == true){
        val myCoop_InternalTransfer_PaymentDetailsResponse_Batch = new Coop_InternalTransfer_PaymentDetailsResponse_Batch("", "", responseCode, responseMessage)
        myCoop_InternalTransfer_PaymentDetailsResponse_BatchData  = myCoop_InternalTransfer_PaymentDetailsResponse_BatchData :+ myCoop_InternalTransfer_PaymentDetailsResponse_Batch
      }

      val myPaymentDetailsResponse = new Coop_InternalTransfer_PaymentDetailsResponse_BatchData(myBatchNo, myCoop_InternalTransfer_PaymentDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myPaymentDetailsResponse)

      try{
        log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def addAccountToPesalinkTransferPaymentDetailsCoopBank = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData : Seq[Coop_AcctoPesalink_PaymentDetailsResponse_Batch] = Seq.empty[Coop_AcctoPesalink_PaymentDetailsResponse_Batch]
      var myBatchNo: BigDecimal = 0
      val strApifunction : String = "addAccountToPesalinkTransferPaymentDetailsCoopBank"

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

        log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val Coop_AcctoPesalink_PaymentDetails_Request_Reads: Reads[Coop_AcctoPesalink_PaymentDetails_Request] = (
              (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "transactioncurrency_debitaccount").readNullable[JsValue] and
                (JsPath \ "description_debitaccount").readNullable[JsValue] and
                (JsPath \ "accountnumber").readNullable[JsValue] and
                (JsPath \ "accountname").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue] and
                (JsPath \ "customerreference").readNullable[JsValue] and
                (JsPath \ "amount").readNullable[JsValue] and
                (JsPath \ "transactioncurrency_creditaccount").readNullable[JsValue] and
                (JsPath \ "purposeofpayment").readNullable[JsValue] and
                (JsPath \ "description_creditaccount").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(Coop_AcctoPesalink_PaymentDetails_Request.apply _)

            implicit val Coop_AcctoPesalink_PaymentDetails_BatchRequest_Reads: Reads[Coop_AcctoPesalink_PaymentDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "paymentdata").read[Seq[Coop_AcctoPesalink_PaymentDetails_Request]]
              )(Coop_AcctoPesalink_PaymentDetails_BatchRequest.apply _)

            myjson.validate[Coop_AcctoPesalink_PaymentDetails_BatchRequest] match {
              case JsSuccess(myCoop_AcctoPesalink_PaymentDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myCoop_AcctoPesalink_PaymentDetails_BatchRequest.paymentdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                  myBatchNo =  BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("LocalBankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Amount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PaymentType", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PurposeofPayment", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("MobileNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransactionCurrency_DebitAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransactionCurrency_CreditAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description_DebitAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description_CreditAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("EmailAddress", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)

                    var myAmount : BigDecimal = 0
                    var strDebitAccountNumber: String = ""
                    var strTransactionCurrency_DebitAccount: String = ""
                    var strDescription_DebitAccount: String = ""
                    var strAccountNumber: String = ""
                    var strAccountName: String = ""
                    var strCustomerReference: String = ""
                    var strLocalBankCode: String = ""
                    var strAmount: String = ""
                    val strPaymentType: String = "pesalinktransfer"
                    var strTransactionCurrency_CreditAccount: String = ""
                    var strPurposeofPayment: String = ""
                    val strMobileNumber: String = ""
                    var strDescription_CreditAccount: String = ""
                    var strEmailAddress: String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myCoop_AcctoPesalink_PaymentDetails_BatchRequest.paymentdata.foreach(myPaymentDetails => {

                      myAmount = 0
                      strDebitAccountNumber = ""
                      strTransactionCurrency_DebitAccount = ""
                      strDescription_DebitAccount = ""
                      strAccountNumber = ""
                      strAccountName = ""
                      strCustomerReference = ""
                      strLocalBankCode = ""
                      strAmount = ""
                      strTransactionCurrency_CreditAccount = ""
                      strPurposeofPayment = ""
                      strDescription_CreditAccount = ""
                      strEmailAddress = ""

                      try{
                        //strDebitAccountNumber
                        if (myPaymentDetails.debitaccountnumber != None) {
                          if (myPaymentDetails.debitaccountnumber.get != None) {
                            val myData = myPaymentDetails.debitaccountnumber.get
                            strDebitAccountNumber = myData.toString()
                            if (strDebitAccountNumber != null && strDebitAccountNumber != None){
                              strDebitAccountNumber = strDebitAccountNumber.trim
                              if (strDebitAccountNumber.length > 0){
                                strDebitAccountNumber = strDebitAccountNumber.replace("'","")//Remove apostrophe
                                strDebitAccountNumber = strDebitAccountNumber.replace(" ","")//Remove spaces
                                strDebitAccountNumber = strDebitAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDebitAccountNumber = strDebitAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strTransactionCurrency_DebitAccount
                        if (myPaymentDetails.transactioncurrency_debitaccount != None) {
                          if (myPaymentDetails.transactioncurrency_debitaccount.get != None) {
                            val myData = myPaymentDetails.transactioncurrency_debitaccount.get
                            strTransactionCurrency_DebitAccount = myData.toString()
                            if (strTransactionCurrency_DebitAccount != null && strTransactionCurrency_DebitAccount != None){
                              strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.trim
                              if (strTransactionCurrency_DebitAccount.length > 0){
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replace("'","")//Remove apostrophe
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replace(" ","")//Remove spaces
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.trim
                              }
                            }
                          }
                        }

                        //strDescription_DebitAccount
                        if (myPaymentDetails.description_debitaccount != None) {
                          if (myPaymentDetails.description_debitaccount.get != None) {
                            val myData = myPaymentDetails.description_debitaccount.get
                            strDescription_DebitAccount = myData.toString()
                            if (strDescription_DebitAccount != null && strDescription_DebitAccount != None){
                              strDescription_DebitAccount = strDescription_DebitAccount.trim
                              if (strDescription_DebitAccount.length > 0){
                                strDescription_DebitAccount = strDescription_DebitAccount.replace("'","")//Remove apostrophe
                                strDescription_DebitAccount = strDescription_DebitAccount.replace("  "," ")//Remove double spaces
                                strDescription_DebitAccount = strDescription_DebitAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDescription_DebitAccount = strDescription_DebitAccount.trim
                              }
                            }
                          }
                        }

                        //strAccountNumber
                        if (myPaymentDetails.accountnumber != None) {
                          if (myPaymentDetails.accountnumber.get != None) {
                            val myData = myPaymentDetails.accountnumber.get
                            strAccountNumber = myData.toString()
                            if (strAccountNumber != null && strAccountNumber != None){
                              strAccountNumber = strAccountNumber.trim
                              if (strAccountNumber.length > 0){
                                strAccountNumber = strAccountNumber.replace("'","")//Remove apostrophe
                                strAccountNumber = strAccountNumber.replace(" ","")//Remove spaces
                                strAccountNumber = strAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAccountNumber = strAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strAccountName
                        if (myPaymentDetails.accountname != None) {
                          if (myPaymentDetails.accountname.get != None) {
                            val myData = myPaymentDetails.accountname.get
                            strAccountName = myData.toString()
                            if (strAccountName != null && strAccountName != None){
                              strAccountName = strAccountName.trim
                              if (strAccountName.length > 0){
                                strAccountName = strAccountName.replace("'","")//Remove apostrophe
                                strAccountName = strAccountName.replace("  "," ")//Remove double spaces
                                strAccountName = strAccountName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAccountName = strAccountName.trim
                              }
                            }
                          }
                        }

                        //strCustomerReference
                        if (myPaymentDetails.customerreference != None) {
                          if (myPaymentDetails.customerreference.get != None) {
                            val myData = myPaymentDetails.customerreference.get
                            strCustomerReference = myData.toString()
                            if (strCustomerReference != null && strCustomerReference != None){
                              strCustomerReference = strCustomerReference.trim
                              if (strCustomerReference.length > 0){
                                strCustomerReference = strCustomerReference.replace("'","")//Remove apostrophe
                                strCustomerReference = strCustomerReference.replace(" ","")//Remove spaces
                                strCustomerReference = strCustomerReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strCustomerReference = strCustomerReference.trim
                              }
                            }
                          }
                        }

                        //strLocalBankCode
                        if (myPaymentDetails.bankcode != None) {
                          if (myPaymentDetails.bankcode.get != None) {
                            val myData = myPaymentDetails.bankcode.get
                            strLocalBankCode = myData.toString()
                            if (strLocalBankCode != null && strLocalBankCode != None){
                              strLocalBankCode = strLocalBankCode.trim
                              if (strLocalBankCode.length > 0){
                                strLocalBankCode = strLocalBankCode.replace("'","")//Remove apostrophe
                                strLocalBankCode = strLocalBankCode.replace(" ","")//Remove spaces
                                strLocalBankCode = strLocalBankCode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strLocalBankCode = strLocalBankCode.trim
                              }
                            }
                          }
                        }

                        //strAmount
                        if (myPaymentDetails.amount != None) {
                          if (myPaymentDetails.amount.get != None) {
                            val myData = myPaymentDetails.amount.get
                            strAmount = myData.toString()
                            if (strAmount != null && strAmount != None){
                              strAmount = strAmount.trim
                              if (strAmount.length > 0){
                                strAmount = strAmount.replace("'","")//Remove apostrophe
                                strAmount = strAmount.replace(" ","")//Remove spaces
                                strAmount = strAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAmount = strAmount.trim
                                if (strAmount.length > 0){
                                  //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                  val isNumeric : Boolean = strAmount.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                  if (isNumeric == true){
                                    myAmount = BigDecimal(strAmount)
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strTransactionCurrency_CreditAccount
                        if (myPaymentDetails.transactioncurrency_creditaccount != None) {
                          if (myPaymentDetails.transactioncurrency_creditaccount.get != None) {
                            val myData = myPaymentDetails.transactioncurrency_creditaccount.get
                            strTransactionCurrency_CreditAccount = myData.toString()
                            if (strTransactionCurrency_CreditAccount != null && strTransactionCurrency_CreditAccount != None){
                              strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.trim
                              if (strTransactionCurrency_CreditAccount.length > 0){
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.replace("'","")//Remove apostrophe
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.replace(" ","")//Remove spaces
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strTransactionCurrency_CreditAccount = strTransactionCurrency_CreditAccount.trim
                              }
                            }
                          }
                        }


                        //strPurposeofPayment
                        if (myPaymentDetails.purposeofpayment != None) {
                          if (myPaymentDetails.purposeofpayment.get != None) {
                            val myData = myPaymentDetails.purposeofpayment.get
                            strPurposeofPayment = myData.toString()
                            if (strPurposeofPayment != null && strPurposeofPayment != None){
                              strPurposeofPayment = strPurposeofPayment.trim
                              if (strPurposeofPayment.length > 0){
                                strPurposeofPayment = strPurposeofPayment.replace("'","")//Remove apostrophe
                                strPurposeofPayment = strPurposeofPayment.replace(" ","")//Remove spaces
                                strPurposeofPayment = strPurposeofPayment.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPurposeofPayment = strPurposeofPayment.trim
                              }
                            }
                          }
                        }

                        //strDescription_CreditAccount
                        if (myPaymentDetails.description_creditaccount != None) {
                          if (myPaymentDetails.description_creditaccount.get != None) {
                            val myData = myPaymentDetails.description_creditaccount.get
                            strDescription_CreditAccount = myData.toString()
                            if (strDescription_CreditAccount != null && strDescription_CreditAccount != None){
                              strDescription_CreditAccount = strDescription_CreditAccount.trim
                              if (strDescription_CreditAccount.length > 0){
                                strDescription_CreditAccount = strDescription_CreditAccount.replace("'","")//Remove apostrophe
                                strDescription_CreditAccount = strDescription_CreditAccount.replace("  "," ")//Remove double spaces
                                strDescription_CreditAccount = strDescription_CreditAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDescription_CreditAccount = strDescription_CreditAccount.trim
                              }
                            }
                          }
                        }

                        //strEmailAddress
                        if (myPaymentDetails.emailaddress != None) {
                          if (myPaymentDetails.emailaddress.get != None) {
                            val myData = myPaymentDetails.emailaddress.get
                            strEmailAddress = myData.toString()
                            if (strEmailAddress != null && strEmailAddress != None){
                              strEmailAddress = strEmailAddress.trim
                              if (strEmailAddress.length > 0){
                                strEmailAddress = strEmailAddress.replace("'","")//Remove apostrophe
                                strEmailAddress = strEmailAddress.replace(" ","")//Remove spaces
                                strEmailAddress = strEmailAddress.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strEmailAddress = strEmailAddress.trim
                              }
                            }
                          }
                        }

                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from ERP Core System */
                      if (!isValidInputData){
                        if (strDebitAccountNumber.length > 0 && strAccountNumber.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myBatchReference,strDebitAccountNumber,strAccountNumber,strAccountName,strCustomerReference,strLocalBankCode,myAmount,strPaymentType,strPurposeofPayment,strMobileNumber,strTransactionCurrency_DebitAccount,strTransactionCurrency_CreditAccount,strDescription_DebitAccount,strDescription_CreditAccount,strEmailAddress,myBatchSize)
                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.insertOutgoingCoopPaymentDetailsBatch_PesalinkTransfer(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  try{
                                    val myaccountnumber = resultSet.getString("accountnumber")
                                    val mycustomerreference = resultSet.getString("customerreference")
                                    val myresponseCode = resultSet.getInt("responseCode")
                                    val myresponseMessage = resultSet.getString("responseMessage")
                                    val myCoop_AcctoPesalink_PaymentDetailsResponse_Batch = new Coop_AcctoPesalink_PaymentDetailsResponse_Batch(myaccountnumber, mycustomerreference, myresponseCode, myresponseMessage)
                                    myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData  = myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData :+ myCoop_AcctoPesalink_PaymentDetailsResponse_Batch
                                  }
                                  catch{
                                    case io: Throwable =>
                                      log_errors(strApifunction + " : resultSet.next - " + io.getMessage())
                                    case ex: Exception =>
                                      log_errors(strApifunction + " : resultSet.next - " + ex.getMessage())
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
                                log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                log_errors(strApifunction + " : " + ex.getMessage())
                            }

                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              log_errors(strApifunction + " : " + ex.getMessage())
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
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        log_errors(strApifunction + " : " + ex.getMessage())
                    }
                  }
                  catch {
                    case io: IOException =>
                      //io.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(io.printStackTrace())
                      entryID = 2
                      log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      log_errors(strApifunction + " : " + ex.getMessage())
                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + tr.getMessage())
                  }
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values"
                log_errors(strApifunction + " : " + e.toString())
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
            isProcessed = false
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + tr.getMessage())
        }

      implicit val Coop_AcctoPesalink_PaymentDetailsResponse_BatchWrites = Json.writes[Coop_AcctoPesalink_PaymentDetailsResponse_Batch]
      implicit val Coop_AcctoPesalink_PaymentDetailsResponse_BatchDataWrites = Json.writes[Coop_AcctoPesalink_PaymentDetailsResponse_BatchData]

      if (myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData.isEmpty == true || myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData == true){
        val myCoop_AcctoPesalink_PaymentDetailsResponse_Batch = new Coop_AcctoPesalink_PaymentDetailsResponse_Batch("", "", responseCode, responseMessage)
        myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData  = myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData :+ myCoop_AcctoPesalink_PaymentDetailsResponse_Batch
      }

      val myPaymentDetailsResponse = new Coop_AcctoPesalink_PaymentDetailsResponse_BatchData(myBatchNo, myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myPaymentDetailsResponse)

      try{
        log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def addAccountToMpesaTransferPaymentDetailsCoopBank = Action.async { request =>
    Future {
      val startDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var isProcessed : Boolean = false
      var entryID: Int = 0
      var responseCode : Int = 1
      var responseMessage : String = "Error occured during processing, please try again."
      var myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData : Seq[Coop_AcctoMpesa_PaymentDetailsResponse_Batch] = Seq.empty[Coop_AcctoMpesa_PaymentDetailsResponse_Batch]
      var myBatchNo: BigDecimal = 0
      val strApifunction : String = "addAccountToMpesaTransferPaymentDetailsCoopBank"

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

        log_data(strApifunction + " : " + " channeltype - " + strChannelType + " , request - " + strRequest  + " , startdate - " + startDate + " , header - " + strRequestHeader + " , remoteAddress - " + request.remoteAddress)

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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
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
                log_errors(strApifunction + " : " + ex.getMessage())
              case tr: Throwable =>
                isProcessed = false//strname = "no data"//println("Got some other kind of exception")
                log_errors(strApifunction + " : " + tr.getMessage())
            }

          if (isCredentialsFound == true && responseCode == 0){

            val myjson = request.body.asJson.get

            //Initialise responseCode
            responseCode = 1
            responseMessage  = "Error occured during processing, please try again."

            implicit val Coop_AcctoMpesa_PaymentDetails_Request_Reads: Reads[Coop_AcctoMpesa_PaymentDetails_Request] = (
              (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "transactioncurrency_debitaccount").readNullable[JsValue] and
                (JsPath \ "description_debitaccount").readNullable[JsValue] and
                (JsPath \ "mobilenumber").readNullable[JsValue] and
                (JsPath \ "customerreference").readNullable[JsValue] and
                (JsPath \ "amount").readNullable[JsValue] and
                (JsPath \ "purposeofpayment").readNullable[JsValue] and
                (JsPath \ "description_creditaccount").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(Coop_AcctoMpesa_PaymentDetails_Request.apply _)

            implicit val Coop_AcctoMpesa_PaymentDetails_BatchRequest_Reads: Reads[Coop_AcctoMpesa_PaymentDetails_BatchRequest] = (
              (JsPath \ "batchno").readNullable[Int] and
                (JsPath \ "paymentdata").read[Seq[Coop_AcctoMpesa_PaymentDetails_Request]]
              )(Coop_AcctoMpesa_PaymentDetails_BatchRequest.apply _)

            myjson.validate[Coop_AcctoMpesa_PaymentDetails_BatchRequest] match {
              case JsSuccess(myCoop_AcctoMpesa_PaymentDetails_BatchRequest, _) => {

                var isValidInputData : Boolean = false
                var myBatchSize : Integer = 0
                var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  entryID = 0
                  myBatchSize = myCoop_AcctoMpesa_PaymentDetails_BatchRequest.paymentdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                  myBatchNo =  BigDecimal(strBatchReference)

                  try{

                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("AccountName", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("LocalBankCode", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Amount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("PaymentType", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("PurposeofPayment", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("MobileNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransactionCurrency_DebitAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("TransactionCurrency_CreditAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description_DebitAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Description_CreditAccount", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("EmailAddress", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)

                    var myAmount : BigDecimal = 0
                    var strDebitAccountNumber: String = ""
                    var strTransactionCurrency_DebitAccount: String = ""
                    var strDescription_DebitAccount: String = ""
                    val strAccountNumber: String = ""
                    val strAccountName: String = ""
                    var strCustomerReference: String = ""
                    val strLocalBankCode: String = ""
                    var strAmount: String = ""
                    val strPaymentType: String = "mpesatransfer"
                    val strTransactionCurrency_CreditAccount: String = ""
                    var strPurposeofPayment: String = ""
                    var strMobileNumber: String = ""
                    var strDescription_CreditAccount: String = ""
                    var strEmailAddress: String = ""

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    myCoop_AcctoMpesa_PaymentDetails_BatchRequest.paymentdata.foreach(myPaymentDetails => {

                      myAmount = 0
                      strDebitAccountNumber = ""
                      strTransactionCurrency_DebitAccount = ""
                      strDescription_DebitAccount = ""
                      strMobileNumber = ""
                      strCustomerReference = ""
                      strAmount = ""
                      strPurposeofPayment = ""
                      strDescription_CreditAccount = ""
                      strEmailAddress = ""

                      try{
                        //strDebitAccountNumber
                        if (myPaymentDetails.debitaccountnumber != None) {
                          if (myPaymentDetails.debitaccountnumber.get != None) {
                            val myData = myPaymentDetails.debitaccountnumber.get
                            strDebitAccountNumber = myData.toString()
                            if (strDebitAccountNumber != null && strDebitAccountNumber != None){
                              strDebitAccountNumber = strDebitAccountNumber.trim
                              if (strDebitAccountNumber.length > 0){
                                strDebitAccountNumber = strDebitAccountNumber.replace("'","")//Remove apostrophe
                                strDebitAccountNumber = strDebitAccountNumber.replace(" ","")//Remove spaces
                                strDebitAccountNumber = strDebitAccountNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDebitAccountNumber = strDebitAccountNumber.trim
                              }
                            }
                          }
                        }

                        //strTransactionCurrency_DebitAccount
                        if (myPaymentDetails.transactioncurrency_debitaccount != None) {
                          if (myPaymentDetails.transactioncurrency_debitaccount.get != None) {
                            val myData = myPaymentDetails.transactioncurrency_debitaccount.get
                            strTransactionCurrency_DebitAccount = myData.toString()
                            if (strTransactionCurrency_DebitAccount != null && strTransactionCurrency_DebitAccount != None){
                              strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.trim
                              if (strTransactionCurrency_DebitAccount.length > 0){
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replace("'","")//Remove apostrophe
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replace(" ","")//Remove spaces
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strTransactionCurrency_DebitAccount = strTransactionCurrency_DebitAccount.trim
                              }
                            }
                          }
                        }

                        //strDescription_DebitAccount
                        if (myPaymentDetails.description_debitaccount != None) {
                          if (myPaymentDetails.description_debitaccount.get != None) {
                            val myData = myPaymentDetails.description_debitaccount.get
                            strDescription_DebitAccount = myData.toString()
                            if (strDescription_DebitAccount != null && strDescription_DebitAccount != None){
                              strDescription_DebitAccount = strDescription_DebitAccount.trim
                              if (strDescription_DebitAccount.length > 0){
                                strDescription_DebitAccount = strDescription_DebitAccount.replace("'","")//Remove apostrophe
                                strDescription_DebitAccount = strDescription_DebitAccount.replace("  "," ")//Remove double spaces
                                strDescription_DebitAccount = strDescription_DebitAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDescription_DebitAccount = strDescription_DebitAccount.trim
                              }
                            }
                          }
                        }

                        //strMobileNumber
                        if (myPaymentDetails.mobilenumber != None) {
                          if (myPaymentDetails.mobilenumber.get != None) {
                            val myData = myPaymentDetails.mobilenumber.get
                            strMobileNumber = myData.toString()
                            if (strMobileNumber != null && strMobileNumber != None){
                              strMobileNumber = strMobileNumber.trim
                              if (strMobileNumber.length > 0){
                                strMobileNumber = strMobileNumber.replace("'","")//Remove apostrophe
                                strMobileNumber = strMobileNumber.replace(" ","")//Remove spaces
                                strMobileNumber = strMobileNumber.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strMobileNumber = strMobileNumber.trim
                              }
                            }
                          }
                        }

                        //strCustomerReference
                        if (myPaymentDetails.customerreference != None) {
                          if (myPaymentDetails.customerreference.get != None) {
                            val myData = myPaymentDetails.customerreference.get
                            strCustomerReference = myData.toString()
                            if (strCustomerReference != null && strCustomerReference != None){
                              strCustomerReference = strCustomerReference.trim
                              if (strCustomerReference.length > 0){
                                strCustomerReference = strCustomerReference.replace("'","")//Remove apostrophe
                                strCustomerReference = strCustomerReference.replace(" ","")//Remove spaces
                                strCustomerReference = strCustomerReference.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strCustomerReference = strCustomerReference.trim
                              }
                            }
                          }
                        }

                        //strAmount
                        if (myPaymentDetails.amount != None) {
                          if (myPaymentDetails.amount.get != None) {
                            val myData = myPaymentDetails.amount.get
                            strAmount = myData.toString()
                            if (strAmount != null && strAmount != None){
                              strAmount = strAmount.trim
                              if (strAmount.length > 0){
                                strAmount = strAmount.replace("'","")//Remove apostrophe
                                strAmount = strAmount.replace(" ","")//Remove spaces
                                strAmount = strAmount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strAmount = strAmount.trim
                                if (strAmount.length > 0){
                                  //val isNumeric : Boolean = strAmount.toString.matches("[0-9]+") //validate numbers only
                                  val isNumeric : Boolean = strAmount.matches("^[1-9]\\d*(\\.\\d+)?$") //validate number and decimals
                                  if (isNumeric == true){
                                    myAmount = BigDecimal(strAmount)
                                  }
                                }
                              }
                            }
                          }
                        }

                        //strPurposeofPayment
                        if (myPaymentDetails.purposeofpayment != None) {
                          if (myPaymentDetails.purposeofpayment.get != None) {
                            val myData = myPaymentDetails.purposeofpayment.get
                            strPurposeofPayment = myData.toString()
                            if (strPurposeofPayment != null && strPurposeofPayment != None){
                              strPurposeofPayment = strPurposeofPayment.trim
                              if (strPurposeofPayment.length > 0){
                                strPurposeofPayment = strPurposeofPayment.replace("'","")//Remove apostrophe
                                strPurposeofPayment = strPurposeofPayment.replace(" ","")//Remove spaces
                                strPurposeofPayment = strPurposeofPayment.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strPurposeofPayment = strPurposeofPayment.trim
                              }
                            }
                          }
                        }

                        //strDescription_CreditAccount
                        if (myPaymentDetails.description_creditaccount != None) {
                          if (myPaymentDetails.description_creditaccount.get != None) {
                            val myData = myPaymentDetails.description_creditaccount.get
                            strDescription_CreditAccount = myData.toString()
                            if (strDescription_CreditAccount != null && strDescription_CreditAccount != None){
                              strDescription_CreditAccount = strDescription_CreditAccount.trim
                              if (strDescription_CreditAccount.length > 0){
                                strDescription_CreditAccount = strDescription_CreditAccount.replace("'","")//Remove apostrophe
                                strDescription_CreditAccount = strDescription_CreditAccount.replace("  "," ")//Remove double spaces
                                strDescription_CreditAccount = strDescription_CreditAccount.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strDescription_CreditAccount = strDescription_CreditAccount.trim
                              }
                            }
                          }
                        }

                        //strEmailAddress
                        if (myPaymentDetails.emailaddress != None) {
                          if (myPaymentDetails.emailaddress.get != None) {
                            val myData = myPaymentDetails.emailaddress.get
                            strEmailAddress = myData.toString()
                            if (strEmailAddress != null && strEmailAddress != None){
                              strEmailAddress = strEmailAddress.trim
                              if (strEmailAddress.length > 0){
                                strEmailAddress = strEmailAddress.replace("'","")//Remove apostrophe
                                strEmailAddress = strEmailAddress.replace(" ","")//Remove spaces
                                strEmailAddress = strEmailAddress.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strEmailAddress = strEmailAddress.trim
                              }
                            }
                          }
                        }

                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }

                      /* Lets set var isValidInputData to true if valid data is received from FundMaster Core System */
                      if (!isValidInputData){
                        if (strDebitAccountNumber.length > 0 && strMobileNumber.length > 0){
                          isValidInputData = true
                        }
                      }

                      try{
                        sourceDataTable.addRow(myBatchReference,strDebitAccountNumber,strAccountNumber,strAccountName,strCustomerReference,strLocalBankCode,myAmount,strPaymentType,strPurposeofPayment,strMobileNumber,strTransactionCurrency_DebitAccount,strTransactionCurrency_CreditAccount,strDescription_DebitAccount,strDescription_CreditAccount,strEmailAddress,myBatchSize)
                      }
                      catch {
                        case io: Throwable =>
                          log_errors(strApifunction + " : " + io.getMessage())
                        case ex: Exception =>
                          log_errors(strApifunction + " : " + ex.getMessage())
                      }
                    })

                    try{
                      if (isValidInputData){
                        myDB.withConnection { implicit  myconn =>

                          try {
                            val strSQL = "{ call dbo.insertOutgoingCoopPaymentDetailsBatch_MpesaTransfer(?) }"
                            val mystmt = myconn.prepareCall(strSQL)
                            try {
                              mystmt.setObject(1, sourceDataTable)
                              val resultSet = mystmt.executeQuery()
                              isProcessed = true
                              if (resultSet != null){
                                while ( resultSet.next()){
                                  try{
                                    val mymobilenumber = resultSet.getString("mobilenumber")
                                    val mycustomerreference = resultSet.getString("customerreference")
                                    val myresponseCode = resultSet.getInt("responseCode")
                                    val myresponseMessage = resultSet.getString("responseMessage")
                                    val myCoop_AcctoMpesa_PaymentDetailsResponse_Batch = new Coop_AcctoMpesa_PaymentDetailsResponse_Batch(mymobilenumber, mycustomerreference, myresponseCode, myresponseMessage)
                                    myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData  = myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData :+ myCoop_AcctoMpesa_PaymentDetailsResponse_Batch
                                  }
                                  catch{
                                    case io: Throwable =>
                                      log_errors(strApifunction + " : resultSet.next - " + io.getMessage())
                                    case ex: Exception =>
                                      log_errors(strApifunction + " : resultSet.next - " + ex.getMessage())
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
                                log_errors(strApifunction + " : " + io.getMessage())
                              //strErrorMsg = io.toString
                              case ex: Exception =>
                                //ex.printStackTrace()
                                responseMessage = "Error occured during processing, please try again."
                                //println(ex.printStackTrace())
                                entryID = 3
                                log_errors(strApifunction + " : " + ex.getMessage())
                            }

                          }
                          catch{
                            case io: IOException =>
                              //io.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(io.printStackTrace())
                              entryID = 2
                              log_errors(strApifunction + " : " + io.getMessage())
                            //strErrorMsg = io.toString
                            case ex: Exception =>
                              //ex.printStackTrace()
                              responseMessage = "Error occured during processing, please try again."
                              //println(ex.printStackTrace())
                              entryID = 3
                              log_errors(strApifunction + " : " + ex.getMessage())
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
                        log_errors(strApifunction + " : " + io.getMessage())
                      case ex: Exception =>
                        //ex.printStackTrace()
                        responseMessage = "Error occured during processing, please try again."
                        //println(ex.printStackTrace())
                        entryID = 3
                        log_errors(strApifunction + " : " + ex.getMessage())
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
                      log_errors(strApifunction + " : " + io.getMessage())
                    case ex: Exception =>
                      //ex.printStackTrace()
                      responseMessage = "Error occured during processing, please try again."
                      //println(ex.printStackTrace())
                      entryID = 3
                      log_errors(strApifunction + " : " + ex.getMessage())
                  }
                }
                catch
                  {
                    case ex: Exception =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + ex.getMessage())
                    case tr: Throwable =>
                      isProcessed = false
                      log_errors(strApifunction + " : " + tr.getMessage())
                  }
              }
              case JsError(e) => {
                // do something
                isProcessed = false
                responseMessage = "Error occured when unpacking Json values"
                log_errors(strApifunction + " : " + e.toString())
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
            isProcessed = false
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + ex.getMessage())
          case tr: Throwable =>
            isProcessed = false
            responseMessage = "Error occured during processing, please try again."
            log_errors(strApifunction + " : " + tr.getMessage())
        }

      implicit val Coop_AcctoMpesa_PaymentDetailsResponse_BatchWrites = Json.writes[Coop_AcctoMpesa_PaymentDetailsResponse_Batch]
      implicit val Coop_AcctoMpesa_PaymentDetailsResponse_BatchDataWrites = Json.writes[Coop_AcctoMpesa_PaymentDetailsResponse_BatchData]

      if (myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData.isEmpty == true || myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData == true){
        val myCoop_AcctoMpesa_PaymentDetailsResponse_Batch = new Coop_AcctoMpesa_PaymentDetailsResponse_Batch("", "", responseCode, responseMessage)
        myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData  = myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData :+ myCoop_AcctoMpesa_PaymentDetailsResponse_Batch
      }

      val myPaymentDetailsResponse = new Coop_AcctoMpesa_PaymentDetailsResponse_BatchData(myBatchNo, myCoop_AcctoMpesa_PaymentDetailsResponse_BatchData)

      val jsonResponse = Json.toJson(myPaymentDetailsResponse)

      try{
        log_data(strApifunction + " : " + "response - " + jsonResponse.toString() + " , remoteAddress - " + request.remoteAddress)
      }
      catch{
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage())
        case io: IOException =>
          log_errors(strApifunction + " : " + io.getMessage())
        case tr: Throwable =>
          log_errors(strApifunction + " : " + tr.getMessage())
      }

      val r: Result = Ok(jsonResponse)
      r
    }(myExecutionContext)
  }
  def sendAccountVerificationRequestsIpsl(myRequestData: String): Unit = {
    val strApifunction : String = "sendAccountVerificationRequestsIpsl"
    var strProjectionType  : String = "RetirementsReduced"
    var strApiURL  : String = "http://localhost:9001/iso20022/v1/verification-request"
    val myMemberNo : Int = 1
    val myMemberId : Int = 1
    val myProjectionType  : Int = 1

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
        log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage())
    }
    /*
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
    */
    val myuri : Uri = strApiURL //"http://172.16.109.253:8088/Xi/api/getProjectionsForMember/283632/60/6973/Retirements Reduced"

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myXmlData : String = ""
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
        log_errors(strApifunction + " : Failure in fetching  MemberNo - " + myMemberNo + " , MemberId - " + myMemberId)
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
      if (isValidData) {

        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        var strUserName: String = "testUid"
        var strPassWord: String = "testPwd"
        /*
        try {
          strUserName = getCbsApiUserName
          var strPwd: String = getCbsApiPassword //n6,e$=p8QK\+c^h~
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
        */
        if (strUserName == null){
          strUserName = ""
        }

        if (strPassWord == null){
          strPassWord = ""
        }

        if (strUserName.trim.length == 0){
          log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strUserName + " , application error occured.")
          return
        }

        if (strPassWord.trim.length == 0){
          log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strPassWord + " , application error occured.")
          return
        }

        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //***working*** val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        /* TESTS ONLY */
        val accessToken: String = "sassasasss"
        myXmlData = myRequestData
        val data = HttpEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), myXmlData)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time: Future[String] = Future(start_time_DB)
        val myMember_No: Future[Int] = Future(myMemberNo)
        //TESTS ONLY
        println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              println("start 2: " + res.status.intValue())
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
                  //val resByteStr: String = res.entity.toString
                  //val resByteStr: akka.util.ByteString = res.entity
                  //println("res.entity - " + res.entity.toString())

                  val myData = res.entity
                  if (myData != null){
                    val x = myData.asInstanceOf[HttpEntity.Strict].getData().decodeString(StandardCharsets.UTF_8)
                    //println("res.entity x - " + x.toString)
                    val y: scala.xml.Node = scala.xml.XML.loadString(x)
                    val myAccountVerification = AccountVerificationResponse.fromXml(y)
                    //println("myAccountVerification - " + myAccountVerification.toString)
                    //println("myAccountVerification.originalMessageIdentification - " + myAccountVerification.originalMessageIdentification.toString)
                    //println("myAccountVerification.updatedBeneficiaryAccountNumber - " + myAccountVerification.updatedBeneficiaryAccountNumber.toString)
                    //println("myAccountVerification.updatedBeneficiaryAccountName - " + myAccountVerification.updatedBeneficiaryAccountName.toString)
                    try{
                      var strMessageReference: String = myAccountVerification.originalassignmentinformation.messageIdentification//originalMessageIdentification
                      var strTransactionReference: String = myAccountVerification.verificationreportinformation.originalidentification//originalVerificationIdentification
                      var strAccountNumber: String = myAccountVerification.verificationreportinformation.updatedpartyandaccountidentificationinformation.updatedAccountInformation.accountIdentification//updatedBeneficiaryAccountNumber
                      //var strSchemeMode: String = ""
                      var strBankCode : String = myAccountVerification.verificationreportinformation.updatedpartyandaccountidentificationinformation.agentInformation.financialInstitutionIdentification//.updatedBeneficiaryAgentIdentification
                      var strAccountname: String = myAccountVerification.verificationreportinformation.updatedpartyandaccountidentificationinformation.updatedAccountInformation.accountIdentificationName//updatedBeneficiaryAccountName
                      var verificationStatus: String = myAccountVerification.verificationreportinformation.verificationstatus//.verificationStatus
                      var verificationReasonCode: String = myAccountVerification.verificationreportinformation.verificationreasoncode//.verificationReasonCode
                      var isVerified: Boolean = false
                      var responseCode: Int = 1
                      var responseMessage: String = "Error occured during processing, please try again."

                      if (verificationStatus.length > 0){
                        if (verificationStatus.equalsIgnoreCase("true")){
                          isVerified = true
                        }
                      }
                      if (isVerified){
                        responseCode = 0
                        responseMessage = "successful"
                      }
                      else{
                        strAccountNumber = ""
                        strAccountname = ""
                        strBankCode = ""
                        responseCode = 1
                        responseMessage = verificationReasonCode
                      }

                      val myAccountVerificationDetailsResponse_Batch = AccountVerificationDetailsResponse_Batch(strTransactionReference, strAccountNumber, strAccountname, strBankCode)
                      val myAccountVerificationResponse = AccountVerificationDetailsResponse_BatchData(strMessageReference, responseCode, responseMessage, myAccountVerificationDetailsResponse_Batch)
                      /*
                      implicit val AccountVerificationDetailsResponse_BatchWrites = Json.writes[AccountVerificationDetailsResponse_Batch]
                      implicit val AccountVerificationDetailsResponse_BatchDataWrites = Json.writes[AccountVerificationDetailsResponse_BatchData]

                      val jsonResponse = Json.toJson(myAccountVerificationResponse)

                      //println("sendAccountVerificationRequestsIpsl: myAccountVerificationResponse - " + myAccountVerificationResponse)
                      println("sendAccountVerificationRequestsIpsl: jsonResponse - " + jsonResponse)
                      */
                      val f = Future {sendAccountVerificationResponseEchannel(myAccountVerificationResponse)
                      }

                    }
                    catch{
                      case ex: Exception =>
                        log_errors(strApifunction + " : " + ex.getMessage())
                      case io: IOException =>
                        log_errors(strApifunction + " : " + io.getMessage())
                      case tr: Throwable =>
                        log_errors(strApifunction + " : " + tr.getMessage())
                    }

                  }
                  /*
                  val x = myData.value
                  //val y = (x.toArray, Charset.forName("UTF-8"))
                  val y = myData.asInstanceOf[String]
                  println("res.entity x - " + x.get.toString)
                  println("res.entity y - " + y)
                  */
                  //val myData = Unmarshal(res.entity).to[CbsMessage_ProjectionBenefits_Batch]
                  /*
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
                  */
                }
                else {

                  //Lets log the status code returned by CBS webservice
                  val myStatusCode : Int = res.status.intValue()
                  val strStatusMessage: String = "Failed"
                  /*
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
                    log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

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
                    */
                }
              }
            //println(res)
            //case Failure(_)   => sys.error("something wrong")
            case Failure(f) =>
              println("start 3: " + f.getMessage)
              //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
              /*
              try {

                //Log_errors(strApifunction + " : " + f.getMessage + " - ex exception error occured.")
                log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

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
                  log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                case t: Throwable =>
                  log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
              }
              */
          }

      }
    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false
          log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          isSuccessful = false
          log_errors(strApifunction + " : " + t.getMessage + "t exception error occured.")
      }
    finally
    {
    }

  }
  def sendBulkCreditTransferRequestsIpsl(myRequestData: String): Unit = {
    val strApifunction : String = "sendBulkCreditTransferRequestsIpsl"
    var strProjectionType  : String = "RetirementsReduced"
    var strApiURL  : String = "http://localhost:9001/getbulkcredittransferresponsedetails"
    val myMemberNo : Int = 1
    val myMemberId : Int = 1
    val myProjectionType  : Int = 1


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
        log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage())
    }
    /*
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
    */
    val myuri : Uri = strApiURL //"http://172.16.109.253:8088/Xi/api/getProjectionsForMember/283632/60/6973/Retirements Reduced"

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myXmlData : String = ""
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
        log_errors(strApifunction + " : Failure in fetching  MemberNo - " + myMemberNo + " , MemberId - " + myMemberId)
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
      if (isValidData) {

        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        var strUserName: String = "testUid"
        var strPassWord: String = "testPwd"
        /*
        try {
          strUserName = getCbsApiUserName
          var strPwd: String = getCbsApiPassword //n6,e$=p8QK\+c^h~
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
        */
        if (strUserName == null){
          strUserName = ""
        }

        if (strPassWord == null){
          strPassWord = ""
        }

        if (strUserName.trim.length == 0){
          log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strUserName + " , application error occured.")
          return
        }

        if (strPassWord.trim.length == 0){
          log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strPassWord + " , application error occured.")
          return
        }

        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //***working*** val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        /* TESTS ONLY */
        val accessToken: String = "sassasasss"
        myXmlData = myRequestData
        val data = HttpEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), myXmlData)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time: Future[String] = Future(start_time_DB)
        val myMember_No: Future[Int] = Future(myMemberNo)
        //TESTS ONLY
        println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              println("start 2: " + res.status.intValue())
              if (res.status != None) {
                if (res.status.intValue() == 202) {
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
                  //val resByteStr: String = res.entity.toString
                  //val resByteStr: akka.util.ByteString = res.entity
                  //println("res.entity - " + res.entity.toString())

                  val myData = res.entity
                  if (myData != null){
                    val x = myData.asInstanceOf[HttpEntity.Strict].getData().decodeString(StandardCharsets.UTF_8)
                    //println("res.entity x - " + x.toString)
                    println("myBulkCreditTransfer - " + x.toString)

                  }
                  /*
                  val x = myData.value
                  //val y = (x.toArray, Charset.forName("UTF-8"))
                  val y = myData.asInstanceOf[String]
                  println("res.entity x - " + x.get.toString)
                  println("res.entity y - " + y)
                  */
                  //val myData = Unmarshal(res.entity).to[CbsMessage_ProjectionBenefits_Batch]
                  /*
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
                  */
                }
                else {

                  //Lets log the status code returned by CBS webservice
                  val myStatusCode : Int = res.status.intValue()
                  val strStatusMessage: String = "Failed"
                  /*
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
                    log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

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
                    */
                }
              }
            //println(res)
            //case Failure(_)   => sys.error("something wrong")
            case Failure(f) =>
              println("start 3: " + f.getMessage)
            //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
            /*
            try {

              //Log_errors(strApifunction + " : " + f.getMessage + " - ex exception error occured.")
              log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

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
                log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
              case t: Throwable =>
                log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
            }
            */
          }

      }
    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false
          log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          isSuccessful = false
          log_errors(strApifunction + " : " + t.getMessage + "t exception error occured.")
      }
    finally
    {
    }

  }
  def sendSingleCreditTransferRequestsIpsl(myRequestData: String): Unit = {
    val strApifunction : String = "sendSingleCreditTransferRequestsIpsl"
    var strProjectionType  : String = "RetirementsReduced"
    var strApiURL  : String = "http://localhost:9001/getsinglecredittransferresponsedetails"
    val myMemberNo : Int = 1
    val myMemberId : Int = 1
    val myProjectionType  : Int = 1


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
        log_errors(strApifunction + " : " + io.getMessage())
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage())
    }
    /*
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
    */
    val myuri : Uri = strApiURL //"http://172.16.109.253:8088/Xi/api/getProjectionsForMember/283632/60/6973/Retirements Reduced"

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myXmlData : String = ""
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
        log_errors(strApifunction + " : Failure in fetching  MemberNo - " + myMemberNo + " , MemberId - " + myMemberId)
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
      if (isValidData) {

        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        var strUserName: String = "testUid"
        var strPassWord: String = "testPwd"
        /*
        try {
          strUserName = getCbsApiUserName
          var strPwd: String = getCbsApiPassword //n6,e$=p8QK\+c^h~
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
        */
        if (strUserName == null){
          strUserName = ""
        }

        if (strPassWord == null){
          strPassWord = ""
        }

        if (strUserName.trim.length == 0){
          log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strUserName + " , application error occured.")
          return
        }

        if (strPassWord.trim.length == 0){
          log_errors(strApifunction + " : Failure in fetching  Api UserName - " + strPassWord + " , application error occured.")
          return
        }

        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //***working*** val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        /* TESTS ONLY */
        val accessToken: String = "sassasasss"
        myXmlData = myRequestData
        val data = HttpEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), myXmlData)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time: Future[String] = Future(start_time_DB)
        val myMember_No: Future[Int] = Future(myMemberNo)
        //TESTS ONLY
        println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              println("start 2: " + res.status.intValue())
              if (res.status != None) {
                if (res.status.intValue() == 202) {
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
                  //val resByteStr: String = res.entity.toString
                  //val resByteStr: akka.util.ByteString = res.entity
                  //println("res.entity - " + res.entity.toString())

                  val myData = res.entity
                  if (myData != null){
                    val x = myData.asInstanceOf[HttpEntity.Strict].getData().decodeString(StandardCharsets.UTF_8)
                    //println("res.entity x - " + x.toString)
                    println("mySingleCreditTransfer - " + x.toString)

                  }
                  /*
                  val x = myData.value
                  //val y = (x.toArray, Charset.forName("UTF-8"))
                  val y = myData.asInstanceOf[String]
                  println("res.entity x - " + x.get.toString)
                  println("res.entity y - " + y)
                  */
                  //val myData = Unmarshal(res.entity).to[CbsMessage_ProjectionBenefits_Batch]
                  /*
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
                  */
                }
                else {

                  //Lets log the status code returned by CBS webservice
                  val myStatusCode : Int = res.status.intValue()
                  val strStatusMessage: String = "Failed"
                  /*
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
                    log_errors(strApifunction + " : " + strMessage + " - http != 200 error occured. error occured.")

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
                    */
                }
              }
            //println(res)
            //case Failure(_)   => sys.error("something wrong")
            case Failure(f) =>
              println("start 3: " + f.getMessage)
            //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
            /*
            try {

              //Log_errors(strApifunction + " : " + f.getMessage + " - ex exception error occured.")
              log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

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
                log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
              case t: Throwable =>
                log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
            }
            */
          }

      }
    }
    catch
      {
        case ex: Exception =>
          isSuccessful = false
          log_errors(strApifunction + " : " + ex.getMessage + "exception error occured.")
        case t: Throwable =>
          isSuccessful = false
          log_errors(strApifunction + " : " + t.getMessage + "t exception error occured.")
      }
    finally
    {
    }

  }
  def sendAccountVerificationResponseEchannel(myAccountVerificationData: AccountVerificationDetailsResponse_BatchData): Unit = {

    var strApiURL: String = "http://localhost:9001/addaccountverificationreesponsebcbs"
    var isSuccessful : Boolean = false
    var isValidData : Boolean = false
    var myjsonData : String = ""
    val strApifunction : String = "sendAccountVerificationResponseEchannel"
    val entryID: java.math.BigDecimal = new java.math.BigDecimal(0)

    try{
      if (myAccountVerificationData != null){
        isValidData = true
      }
      /*
      strApiURL = getEchannelsProjectionBenefitsURL()
      if (strApiURL == null){
        strApiURL = ""
      }
      */
      if (strApiURL.trim.length == 0){
        log_errors(strApifunction + " : Failure in fetching  Api URL - " + strApiURL + " , application error occured.")
      }
    }
    catch{
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
    }

    val myuri : Uri = strApiURL //Maintain in DB

    try
    {
      if (isValidData){

        implicit val AccountVerificationDetailsResponse_BatchWrites = Json.writes[AccountVerificationDetailsResponse_Batch]
        implicit val AccountVerificationDetailsResponse_BatchDataWrites = Json.writes[AccountVerificationDetailsResponse_BatchData]

        val jsonResponse = Json.toJson(myAccountVerificationData)

        myjsonData = jsonResponse.toString()

        //println("sendAccountVerificationResponseEchannel: myjsonData - " + myjsonData)
      }
    }
    catch
      {
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
          log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
      }

    try
    {
      if (isValidData){
        val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        val requestData : Future[String] = Future(myjsonData)
        var start_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time : Future[String] = Future(start_time_DB)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data))
        val accessToken: String = "hsbjbahvs7ahvshvshv"
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization","Bearer " + accessToken)))

        //TESTS ONLY
        //println("start 1: " + strApifunction + " " + start_time_DB+ " " + myjsonData)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + strApifunction + " " + res.status.intValue())
              if (res.status != None){
                if (res.status.intValue() == 200){
                  var mystatuscode: Int = 1
                  var strstatuscode: String = ""
                  var strstatusdescription: String = ""
                  var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                  var strRequestData: String = ""
                  val myData = Unmarshal(res.entity).to[TransactionResponse]
                  var start_time_DB : String  = ""
                  val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                  if (myData != None){
                    if (myData.value.getOrElse(None) != None){
                      val myTransactionResponse =  myData.value.get
                      if (myTransactionResponse.get != None){

                        if (myTransactionResponse.get.statuscode != None) {
                          val myData = myTransactionResponse.get.statuscode
                          strstatuscode = myData.get.toString()
                          if (strstatuscode != null && strstatuscode != None){
                            strstatuscode = strstatuscode.trim
                            if (strstatuscode.length > 0){
                              strstatuscode = strstatuscode.replace("'","")//Remove apostrophe
                              strstatuscode = strstatuscode.replace(" ","")//Remove spaces
                              strstatuscode = strstatuscode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strstatuscode = strstatuscode.trim
                              if (strstatuscode.length > 0){
                                val isNumeric: Boolean = strstatuscode.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                                if (isNumeric){
                                  mystatuscode = strstatuscode.toInt
                                }
                              }
                            }
                          }
                        }

                        if (myTransactionResponse.get.statusdescription != None) {
                          val myData = myTransactionResponse.get.statusdescription
                          strstatusdescription = myData.get.toString()
                          if (strstatusdescription != null && strstatusdescription != None){
                            strstatusdescription = strstatusdescription.trim
                            if (strstatusdescription.length > 0){
                              strstatusdescription = strstatusdescription.replace("'","")//Remove apostrophe
                              strstatusdescription = strstatusdescription.replace(" ","")//Remove spaces
                              strstatusdescription = strstatusdescription.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strstatusdescription = strstatusdescription.trim
                            }
                          }
                        }

                      }

                      println("sendAccountVerificationResponseEchannel: mystatuscode - " + mystatuscode + ", strstatusdescription - " + strstatusdescription)

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
                      //updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)
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
                      log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None error occured.")

                      val posted_to_Echannels: Boolean = false
                      val post_picked_Echannels: Boolean = false
                      val strDate_to_Echannels: String = start_time_DB
                      val strDate_from_Echannels: String = stop_time_DB
                      val myStatusCode_Echannels : Int = res.status.intValue()
                      val strStatusMessage_Echannels: String = "Failure occured. No request was received the from API"

                      //updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)

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

                  //updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)

                }
                else{
                  var mystatuscode: Int = 1
                  var strstatuscode: String = ""
                  var strstatusdescription: String = ""
                  var myTxnID : java.math.BigDecimal = new java.math.BigDecimal(0)
                  var strRequestData: String = ""

                  val myData = Unmarshal(res.entity).to[TransactionResponse]
                  var start_time_DB : String  = ""
                  val stop_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)

                  if (myData != None){
                    if (myData.value.get != None){
                      val myTransactionResponse =  myData.value.get
                      if (myTransactionResponse.get != None){
                        val myTransactionResponse =  myData.value.get
                        if (myTransactionResponse.get != None){

                          if (myTransactionResponse.get.statuscode != None) {
                            val myData = myTransactionResponse.get.statuscode
                            strstatuscode = myData.toString()
                            if (strstatuscode != null && strstatuscode != None){
                              strstatuscode = strstatuscode.trim
                              if (strstatuscode.length > 0){
                                strstatuscode = strstatuscode.replace("'","")//Remove apostrophe
                                strstatuscode = strstatuscode.replace(" ","")//Remove spaces
                                strstatuscode = strstatuscode.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strstatuscode = strstatuscode.trim
                                if (strstatuscode.length > 0){
                                  val isNumeric: Boolean = strstatuscode.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                                  if (isNumeric){
                                    mystatuscode = strstatuscode.toInt
                                  }
                                }
                              }
                            }
                          }

                          if (myTransactionResponse.get.statusdescription != None) {
                            val myData = myTransactionResponse.get.statusdescription
                            strstatusdescription = myData.toString()
                            if (strstatusdescription != null && strstatusdescription != None){
                              strstatusdescription = strstatusdescription.trim
                              if (strstatusdescription.length > 0){
                                strstatusdescription = strstatusdescription.replace("'","")//Remove apostrophe
                                strstatusdescription = strstatusdescription.replace(" ","")//Remove spaces
                                strstatusdescription = strstatusdescription.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                strstatusdescription = strstatusdescription.trim
                              }
                            }
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

                  //updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)

                }
              }
            //println(res)
            case Failure(f)   =>
              //sys.error("something wrong")
              //println("start 3: " + strApifunction + " " + f.getMessage)
              try {

                log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

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

                //updateMemberProjectionBenefitsDetailsRequests(myTxnID, posted_to_Echannels, post_picked_Echannels, strDate_to_Echannels, strDate_from_Echannels, myStatusCode_Echannels, strStatusMessage_Echannels, strRequestData)
              }
              catch
                {
                  case ex: Exception =>
                    log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
                  case t: Throwable =>
                    log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
                }

          }
      }
    }
    catch
      {
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
        case t: Throwable =>
          log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
      }

  }
  def getServiceEsbApi(myServiceCode : Int) : Boolean = {
    var isServiceEsbApi : Boolean = false

    val strSQL : String = "{ call dbo.GetServiceEsbApi(?,?) }"
    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.setInt(1,myServiceCode)
          mystmt.registerOutParameter("ServiceEsbApi", java.sql.Types.BOOLEAN)
          mystmt.execute()
          isServiceEsbApi = mystmt.getBoolean("ServiceEsbApi")
        }
        catch{
          case ex : Exception =>
            log_errors("getServiceEsbApi : " + ex.getMessage + " - ex exception error occured." + " ServiceCode - " + myServiceCode.toString())
          case t: Throwable =>
            log_errors("getServiceEsbApi : " + t.getMessage + " exception error occured." + " ServiceCode - " + myServiceCode.toString())
        }

      }
    }catch {
      case ex: Exception =>
        log_errors("getServiceEsbApi : " + ex.getMessage + " exception error occured." + " ServiceCode - " + myServiceCode.toString())
      case t: Throwable =>
        log_errors("getServiceEsbApi : " + t.getMessage + " exception error occured." + " ServiceCode - " + myServiceCode.toString())
    }

    return  isServiceEsbApi
  }
  def getReturnDate_BVS() : String = {
    var strReturnDate : String = ""

    val strSQL : String = "{ call dbo.GetReturnDate_BVS(?) }"
    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("ReturnDate", java.sql.Types.VARCHAR)
          mystmt.execute()
          strReturnDate = mystmt.getString("ReturnDate")
        }
        catch{
          case ex : Exception =>
            log_errors("getReturnDate_BVS : " + ex.getMessage + " - ex exception error occured." + " ReturnDate - " + strReturnDate.toString())
          case t: Throwable =>
            log_errors("getReturnDate_BVS : " + t.getMessage + " exception error occured." + " ReturnDate - " + strReturnDate.toString())
        }

      }
    }catch {
      case ex: Exception =>
        log_errors("getReturnDate_BVS : " + ex.getMessage + " exception error occured." + " ReturnDate - " + strReturnDate.toString())
      case t: Throwable =>
        log_errors("getReturnDate_BVS : " + t.getMessage + " exception error occured." + " ReturnDate - " + strReturnDate.toString())
    }finally {

    }

    return  strReturnDate
  }
  def getApprovedById_BVS() : java.math.BigDecimal = {

    var myApprovedby_Id: java.math.BigDecimal = new java.math.BigDecimal(0)

    val strSQL : String = "{ call dbo.GetApprovedById_BVS(?) }"
    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("ApprovedBy_Id", java.sql.Types.NUMERIC)
          mystmt.execute()
          myApprovedby_Id = mystmt.getBigDecimal("ApprovedBy_Id")
        }
        catch{
          case ex : Exception =>
            log_errors("getApprovedById_BVS : " + ex.getMessage + " - ex exception error occured." + " Approvedby_Id - " + myApprovedby_Id.toString())
          case t: Throwable =>
            log_errors("getApprovedById_BVS : " + t.getMessage + " exception error occured." + " Approvedby_Id - " + myApprovedby_Id.toString())
        }

      }
    }catch {
      case ex: Exception =>
        log_errors("getApprovedById_BVS : " + ex.getMessage + " exception error occured." + " Approvedby_Id - " + myApprovedby_Id.toString())
      case t: Throwable =>
        log_errors("getApprovedById_BVS : " + t.getMessage + " exception error occured." + " Approvedby_Id - " + myApprovedby_Id.toString())
    }finally {

    }

    return  myApprovedby_Id
  }
  def getAuthorizeById_BVS() : java.math.BigDecimal = {

    var myAuthorizeby_Id: java.math.BigDecimal = new java.math.BigDecimal(0)

    val strSQL : String = "{ call dbo.GetAuthorizeById_BVS(?) }"
    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("AuthorizeBy_Id", java.sql.Types.NUMERIC)
          mystmt.execute()
          myAuthorizeby_Id = mystmt.getBigDecimal("AuthorizeBy_Id")
        }
        catch{
          case ex : Exception =>
            log_errors("getAuthorizeById_BVS : " + ex.getMessage + " - ex exception error occured." + " Approvedby_Id - " + myAuthorizeby_Id.toString())
          case t: Throwable =>
            log_errors("getAuthorizeById_BVS : " + t.getMessage + " exception error occured." + " Approvedby_Id - " + myAuthorizeby_Id.toString())
        }

      }
    }catch {
      case ex: Exception =>
        log_errors("getAuthorizeById_BVS : " + ex.getMessage + " exception error occured." + " Approvedby_Id - " + myAuthorizeby_Id.toString())
      case t: Throwable =>
        log_errors("getAuthorizeById_BVS : " + t.getMessage + " exception error occured." + " Approvedby_Id - " + myAuthorizeby_Id.toString())
    }finally {

    }

    return  myAuthorizeby_Id
  }
  def getPreparedById_BVS() : java.math.BigDecimal = {

    var myPreparedby_Id: java.math.BigDecimal = new java.math.BigDecimal(0)

    val strSQL : String = "{ call dbo.GetPreparedBy_Id_BVS(?) }"
    try {
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("PreparedBy_Id", java.sql.Types.NUMERIC)
          mystmt.execute()
          myPreparedby_Id = mystmt.getBigDecimal("PreparedBy_Id")
        }
        catch{
          case ex : Exception =>
            log_errors("getPreparedById_BVS : " + ex.getMessage + " - ex exception error occured." + " Preparedby_Id - " + myPreparedby_Id.toString())
          case t: Throwable =>
            log_errors("getPreparedById_BVS : " + t.getMessage + " exception error occured." + " Preparedby_Id - " + myPreparedby_Id.toString())
        }

      }
    }catch {
      case ex: Exception =>
        log_errors("getPreparedById_BVS : " + ex.getMessage + " exception error occured." + " Preparedby_Id - " + myPreparedby_Id.toString())
      case t: Throwable =>
        log_errors("getPreparedById_BVS : " + t.getMessage + " exception error occured." + " Preparedby_Id - " + myPreparedby_Id.toString())
    }finally {

    }

    return  myPreparedby_Id
  }
  def getAccountVerificationDetails(accountVerificationDetails: AccountVerificationDetails) : String = {

    var strOutput: String = ""
    try {
      /*
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.setInt(1,myServiceCode)
          mystmt.registerOutParameter("ServiceEsbApi", java.sql.Types.BOOLEAN)
          mystmt.execute()
          isServiceEsbApi = mystmt.getBoolean("ServiceEsbApi")
        }
        catch{
          case ex : Exception =>
            log_errors("getServiceEsbApi : " + ex.getMessage + " - ex exception error occured." + " ServiceCode - " + myServiceCode.toString())
          case t: Throwable =>
            log_errors("getServiceEsbApi : " + t.getMessage + " exception error occured." + " ServiceCode - " + myServiceCode.toString())
        }

      }
      */
      val messageIdentification: String = accountVerificationDetails.messagereference//"001"
      val creationDateTime: String = accountVerificationDetails.creationdatetime//"2021-03-22"
      val firstAgentIdentification: String = accountVerificationDetails.firstagentidentification//"2031"
      val assignerAgentIdentification: String = accountVerificationDetails.assigneragentidentification//"2031"
      val assigneeAgentIdentification: String = accountVerificationDetails.assigneeagentidentification//"009"
      val identification: String = accountVerificationDetails.transactionreference//"0002"
      val accountNumber: String = accountVerificationDetails.accountnumber//"219277372626"
      val schemeName: String = accountVerificationDetails.schemename//"PHNE"
      val agentIdentification: String = accountVerificationDetails.bankcode//"2010"

      val firstAgentInformation: FirstAgentInformation = FirstAgentInformation(firstAgentIdentification)
      val assignerAgentInformation: AssignerAgentInformation = AssignerAgentInformation(assignerAgentIdentification)
      val assigneeAgentInformation: AssigneeAgentInformation = AssigneeAgentInformation(assigneeAgentIdentification)
      val assignmentInformation: AssignmentInformation = AssignmentInformation(messageIdentification, creationDateTime, firstAgentInformation, assignerAgentInformation, assigneeAgentInformation)
      val accountInformation: AccountInformation = AccountInformation(accountNumber, schemeName)
      val agentInformation: AgentInformation = AgentInformation(agentIdentification)
      val partyAndAccountIdentificationInformation: PartyAndAccountIdentificationInformation = PartyAndAccountIdentificationInformation(accountInformation, agentInformation)
      val verificationInformation: VerificationInformation = VerificationInformation(identification, partyAndAccountIdentificationInformation)
      val accountVerification = new AccountVerification(assignmentInformation, verificationInformation)
      val myData = accountVerification.toXml
      strOutput = myData.toString()
    }catch {
      case ex: Exception =>
        log_errors("getAccountVerificationDetails : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getAccountVerificationDetails : " + t.getMessage + " exception error occured.")
    }

    strOutput
  }
  def getSingleCreditTransferDetails(creditTransferPaymentInfo: SingleCreditTransferPaymentInfo) : String = {

    var strOutput: String = ""
    try {
      val messageidentification: String = creditTransferPaymentInfo.messagereference//"001"
      val creationdatetime: String = creditTransferPaymentInfo.creationdatetime//"2021-03-27"
      val numberoftransactions: Int = creditTransferPaymentInfo.numberoftransactions
      val totalinterbanksettlementamount: BigDecimal = creditTransferPaymentInfo.totalinterbanksettlementamount
      val settlementmethod: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.settlementmethod//"CLRG"
      val clearingSystem: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.clearingsystem//"IPS"
      val servicelevel: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.servicelevel//"P2PT"
      val localinstrumentcode: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.localinstrumentcode//"INST"
      val categorypurpose: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.categorypurpose//"IBNK"
      val instructingagentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val instructedagentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.assigneeagentidentification//"1990"
      val paymentendtoendidentification: String = creditTransferPaymentInfo.paymentdata.transactionreference//"2031203220210120095543e10b05af"
      val interbanksettlementamount: BigDecimal = creditTransferPaymentInfo.paymentdata.amount//6545.56
      val acceptancedatetime: String = creditTransferPaymentInfo.creationdatetime//"2021-03-27"
      val chargebearer: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.chargebearer//"SLEV"
      val mandateidentification: String = creditTransferPaymentInfo.paymentdata.mandateinformation.mandateidentification//""
      val ultimatedebtorinformationdebtorname: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitaccountname//"paul wakimani"
      val ultimatedebtorinformationdebtororganisationidentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val ultimatedebtorinformationdebtorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
      val initiatingpartyinformationorganisationidentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val debtorinformationdebtorname: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitaccountname//"paul wakimani"
      val debtorinformationdebtororganisationidentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val debtorinformationdebtorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
      val debtoraccountinformationdebtoraccountidentification: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitaccountnumber//"0711000000"
      val debtoraccountinformationdebtoraccountschemename: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.schemename//"PHNE"
      val debtoraccountinformationdebtoraccountname: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitaccountname//"paul wakimani"
      val debtoragentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val creditoragentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      val creditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val creditorinformationcreditororganisationidentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      val creditorinformationcreditorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
      val creditoraccountinformationcreditoraccountidentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountnumber//"0756000000"
      val creditoraccountinformationcreditoraccountschemename: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.schemename//"PHNE"
      val creditoraccountinformationcreditoraccountname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val ultimatecreditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val ultimatecreditorinformationcreditororganisationidentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      val ultimatecreditorinformationcreditorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
      val purposeinformationpurposecode: String = creditTransferPaymentInfo.paymentdata.purposeinformation.purposecode//""
      val remittanceinformationunstructured: String = creditTransferPaymentInfo.paymentdata.remittanceinformation.unstructured//""
      val remittanceinformationtaxremittancereferencenumber: String = creditTransferPaymentInfo.paymentdata.remittanceinformation.taxremittancereferencenumber//""

      //Group Header Information
      /*
      val messageidentification: String = ""
      val creationdatetime: String = ""
      val numberoftransactions: Int = 0
      val totalinterbanksettlementamount: BigDecimal = 0
      */
      val settlementinformation: SettlementInformation = SettlementInformation(settlementmethod, clearingSystem)
      val paymenttypeinformation: PaymentTypeInformation = PaymentTypeInformation(servicelevel, localinstrumentcode, categorypurpose)
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation(instructingagentinformationfinancialInstitutionIdentification)
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation(instructedagentinformationfinancialInstitutionIdentification)
      val groupHeaderInformation = GroupHeaderInformation(
        messageidentification, creationdatetime, numberoftransactions, totalinterbanksettlementamount,
        settlementinformation, paymenttypeinformation,
        instructingagentinformation, instructedagentinformation
      )
      //Credit Transfer Transaction Information
      /*
      val paymentendtoendidentification: String = ""
      val interbanksettlementamount: String = ""
      val acceptancedatetime: String = ""
      val chargebearer: String = ""
      */
      val mandaterelatedinformation: MandateRelatedInformation = MandateRelatedInformation(mandateidentification)
      val ultimatedebtorinformation: UltimateDebtorInformation = UltimateDebtorInformation(ultimatedebtorinformationdebtorname, ultimatedebtorinformationdebtororganisationidentification, ultimatedebtorinformationdebtorcontactphonenumber)
      val initiatingpartyinformation: InitiatingPartyInformation = InitiatingPartyInformation(initiatingpartyinformationorganisationidentification)
      val debtorinformation: DebtorInformation = DebtorInformation(debtorinformationdebtorname, debtorinformationdebtororganisationidentification, debtorinformationdebtorcontactphonenumber)
      val debtoraccountinformation: DebtorAccountInformation = DebtorAccountInformation(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountschemename, debtoraccountinformationdebtoraccountname)
      val debtoragentinformation: DebtorAgentInformation = DebtorAgentInformation(debtoragentinformationfinancialInstitutionIdentification)
      val creditoragentinformation: CreditorAgentInformation = CreditorAgentInformation(creditoragentinformationfinancialInstitutionIdentification)
      val creditorinformation: CreditorInformation = CreditorInformation(creditorinformationcreditorname, creditorinformationcreditororganisationidentification, creditorinformationcreditorcontactphonenumber)
      val creditoraccountinformation: CreditorAccountInformation = CreditorAccountInformation(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountschemename, creditoraccountinformationcreditoraccountname)
      val ultimatecreditorinformation: UltimateCreditorInformation = UltimateCreditorInformation(ultimatecreditorinformationcreditorname, ultimatecreditorinformationcreditororganisationidentification, ultimatecreditorinformationcreditorcontactphonenumber)
      val purposeinformation: PurposeInformation = PurposeInformation(purposeinformationpurposecode)
      val remittanceinformation: RemittanceInformation = RemittanceInformation(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
      val creditTransferTransactionInformation = CreditTransferTransactionInformation(
        paymentendtoendidentification, interbanksettlementamount, acceptancedatetime, chargebearer,
        mandaterelatedinformation, ultimatedebtorinformation, initiatingpartyinformation,
        debtorinformation, debtoraccountinformation, debtoragentinformation,
        creditoragentinformation, creditorinformation, creditoraccountinformation,
        ultimatecreditorinformation, purposeinformation, remittanceinformation
      )

      val singleCreditTransfer = new SingleCreditTransfer(groupHeaderInformation, creditTransferTransactionInformation)

      val myData = singleCreditTransfer.toXml
      strOutput = myData.toString()
    }catch {
      case ex: Exception =>
        log_errors("getSingleCreditTransferDetails : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getSingleCreditTransferDetails : " + t.getMessage + " exception error occured.")
    }

    strOutput
  }
  def getBulkCreditTransferDetails() : String = {

    var strOutput: String = ""
    try {
      /*
      myDB.withConnection { implicit myconn =>

        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.setInt(1,myServiceCode)
          mystmt.registerOutParameter("ServiceEsbApi", java.sql.Types.BOOLEAN)
          mystmt.execute()
          isServiceEsbApi = mystmt.getBoolean("ServiceEsbApi")
        }
        catch{
          case ex : Exception =>
            log_errors("getServiceEsbApi : " + ex.getMessage + " - ex exception error occured." + " ServiceCode - " + myServiceCode.toString())
          case t: Throwable =>
            log_errors("getServiceEsbApi : " + t.getMessage + " exception error occured." + " ServiceCode - " + myServiceCode.toString())
        }

      }
      */
      val messageidentification: String = "001"
      val creationdatetime: String = "2021-03-27"
      val numberoftransactions: Int = 1
      val totalinterbanksettlementamount: BigDecimal = 6545.56
      val settlementmethod: String = "CLRG"
      val clearingSystem: String = "IPS"
      val servicelevel: String = "P2PT"
      val localinstrumentcode: String = "INST"
      val categorypurpose: String = "IBNK"
      val instructingagentinformationfinancialInstitutionIdentification: String = "2000"
      val instructedagentinformationfinancialInstitutionIdentification: String = "1990"
      val paymentendtoendidentification: String = "2031203220210120095543e10b05af"
      //val interbanksettlementamount: BigDecimal = 6545.56
      val interbanksettlementamount: BigDecimal = 3545.56
      val interbanksettlementamount2: BigDecimal = 3000.00
      val acceptancedatetime: String = "2021-03-27"
      val chargebearer: String = "SLEV"
      val mandateidentification: String = ""
      val ultimatedebtorinformationdebtorname: String = "paul wakimani"
      val ultimatedebtorinformationdebtororganisationidentification: String = "2000"
      val ultimatedebtorinformationdebtorcontactphonenumber: String = "0711000000"
      val initiatingpartyinformationorganisationidentification: String = "2000"
      val debtorinformationdebtorname: String = "paul wakimani"
      val debtorinformationdebtororganisationidentification: String = "2000"
      val debtorinformationdebtorcontactphonenumber: String = "0711000000"
      val debtoraccountinformationdebtoraccountidentification: String = "0711000000"
      val debtoraccountinformationdebtoraccountschemename: String = "PHNE"
      val debtoraccountinformationdebtoraccountname: String = "paul wakimani"
      val debtoragentinformationfinancialInstitutionIdentification: String = "2000"
      val creditoragentinformationfinancialInstitutionIdentification: String = "1990"
      val creditorinformationcreditorname: String = "Nancy Mbera"
      val creditorinformationcreditororganisationidentification: String = "1990"
      val creditorinformationcreditorcontactphonenumber: String = "0756000000"
      val creditoraccountinformationcreditoraccountidentification: String = "0756000000"
      val creditoraccountinformationcreditoraccountschemename: String = "PHNE"
      val creditoraccountinformationcreditoraccountname: String = "Nancy Mbera"
      val ultimatecreditorinformationcreditorname: String = "Nancy Mbera"
      val ultimatecreditorinformationcreditororganisationidentification: String = "1990"
      val ultimatecreditorinformationcreditorcontactphonenumber: String = "0756000000"
      val purposeinformationpurposecode: String = ""
      val remittanceinformationunstructured: String = ""
      val remittanceinformationtaxremittancereferencenumber: String = ""

      //Group Header Information
      /*
      val messageidentification: String = ""
      val creationdatetime: String = ""
      val numberoftransactions: Int = 0
      val totalinterbanksettlementamount: BigDecimal = 0
      */
      val settlementinformation: SettlementInformation = SettlementInformation(settlementmethod, clearingSystem)
      val paymenttypeinformation: PaymentTypeInformation = PaymentTypeInformation(servicelevel, localinstrumentcode, categorypurpose)
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation(instructingagentinformationfinancialInstitutionIdentification)
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation(instructedagentinformationfinancialInstitutionIdentification)
      val groupHeaderInformation = GroupHeaderInformation(
        messageidentification, creationdatetime, numberoftransactions, totalinterbanksettlementamount,
        settlementinformation, paymenttypeinformation,
        instructingagentinformation, instructedagentinformation
      )
      //Credit Transfer Transaction Information
      /*
      val paymentendtoendidentification: String = ""
      val interbanksettlementamount: String = ""
      val acceptancedatetime: String = ""
      val chargebearer: String = ""
      */
      val mandaterelatedinformation: MandateRelatedInformation = MandateRelatedInformation(mandateidentification)
      val ultimatedebtorinformation: UltimateDebtorInformation = UltimateDebtorInformation(ultimatedebtorinformationdebtorname, ultimatedebtorinformationdebtororganisationidentification, ultimatedebtorinformationdebtorcontactphonenumber)
      val initiatingpartyinformation: InitiatingPartyInformation = InitiatingPartyInformation(initiatingpartyinformationorganisationidentification)
      val debtorinformation: DebtorInformation = DebtorInformation(debtorinformationdebtorname, debtorinformationdebtororganisationidentification, debtorinformationdebtorcontactphonenumber)
      val debtoraccountinformation: DebtorAccountInformation = DebtorAccountInformation(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountschemename, debtoraccountinformationdebtoraccountname)
      val debtoragentinformation: DebtorAgentInformation = DebtorAgentInformation(debtoragentinformationfinancialInstitutionIdentification)
      val creditoragentinformation: CreditorAgentInformation = CreditorAgentInformation(creditoragentinformationfinancialInstitutionIdentification)
      val creditorinformation: CreditorInformation = CreditorInformation(creditorinformationcreditorname, creditorinformationcreditororganisationidentification, creditorinformationcreditorcontactphonenumber)
      val creditoraccountinformation: CreditorAccountInformation = CreditorAccountInformation(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountschemename, creditoraccountinformationcreditoraccountname)
      val ultimatecreditorinformation: UltimateCreditorInformation = UltimateCreditorInformation(ultimatecreditorinformationcreditorname, ultimatecreditorinformationcreditororganisationidentification, ultimatecreditorinformationcreditorcontactphonenumber)
      val purposeinformation: PurposeInformation = PurposeInformation(purposeinformationpurposecode)
      val remittanceinformation: RemittanceInformation = RemittanceInformation(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
      //Txn 1
      val creditTransferTransactionInformation = CreditTransferTransactionInformation(
        paymentendtoendidentification, interbanksettlementamount, acceptancedatetime, chargebearer,
        mandaterelatedinformation, ultimatedebtorinformation, initiatingpartyinformation,
        debtorinformation, debtoraccountinformation, debtoragentinformation,
        creditoragentinformation, creditorinformation, creditoraccountinformation,
        ultimatecreditorinformation, purposeinformation, remittanceinformation
      )
      //Txn 2
      val creditTransferTransactionInformation2 = CreditTransferTransactionInformation(
        paymentendtoendidentification, interbanksettlementamount2, acceptancedatetime, chargebearer,
        mandaterelatedinformation, ultimatedebtorinformation, initiatingpartyinformation,
        debtorinformation, debtoraccountinformation, debtoragentinformation,
        creditoragentinformation, creditorinformation, creditoraccountinformation,
        ultimatecreditorinformation, purposeinformation, remittanceinformation
      )
      var creditTransfertransactioninformationbatch = Seq[CreditTransferTransactionInformation]()
      //Add first txn
      creditTransfertransactioninformationbatch = creditTransfertransactioninformationbatch :+ creditTransferTransactionInformation
      //Add second txn
      creditTransfertransactioninformationbatch = creditTransfertransactioninformationbatch :+ creditTransferTransactionInformation2

      val bulkCreditTransfer = new BulkCreditTransfer(groupHeaderInformation, creditTransfertransactioninformationbatch)

      val myData = bulkCreditTransfer.toXml
      //println("getBulkCreditTransferDetails:myData - " + System.lineSeparator() + myData.toString())
      strOutput = myData.toString()
    }catch {
      case ex: Exception =>
        log_errors("getBulkCreditTransferDetails : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getBulkCreditTransferDetails : " + t.getMessage + " exception error occured.")
    }

    strOutput
  }
  def log_data(mydetail : String) : Unit = {
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
      //strdetail  =  mydetail + " - " + new java.util.Date

      val requestDate : String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
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
  def log_errors(mydetail : String) : Unit = {
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
      case io: IOException => log_errors("create_Folderpaths : " + io.getMessage + "exception error occured")
      //io.printStackTrace()
      case t: Throwable => log_errors("create_Folderpaths : " + t.getMessage + "exception error occured")
      //strErrorMsg = io.toString
      case ex : Exception => log_errors("create_Folderpaths : " + ex.getMessage + "exception error occured")
      //ex.printStackTrace()
    }
    finally {
    }
    return  is_Successful
  }
}

