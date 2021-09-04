package controllers

import java.io.{BufferedWriter, FileWriter, IOException, PrintWriter}
import java.nio.charset.{StandardCharsets}//Charset,
import java.nio.file.{Files, Paths}
import java.sql.{CallableStatement}//, ResultSet
import java.text.SimpleDateFormat
import java.util.UUID

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.db.{Database, NamedDatabase}
import javax.inject.Inject
import java.util.{Base64}//, Date

import play.api.mvc.{AbstractController, ControllerComponents}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
//import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.headers.{RawHeader}//BasicHttpCredentials, 
import akka.stream.ActorMaterializer
//import akka.util.Timeout
import com.google.inject.AbstractModule
import com.microsoft.sqlserver.jdbc.SQLServerDataTable
import spray.json.DefaultJsonProtocol

//import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

//import com.microsoft.sqlserver.jdbc.{SQLServerCallableStatement, SQLServerDataTable}
//import com.microsoft.sqlserver.jdbc.SQLServerDataTable
import play.api.libs.concurrent.CustomExecutionContext
import java.security.MessageDigest
import javax.crypto.Cipher
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PublicKey
import java.security.PrivateKey
//send pem certificate in http request
import java.io.InputStream
import java.security.{ KeyStore, SecureRandom }
import java.security.cert.{ Certificate, CertificateFactory }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import akka.http.scaladsl.ConnectionContext 
//import scala.util.control.Breaks
//import scala.util.control.Breaks.break
//import oracle.jdbc.OracleTypes
//import com.microsoft.sqlserver.jdbc.SQLServerDataTable
//(cc: ControllerComponents,myDB : Database,myExecutionContext: MyExecutionContext)
//Java Digital signature
import org.w3c.dom.Document
import org.w3c.dom.Element

import javax.xml.XMLConstants
import javax.xml.crypto.XMLStructure
import javax.xml.crypto.dsig._
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory
import javax.xml.crypto.dsig.keyinfo.X509Data
import javax.xml.crypto.dsig.keyinfo.X509IssuerSerial
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io._
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.Collections
import java.util.List
import java.util.Objects
import org.xml.sax.InputSource

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
  case class AccountVerificationDetails_Request(transactionreference: Option[JsValue], accountnumber: Option[JsValue], schemename: Option[JsValue], bankcode: Option[JsValue])
  case class AccountVerificationDetails_BatchRequest(messagereference: Option[JsValue], accountdata: AccountVerificationDetails_Request)
  case class AccountVerificationDetailsResponse_Batch(transactionreference: String, accountnumber: String, accountname: String, bankcode: String)
  case class AccountVerificationDetailsResponse_BatchData(messagereference: String, statuscode: Int, statusdescription: String, accountdata: AccountVerificationDetailsResponse_Batch)
  case class AccountVerificationDetailsResponse(statuscode: Int, statusdescription: String)
  //AccountVerification Details i.e AccountVerification request from ESB
  case class AccountVerificationDetails(messagereference: String, creationdatetime: String, firstagentidentification: String, assigneragentidentification: String, assigneeagentidentification: String, transactionreference: String, accountnumber: String, schemename: String, bankcode: String)

  //SingleCreditTransfer Details i.e SingleCreditTransfer request to IPSL through ESB
  //The request is initiated from ESB-CBS/Other Channels
  case class ContactInformation(fullnames: Option[JsValue], phonenumber: Option[JsValue], emailaddress: Option[JsValue])
  case class DebitAccountInformation(debitaccountnumber: Option[JsValue], debitaccountname: Option[JsValue], debitcontactinformation: Option[ContactInformation])
  case class CreditAccountInformation(creditaccountnumber: Option[JsValue], creditaccountname: Option[JsValue], schemename: Option[JsValue], bankcode: Option[JsValue], creditcontactinformation: Option[ContactInformation])
  case class TransferPurposeInformation(purposecode: Option[JsValue], purposedescription: Option[JsValue])
  case class TransferRemittanceInformation(unstructured: Option[JsValue], taxremittancereferencenumber: Option[JsValue])
  case class TransferMandateInformation(mandateidentification: Option[JsValue], mandatedescription: Option[JsValue])
  case class CreditTransferPaymentInformation(transactionreference: Option[JsValue], amount: Option[JsValue], debitaccountinformation: Option[DebitAccountInformation], creditaccountinformation: Option[CreditAccountInformation], mandateinformation: Option[TransferMandateInformation], remittanceinformation: Option[TransferRemittanceInformation], purposeinformation: Option[TransferPurposeInformation])
  case class SingleCreditTransferPaymentDetails_Request(messagereference: Option[JsValue], paymentdata: Option[CreditTransferPaymentInformation])
  case class SingleCreditTransferPaymentDetailsResponse(statuscode: Int, statusdescription: String)
  case class BulkCreditTransferPaymentDetails_Request(messagereference: Option[JsValue], paymentdata: Seq[CreditTransferPaymentInformation])
  case class BulkCreditTransferPaymentDetailsResponse(statuscode: Int, statusdescription: String)
  case class OriginalGroupInformation(originalmessageidentification: Option[JsValue], originalmessagenameidentification: Option[JsValue],  originalcreationdatetime: Option[JsValue], originalendtoendidentification: Option[JsValue])
  case class PaymentCancellationInformation(transactionreference: Option[JsValue], amount: Option[JsValue], debitaccountinformation: Option[DebitAccountInformation], creditaccountinformation: Option[CreditAccountInformation], mandateinformation: Option[TransferMandateInformation], remittanceinformation: Option[TransferRemittanceInformation], purposeinformation: Option[TransferPurposeInformation],
                                            originalgroupinformation: Option[OriginalGroupInformation])
  case class PaymentCancellationDetails_Request(messagereference: Option[JsValue], paymentdata: Option[PaymentCancellationInformation])
  case class PaymentCancellationDetailsResponse(statuscode: Int, statusdescription: String)
  //Below classes are used for mapping/holding data internally
  case class TransferDefaultInfo(firstagentidentification: String, assigneeagentidentification: String, chargebearer: String, settlementmethod: String, clearingsystem: String, servicelevel: String, localinstrumentcode: String, categorypurpose: String)
  case class ContactInfo(fullnames: String, phonenumber: String)
  case class DebitAccountInfo(debitaccountnumber: String, debitaccountname: String, debitcontactinformation: ContactInfo, schemename: String)
  case class CreditAccountInfo(creditaccountnumber: String, creditaccountname: String, schemename: String, bankcode: String, creditcontactinformation: ContactInfo)
  case class TransferPurposeInfo(purposecode: String)
  case class TransferRemittanceInfo(unstructured: String, taxremittancereferencenumber: String)
  case class TransferMandateInfo(mandateidentification: String)
  case class CreditTransferPaymentInfo(transactionreference: String, amount: BigDecimal, debitaccountinformation: DebitAccountInfo, creditaccountinformation: CreditAccountInfo, mandateinformation: TransferMandateInfo, remittanceinformation: TransferRemittanceInfo, purposeinformation: TransferPurposeInfo, transferdefaultinformation: TransferDefaultInfo)
  case class SingleCreditTransferPaymentInfo(messagereference: String, creationdatetime: String, numberoftransactions: Int, totalinterbanksettlementamount: BigDecimal, paymentdata: CreditTransferPaymentInfo)
  case class OriginalGroupInfo(originalmessageidentification: String, originalmessagenameidentification: String,  originalcreationdatetime: String, originalendtoendidentification: String)
  case class CancellationStatusReasonInfo(originatorname: String, reasoncode: String, additionalinformation: String)
  case class PaymentCancellationInfo(transactionreference: String, amount: BigDecimal, debitaccountinformation: DebitAccountInfo, creditaccountinformation: CreditAccountInfo, mandateinformation: TransferMandateInfo, remittanceinformation: TransferRemittanceInfo, purposeinformation: TransferPurposeInfo, transferdefaultinformation: TransferDefaultInfo, originalgroupinformation: OriginalGroupInfo, cancellationstatusreasoninformation: CancellationStatusReasonInfo)
  case class SinglePaymentCancellationInfo(messagereference: String, creationdatetime: String, numberoftransactions: Int, totalinterbanksettlementamount: BigDecimal, paymentdata: PaymentCancellationInfo)
  case class BulkPaymentInfo(transactionreference: String, amount: BigDecimal, debitaccountinformation: DebitAccountInfo, creditaccountinformation: CreditAccountInfo, mandateinformation: TransferMandateInfo, remittanceinformation: TransferRemittanceInfo, purposeinformation: TransferPurposeInfo)
  case class BulkCreditTransferPaymentInfo(messagereference: String, creationdatetime: String, numberoftransactions: Int, totalinterbanksettlementamount: BigDecimal, transferdefaultinformation: TransferDefaultInfo, paymentdata: Seq[BulkPaymentInfo])

  /*** Xml data ***/
  //val prettyPrinter = new scala.xml.PrettyPrinter(80, 2)
  //val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)
  //val prettyPrinter = new scala.xml.PrettyPrinter(400, 4)//set it this was because one of the fields has a length of 344
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
  /* Payment-return response */
  case class ResponseGroupHeaderPaymentReturnInformation(messageidentification: String, creationdatetime: String,
                                            instructingagentinformation: InstructingAgentInformation, instructedagentinformation: InstructedAgentInformation,
                                            numberoftransactions: String, settlementmethod: String, clearingsystem: String)
  case class TransactionInformationAndStatusPaymentReturn(returnid: String, originalendtoendidentification: String,
                                             originalTransactionReference: OriginalTransactionReference,
                                             returninterbanksettlementamount: String
                                            )  
  case class PaymentReturnDetailsIpsl(messagereference: String, creationdatetime: String, instructingagentidentification: String, instructedagentidentification: String,
                                             transactionreference: String)
  case class CancellationAssignmentInformation(messageidentification: String, creationdatetime: String,
                                            instructingagentinformation: InstructingAgentInformation, instructedagentinformation: InstructedAgentInformation)
  case class OriginalGroupInformationAndStatus(originalmessageidentification: String, originalmessagenameidentification: String,  originalcreationdatetime: String)
  case class RequestExecutionDateTime(requestdatetime: String)
  case class CancellationStatusReasonInformation(originatorname: String, reasoncode: String, additionalinformation: String)
  case class CancellationTransactionInformationAndStatus(cancellationstatusidentification: String, originalGroupInformationAndStatus: OriginalGroupInformationAndStatus, originalendtoendidentification: String, transactioncancellationstatus: String,
                                             cancellationStatusReasonInformation: CancellationStatusReasonInformation,
                                             originalTransactionReference: OriginalTransactionReference
                                            )
  case class CancellationDetails(cancellationTransactionInformationAndStatus: CancellationTransactionInformationAndStatus)
  case class OriginalTransactionReference(interbanksettlementamount: String, requestExecutionDateTime: RequestExecutionDateTime,
                                          settlementInformation: SettlementInformation, paymentTypeInformation: PaymentTypeInformation,
                                          mandateRelatedInformation: MandateRelatedInformation, remittanceInformation: RemittanceInformation,
                                          ultimateDebtorInformation: UltimateDebtorInformation, debtorInformation: DebtorInformation,
                                          debtorAccountInformation: DebtorAccountInformation, debtorAgentInformation: DebtorAgentInformation,
                                          creditorAgentInformation: CreditorAgentInformation, creditorInformation: CreditorInformation,
                                          creditorAccountInformation: CreditorAccountInformation, ultimateCreditorInformation: UltimateCreditorInformation,
                                          purposeInformation: PurposeInformation)
  //
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
  class AccountVerification(val assignmentInformation: AssignmentInformation, val verificationInformation: VerificationInformation, val isAccSchemeName: Boolean) {

    // (a) convert AccountVerification fields to XML
    def toXml = {
      val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)//value 80 represents max length of "<Document>" header
      //val prettyPrinter = new scala.xml.PrettyPrinter(2850, 4)//value 80 represents max length of "<Document>" header
      val a = toXmlAssignmentInformation
      val assignmentInfo: String = a.toString
      val b = toXmlVerificationInformation
      val verificationInfo = b.toString
      //val requestType: String = "accountverification"
      //val SignatureId: String = getSignatureId(requestType)
      //val myKeyInfoId: String = getKeyInfoId()
      //val myReferenceURI: String = getReferenceURI(myKeyInfoId)
      //val myX509Certificate: String = getX509Certificate()
      //val encodedX509Certificate: String = Base64.getEncoder.encodeToString(myX509Certificate.getBytes)
	  	/*
      val c = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.023.001.02\"><IdVrfctnReq>" +
          assignmentInfo + System.lineSeparator() +
          verificationInfo + System.lineSeparator() +
          "</IdVrfctnReq></Document>"
      }
	    */
      val c = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.023.001.02\"><IdVrfctnReq>" +
          assignmentInfo +
          verificationInfo +
          "</IdVrfctnReq></Document>"
      }

      val xmlData1: scala.xml.Node = scala.xml.XML.loadString(c)
      val requestData: String = prettyPrinter.format(xmlData1)
      /*
      val myDigestValue: String = getDigestValue(requestData)
      val encodedDigestValue: String = Base64.getEncoder.encodeToString(myDigestValue.getBytes)
      val mySignatureValue = getSignatureValue(requestData)
      val myEncodedSignatureValue: String = Base64.getEncoder.encodeToString(mySignatureValue)
	    val encodedSignatureValue: String = myEncodedSignatureValue//myEncodedSignatureValue.replace(" ","").trim
      println("encodedSignatureValue - " + encodedSignatureValue.length)
      println("encodedSignatureValue 2 - " + encodedSignatureValue.replace(" ","").trim.length)
      //val encodedSignatureValue: String = new String(Base64.getEncoder().encode(mySignatureValue.getBytes(StandardCharsets.UTF_8)))
      //val encodedSignatureValue: String = Base64.getEncoder.encodeToString(mySignatureValue.getBytes(StandardCharsets.UTF_8))
      /*** Tests only ***/
        /*
      val decodedWithMime = Base64.getMimeDecoder.decode(encodedSignatureValue)
      val mySignatureValue2: String = decodedWithMime.map(_.toChar).mkString
      val decryptedMessageHash = decryptedSignatureValue(mySignatureValue)
      */
        //val myVar1 = getSignatureValue_test(requestData)
        //val encodedSignatureValue1: String = Base64.getEncoder.encodeToString(myVar1)
        //val myVar1 = mySignatureValue.getBytes("UTF-8")
        //val myVar2 =  new String(myVar1, "UTF-8")
        //val myVar2 = myVar1.map(_.toChar).mkString
        //val myVar3 =  myVar2.getBytes(StandardCharsets.UTF_8)//("UTF-8")
      val myVar2 = Base64.getDecoder.decode(encodedSignatureValue)
      val decryptedMessageHash = decryptedSignatureValue(myVar2)
      val originalMessageHash = getMessageHash(requestData)
      var isVerified: Boolean = verifyMessageHash(originalMessageHash, decryptedMessageHash)
      println("isVerified - " + isVerified)
      /*** Tests only ***/
      val d = toXmlSignatureInformation(SignatureId, encodedDigestValue, myReferenceURI, encodedSignatureValue, myKeyInfoId, encodedX509Certificate)
      val signatureInfo = d.toString
      /*
      val e = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.023.001.02\"><IdVrfctnReq>" +
          assignmentInfo + System.lineSeparator() +
          verificationInfo + System.lineSeparator() +
          "</IdVrfctnReq>" + System.lineSeparator() +
          signatureInfo + System.lineSeparator() +
          "</Document>"
      }
      */
      val finalRequestData = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.023.001.02\"><IdVrfctnReq>" +
          assignmentInfo +
          verificationInfo +
          "</IdVrfctnReq>" +
          signatureInfo +
          "</Document>"
      }
      //val x = encodedSignatureValue.length
      //val y = "<ds:SignatureValue></ds:SignatureValue>".length + 7//value 7 is a default value given
      //val z = x  + y// var z equals the width value of the document
      //prettyPrinter = new scala.xml.PrettyPrinter(z, 4)//set it this was because one of the fields has a variable length eg 344
      val xmlData: scala.xml.Node = scala.xml.XML.loadString(finalRequestData)
      //val accountVerification = prettyPrinter.format(xmlData)
      */
      val accountVerification = getSignedXml(requestData)
      accountVerification
    }
    def toXmlAssignmentInformation = {
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
    }
    private def toXmlVerificationInformation = {
        <Vrfctn>
          <Id>{verificationInformation.identification}</Id>
          <PtyAndAcctId>
            <Acct>
              <Othr>
                {getAccountIdentification(isAccSchemeName)}
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
    }
    private def getAccountIdentification(isAccSchemeName: Boolean) = {
      val accountIdentification = 
      {
        if (isAccSchemeName){//Only show these details where "account scheme" is specified i.e ACC
        <Id>{verificationInformation.partyAndAccountIdentificationInformation.accountInformation.accountIdentification}</Id>
        }
        else
        {//Only show these other details where "account scheme" is not specified i.e PHNE
          <Id>{verificationInformation.partyAndAccountIdentificationInformation.accountInformation.accountIdentification}</Id>
          <SchmeNm>
            <Prtry>{verificationInformation.partyAndAccountIdentificationInformation.accountInformation.schemeName}</Prtry>
          </SchmeNm>
        }
      }
      accountIdentification
    }
    /*
    private def toXmlSignatureInformation(SignatureId: String, myDigestValue: String, myReferenceURI: String, mySignatureValue: String, myKeyInfoId: String, myX509Certificate: String) = {
        <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id={SignatureId}>
          <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
            <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
            <ds:Reference URI="">
              <ds:Transforms>
                <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                <ds:Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
              </ds:Transforms>
              <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
              <ds:DigestValue>{myDigestValue}</ds:DigestValue>
            </ds:Reference>
            <ds:Reference URI={myReferenceURI}>
              <ds:Transforms>
                <ds:Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
              </ds:Transforms>
              <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
              <ds:DigestValue>{myDigestValue}</ds:DigestValue>
            </ds:Reference>
          </ds:SignedInfo>
          <ds:SignatureValue>{mySignatureValue}</ds:SignatureValue>
          <ds:KeyInfo Id={myKeyInfoId}>
            <ds:X509Data>
              <ds:X509Certificate>{myX509Certificate}</ds:X509Certificate>
            </ds:X509Data>
          </ds:KeyInfo>
        </ds:Signature>
        /*
        <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id={SignatureId}>
        <ds:SignedInfo>
          <ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"></ds:CanonicalizationMethod>
          <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"></ds:SignatureMethod>
          <ds:Reference URI="">
            <ds:Transforms>
              <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"></ds:Transform>
              <ds:Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"></ds:Transform>
            </ds:Transforms>
            <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
            <ds:DigestValue>{myDigestValue}</ds:DigestValue>
          </ds:Reference>
          <ds:Reference URI={myReferenceURI}>
            <ds:Transforms>
              <ds:Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"></ds:Transform>
            </ds:Transforms>
            <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
            <ds:DigestValue>{myDigestValue}</ds:DigestValue>
          </ds:Reference>
        </ds:SignedInfo>
        <ds:SignatureValue>{mySignatureValue}</ds:SignatureValue>
        <ds:KeyInfo Id={myKeyInfoId}>
          <ds:X509Data>
            <ds:X509Certificate>{myX509Certificate}</ds:X509Certificate>
          </ds:X509Data>
        </ds:KeyInfo>
      </ds:Signature>
         */
    }
    */

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
      new AccountVerification(assignmentInformation, verificationInformation, false)
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
  class SingleCreditTransfer(val groupHeaderInformation: GroupHeaderInformation, val creditTransferTransactionInformation: CreditTransferTransactionInformation, val isAccSchemeName: Boolean) {

    // (a) convert SingleCreditTransfer fields to XML
    def toXml = {
      val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)//value 80 represents max length of "<Document>" header
      //val prettyPrinter = new scala.xml.PrettyPrinter(2850, 4)//value 80 represents max length of "<Document>" header
      val currencyCode = "KES"
      val a = toXmlGroupHeaderInformation
      val groupHeaderInfo: String = a.toString
      val b = toXmlCreditTransferTransactionInformation
      val creditTransferTransactionInfo = b.toString
      //val requestType: String = "singlecredittransfer"
      //val SignatureId: String = getSignatureId(requestType)
      //val myKeyInfoId: String = getKeyInfoId()
      //val myReferenceURI: String = getReferenceURI(myKeyInfoId)
      //val myX509Certificate: String = getX509Certificate()
      //val encodedX509Certificate: String = Base64.getEncoder.encodeToString(myX509Certificate.getBytes)

      val c = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\"><FIToFICstmrCdtTrf>" +
          groupHeaderInfo +
          creditTransferTransactionInfo +
          "</FIToFICstmrCdtTrf></Document>"
      }

      val xmlData1: scala.xml.Node = scala.xml.XML.loadString(c)
      val requestData: String = prettyPrinter.format(xmlData1)
      /*
      val myDigestValue: String = getDigestValue(requestData)
      val encodedDigestValue: String = Base64.getEncoder.encodeToString(myDigestValue.getBytes)
      val mySignatureValue = getSignatureValue(requestData)
      val myEncodedSignatureValue: String = Base64.getEncoder.encodeToString(mySignatureValue)
	    val encodedSignatureValue: String = myEncodedSignatureValue.replace(" ","").trim
      println("encodedSignatureValue - " + encodedSignatureValue.length)
      println("encodedSignatureValue 2 - " + encodedSignatureValue.replace(" ","").trim.length)
      println("encodedSignatureValue 2 - " + encodedSignatureValue.replace(" ","").trim.length)

      val myVar2 = Base64.getDecoder.decode(encodedSignatureValue)
      val decryptedMessageHash = decryptedSignatureValue(myVar2)
      val originalMessageHash = getMessageHash(requestData)
      var isVerified: Boolean = verifyMessageHash(originalMessageHash, decryptedMessageHash)
      println("isVerified - " + isVerified)
      /*** Tests only ***/
      val d = toXmlSignatureInformation(SignatureId, encodedDigestValue, myReferenceURI, encodedSignatureValue, myKeyInfoId, encodedX509Certificate)
      val signatureInfo = d.toString

      val finalRequestData = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\"><FIToFICstmrCdtTrf>" +
          groupHeaderInfo +
          creditTransferTransactionInfo +
          "</FIToFICstmrCdtTrf>" +
          signatureInfo +
          "</Document>"
      }
      //val x = encodedSignatureValue.length
      //val y = "<ds:SignatureValue></ds:SignatureValue>".length + 7//value 7 is a default value given
      val x = encodedX509Certificate.length
      val y = "<ds:X509Certificate></ds:X509Certificate>".length + 18//value 18 is a default value given
      val z = x  + y// var z equals the width value of the document
      println("z: encodedX509Certificate len: " + z)
      //prettyPrinter = new scala.xml.PrettyPrinter(z, 4)//set it this was because one of the fields has a variable length eg 344
      val xmlData: scala.xml.Node = scala.xml.XML.loadString(finalRequestData)
      val singleCreditTransfer = prettyPrinter.format(xmlData)//<ds:X509Certificate>xxx</ds:X509Certificate>
      */
      val singleCreditTransfer = getSignedXml(requestData)
      singleCreditTransfer

    }
    def toXmlGroupHeaderInformation = {
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
    }
    def toXmlCreditTransferTransactionInformation = {
        <CdtTrfTxInf>
          <PmtId>
            <EndToEndId>{creditTransferTransactionInformation.paymentendtoendidentification}</EndToEndId>
          </PmtId>
          <IntrBkSttlmAmt Ccy="KES">{creditTransferTransactionInformation.interbanksettlementamount}</IntrBkSttlmAmt>
          <AccptncDtTm>{creditTransferTransactionInformation.acceptancedatetime}</AccptncDtTm>
          <ChrgBr>{creditTransferTransactionInformation.chargebearer}</ChrgBr>
          {getMandateRelatedInformation(creditTransferTransactionInformation.mandaterelatedinformation.mandateidentification)}
          {getUltimateDebtorInformation(false)}
          {getInitiatingPartyInformation(false)}
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
                {getDebtorAccountIdentification(true)}
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
          {getCreditorInformation(isAccSchemeName)}
          <CdtrAcct>
            <Id>
              <Othr>
                {getCreditorAccountIdentification(isAccSchemeName)}
              </Othr>
            </Id>
            <Nm>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountname}</Nm>
          </CdtrAcct>
          {getUltimateCreditorInformation(false)}
          <Purp>
            <Prtry>{creditTransferTransactionInformation.purposeinformation.purposecode}</Prtry>
          </Purp>
          <RmtInf>
            <Ustrd>{creditTransferTransactionInformation.remittanceinformation.unstructured}</Ustrd>
            {getTaxRemittanceReferenceNumber(creditTransferTransactionInformation.remittanceinformation.taxremittancereferencenumber)}
          </RmtInf>
        </CdtTrfTxInf>
    }
    private def getMandateRelatedInformation(mandateRelatedInfo: String) = {
      val mandateRelatedInformation = 
      {
        if (mandateRelatedInfo.length > 0){
          <MndtRltdInf>
            <MndtId>{creditTransferTransactionInformation.mandaterelatedinformation.mandateidentification}</MndtId>
          </MndtRltdInf>
        }
      }
      mandateRelatedInformation
    }
    private def getUltimateDebtorInformation(IsUltimateDebtorInfoEnabled: Boolean) = {
      val ultimateDebtorInformation = 
      {
        if (IsUltimateDebtorInfoEnabled){
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
        }
      }
      ultimateDebtorInformation
    }
    private def getInitiatingPartyInformation(IsInitiatingPartyInfoEnabled: Boolean) = {
      val initiatingPartyInformation = 
      {
        if (IsInitiatingPartyInfoEnabled){
          <InitgPty>
            <Id>
              <OrgId>
                <Othr>
                  <Id>{creditTransferTransactionInformation.initiatingpartyinformation.organisationidentification}</Id>
                </Othr>
              </OrgId>
            </Id>
          </InitgPty>
        }
      }
      initiatingPartyInformation
    }
    private def getCreditorInformation(isAccSchemeName: Boolean) = {
      val creditorInformation = 
      {
        if (isAccSchemeName){//i.e ACC
          <Cdtr/>
        }
        else//i.e PHNE 
        {
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
        }
      }
      creditorInformation
    }
    private def getUltimateCreditorInformation(IsUltimateCreditorInfoEnabled: Boolean) = {
      val ultimateCreditorInformation = 
      {
        if (IsUltimateCreditorInfoEnabled){
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
        }
      }
      ultimateCreditorInformation
    }
    private def getDebtorAccountIdentification(isAccSchemeName: Boolean) = {
      val accountIdentification = 
      {
        if (isAccSchemeName){//Only show these details where "account scheme" is specified i.e ACC
          <Id>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountidentification}</Id>
        }
        else
        {//Only show these other details where "account scheme" is not specified i.e PHNE
          <Id>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountidentification}</Id>
          <SchmeNm>
            <Prtry>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountschemename}</Prtry>
          </SchmeNm>
        }
      }
      accountIdentification
    }
    private def getCreditorAccountIdentification(isAccSchemeName: Boolean) = {
      val accountIdentification = 
      {
        if (isAccSchemeName){//Only show these details where "account scheme" is specified i.e ACC
          <Id>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountidentification}</Id>
        }
        else
        {//Only show these other details where "account scheme" is not specified i.e PHNE
          <Id>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountidentification}</Id>
          <SchmeNm>
            <Prtry>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountschemename}</Prtry>
          </SchmeNm>
        }
      }
      accountIdentification
    }
    private def getTaxRemittanceReferenceNumber(TaxRemittanceRef: String) = {
      val taxRemittanceReferenceNumber = 
      {
        if (TaxRemittanceRef.length > 0){
          <Strd>
            <TaxRmt>
              <RefNb>{creditTransferTransactionInformation.remittanceinformation.taxremittancereferencenumber}</RefNb>
            </TaxRmt>
          </Strd>
        }
      }
      taxRemittanceReferenceNumber
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

      new SingleCreditTransfer(groupHeaderInformation, creditTransferTransactionInformation, false)
    }

  }

  //BulkCreditTransfer
  class BulkCreditTransfer(val groupHeaderInformation: GroupHeaderInformation, val creditTransferTransactionInformationBatch: Seq[CreditTransferTransactionInformation], val isAccSchemeName: Boolean) {

    // (a) convert BulkCreditTransfer fields to XML
    def toXml = {
      val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)
      val currencyCode = "KES"
      val a = toXmlGroupHeaderInformation
      val groupHeaderInfo: String = a.toString
      //val b = toXmlCreditTransferTransactionInformation
      //val creditTransferTransactionInfo = b.toString
      val creditTransferTransactionInfo = toXmlBulkCreditTransferTransactionInformation

      val c = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\"><FIToFICstmrCdtTrf>" +
          groupHeaderInfo +
          creditTransferTransactionInfo +
          "</FIToFICstmrCdtTrf></Document>"
      }

      val xmlData1: scala.xml.Node = scala.xml.XML.loadString(c)
      val requestData: String = prettyPrinter.format(xmlData1)
      val bulkCreditTransfer = getSignedXml(requestData)
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
    def toXmlBulkCreditTransferTransactionInformation = {
      var creditTransferTransactionBatch: String = ""
      try{
        if (!creditTransferTransactionInformationBatch.isEmpty){
          if (creditTransferTransactionInformationBatch.length > 0){
            creditTransferTransactionInformationBatch.foreach(creditTransferTransactionInformation => {
              val creditTransferTransaction = toXmlCreditTransferTransactionInformation(creditTransferTransactionInformation)  
              creditTransferTransactionBatch = creditTransferTransactionBatch + creditTransferTransaction.toString()
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
      creditTransferTransactionBatch
    }
    def toXmlCreditTransferTransactionInformation(creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
        <CdtTrfTxInf>
          <PmtId>
            <EndToEndId>{creditTransferTransactionInformation.paymentendtoendidentification}</EndToEndId>
          </PmtId>
          <IntrBkSttlmAmt Ccy="KES">{creditTransferTransactionInformation.interbanksettlementamount}</IntrBkSttlmAmt>
          <AccptncDtTm>{creditTransferTransactionInformation.acceptancedatetime}</AccptncDtTm>
          <ChrgBr>{creditTransferTransactionInformation.chargebearer}</ChrgBr>
          {getMandateRelatedInformation(creditTransferTransactionInformation.mandaterelatedinformation.mandateidentification)}
          {getUltimateDebtorInformation(false, creditTransferTransactionInformation)}
          {getInitiatingPartyInformation(false, creditTransferTransactionInformation)}
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
                {getDebtorAccountIdentification(true, creditTransferTransactionInformation)}
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
          {getCreditorInformation(isAccSchemeName, creditTransferTransactionInformation)}
          <CdtrAcct>
            <Id>
              <Othr>
                {getCreditorAccountIdentification(isAccSchemeName, creditTransferTransactionInformation)}
              </Othr>
            </Id>
            <Nm>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountname}</Nm>
          </CdtrAcct>
          {getUltimateCreditorInformation(false, creditTransferTransactionInformation)}
          <Purp>
            <Prtry>{creditTransferTransactionInformation.purposeinformation.purposecode}</Prtry>
          </Purp>
          <RmtInf>
            <Ustrd>{creditTransferTransactionInformation.remittanceinformation.unstructured}</Ustrd>
            {getTaxRemittanceReferenceNumber(creditTransferTransactionInformation.remittanceinformation.taxremittancereferencenumber)}
          </RmtInf>
        </CdtTrfTxInf>
    }
    private def getMandateRelatedInformation(mandateRelatedInfo: String) = {
      val mandateRelatedInformation = 
      {
        if (mandateRelatedInfo.length > 0){
          <MndtRltdInf>
            <MndtId>{mandateRelatedInfo}</MndtId>
          </MndtRltdInf>
        }
      }
      mandateRelatedInformation
    }
    private def getUltimateDebtorInformation(IsUltimateDebtorInfoEnabled: Boolean, creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
      val ultimateDebtorInformation = 
      {
        if (IsUltimateDebtorInfoEnabled){
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
        }
      }
      ultimateDebtorInformation
    }
    private def getInitiatingPartyInformation(IsInitiatingPartyInfoEnabled: Boolean, creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
      val initiatingPartyInformation = 
      {
        if (IsInitiatingPartyInfoEnabled){
          <InitgPty>
            <Id>
              <OrgId>
                <Othr>
                  <Id>{creditTransferTransactionInformation.initiatingpartyinformation.organisationidentification}</Id>
                </Othr>
              </OrgId>
            </Id>
          </InitgPty>
        }
      }
      initiatingPartyInformation
    }
    private def getCreditorInformation(isAccSchemeName: Boolean, creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
      val creditorInformation = 
      {
        if (isAccSchemeName){//i.e ACC
          <Cdtr/>
        }
        else//i.e PHNE 
        {
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
        }
      }
      creditorInformation
    }
    private def getUltimateCreditorInformation(IsUltimateCreditorInfoEnabled: Boolean, creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
      val ultimateCreditorInformation = 
      {
        if (IsUltimateCreditorInfoEnabled){
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
        }
      }
      ultimateCreditorInformation
    }
    private def getDebtorAccountIdentification(isAccSchemeName: Boolean, creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
      val accountIdentification = 
      {
        if (isAccSchemeName){//Only show these details where "account scheme" is specified i.e ACC
          <Id>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountidentification}</Id>
        }
        else
        {//Only show these other details where "account scheme" is not specified i.e PHNE
          <Id>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountidentification}</Id>
          <SchmeNm>
            <Prtry>{creditTransferTransactionInformation.debtoraccountinformation.debtoraccountschemename}</Prtry>
          </SchmeNm>
        }
      }
      accountIdentification
    }
    private def getCreditorAccountIdentification(isAccSchemeName: Boolean, creditTransferTransactionInformation: CreditTransferTransactionInformation) = {
      val accountIdentification = 
      {
        if (isAccSchemeName){//Only show these details where "account scheme" is specified i.e ACC
          <Id>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountidentification}</Id>
        }
        else
        {//Only show these other details where "account scheme" is not specified i.e PHNE
          <Id>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountidentification}</Id>
          <SchmeNm>
            <Prtry>{creditTransferTransactionInformation.creditoraccountinformation.creditoraccountschemename}</Prtry>
          </SchmeNm>
        }
      }
      accountIdentification
    }
    private def getTaxRemittanceReferenceNumber(TaxRemittanceRef: String) = {
      val taxRemittanceReferenceNumber = 
      {
        if (TaxRemittanceRef.length > 0){
          <Strd>
            <TaxRmt>
              <RefNb>{TaxRemittanceRef}</RefNb>
            </TaxRmt>
          </Strd>
        }
      }
      taxRemittanceReferenceNumber
    }

    override def toString =
    s"groupHeaderInformation: $groupHeaderInformation, creditTransferTransactionInformationBatch: $creditTransferTransactionInformationBatch"
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
      new BulkCreditTransfer(groupHeaderInformation, creditTransfertransactioninformationbatch, false)
    }
  }

  //PaymentCancellation
  class PaymentCancellation(val cancellationAssignmentInformation: CancellationAssignmentInformation, val cancellationDetails: CancellationDetails) {

    // (a) convert PaymentCancellation fields to XML
    //need to work on this. It still uses "SingleCreditTransferResponse" logic
    def toXml = {
      val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)//value 80 represents max length of "<Document>" header
      val numberofTransactions: String = "1"
      val a = toXmlGroupHeaderInformation
      val groupHeaderInfo: String = a.toString
      val b = toXmlNumberofTransactions(numberofTransactions)
      val numberofTransactionsInfo: String = b.toString
      val c = toXmlTransactionInformationAndStatus
      val creditTransferTransactionInfo = c.toString
    
      val d = {
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.056.001.09\"><FIToFIPmtCxlReq>" +
          groupHeaderInfo +
          numberofTransactionsInfo +
          creditTransferTransactionInfo +
          "</FIToFIPmtCxlReq></Document>"
      }

      val xmlData1: scala.xml.Node = scala.xml.XML.loadString(d)
      val requestData: String = prettyPrinter.format(xmlData1)
    
      val paymentCancellation = getSignedXml(requestData)
      paymentCancellation
    }
    def toXmlGroupHeaderInformation = {
        <Assgnmt>
          <Id>{cancellationAssignmentInformation.messageidentification}</Id>
          <Assgnr>
            <Agt>
              <FinInstnId>
                <Othr>
                  <Id>{cancellationAssignmentInformation.instructingagentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </Agt>
          </Assgnr>
          <Assgne>
            <Agt>
              <FinInstnId>
                <Othr>
                  <Id>{cancellationAssignmentInformation.instructedagentinformation.financialInstitutionIdentification}</Id>
                </Othr>
              </FinInstnId>
            </Agt>
          </Assgne>
          <CreDtTm>{cancellationAssignmentInformation.creationdatetime}</CreDtTm>
        </Assgnmt>
    }
    def toXmlNumberofTransactions(noofTransactions: String) = {
        <CtrlData>
          <NbOfTxs>{noofTransactions}</NbOfTxs>
        </CtrlData>
    }
    def toXmlTransactionInformationAndStatus = {
        <Undrlyg>
          <TxInf>
            <CxlId>{cancellationDetails.cancellationTransactionInformationAndStatus.cancellationstatusidentification}</CxlId>
            <OrgnlGrpInf>
              <OrgnlMsgId>{cancellationDetails.cancellationTransactionInformationAndStatus.originalGroupInformationAndStatus.originalmessageidentification}</OrgnlMsgId>
              <OrgnlMsgNmId>{cancellationDetails.cancellationTransactionInformationAndStatus.originalGroupInformationAndStatus.originalmessagenameidentification}</OrgnlMsgNmId>
              <OrgnlCreDtTm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalGroupInformationAndStatus.originalcreationdatetime}</OrgnlCreDtTm>
            </OrgnlGrpInf>
            <OrgnlEndToEndId>{cancellationDetails.cancellationTransactionInformationAndStatus.originalendtoendidentification}</OrgnlEndToEndId>
            <CxlRsnInf>
              <Orgtr>
                <Id>
                  <OrgId>
                    <Othr>
                      <Id>{cancellationAssignmentInformation.instructingagentinformation.financialInstitutionIdentification}</Id>
                    </Othr>
                  </OrgId>
                </Id>
              </Orgtr>
              <Rsn>
                <Cd>{cancellationDetails.cancellationTransactionInformationAndStatus.cancellationStatusReasonInformation.reasoncode}</Cd></Rsn>
              <AddtlInf>{cancellationDetails.cancellationTransactionInformationAndStatus.cancellationStatusReasonInformation.additionalinformation}</AddtlInf>
            </CxlRsnInf>
            <OrgnlTxRef>
              <IntrBkSttlmAmt Ccy="KES">{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.interbanksettlementamount}</IntrBkSttlmAmt>
              {getRequestedExecutionDateInformation(false)}
              {getSettlementInformation(false)}
              {getPaymentTypeInformation(false)}
              {getMandateRelatedInformation(false)}
              {getRemmittanceInformation(cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.remittanceInformation.unstructured)}
              {getUltimateDebtorInformation(false)}
              <Dbtr>
                <Pty>
                  <Nm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorInformation.debtorname}</Nm>
                  {getDebtorOrganisationIdentificationInformation(cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorInformation.debtororganisationidentification)}
                  <CtctDtls>
                    <PhneNb>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorInformation.debtorcontactphonenumber}</PhneNb>
                  </CtctDtls>
                </Pty>
              </Dbtr>
              <DbtrAcct>
                <Id>
                  <Othr>
                    <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorAccountInformation.debtoraccountidentification}</Id>
                  </Othr>
                </Id>
                <Nm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorAccountInformation.debtoraccountname}</Nm>
              </DbtrAcct>
              <DbtrAgt>
                <FinInstnId>
                  <Othr>
                    <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorAgentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </DbtrAgt>
              <CdtrAgt>
                <FinInstnId>
                  <Othr>
                    <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorAgentInformation.financialInstitutionIdentification}</Id>
                  </Othr>
                </FinInstnId>
              </CdtrAgt>
              {getCreditorInformation(cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorInformation.creditorname, cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorInformation.creditororganisationidentification, cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorInformation.creditorcontactphonenumber)}
              <CdtrAcct>
                <Id>
                  <Othr>
                    <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorAccountInformation.creditoraccountidentification}</Id>
                  </Othr>
                </Id>
                <Nm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorAccountInformation.creditoraccountname}</Nm>
              </CdtrAcct>
              {getUltimateCreditorInformation(false)}
              {getPurposeInformation(false)}
            </OrgnlTxRef>
          </TxInf>
        </Undrlyg>
    }
    private def getRequestedExecutionDateInformation(isRequestedExecutionDateInfoEnabled: Boolean) = {
      val requestedExecutionDateInformation = 
      {
        if (isRequestedExecutionDateInfoEnabled){
          <ReqdExctnDt>
            <DtTm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.requestExecutionDateTime.requestdatetime}</DtTm>
          </ReqdExctnDt>
        }
      }
      requestedExecutionDateInformation
    }
    private def getSettlementInformation(isSettlementInfoEnabled: Boolean) = {
      val settlementInformation = 
      {
        if (isSettlementInfoEnabled){
          <SttlmInf>
            <SttlmMtd>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.settlementInformation.settlementmethod}</SttlmMtd>
            <ClrSys>
              <Prtry>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.settlementInformation.clearingSystem}</Prtry>
            </ClrSys>
          </SttlmInf>
        }
      }
      settlementInformation
    }
    private def getPaymentTypeInformation(isPaymentTypeInfoEnabled: Boolean) = {
      val paymentTypeInformation = 
      {
        if (isPaymentTypeInfoEnabled){
          <PmtTpInf>
            <SvcLvl>
              <Prtry>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.paymentTypeInformation.servicelevel}</Prtry>
            </SvcLvl>
            <LclInstrm>
              <Cd>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.paymentTypeInformation.localinstrumentcode}</Cd>
            </LclInstrm>
            <CtgyPurp>
              <Prtry>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.paymentTypeInformation.categorypurpose}</Prtry>
            </CtgyPurp>
          </PmtTpInf>
        }
      }
      paymentTypeInformation
    }
    private def getMandateRelatedInformation(isMandateRelatedInfoEnabled: Boolean) = {
      val mandateRelatedInformation = 
      {
        if (isMandateRelatedInfoEnabled){
          <MndtRltdInf>
            <CdtTrfMndt>
              <MndtId>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.mandateRelatedInformation.mandateidentification}</MndtId>
            </CdtTrfMndt>
          </MndtRltdInf>
        }
      }
      mandateRelatedInformation
    }
    private def getRemmittanceInformation(unstructured: String) = {
      val remmittanceInformation = 
      {
        if (unstructured.length > 0){
          <RmtInf>
            <Ustrd>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.remittanceInformation.unstructured}</Ustrd>
            {getTaxRemittanceReferenceNumber(cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.remittanceInformation.taxremittancereferencenumber)}
          </RmtInf>
        }
      }
      remmittanceInformation
    }
    private def getTaxRemittanceReferenceNumber(taxRemittanceRefNo: String) = {
      val taxRemittanceReferenceNumber = 
      {
        if (taxRemittanceRefNo.length > 0){
          <Strd>
            <TaxRmt>
              <RefNb>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.remittanceInformation.taxremittancereferencenumber}</RefNb>
            </TaxRmt>
          </Strd>
        }
      }
      taxRemittanceReferenceNumber
    }
    private def getUltimateDebtorInformation(IsUltimateDebtorInfoEnabled: Boolean) = {
      val ultimateDebtorInformation = 
      {
        if (IsUltimateDebtorInfoEnabled){
          <UltmtDbtr>
            <Pty>
              <Nm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.ultimateDebtorInformation.debtorname}</Nm>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.ultimateDebtorInformation.debtororganisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
              <CtctDtls>
                <PhneNb>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.ultimateDebtorInformation.debtorcontactphonenumber}</PhneNb>
              </CtctDtls>
            </Pty>
          </UltmtDbtr>
        }
      }
      ultimateDebtorInformation
    }
    private def getDebtorOrganisationIdentificationInformation(organisationIdentification: String) = {
      val debtorOrganisationIdentificationInformation = 
      {
        if (organisationIdentification.length > 0){
          <Id>
            <OrgId>
              <Othr>
                <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.debtorInformation.debtororganisationidentification}</Id>
              </Othr>
            </OrgId>
          </Id>
        }
      }
      debtorOrganisationIdentificationInformation
    }
    private def getCreditorInformation(creditorName: String, organisationIdentification: String, contactPhonenumber: String) = {
      val creditorInformation = 
      {
        if (creditorName.length == 0 && organisationIdentification.length == 0 && contactPhonenumber.length == 0){
          <Cdtr/>
        }
        else {
          <Cdtr>
            <Pty>
              {getCreditorNameInformation(creditorName)}
              {getCreditorOrganisationIdentificationInformation(organisationIdentification)}
              {getCreditorContactPhonenumberInformation(contactPhonenumber)}
            </Pty>
          </Cdtr>
        }
      }
      creditorInformation
    }
    private def getCreditorNameInformation(creditorName: String) = {
      val creditorNameInformation = 
      {
        if (creditorName.length > 0){
          <Nm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorInformation.creditorname}</Nm>
        }
      }
      creditorNameInformation
    }
    private def getCreditorOrganisationIdentificationInformation(organisationIdentification: String) = {
      val creditorOrganisationIdentificationInformation = 
      {
        if (organisationIdentification.length > 0){
          <Id>
            <OrgId>
              <Othr>
                <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorInformation.creditororganisationidentification}</Id>
              </Othr>
            </OrgId>
          </Id>
        }
      }
      creditorOrganisationIdentificationInformation
    }
    private def getCreditorContactPhonenumberInformation(contactPhoneNumber: String) = {
      val creditorContactPhoneNumberInformation = 
      {
        if (contactPhoneNumber.length > 0){
          <CtctDtls>
            <PhneNb>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.creditorInformation.creditorcontactphonenumber}</PhneNb>
          </CtctDtls>
        }
      }
      creditorContactPhoneNumberInformation
    }
    private def getUltimateCreditorInformation(IsUltimateCreditorInfoEnabled: Boolean) = {
      val ultimateCreditorInformation = 
      {
        if (IsUltimateCreditorInfoEnabled){
          <UltmtCdtr>
            <Pty>
              <Nm>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.ultimateCreditorInformation.creditorname}</Nm>
              <Id>
                <OrgId>
                  <Othr>
                    <Id>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.ultimateCreditorInformation.creditororganisationidentification}</Id>
                  </Othr>
                </OrgId>
              </Id>
              <CtctDtls>
                <PhneNb>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.ultimateCreditorInformation.creditorcontactphonenumber}</PhneNb>
              </CtctDtls>
            </Pty>
          </UltmtCdtr>
        }
      }
      ultimateCreditorInformation
    }
    private def getPurposeInformation(isPurposeInfoEnabled: Boolean) = {
      val purposeInformation = 
      {
        if (isPurposeInfoEnabled){
          <Purp>
            <Prtry>{cancellationDetails.cancellationTransactionInformationAndStatus.originalTransactionReference.purposeInformation.purposecode}</Prtry>
          </Purp>
        }
      }
      purposeInformation
    }
    override def toString =
      s"cancellationAssignmentInformation: $cancellationAssignmentInformation, cancellationDetails: $cancellationDetails"
  }

  object PaymentCancellation {

    // (b) convert XML to a PaymentCancellation
    def fromXml(node: scala.xml.Node):PaymentCancellation = {
      //Assignment Information
      val messageidentification: String = (node \ "FIToFIPmtCxlReq" \ "Assgnmt" \ "Id").text
      val creationdatetime: String = (node \ "FIToFIPmtCxlReq" \ "Assgnmt" \ "CreDtTm").text
      val instructingagentinformationfinancialInstitutionIdentification: String = (node \ "FIToFIPmtCxlReq" \ "Assgnmt" \ "Assgnr" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
      val instructedagentinformationfinancialInstitutionIdentification: String = (node \ "FIToFIPmtCxlReq" \ "Assgnmt" \ "Assgne" \ "Agt" \ "FinInstnId" \ "Othr" \ "Id").text
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation(instructingagentinformationfinancialInstitutionIdentification)
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation(instructedagentinformationfinancialInstitutionIdentification)
      val nbOfTxs: String = (node \ "FIToFIPmtCxlReq" \ "CtrlData" \ "NbOfTxs").text
      val cancellationAssignmentInformation = CancellationAssignmentInformation(messageidentification, creationdatetime, instructingagentinformation, instructedagentinformation)
      //Details of the original transactions being cancelled
      val cancellationidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "CxlId").text
      //Original Group Information And Status
      val originalmessageidentification: String =  (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlGrpInf" \ "OrgnlMsgId").text
      val originalmessagenameidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlGrpInf" \ "OrgnlMsgNmId").text
      val originalcreationdatetime: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlGrpInf" \ "OrgnlCreDtTm").text
      val originalGroupInformationAndStatus = OriginalGroupInformationAndStatus(originalmessageidentification, originalmessagenameidentification,  originalcreationdatetime)
      val originalendtoendidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlEndToEndId").text
      val transactioncancellationstatus: String = ""
      //Cancellation status reason Information
      val originatorname: String = ""
      val reasoncode: String = ""
      val additionalinformation: String = ""
      //Original transaction
      val interbanksettlementamount: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "IntrBkSttlmAmt").text
      val requestdatetime: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "ReqdExctnDt" \ "DtTm").text
      val settlementmethod: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "SttlmInf" \ "SttlmMtd").text
      val clearingsystem: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "SttlmInf" \ "ClrSys" \ "Prtry").text
      val servicelevel: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "PmtTpInf" \ "SvcLvl" \ "Prtry").text
      val localinstrumentcode: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "PmtTpInf" \ "LclInstrm" \ "Cd").text
      val categorypurpose: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "PmtTpInf" \ "CtgyPurp" \ "Prtry").text
      var mandateidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "MndtRltdInf" \ "CdtTrfMndt" \ "MndtId").text
      var remittanceinformationunstructured: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "RmtInf" \ "Ustrd").text
      var remittanceinformationtaxremittancereferencenumber: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "RmtInf" \ "Strd" \ "TaxRmt" \ "RefNb").text
      //ultimatedebtor
      var ultimatedebtorinformationdebtorname: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "UltmtDbtr" \ "Pty" \ "Nm").text
      val ultimatedebtorinformationdebtororganisationidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "UltmtDbtr" \ "Pty" \ "Id" \ "OrgId" \ "Othr" \ "Id").text
      var ultimatedebtorinformationdebtorcontactphonenumber: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "UltmtDbtr" \ "Pty" \ "CtctDtls" \ "PhneNb").text
      //debtor
      var debtorinformationdebtorname: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Dbtr" \ "Pty" \ "Nm").text
      val debtorinformationdebtororganisationidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Dbtr" \ "Pty" \ "Id" \ "OrgId" \ "Othr" \ "Id").text
      var debtorinformationdebtorcontactphonenumber: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Dbtr" \ "Pty" \ "CtctDtls" \ "PhneNb").text
      var debtoraccountinformationdebtoraccountidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "DbtrAcct" \ "Id" \ "Othr" \ "Id").text
      val debtoraccountinformationdebtoraccountschemename: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "DbtrAcct" \ "Id" \ "Othr" \ "SchmeNm" \ "Prtry").text
      var debtoraccountinformationdebtoraccountname: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "DbtrAcct" \ "Nm").text
      val debtoragentinformationfinancialInstitutionIdentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "DbtrAgt" \ "FinInstnId" \ "Othr" \ "Id").text
      //ultimatecreditor
      var ultimatecreditorinformationcreditorname: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "UltmtCdtr" \ "Pty" \ "Nm").text
      var ultimatecreditorinformationcreditororganisationidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "UltmtCdtr" \ "Pty" \ "Id" \ "OrgId" \ "Othr" \ "Id").text
      var ultimatecreditorinformationcreditorcontactphonenumber: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "UltmtCdtr" \ "Pty" \ "CtctDtls" \ "PhneNb").text
      //creditor
      var creditoragentinformationfinancialInstitutionIdentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "CdtrAgt" \ "FinInstnId" \ "Othr" \ "Id").text
      var creditorinformationcreditorname: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Cdtr" \ "Pty" \ "Nm").text
      var creditorinformationcreditororganisationidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Cdtr" \ "Pty" \ "Id" \ "OrgId" \ "Othr" \ "Id").text
      var creditorinformationcreditorcontactphonenumber: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Cdtr" \ "Pty" \ "CtctDtls" \ "PhneNb").text
      var creditoraccountinformationcreditoraccountidentification: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "CdtrAcct" \ "Id" \ "Othr" \ "Id").text
      var creditoraccountinformationcreditoraccountschemename: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "CdtrAcct" \ "Id" \ "Othr" \ "SchmeNm" \ "Prtry").text
      var creditoraccountinformationcreditoraccountname: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "CdtrAcct" \ "Nm").text
      var purposeinformationpurposecode: String = (node \ "FIToFIPmtCxlReq" \ "Undrlyg" \ "TxInf" \ "OrgnlTxRef" \ "Purp" \ "Prtry").text

      val requestExecutionDateTime: RequestExecutionDateTime = RequestExecutionDateTime(requestdatetime)
      val settlementInformation: SettlementInformation = SettlementInformation(settlementmethod, clearingsystem)
      val paymentTypeInformation: PaymentTypeInformation = PaymentTypeInformation(servicelevel, localinstrumentcode, categorypurpose)
      val mandateRelatedInformation: MandateRelatedInformation = MandateRelatedInformation(mandateidentification)
      val remittanceInformation: RemittanceInformation = RemittanceInformation(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
      val ultimateDebtorInformation: UltimateDebtorInformation = UltimateDebtorInformation(ultimatedebtorinformationdebtorname, ultimatedebtorinformationdebtororganisationidentification, ultimatedebtorinformationdebtorcontactphonenumber)
      val debtorInformation: DebtorInformation = DebtorInformation(debtorinformationdebtorname, debtorinformationdebtororganisationidentification, debtorinformationdebtorcontactphonenumber)
      val debtorAccountInformation: DebtorAccountInformation = DebtorAccountInformation(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountschemename, debtoraccountinformationdebtoraccountname)
      val debtorAgentInformation: DebtorAgentInformation = DebtorAgentInformation(debtoragentinformationfinancialInstitutionIdentification)
      val creditorAgentInformation: CreditorAgentInformation = CreditorAgentInformation(creditoragentinformationfinancialInstitutionIdentification)
      val creditorInformation: CreditorInformation = CreditorInformation(creditorinformationcreditorname, creditorinformationcreditororganisationidentification, creditorinformationcreditorcontactphonenumber)
      val creditorAccountInformation: CreditorAccountInformation = CreditorAccountInformation(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountschemename, creditoraccountinformationcreditoraccountname)
      val ultimateCreditorInformation: UltimateCreditorInformation = UltimateCreditorInformation(ultimatecreditorinformationcreditorname, ultimatecreditorinformationcreditororganisationidentification, ultimatecreditorinformationcreditorcontactphonenumber)
      val purposeInformation: PurposeInformation = PurposeInformation(purposeinformationpurposecode)
      val cancellationStatusReasonInformation: CancellationStatusReasonInformation = CancellationStatusReasonInformation(originatorname, reasoncode, additionalinformation)
      val originalTransactionReference: OriginalTransactionReference = OriginalTransactionReference(interbanksettlementamount, requestExecutionDateTime,
        settlementInformation, paymentTypeInformation,
        mandateRelatedInformation, remittanceInformation,
        ultimateDebtorInformation, debtorInformation,
        debtorAccountInformation, debtorAgentInformation,
        creditorAgentInformation, creditorInformation,
        creditorAccountInformation, ultimateCreditorInformation,
        purposeInformation)
      val cancellationTransactionInformationAndStatus: CancellationTransactionInformationAndStatus = CancellationTransactionInformationAndStatus(cancellationidentification, originalGroupInformationAndStatus, originalendtoendidentification, transactioncancellationstatus,
        cancellationStatusReasonInformation, originalTransactionReference)
      val cancellationDetails = CancellationDetails(cancellationTransactionInformationAndStatus)

      new PaymentCancellation(cancellationAssignmentInformation, cancellationDetails)
    }
  }

  /*** Xml data ***/

  case class AccountVerificationTableDetails(batchreference: java.math.BigDecimal, accountnumber: String, bankcode: String, messagereference: String, transactionreference: String, schemename: String, batchsize: Integer, requestmessagecbsapi: String, datefromcbsapi: String, remoteaddresscbsapi: String)
  case class AccountVerificationTableResponseDetails(id: java.math.BigDecimal, responsecode: Int, responsemessage: String)
  case class ClientApiResponseDetails(responsecode: Int, responsemessage: String)

  case class SingleCreditTransferPaymentTableDetails(batchreference: java.math.BigDecimal, 
  debtoraccountnumber: String, debtoraccountname: String, debtorbankcode: String, 
  messagereference: String, transactionreference: String, debtorschemename: String, 
  amount: java.math.BigDecimal, debtorfullnames: String, debtorphonenumber: String, 
  creditoraccountnumber: String, creditoraccountname: String, creditorbankcode: String, creditorschemename: String, 
  remittanceinfounstructured: String, taxremittancereferenceno: String, purposecode: String, 
  chargebearer: String, mandateidentification: String, instructingagentbankcode: String, instructedagentbankcode: String, 
  batchsize: Integer, requestmessagecbsapi: String, datefromcbsapi: String, remoteaddresscbsapi: String)

  case class SingleCreditTransferPaymentTableResponseDetails(id: java.math.BigDecimal, responsecode: Int, responsemessage: String)

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
  val firstAgentIdentification: String = getSettings("assignerAgentCode")//"0074" //Request creator
  val assignerAgentIdentification: String = firstAgentIdentification //party that deliveres request to IPSL
  val assigneeAgentIdentification: String = getSettings("assigneeAgentCode")//"9999" //party that processes request i.e IPSL
  //
  val chargeBearer: String = getSettings("chargeBearer")//"SLEV"
  val settlementMethod: String = getSettings("settlementMethod")//"CLRG"
  val clearingSystem: String = getSettings("clearingSystem")//"IPS"
  val serviceLevel: String = getSettings("serviceLevel")//"P2PT"
  val localInstrumentCode: String = getSettings("localInstrumentCode")//"INST"
  val categoryPurpose: String = getSettings("categoryPurpose")//"IBNK"

  val keystore_type: String =  getSettings("keyStoreType")//"PKCS12"
  val encryptionAlgorithm: String =  getSettings("encryptionAlgorithm")//"RSA"
  val messageHashAlgorithm: String =  getSettings("messageHashAlgorithm")//"SHA-256"
  val sender_keystore_path: String =  getSettings("senderKeyStorePath")//"certsconf/sender_keystore.p12"
  val senderKeyPairName: String =  getSettings("senderKeyPairName")//"senderKeyPair"
  val senderKeyStorePwd: String =  getSettings("senderKeyStorePwd")//"CYv.BF33*cpb%s4U"
  val senderKeyStorePwdCharArray = senderKeyStorePwd.toCharArray()
  val privateKey: PrivateKey = getPrivateKey()

  val receiver_keystore_path: String = getSettings("receiverKeyStorePath")//"certsconf/receiver_keystore.p12"
  val receiverKeyPairName: String = getSettings("receiverKeyPairName")//"receiverKeyPair"
  val receiverKeyStorePwd: String = getSettings("receiverKeyStorePwd")//"5{zN5,4UMf-ZST+5"
  val receiverKeyStorePwdCharArray = receiverKeyStorePwd.toCharArray()
  val publicKey: PublicKey = getPublicKey()
  val strOutgoingAccountVerificationUrlIpsl: String = getSettings("outgoingAccountVerificationUrlIpsl")
  val strOutgoingSingleCreditTransferUrlIpsl: String = getSettings("outgoingSingleCreditTransferUrlIpsl")
  val strOutgoingPaymentCancellationUrlIpsl: String = getSettings("outgoingPaymentCancellationUrlIpsl")
  val fac: XMLSignatureFactory = XMLSignatureFactory.getInstance("DOM")//private static final
  val C14N: String = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"

  def addSingleCreditTransferPaymentDetails = Action.async { request =>
    Future {
      val dateFromCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      val startDate: String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var entryID: Int = 0
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      //var myS2B_PaymentDetailsResponse_BatchData: Seq[S2B_PaymentDetailsResponse_Batch] = Seq.empty[S2B_PaymentDetailsResponse_Batch]
      val strApifunction: String = "addsinglecredittransferpaymentdetails"
      var myHttpStatusCode = HttpStatusCode.BadRequest
      var isValidMessageReference: Boolean = false
      var isValidTransactionReference: Boolean = false
      var isMatchingReference: Boolean = false
      var isValidSchemeName: Boolean = false
      var isValidAmount: Boolean = false
      var isValidDebitAccount: Boolean = false
      var isValidDebitAccountName: Boolean = false
      var isValidDebtorName: Boolean = false
      var isValidDebitPhoneNumber: Boolean = false
      var isValidCreditAccount: Boolean = false
      var isValidCreditAccountName: Boolean = false
      var isValidCreditBankCode: Boolean = false
      //var isValidCreditPhoneNumber: Boolean = false
      var isValidRemittanceinfoUnstructured: Boolean = false
      var isValidPurposeCode: Boolean = false
      var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
      var strClientIP: String = ""
      var strChannelType: String = ""
      var strChannelCallBackUrl: String = ""
      var strRequest: String = ""

      var myAmount: BigDecimal = 0
      var strAmount: String = ""

      var messageidentification: String = ""

      var paymentendtoendidentification: String = ""
      var interbanksettlementamount: BigDecimal = 0
      var totalinterbanksettlementamount: BigDecimal = 0
      var mandateidentification: String = ""
      //debtor
      var debtorinformationdebtorname: String = ""
      //val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
      var debtorinformationdebtorcontactphonenumber: String = ""
      var debtoraccountinformationdebtoraccountidentification: String = ""
      //val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
      var debtoraccountinformationdebtoraccountname: String = ""
      //val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
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

      try
      {
        //var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound: Boolean = false
        var isAuthTokenFound: Boolean = false
        var isCredentialsFound: Boolean = false
        //var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        //var strClientIP : String = ""

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

          if (request.headers.get("ChannelCallBackUrl") != None){
            val myheaderChannelType = request.headers.get("ChannelCallBackUrl")
            if (myheaderChannelType.get != None){
              strChannelCallBackUrl = myheaderChannelType.get.toString
              if (strChannelCallBackUrl != null){
                strChannelCallBackUrl = strChannelCallBackUrl.trim
              }
              else{
                strChannelCallBackUrl = ""
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
            if (!isCredentialsFound){strPassword = ""}

            val myOutput = validateClientApi(strChannelType, strUserName, strPassword, strClientIP, strApifunction)
            if (myOutput.responsecode != null){
              responseCode = myOutput.responsecode
            }

            if (myOutput.responsemessage != null){
              responseMessage = myOutput.responsemessage
            }
            else{
              responseMessage = "Error occured during processing, please try again."
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
              (JsPath \ "fullnames").readNullable[JsValue] and
                (JsPath \ "phonenumber").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(ContactInformation.apply _)

            implicit val DebitAccountInformation_Reads: Reads[DebitAccountInformation] = (
              (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "debitaccountname").readNullable[JsValue] and
                (JsPath \ "debitcontactinformation").readNullable[ContactInformation]
              )(DebitAccountInformation.apply _)

            implicit val CreditAccountInformation_Reads: Reads[CreditAccountInformation] = (
              (JsPath \ "creditaccountnumber").readNullable[JsValue] and
                (JsPath \ "creditaccountname").readNullable[JsValue] and
                (JsPath \ "schemename").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue] and
                (JsPath \ "creditcontactinformation").readNullable[ContactInformation]
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
                (JsPath \ "debitaccountinformation").readNullable[DebitAccountInformation] and
                (JsPath \ "creditaccountinformation").readNullable[CreditAccountInformation] and
                (JsPath \ "mandateinformation").readNullable[TransferMandateInformation] and
                (JsPath \ "remittanceinformation").readNullable[TransferRemittanceInformation] and
                (JsPath \ "purposeinformation").readNullable[TransferPurposeInformation]
              )(CreditTransferPaymentInformation.apply _)

            implicit val SingleCreditTransferPaymentDetails_Request_Reads: Reads[SingleCreditTransferPaymentDetails_Request] = (
              (JsPath \ "messagereference").readNullable[JsValue] and
                (JsPath \ "paymentdata").readNullable[CreditTransferPaymentInformation]
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
                  //val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{
                    /*
                    var myAmount: BigDecimal = 0
                    var strAmount: String = ""

                    var messageidentification: String = ""

                    var paymentendtoendidentification: String = ""
                    var interbanksettlementamount: BigDecimal = 0
                    var totalinterbanksettlementamount: BigDecimal = 0
                    var mandateidentification: String = ""
                    //debtor
                    var debtorinformationdebtorname: String = ""
                    //val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
                    var debtorinformationdebtorcontactphonenumber: String = ""
                    var debtoraccountinformationdebtoraccountidentification: String = ""
                    //val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
                    var debtoraccountinformationdebtoraccountname: String = ""
                    //val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
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
                    */
                    //default values
                    //val instructingagentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
                    //val instructedagentinformationfinancialInstitutionIdentification: String = assigneeAgentIdentification //i.e IPSL
                    //val initiatingpartyinformationorganisationidentification: String = firstAgentIdentification
                    //val chargebearer: String = chargeBearer
                    //val settlementmethod: String = settlementMethod
                    //val clearingsystem: String = clearingSystem
                    //val servicelevel: String = serviceLevel
                    //val localinstrumentcode: String = localInstrumentCode
                    //val categorypurpose: String = categoryPurpose

                    //val creationDateTime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    val t1: String =  new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    val t2: String =  new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date)
                    val creationDateTime: String = t1 + "T" + t2+ "Z"
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
                    //val acceptancedatetime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)

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

                    isValidMessageReference = false
                    isValidTransactionReference = false
                    isMatchingReference = false
                    isValidSchemeName = false
                    isValidAmount = false
                    isValidDebitAccount = false
                    isValidDebitAccountName = false
                    isValidDebtorName = false
                    isValidDebitPhoneNumber = false
                    isValidCreditAccount = false
                    isValidCreditAccountName = false
                    isValidCreditBankCode = false
                    //isValidCreditPhoneNumber = false
                    isValidRemittanceinfoUnstructured = false
                    isValidPurposeCode = false

                    try{
                      var isValidPaymentdata = false

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

                      isValidPaymentdata = {
                        var isValid: Boolean = false
                        if (myPaymentDetails.paymentdata != None){
                          if (myPaymentDetails.paymentdata.get != None){
                            isValid = true
                          }
                        }  
                        isValid
                      }

                      //paymentendtoendidentification
                      /*
                      if (myPaymentDetails.paymentdata != None) {
                        if (myPaymentDetails.paymentdata.get != None) {
                          val myData = myPaymentDetails.paymentdata.get
                          paymentendtoendidentification = myData.transactionreference.getOrElse("").toString()
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
                      */
                      if (isValidPaymentdata){
                        val myData = myPaymentDetails.paymentdata.get
                        paymentendtoendidentification = myData.transactionreference.getOrElse("").toString()
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
                      
                      //strAmount
                      if (isValidPaymentdata){
                        val myData = myPaymentDetails.paymentdata.get
                          strAmount = myData.amount.getOrElse("").toString()
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
                      
                      //mandateidentification
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.mandateinformation != None){
                            if (myData1.mandateinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.mandateinformation.get

                          mandateidentification = myData2.mandateidentification.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        var isValid: Boolean = false
                        val isValid1 = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}  
                            else {false} 
                          }  
                          else {false}
                        }
                        if (isValid1){
                          val myVar1 = myData1.debitaccountinformation.get
                          if (myVar1.debitcontactinformation != None){
                            if (myVar1.debitcontactinformation.get != None){isValid = true}
                          }  
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          val myData3 = myData2.debitcontactinformation.get
                          debtorinformationdebtorname = myData3.fullnames.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        var isValid: Boolean = false
                        val isValid1 = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}  
                            else {false} 
                          }  
                          else {false}
                        }
                        if (isValid1){
                          val myVar1 = myData1.debitaccountinformation.get
                          if (myVar1.debitcontactinformation != None){
                            if (myVar1.debitcontactinformation.get != None){isValid = true}
                          }  
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          val myData3 = myData2.debitcontactinformation.get
                          debtorinformationdebtorcontactphonenumber = myData3.phonenumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          debtoraccountinformationdebtoraccountidentification = myData2.debitaccountnumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          debtoraccountinformationdebtoraccountname = myData2.debitaccountname.getOrElse("").toString()
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
                      /*
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditorinformationcreditorname = myData2.creditaccountname.getOrElse("").toString()
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
                      */
                      
                      //creditorinformationcreditorcontactphonenumber
                      /*
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
                      */
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        var isValid: Boolean = false
                        val isValid1 = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true}  
                            else {false} 
                          }  
                          else {false}
                        }
                        if (isValid1){
                          val myVar1 = myData1.creditaccountinformation.get
                          if (myVar1.creditcontactinformation != None){
                            if (myVar1.creditcontactinformation.get != None){isValid = true}
                          }  
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          val myData3 = myData2.creditcontactinformation.get
                          //creditorinformationcreditorname
                          creditorinformationcreditorname = myData3.fullnames.getOrElse("").toString()
                          if (creditorinformationcreditorname != null && creditorinformationcreditorname != None){
                            creditorinformationcreditorname = creditorinformationcreditorname.trim
                            if (creditorinformationcreditorname.length > 0){
                              creditorinformationcreditorname = creditorinformationcreditorname.replace("'","")//Remove apostrophe
                              creditorinformationcreditorname = creditorinformationcreditorname.replace("  "," ")//Remove double spaces
                              creditorinformationcreditorname = creditorinformationcreditorname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditorinformationcreditorname = creditorinformationcreditorname.trim
                            }
                          }

                          //creditorinformationcreditorcontactphonenumber
                          creditorinformationcreditorcontactphonenumber = myData3.phonenumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditoraccountinformationcreditoraccountidentification = myData2.creditaccountnumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true}   
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          //println("myData1: " + myData1)
                          //println("myData2: " + myData2)
                          creditoraccountinformationcreditoraccountschemename = myData2.schemename.getOrElse("").toString()
                          //println("creditoraccountinformationcreditoraccountschemename: " + creditoraccountinformationcreditoraccountschemename)
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditoraccountinformationcreditoraccountname = myData2.creditaccountname.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditoragentinformationfinancialInstitutionIdentification = myData2.bankcode.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.purposeinformation != None){
                            if (myData1.purposeinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.purposeinformation.get
                          purposeinformationpurposecode = myData2.purposecode.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.remittanceinformation != None){
                            if (myData1.remittanceinformation.get != None){true}   
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.remittanceinformation.get
                          remittanceinformationunstructured = myData2.unstructured.getOrElse("").toString()
                          if (remittanceinformationunstructured != null && remittanceinformationunstructured != None){
                            remittanceinformationunstructured = remittanceinformationunstructured.trim
                            if (remittanceinformationunstructured.length > 0){
                              remittanceinformationunstructured = remittanceinformationunstructured.replace("'","")//Remove apostrophe
                              remittanceinformationunstructured = remittanceinformationunstructured.replace("  "," ")//Remove double spaces
                              remittanceinformationunstructured = remittanceinformationunstructured.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              remittanceinformationunstructured = remittanceinformationunstructured.trim
                            }
                          }
                        }
                      }

                      //remittanceinformationtaxremittancereferencenumber
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.remittanceinformation != None){
                            if (myData1.remittanceinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.remittanceinformation.get
                          remittanceinformationtaxremittancereferencenumber = myData2.taxremittancereferencenumber.getOrElse("").toString()
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
                    isValidSchemeName = {
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

                    isValidMessageReference = {
                      var isValid: Boolean = false
                      if (messageidentification.length > 0 && messageidentification.length <= 35){
                        val isNumeric: Boolean = messageidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myMessageidentification = BigDecimal(messageidentification)
                          if (myMessageidentification > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidTransactionReference = {
                      var isValid: Boolean = false
                      if (paymentendtoendidentification.length > 0 && paymentendtoendidentification.length <= 35){
                        val isNumeric: Boolean = paymentendtoendidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myPaymentendtoendidentification = BigDecimal(paymentendtoendidentification)
                          if (myPaymentendtoendidentification > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isMatchingReference = {
                      var isValid: Boolean = false
                      if (isValidMessageReference && isValidTransactionReference) {
                        if (messageidentification.equalsIgnoreCase(paymentendtoendidentification)){
                          isValid = true  
                        }  
                      }
                      isValid
                    }

                    isValidAmount = {
                      if (myAmount > 0){true}
                      else {false}
                    }

                    isValidDebitAccount = {
                      var isValid: Boolean = false
                      if (debtoraccountinformationdebtoraccountidentification.length > 0 && debtoraccountinformationdebtoraccountidentification.length <= 35){
                        val isNumeric: Boolean = debtoraccountinformationdebtoraccountidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myDebtoraccount = BigDecimal(debtoraccountinformationdebtoraccountidentification)
                          if (myDebtoraccount > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidDebitAccountName = {
                      var isValid: Boolean = false
                      if (debtoraccountinformationdebtoraccountname.replace("  ","").length > 0 && debtoraccountinformationdebtoraccountname.replace("  ","").length <= 70){
                        isValid = true
                      }
                      isValid
                    }

                    isValidDebtorName  = {
                      var isValid: Boolean = false
                      if (debtorinformationdebtorname.replace("  ","").length > 0 && debtorinformationdebtorname.replace("  ","").length <= 140){
                        isValid = true
                      }
                      isValid
                    }

                    isValidDebitPhoneNumber = {
                      var isValid: Boolean = false
                      //if (debtorinformationdebtorcontactphonenumber.length == 10 || debtorinformationdebtorcontactphonenumber.length == 12){
                      if (debtorinformationdebtorcontactphonenumber.length == 14){
                        /*
                        val isNumeric: Boolean = debtorinformationdebtorcontactphonenumber.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myPhonenumber = BigDecimal(debtorinformationdebtorcontactphonenumber)
                          if (myPhonenumber > 0){isValid = true}
                        }
                        */
                        isValid = true
                      }
                      isValid
                    }

                    isValidCreditAccount = {
                      var isValid: Boolean = false
                      if (creditoraccountinformationcreditoraccountidentification.length > 0 && creditoraccountinformationcreditoraccountidentification.length <= 35){
                        val isNumeric: Boolean = creditoraccountinformationcreditoraccountidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myCreditoraccount = BigDecimal(creditoraccountinformationcreditoraccountidentification)
                          if (myCreditoraccount > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidCreditAccountName = {
                      var isValid: Boolean = false
                      if (creditoraccountinformationcreditoraccountname.replace("  ","").length > 0 && creditoraccountinformationcreditoraccountname.replace("  ","").length <= 140){
                        isValid = true
                      }
                      isValid
                    }

                    isValidCreditBankCode = {
                      var isValid: Boolean = false
                      if (creditoragentinformationfinancialInstitutionIdentification.length > 0 && creditoragentinformationfinancialInstitutionIdentification.length <= 35){
                        val isNumeric: Boolean = creditoragentinformationfinancialInstitutionIdentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myCreditoragent = BigDecimal(creditoragentinformationfinancialInstitutionIdentification)
                          if (myCreditoragent > 0){isValid = true}
                        }
                      }
                      isValid
                    }
                    /*
                    isValidCreditPhoneNumber = {
                      var isValid: Boolean = false
                      if (creditorinformationcreditorcontactphonenumber.length == 10 || creditorinformationcreditorcontactphonenumber.length == 12){
                        val isNumeric: Boolean = creditorinformationcreditorcontactphonenumber.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myPhonenumber = creditorinformationcreditorcontactphonenumber.toInt
                          if (myPhonenumber > 0){isValid = true}
                        }
                      }
                      isValid
                    }
                    */
                    isValidRemittanceinfoUnstructured = {
                      var isValid: Boolean = false
                      if (remittanceinformationunstructured.replace(" ","").length > 0 && remittanceinformationunstructured.replace(" ","").length <= 140){
                        isValid = true
                      }
                      isValid
                    }

                    isValidPurposeCode = {
                      var isValid: Boolean = false
                      if (purposeinformationpurposecode.length > 0 && purposeinformationpurposecode.length <= 35){
                        isValid = true
                      }
                      isValid
                    }

                    if (isValidMessageReference && isValidTransactionReference && !isMatchingReference && isValidSchemeName && isValidAmount && isValidDebitAccount && isValidDebitAccountName && isValidDebitPhoneNumber && isValidCreditAccount && isValidCreditAccountName && isValidCreditBankCode && isValidDebtorName && isValidRemittanceinfoUnstructured && isValidPurposeCode){
                      isValidInputData = true
                      /*
                      myHttpStatusCode = HttpStatusCode.Accepted //TESTS ONLY
                      responseCode = 0
                      responseMessage = "Message accepted for processing."
                      println("messageidentification - " + messageidentification + ", paymentendtoendidentification - " + paymentendtoendidentification + ", totalinterbanksettlementamount - " + totalinterbanksettlementamount +
                        ", debtoraccountinformationdebtoraccountidentification - " + debtoraccountinformationdebtoraccountidentification + ", creditoraccountinformationcreditoraccountidentification - " + creditoraccountinformationcreditoraccountidentification)
                      */  
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
                      if (isValidInputData){
                        var isAccSchemeName: Boolean = false
                        val schemeName: String = {
                          var scheme: String = ""
                          if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(accSchemeName)){
                            scheme = accSchemeName
                            isAccSchemeName = true
                          }
                          else if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(phneSchemeName)){
                            scheme = phneSchemeName
                          }

                          scheme

                        }

                        creditoraccountinformationcreditoraccountschemename = schemeName

                        val transferDefaultInfo = TransferDefaultInfo(firstAgentIdentification, assigneeAgentIdentification, chargeBearer, settlementMethod, clearingSystem, serviceLevel, localInstrumentCode, categoryPurpose)
                        val debitcontactinformation = ContactInfo(debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber)
                        val debitAccountInfo = DebitAccountInfo(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, debitcontactinformation, SchemeName.ACC.toString.toUpperCase)
                        val creditcontactinformation = ContactInfo(creditorinformationcreditorname, creditorinformationcreditorcontactphonenumber)
                        val creditAccountInfo = CreditAccountInfo(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoraccountinformationcreditoraccountschemename, creditoragentinformationfinancialInstitutionIdentification, creditcontactinformation)
                        val purposeInfo = TransferPurposeInfo(purposeinformationpurposecode)
                        val remittanceInfo = TransferRemittanceInfo(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
                        val mandateInfo = TransferMandateInfo(mandateidentification)
                        val paymentdata = CreditTransferPaymentInfo(paymentendtoendidentification, interbanksettlementamount, debitAccountInfo, creditAccountInfo, mandateInfo, remittanceInfo, purposeInfo, transferDefaultInfo)
                        val singleCreditTransferPaymentInfo = SingleCreditTransferPaymentInfo(messageidentification, creationDateTime, numberoftransactions, totalinterbanksettlementamount, paymentdata)
                        /*  
                        val f = Future {
                          //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                          val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo, isAccSchemeName)
                          sendSingleCreditTransferRequestsIpsl(myID, myRespData)
                        }  
                        */
                        val myBatchSize: Integer = 1
                        val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                        val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                        val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())
                        val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                          SingleCreditTransferPaymentTableDetails(myBatchReference, 
                          debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
                          messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
                          amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
                          creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
                          remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
                          chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
                          myBatchSize, strRequestData, dateFromCbsApi, strClientIP)

                        val myTableResponseDetails = addOutgoingSingleCreditTransferPaymentDetails(mySingleCreditTransferPaymentTableDetails, strChannelType, strChannelCallBackUrl)
                        myID = myTableResponseDetails.id
                        responseCode = myTableResponseDetails.responsecode
                        responseMessage = myTableResponseDetails.responsemessage
                        //println("myID - " + myID)
                        //println("responseCode - " + responseCode)
                        //println("responseMessage - " + responseMessage)
                        if (responseCode == 0){
                          myHttpStatusCode = HttpStatusCode.Accepted
                          responseMessage = "Message accepted for processing."

                          val f = Future {
                            //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                            val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo, isAccSchemeName)
                            sendSingleCreditTransferRequestsIpsl(myID, myRespData, strOutgoingSingleCreditTransferUrlIpsl)
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
                    //})

                    try{
                      if (isValidInputData){
                        /*
                        val myBatchSize: Integer = 1
                        val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                        val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                        val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())
                        val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                          SingleCreditTransferPaymentTableDetails(myBatchReference, 
                          debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
                          messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
                          amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
                          creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
                          remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
                          chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
                          myBatchSize, strRequestData, dateFromCbsApi, strClientIP)

                        val myTableResponseDetails = addOutgoingSingleCreditTransferPaymentDetails(mySingleCreditTransferPaymentTableDetails)
                        myID = myTableResponseDetails.id
                        responseCode = myTableResponseDetails.responsecode
                        responseMessage = myTableResponseDetails.responsemessage
                        //println("myID - " + myID)
                        //println("responseCode - " + responseCode)
                        //println("responseMessage - " + responseMessage)
                        if (responseCode == 0){
                          myHttpStatusCode = HttpStatusCode.Accepted
                          responseMessage = "Message accepted for processing."

                          val f = Future {
                            //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                            val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo, isAccSchemeName)
                            sendSingleCreditTransferRequestsIpsl(myID, myRespData)
                          }

                        }
                        */
                      }
                      else{
                        responseMessage = "Invalid Input Data length"
                        if (!isValidMessageReference){
                          responseMessage = "Invalid Input Data. messagereference"
                        }
                        else if (!isValidTransactionReference){
                          responseMessage = "Invalid Input Data. transactionreference"
                        }
                        else if (isMatchingReference){
                          responseMessage = "Invalid Input Data. messagereference and transactionreference should have different values"
                        }
                        else if (!isValidSchemeName){
                          responseMessage = "Invalid Input Data. schemename"
                        }
                        else if (!isValidAmount){
                          responseMessage = "Invalid Input Data. amount"
                        }
                        else if (!isValidDebitAccount){
                          responseMessage = "Invalid Input Data. debitaccountnumber"
                        }
                        else if (!isValidDebitAccountName){
                          responseMessage = "Invalid Input Data. debitaccountname"
                        }
                        else if (!isValidDebitPhoneNumber){
                          responseMessage = "Invalid Input Data. debit phonenumber"
                        }
                        else if (!isValidCreditAccount){
                          responseMessage = "Invalid Input Data. creditaccountnumber"
                        }
                        else if (!isValidCreditAccountName){
                          responseMessage = "Invalid Input Data. creditaccountname"
                        }
                        else if (!isValidCreditBankCode){
                          responseMessage = "Invalid Input Data. credit bankcode"
                        }
                        else if (!isValidDebtorName){
                          responseMessage = "Invalid Input Data. debit fullnames"
                        }
                        else if (!isValidRemittanceinfoUnstructured){
                          responseMessage = "Invalid Input Data. remittanceinformation - unstructured"
                        }
                        else if (!isValidPurposeCode){
                          responseMessage = "Invalid Input Data. purposeinformation - purposecode"
                        }
                        /*
                        else if (!isValidCreditPhoneNumber){
                          responseMessage = "Invalid Input Data. credit phonenumber"
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
          insertApiValidationRequests(strChannelType, strUserName, strPassword, strClientIP, strApifunction, responseCode, responseMessage)
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
      
      implicit val  SingleCreditTransferPaymentDetailsResponse_Writes = Json.writes[SingleCreditTransferPaymentDetailsResponse]

      val mySingleCreditTransferPaymentResponse =  SingleCreditTransferPaymentDetailsResponse(responseCode, responseMessage)
      val jsonResponse = Json.toJson(mySingleCreditTransferPaymentResponse)

      try{
        val dateToCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        var isSuccessful: Boolean = false
        val myCode: Int = {
          myHttpStatusCode match {
            case HttpStatusCode.Accepted =>
              isSuccessful = true
              202
            case HttpStatusCode.BadRequest =>
              400
            case HttpStatusCode.Unauthorized =>
              401
            case _ =>
              400
          }
        }
        if (isSuccessful){
          val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [HttpStatusCode_CbsApi_In] = " + myCode + ", [ResponseMessage_CbsApi_In] = '" + jsonResponse.toString() + "', [Date_to_CbsApi_In] = '" + dateToCbsApi + "' where [ID] = " + myID + ";"
          //println("strSQL - " + strSQL)
          insertUpdateRecord(strSQL)
        }
        else{
          val myBatchSize: Integer = 1
          val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
          val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
          val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())

          var strRequestData: String = ""

          if (strRequest != null && strRequest != None){
            strRequest = strRequest.trim
            if (strRequest.length > 0){
              strRequestData = strRequest.replace("'","")//Remove apostrophe
              strRequestData = strRequestData.trim
            }
          }
          
          val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
            SingleCreditTransferPaymentTableDetails(myBatchReference, 
            debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
            messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
            amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
            creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
            remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
            chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
            myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
          //println("jsonResponse + " + jsonResponse.toString())
          addOutgoingSingleCreditTransferPaymentDetailsArchive(responseCode, responseMessage, jsonResponse.toString(), mySingleCreditTransferPaymentTableDetails, strChannelType, strChannelCallBackUrl)
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
      val dateFromCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      val startDate: String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var entryID: Int = 0
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      //var myS2B_PaymentDetailsResponse_BatchData: Seq[S2B_PaymentDetailsResponse_Batch] = Seq.empty[S2B_PaymentDetailsResponse_Batch]
      val strApifunction: String = "addbulkcredittransferpaymentdetails"
      var myHttpStatusCode = HttpStatusCode.BadRequest
      var isValidMessageReference: Boolean = false
      var isValidTransactionReference: Boolean = false
      var isMatchingReference: Boolean = false
      var isValidSchemeName: Boolean = false
      var isValidAmount: Boolean = false
      var isValidDebitAccount: Boolean = false
      var isValidDebitAccountName: Boolean = false
      var isValidDebtorName: Boolean = false
      var isValidDebitPhoneNumber: Boolean = false
      var isValidCreditAccount: Boolean = false
      var isValidCreditAccountName: Boolean = false
      var isValidCreditBankCode: Boolean = false
      //var isValidCreditPhoneNumber: Boolean = false
      var isValidRemittanceinfoUnstructured: Boolean = false
      var isValidPurposeCode: Boolean = false
      var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
      var strClientIP: String = ""
      var strChannelType: String = ""
      var strChannelCallBackUrl: String = ""
      var strRequest: String = ""

      var myAmount: BigDecimal = 0
      var strAmount: String = ""

      var messageidentification: String = ""

      var paymentendtoendidentification: String = ""
      var interbanksettlementamount: BigDecimal = 0
      var totalinterbanksettlementamount: BigDecimal = 0
      var mandateidentification: String = ""
      //debtor
      var debtorinformationdebtorname: String = ""
      //val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
      var debtorinformationdebtorcontactphonenumber: String = ""
      var debtoraccountinformationdebtoraccountidentification: String = ""
      //val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
      var debtoraccountinformationdebtoraccountname: String = ""
      //val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
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

      try
      {
        //var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound: Boolean = false
        var isAuthTokenFound: Boolean = false
        var isCredentialsFound: Boolean = false
        //var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        //var strClientIP : String = ""

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

          if (request.headers.get("ChannelCallBackUrl") != None){
            val myheaderChannelType = request.headers.get("ChannelCallBackUrl")
            if (myheaderChannelType.get != None){
              strChannelCallBackUrl = myheaderChannelType.get.toString
              if (strChannelCallBackUrl != null){
                strChannelCallBackUrl = strChannelCallBackUrl.trim
              }
              else{
                strChannelCallBackUrl = ""
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
            if (!isCredentialsFound){strPassword = ""}

            val myOutput = validateClientApi(strChannelType, strUserName, strPassword, strClientIP, strApifunction)
            if (myOutput.responsecode != null){
              responseCode = myOutput.responsecode
            }

            if (myOutput.responsemessage != null){
              responseMessage = myOutput.responsemessage
            }
            else{
              responseMessage = "Error occured during processing, please try again."
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
              (JsPath \ "fullnames").readNullable[JsValue] and
                (JsPath \ "phonenumber").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(ContactInformation.apply _)

            implicit val DebitAccountInformation_Reads: Reads[DebitAccountInformation] = (
              (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "debitaccountname").readNullable[JsValue] and
                (JsPath \ "debitcontactinformation").readNullable[ContactInformation]
              )(DebitAccountInformation.apply _)

            implicit val CreditAccountInformation_Reads: Reads[CreditAccountInformation] = (
              (JsPath \ "creditaccountnumber").readNullable[JsValue] and
                (JsPath \ "creditaccountname").readNullable[JsValue] and
                (JsPath \ "schemename").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue] and
                (JsPath \ "creditcontactinformation").readNullable[ContactInformation]
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
                (JsPath \ "debitaccountinformation").readNullable[DebitAccountInformation] and
                (JsPath \ "creditaccountinformation").readNullable[CreditAccountInformation] and
                (JsPath \ "mandateinformation").readNullable[TransferMandateInformation] and
                (JsPath \ "remittanceinformation").readNullable[TransferRemittanceInformation] and
                (JsPath \ "purposeinformation").readNullable[TransferPurposeInformation]
              )(CreditTransferPaymentInformation.apply _)

            implicit val BulkCreditTransferPaymentDetails_Request_Reads: Reads[BulkCreditTransferPaymentDetails_Request] = (
              (JsPath \ "messagereference").readNullable[JsValue] and
                (JsPath \ "paymentdata").read[Seq[CreditTransferPaymentInformation]]
                //(JsPath \ "paymentdata").readNullable[Seq[CreditTransferPaymentInformation]]
              )(BulkCreditTransferPaymentDetails_Request.apply _)

            myjson.validate[BulkCreditTransferPaymentDetails_Request] match {
              case JsSuccess(myPaymentDetails_BatchRequest, _) => {

                var isValidInputData: Boolean = false
                var myBatchSize: Integer = 0
                var strBatchReference: String = ""
                var strRequestData: String = ""
                var paymentdata = Seq[BulkPaymentInfo]()

                try
                {
                  entryID = 0
                  myBatchSize = myPaymentDetails_BatchRequest.paymentdata.length
                  strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  //val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{
                    /*
                    var myAmount: BigDecimal = 0
                    var strAmount: String = ""

                    var messageidentification: String = ""

                    var paymentendtoendidentification: String = ""
                    var interbanksettlementamount: BigDecimal = 0
                    var totalinterbanksettlementamount: BigDecimal = 0
                    var mandateidentification: String = ""
                    //debtor
                    var debtorinformationdebtorname: String = ""
                    //val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
                    var debtorinformationdebtorcontactphonenumber: String = ""
                    var debtoraccountinformationdebtoraccountidentification: String = ""
                    //val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
                    var debtoraccountinformationdebtoraccountname: String = ""
                    //val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
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
                    */
                    //default values
                    //val instructingagentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
                    //val instructedagentinformationfinancialInstitutionIdentification: String = assigneeAgentIdentification //i.e IPSL
                    //val initiatingpartyinformationorganisationidentification: String = firstAgentIdentification
                    //val chargebearer: String = chargeBearer
                    //val settlementmethod: String = settlementMethod
                    //val clearingsystem: String = clearingSystem
                    //val servicelevel: String = serviceLevel
                    //val localinstrumentcode: String = localInstrumentCode
                    //val categorypurpose: String = categoryPurpose

                    //val creationDateTime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    val t1: String =  new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    val t2: String =  new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date)
                    val creationDateTime: String = t1 + "T" + t2+ "Z"
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
                    var numberoftransactions: Int = 0
                    var isValidPaymentdata = false
                    var isAccSchemeName: Boolean = false
                    //val acceptancedatetime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)

                    val accSchemeName: String = SchemeName.ACC.toString.toUpperCase
                    val phneSchemeName: String = SchemeName.PHNE.toString.toUpperCase

                    if (strRequest != null && strRequest != None){
                      strRequest = strRequest.trim
                      if (strRequest.length > 0){
                        strRequestData = strRequest.replace("'","")//Remove apostrophe
                        strRequestData = strRequestData.trim
                      }
                    }

                    messageidentification = ""
                    totalinterbanksettlementamount = 0
                    isValidMessageReference = false

                    //messageidentification
                    if (myPaymentDetails_BatchRequest.messagereference != None) {
                      if (myPaymentDetails_BatchRequest.messagereference.get != None) {
                        val myData = myPaymentDetails_BatchRequest.messagereference.get
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

                    isValidPaymentdata = {
                      var isValid: Boolean = false
                      if (myBatchSize > 0){
                        isValid = true
                      }  
                      isValid
                    }

                    myPaymentDetails_BatchRequest.paymentdata.foreach(myPaymentDetails => {

                      numberoftransactions = numberoftransactions + 1

                      myAmount = 0
                      strAmount = ""
                      //messageidentification = ""

                      paymentendtoendidentification = ""
                      interbanksettlementamount = 0
                      //totalinterbanksettlementamount = 0
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

                      //isValidMessageReference = false
                      isValidTransactionReference = false
                      isMatchingReference = false
                      isValidSchemeName = false
                      isValidAmount = false
                      isValidDebitAccount = false
                      isValidDebitAccountName = false
                      isValidDebtorName = false
                      isValidDebitPhoneNumber = false
                      isValidCreditAccount = false
                      isValidCreditAccountName = false
                      isValidCreditBankCode = false
                      //isValidCreditPhoneNumber = false
                      isValidRemittanceinfoUnstructured = false
                      isValidPurposeCode = false

                      try{
                        //var isValidPaymentdata = false

                        //messageidentification
                        /*
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
                        */
                        /*
                        isValidPaymentdata = {
                          var isValid: Boolean = false
                          if (myPaymentDetails.paymentdata != None){
                            if (myPaymentDetails.paymentdata.get != None){
                              isValid = true
                            }
                          }  
                          isValid
                        }
                        */
                        //paymentendtoendidentification
                        /*
                        if (myPaymentDetails.paymentdata != None) {
                          if (myPaymentDetails.paymentdata.get != None) {
                            val myData = myPaymentDetails.paymentdata.get
                            paymentendtoendidentification = myData.transactionreference.getOrElse("").toString()
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
                        */
                        if (isValidPaymentdata){
                          //val myData = myPaymentDetails.paymentdata.get
                          //paymentendtoendidentification = myData.transactionreference.getOrElse("").toString()
                          paymentendtoendidentification = myPaymentDetails.transactionreference.getOrElse("").toString()
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
                        
                        //strAmount
                        if (isValidPaymentdata){
                          //val myData = myPaymentDetails.paymentdata.get
                            strAmount = myPaymentDetails.amount.getOrElse("").toString()
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
                                    totalinterbanksettlementamount = totalinterbanksettlementamount + myAmount
                                  }
                                }
                              }
                            }
                        }
                        
                        //mandateidentification
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.mandateinformation != None){
                              if (myPaymentDetails.mandateinformation.get != None){true}
                              else {false}
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.mandateinformation.get

                            mandateidentification = myData2.mandateidentification.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          var isValid: Boolean = false
                          val isValid1 = {
                            if (myPaymentDetails.debitaccountinformation != None){
                              if (myPaymentDetails.debitaccountinformation.get != None){true}  
                              else {false} 
                            }  
                            else {false}
                          }
                          if (isValid1){
                            val myVar1 = myPaymentDetails.debitaccountinformation.get
                            if (myVar1.debitcontactinformation != None){
                              if (myVar1.debitcontactinformation.get != None){isValid = true}
                            }  
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.debitaccountinformation.get
                            val myData3 = myData2.debitcontactinformation.get
                            debtorinformationdebtorname = myData3.fullnames.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          var isValid: Boolean = false
                          val isValid1 = {
                            if (myPaymentDetails.debitaccountinformation != None){
                              if (myPaymentDetails.debitaccountinformation.get != None){true}  
                              else {false} 
                            }  
                            else {false}
                          }
                          if (isValid1){
                            val myVar1 = myPaymentDetails.debitaccountinformation.get
                            if (myVar1.debitcontactinformation != None){
                              if (myVar1.debitcontactinformation.get != None){isValid = true}
                            }  
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.debitaccountinformation.get
                            val myData3 = myData2.debitcontactinformation.get
                            debtorinformationdebtorcontactphonenumber = myData3.phonenumber.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.debitaccountinformation != None){
                              if (myPaymentDetails.debitaccountinformation.get != None){true}
                              else {false}
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.debitaccountinformation.get
                            debtoraccountinformationdebtoraccountidentification = myData2.debitaccountnumber.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.debitaccountinformation != None){
                              if (myPaymentDetails.debitaccountinformation.get != None){true}
                              else {false}
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.debitaccountinformation.get
                            debtoraccountinformationdebtoraccountname = myData2.debitaccountname.getOrElse("").toString()
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
                        /*
                        if (isValidPaymentdata){
                          val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myData1.creditaccountinformation != None){
                              if (myData1.creditaccountinformation.get != None){true} 
                              else {false}  
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myData1.creditaccountinformation.get
                            creditorinformationcreditorname = myData2.creditaccountname.getOrElse("").toString()
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
                        */
                        
                        //creditorinformationcreditorcontactphonenumber
                        /*
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
                        */
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          var isValid: Boolean = false
                          val isValid1 = {
                            if (myPaymentDetails.creditaccountinformation != None){
                              if (myPaymentDetails.creditaccountinformation.get != None){true}  
                              else {false} 
                            }  
                            else {false}
                          }
                          if (isValid1){
                            val myVar1 = myPaymentDetails.creditaccountinformation.get
                            if (myVar1.creditcontactinformation != None){
                              if (myVar1.creditcontactinformation.get != None){isValid = true}
                            }  
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.creditaccountinformation.get
                            val myData3 = myData2.creditcontactinformation.get
                            //creditorinformationcreditorname
                            creditorinformationcreditorname = myData3.fullnames.getOrElse("").toString()
                            if (creditorinformationcreditorname != null && creditorinformationcreditorname != None){
                              creditorinformationcreditorname = creditorinformationcreditorname.trim
                              if (creditorinformationcreditorname.length > 0){
                                creditorinformationcreditorname = creditorinformationcreditorname.replace("'","")//Remove apostrophe
                                creditorinformationcreditorname = creditorinformationcreditorname.replace("  "," ")//Remove double spaces
                                creditorinformationcreditorname = creditorinformationcreditorname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                creditorinformationcreditorname = creditorinformationcreditorname.trim
                              }
                            }

                            //creditorinformationcreditorcontactphonenumber
                            creditorinformationcreditorcontactphonenumber = myData3.phonenumber.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.creditaccountinformation != None){
                              if (myPaymentDetails.creditaccountinformation.get != None){true} 
                              else {false}  
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.creditaccountinformation.get
                            creditoraccountinformationcreditoraccountidentification = myData2.creditaccountnumber.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.creditaccountinformation != None){
                              if (myPaymentDetails.creditaccountinformation.get != None){true}   
                              else {false}
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.creditaccountinformation.get
                            //println("myData1: " + myData1)
                            //println("myData2: " + myData2)
                            creditoraccountinformationcreditoraccountschemename = myData2.schemename.getOrElse("").toString()
                            //println("creditoraccountinformationcreditoraccountschemename: " + creditoraccountinformationcreditoraccountschemename)
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.creditaccountinformation != None){
                              if (myPaymentDetails.creditaccountinformation.get != None){true}
                              else {false}
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.creditaccountinformation.get
                            creditoraccountinformationcreditoraccountname = myData2.creditaccountname.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.creditaccountinformation != None){
                              if (myPaymentDetails.creditaccountinformation.get != None){true} 
                              else {false}  
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.creditaccountinformation.get
                            creditoragentinformationfinancialInstitutionIdentification = myData2.bankcode.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.purposeinformation != None){
                              if (myPaymentDetails.purposeinformation.get != None){true} 
                              else {false}  
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.purposeinformation.get
                            purposeinformationpurposecode = myData2.purposecode.getOrElse("").toString()
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
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.remittanceinformation != None){
                              if (myPaymentDetails.remittanceinformation.get != None){true}   
                              else {false}
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.remittanceinformation.get
                            remittanceinformationunstructured = myData2.unstructured.getOrElse("").toString()
                            if (remittanceinformationunstructured != null && remittanceinformationunstructured != None){
                              remittanceinformationunstructured = remittanceinformationunstructured.trim
                              if (remittanceinformationunstructured.length > 0){
                                remittanceinformationunstructured = remittanceinformationunstructured.replace("'","")//Remove apostrophe
                                remittanceinformationunstructured = remittanceinformationunstructured.replace("  "," ")//Remove double spaces
                                remittanceinformationunstructured = remittanceinformationunstructured.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                                remittanceinformationunstructured = remittanceinformationunstructured.trim
                              }
                            }
                          }
                        }

                        //remittanceinformationtaxremittancereferencenumber
                        if (isValidPaymentdata){
                          //val myData1 = myPaymentDetails.paymentdata.get
                          val isValid = {
                            if (myPaymentDetails.remittanceinformation != None){
                              if (myPaymentDetails.remittanceinformation.get != None){true} 
                              else {false}  
                            }  
                            else {false}
                          }
                          if (isValid){
                            val myData2 = myPaymentDetails.remittanceinformation.get
                            remittanceinformationtaxremittancereferencenumber = myData2.taxremittancereferencenumber.getOrElse("").toString()
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
                      isValidSchemeName = {
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

                      isValidMessageReference = {
                        var isValid: Boolean = false
                        if (messageidentification.length > 0 && messageidentification.length <= 35){
                          val isNumeric: Boolean = messageidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myMessageidentification = BigDecimal(messageidentification)
                            if (myMessageidentification > 0){isValid = true}
                          }
                          else{
                            isValid = true
                          }
                        }
                        isValid
                      }

                      isValidTransactionReference = {
                        var isValid: Boolean = false
                        if (paymentendtoendidentification.length > 0 && paymentendtoendidentification.length <= 35){
                          val isNumeric: Boolean = paymentendtoendidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myPaymentendtoendidentification = BigDecimal(paymentendtoendidentification)
                            if (myPaymentendtoendidentification > 0){isValid = true}
                          }
                          else{
                            isValid = true
                          }
                        }
                        isValid
                      }

                      isMatchingReference = {
                        var isValid: Boolean = false
                        if (isValidMessageReference && isValidTransactionReference) {
                          if (messageidentification.equalsIgnoreCase(paymentendtoendidentification)){
                            isValid = true  
                          }  
                        }
                        isValid
                      }

                      isValidAmount = {
                        if (myAmount > 0){true}
                        else {false}
                      }

                      isValidDebitAccount = {
                        var isValid: Boolean = false
                        if (debtoraccountinformationdebtoraccountidentification.length > 0 && debtoraccountinformationdebtoraccountidentification.length <= 35){
                          val isNumeric: Boolean = debtoraccountinformationdebtoraccountidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myDebtoraccount = BigDecimal(debtoraccountinformationdebtoraccountidentification)
                            if (myDebtoraccount > 0){isValid = true}
                          }
                          else{
                            isValid = true
                          }
                        }
                        isValid
                      }

                      isValidDebitAccountName = {
                        var isValid: Boolean = false
                        if (debtoraccountinformationdebtoraccountname.replace("  ","").length > 0 && debtoraccountinformationdebtoraccountname.replace("  ","").length <= 70){
                          isValid = true
                        }
                        isValid
                      }

                      isValidDebtorName  = {
                        var isValid: Boolean = false
                        if (debtorinformationdebtorname.replace("  ","").length > 0 && debtorinformationdebtorname.replace("  ","").length <= 140){
                          isValid = true
                        }
                        isValid
                      }

                      isValidDebitPhoneNumber = {
                        var isValid: Boolean = false
                        //if (debtorinformationdebtorcontactphonenumber.length == 10 || debtorinformationdebtorcontactphonenumber.length == 12){
                        if (debtorinformationdebtorcontactphonenumber.length == 14){
                          /*
                          val isNumeric: Boolean = debtorinformationdebtorcontactphonenumber.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myPhonenumber = BigDecimal(debtorinformationdebtorcontactphonenumber)
                            if (myPhonenumber > 0){isValid = true}
                          }
                          */
                          isValid = true
                        }
                        isValid
                      }

                      isValidCreditAccount = {
                        var isValid: Boolean = false
                        if (creditoraccountinformationcreditoraccountidentification.length > 0 && creditoraccountinformationcreditoraccountidentification.length <= 35){
                          val isNumeric: Boolean = creditoraccountinformationcreditoraccountidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myCreditoraccount = BigDecimal(creditoraccountinformationcreditoraccountidentification)
                            if (myCreditoraccount > 0){isValid = true}
                          }
                          else{
                            isValid = true
                          }
                        }
                        isValid
                      }

                      isValidCreditAccountName = {
                        var isValid: Boolean = false
                        if (creditoraccountinformationcreditoraccountname.replace("  ","").length > 0 && creditoraccountinformationcreditoraccountname.replace("  ","").length <= 140){
                          isValid = true
                        }
                        isValid
                      }

                      isValidCreditBankCode = {
                        var isValid: Boolean = false
                        if (creditoragentinformationfinancialInstitutionIdentification.length > 0 && creditoragentinformationfinancialInstitutionIdentification.length <= 35){
                          val isNumeric: Boolean = creditoragentinformationfinancialInstitutionIdentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myCreditoragent = BigDecimal(creditoragentinformationfinancialInstitutionIdentification)
                            if (myCreditoragent > 0){isValid = true}
                          }
                        }
                        isValid
                      }
                      /*
                      isValidCreditPhoneNumber = {
                        var isValid: Boolean = false
                        if (creditorinformationcreditorcontactphonenumber.length == 10 || creditorinformationcreditorcontactphonenumber.length == 12){
                          val isNumeric: Boolean = creditorinformationcreditorcontactphonenumber.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                          if (isNumeric){
                            val myPhonenumber = creditorinformationcreditorcontactphonenumber.toInt
                            if (myPhonenumber > 0){isValid = true}
                          }
                        }
                        isValid
                      }
                      */
                      isValidRemittanceinfoUnstructured = {
                        var isValid: Boolean = false
                        if (remittanceinformationunstructured.replace(" ","").length > 0 && remittanceinformationunstructured.replace(" ","").length <= 140){
                          isValid = true
                        }
                        isValid
                      }

                      isValidPurposeCode = {
                        var isValid: Boolean = false
                        if (purposeinformationpurposecode.length > 0 && purposeinformationpurposecode.length <= 35){
                          isValid = true
                        }
                        isValid
                      }

                      if (isValidMessageReference && isValidTransactionReference && !isMatchingReference && isValidSchemeName && isValidAmount && isValidDebitAccount && isValidDebitAccountName && isValidDebitPhoneNumber && isValidCreditAccount && isValidCreditAccountName && isValidCreditBankCode && isValidDebtorName && isValidRemittanceinfoUnstructured && isValidPurposeCode){
                        isValidInputData = true
                        /*
                        myHttpStatusCode = HttpStatusCode.Accepted //TESTS ONLY
                        responseCode = 0
                        responseMessage = "Message accepted for processing."
                        println("messageidentification - " + messageidentification + ", paymentendtoendidentification - " + paymentendtoendidentification + ", totalinterbanksettlementamount - " + totalinterbanksettlementamount +
                          ", debtoraccountinformationdebtoraccountidentification - " + debtoraccountinformationdebtoraccountidentification + ", creditoraccountinformationcreditoraccountidentification - " + creditoraccountinformationcreditoraccountidentification)
                        */  
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
                        if (isValidInputData){
                          //var isAccSchemeName: Boolean = false
                          val schemeName: String = {
                            var scheme: String = ""
                            if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(accSchemeName)){
                              scheme = accSchemeName
                              isAccSchemeName = true
                            }
                            else if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(phneSchemeName)){
                              scheme = phneSchemeName
                            }

                            scheme

                          }

                          creditoraccountinformationcreditoraccountschemename = schemeName

                          //val transferDefaultInfo = TransferDefaultInfo(firstAgentIdentification, assigneeAgentIdentification, chargeBearer, settlementMethod, clearingSystem, serviceLevel, localInstrumentCode, categoryPurpose)
                          val debitcontactinformation = ContactInfo(debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber)
                          val debitAccountInfo = DebitAccountInfo(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, debitcontactinformation, SchemeName.ACC.toString.toUpperCase)
                          val creditcontactinformation = ContactInfo(creditorinformationcreditorname, creditorinformationcreditorcontactphonenumber)
                          val creditAccountInfo = CreditAccountInfo(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoraccountinformationcreditoraccountschemename, creditoragentinformationfinancialInstitutionIdentification, creditcontactinformation)
                          val purposeInfo = TransferPurposeInfo(purposeinformationpurposecode)
                          val remittanceInfo = TransferRemittanceInfo(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
                          val mandateInfo = TransferMandateInfo(mandateidentification)
                          //val paymentdata = BulkPaymentInfo(paymentendtoendidentification, interbanksettlementamount, debitAccountInfo, creditAccountInfo, mandateInfo, remittanceInfo, purposeInfo)
                          val bulkPaymentInfo = BulkPaymentInfo(paymentendtoendidentification, interbanksettlementamount, debitAccountInfo, creditAccountInfo, mandateInfo, remittanceInfo, purposeInfo)
                          //var paymentdata = Seq[BulkPaymentInfo]()
                          paymentdata = paymentdata :+ bulkPaymentInfo 
                          //val bulkCreditTransferPaymentInfo = BulkCreditTransferPaymentInfo(messageidentification, creationDateTime, numberoftransactions, totalinterbanksettlementamount, transferDefaultInfo, paymentdata)
                          /*  
                          val f = Future {
                            //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                            val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo, isAccSchemeName)
                            sendSingleCreditTransferRequestsIpsl(myID, myRespData)
                          }  
                          */
                          //val myBatchSize: Integer = 1
                          //val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                          val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                          val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())
                          val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                            SingleCreditTransferPaymentTableDetails(myBatchReference, 
                            debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
                            messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
                            amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
                            creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
                            remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
                            chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
                            myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                          /*
                          val myTableResponseDetails = addOutgoingSingleCreditTransferPaymentDetails(mySingleCreditTransferPaymentTableDetails, strChannelType, strChannelCallBackUrl)
                          myID = myTableResponseDetails.id
                          responseCode = myTableResponseDetails.responsecode
                          responseMessage = myTableResponseDetails.responsemessage
                          */
                          //println("myID - " + myID)
                          //println("responseCode - " + responseCode)
                          //println("responseMessage - " + responseMessage)
                          /*
                          if (responseCode == 0){
                            myHttpStatusCode = HttpStatusCode.Accepted
                            responseMessage = "Message accepted for processing."

                            val f = Future {
                              //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                              val myRespData: String = getBulkCreditTransferDetails(bulkCreditTransferPaymentInfo, isAccSchemeName)
                              sendSingleCreditTransferRequestsIpsl(myID, myRespData, strOutgoingSingleCreditTransferUrlIpsl)
                            }
                          }
                          */
                        }
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
                        val transferDefaultInfo = TransferDefaultInfo(firstAgentIdentification, assigneeAgentIdentification, chargeBearer, settlementMethod, clearingSystem, serviceLevel, localInstrumentCode, categoryPurpose)
                        val bulkCreditTransferPaymentInfo = BulkCreditTransferPaymentInfo(messageidentification, creationDateTime, numberoftransactions, totalinterbanksettlementamount, transferDefaultInfo, paymentdata)

                        if (responseCode == 0){
                          myHttpStatusCode = HttpStatusCode.Accepted
                          responseMessage = "Message accepted for processing."

                          val f = Future {
                            //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                            val myRespData: String = getBulkCreditTransferDetails(bulkCreditTransferPaymentInfo, isAccSchemeName)
                            sendSingleCreditTransferRequestsIpsl(myID, myRespData, strOutgoingSingleCreditTransferUrlIpsl)
                          }
                        }
                      }
                      else{
                        responseMessage = "Invalid Input Data length"
                        if (!isValidMessageReference){
                          responseMessage = "Invalid Input Data. messagereference"
                        }
                        else if (!isValidTransactionReference){
                          responseMessage = "Invalid Input Data. transactionreference"
                        }
                        else if (isMatchingReference){
                          responseMessage = "Invalid Input Data. messagereference and transactionreference should have different values"
                        }
                        else if (!isValidSchemeName){
                          responseMessage = "Invalid Input Data. schemename"
                        }
                        else if (!isValidAmount){
                          responseMessage = "Invalid Input Data. amount"
                        }
                        else if (!isValidDebitAccount){
                          responseMessage = "Invalid Input Data. debitaccountnumber"
                        }
                        else if (!isValidDebitAccountName){
                          responseMessage = "Invalid Input Data. debitaccountname"
                        }
                        else if (!isValidDebitPhoneNumber){
                          responseMessage = "Invalid Input Data. debit phonenumber"
                        }
                        else if (!isValidCreditAccount){
                          responseMessage = "Invalid Input Data. creditaccountnumber"
                        }
                        else if (!isValidCreditAccountName){
                          responseMessage = "Invalid Input Data. creditaccountname"
                        }
                        else if (!isValidCreditBankCode){
                          responseMessage = "Invalid Input Data. credit bankcode"
                        }
                        else if (!isValidDebtorName){
                          responseMessage = "Invalid Input Data. debit fullnames"
                        }
                        else if (!isValidRemittanceinfoUnstructured){
                          responseMessage = "Invalid Input Data. remittanceinformation - unstructured"
                        }
                        else if (!isValidPurposeCode){
                          responseMessage = "Invalid Input Data. purposeinformation - purposecode"
                        }
                        /*
                        else if (!isValidCreditPhoneNumber){
                          responseMessage = "Invalid Input Data. credit phonenumber"
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
          insertApiValidationRequests(strChannelType, strUserName, strPassword, strClientIP, strApifunction, responseCode, responseMessage)
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
      
      implicit val  BulkCreditTransferPaymentDetailsResponse_Writes = Json.writes[BulkCreditTransferPaymentDetailsResponse]

      val myBulkCreditTransferPaymentResponse =  BulkCreditTransferPaymentDetailsResponse(responseCode, responseMessage)
      val jsonResponse = Json.toJson(myBulkCreditTransferPaymentResponse)

      try{
        val dateToCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        var isSuccessful: Boolean = false
        val myCode: Int = {
          myHttpStatusCode match {
            case HttpStatusCode.Accepted =>
              isSuccessful = true
              202
            case HttpStatusCode.BadRequest =>
              400
            case HttpStatusCode.Unauthorized =>
              401
            case _ =>
              400
          }
        }
        if (isSuccessful){
          val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [HttpStatusCode_CbsApi_In] = " + myCode + ", [ResponseMessage_CbsApi_In] = '" + jsonResponse.toString() + "', [Date_to_CbsApi_In] = '" + dateToCbsApi + "' where [ID] = " + myID + ";"
          //println("strSQL - " + strSQL)
          //insertUpdateRecord(strSQL)
        }
        else{
          val myBatchSize: Integer = 1
          val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
          val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
          val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())

          var strRequestData: String = ""

          if (strRequest != null && strRequest != None){
            strRequest = strRequest.trim
            if (strRequest.length > 0){
              strRequestData = strRequest.replace("'","")//Remove apostrophe
              strRequestData = strRequestData.trim
            }
          }
          
          val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
            SingleCreditTransferPaymentTableDetails(myBatchReference, 
            debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
            messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
            amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
            creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
            remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
            chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
            myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
          //println("jsonResponse + " + jsonResponse.toString())
          //addOutgoingSingleCreditTransferPaymentDetailsArchive(responseCode, responseMessage, jsonResponse.toString(), mySingleCreditTransferPaymentTableDetails, strChannelType, strChannelCallBackUrl)
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
  def addBulkCreditTransferPaymentDetails_old = Action.async { request =>
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
        var isDataFound: Boolean = false
        var isAuthTokenFound: Boolean = false
        var isCredentialsFound: Boolean = false
        var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        var strClientIP: String = ""

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

      //val jsonResponse = Json.toJson(myPaymentDetailsResponse)
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

      //val myRespData: String = ""//getBulkCreditTransferDetails()
      //val myresponse_processUssdActions = new response_processUssdActions(myData)

      //TESTS ONLY
      //sendBulkCreditTransferRequestsIpsl(myData.toString())
      //val f = Future {sendBulkCreditTransferRequestsIpsl(myRespData)}

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
      val dateFromCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      val startDate: String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      var myHttpStatusCode = HttpStatusCode.BadRequest
      val strApifunction: String = "addaccountverificationdetails"

      var myBankCode: Int = 0
      var strMessageReference: String = ""
      var strTransactionReference: String = ""
      var strAccountNumber: String = ""
      var strSchemeName: String = ""
      var strBankCode: String = ""
      var isValidMessageReference: Boolean = false
      var isValidTransactionReference: Boolean = false
      var isMatchingReference: Boolean = false
      var isValidSchemeName: Boolean = false
      var isValidAccountNumber: Boolean = false
      var isValidBankCode: Boolean = false
	    val accSchemeName: String = SchemeName.ACC.toString.toUpperCase
      val phneSchemeName: String = SchemeName.PHNE.toString.toUpperCase
      var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
      var strClientIP: String = ""
      var strChannelType: String = ""
      var strChannelCallBackUrl: String = ""
      var strRequest: String = ""

      try
      {
        //var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound: Boolean = false
        var isAuthTokenFound: Boolean = false
        var isCredentialsFound: Boolean = false
        //var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        //var strClientIP: String = ""

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

          if (request.headers.get("ChannelCallBackUrl") != None){
            val myheaderChannelType = request.headers.get("ChannelCallBackUrl")
            if (myheaderChannelType.get != None){
              strChannelCallBackUrl = myheaderChannelType.get.toString
              if (strChannelCallBackUrl != null){
                strChannelCallBackUrl = strChannelCallBackUrl.trim
              }
              else{
                strChannelCallBackUrl = ""
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
            if (!isCredentialsFound){strPassword = ""}

            val myOutput = validateClientApi(strChannelType, strUserName, strPassword, strClientIP, strApifunction)
            if (myOutput.responsecode != null){
              responseCode = myOutput.responsecode
            }

            if (myOutput.responsemessage != null){
              responseMessage = myOutput.responsemessage
            }
            else{
              responseMessage = "Error occured during processing, please try again."
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
                (JsPath \ "schemename").readNullable[JsValue] and
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
                //var strBatchReference : String = ""
                var strRequestData : String = ""

                try
                {
                  //myBatchSize = myAccountVerificationDetails_BatchRequest.accountdata.length
                  //strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                  //val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{
                    /*
                    val sourceDataTable = new SQLServerDataTable
                    sourceDataTable.addColumnMetadata("BatchReference", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("DebitAccountNumber", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Prn", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("CustomerReference", java.sql.Types.VARCHAR)
                    sourceDataTable.addColumnMetadata("Amount", java.sql.Types.NUMERIC)
                    sourceDataTable.addColumnMetadata("BatchSize", java.sql.Types.INTEGER)
                    */
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
                    strMessageReference = ""
                    strTransactionReference = ""
                    strAccountNumber = ""
                    strSchemeName = ""
                    strBankCode = ""
                    isValidMessageReference = false
                    isValidTransactionReference = false
                    isMatchingReference = false
                    isValidSchemeName = false
                    isValidAccountNumber = false
                    isValidBankCode = false

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

                      //strSchemeName
                      if (myPaymentDetails.accountdata.schemename != None) {
                        if (myPaymentDetails.accountdata.schemename.get != None) {
                          val myData = myPaymentDetails.accountdata.schemename.get
                          strSchemeName = myData.toString()
                          if (strSchemeName != null && strSchemeName != None){
                            strSchemeName = strSchemeName.trim
                            if (strSchemeName.length > 0){
                              strSchemeName = strSchemeName.replace("'","")//Remove apostrophe
                              strSchemeName = strSchemeName.replace(" ","")//Remove spaces
                              strSchemeName = strSchemeName.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              strSchemeName = strSchemeName.trim
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

					          isValidSchemeName = {
                      var isValid: Boolean = false
                      if (strSchemeName.length == 0 || accSchemeName.length == 0){
                        isValid = false
                      }
                      else if (strSchemeName.equalsIgnoreCase(accSchemeName)){
                        isValid = true
                      }
                      else if (strSchemeName.equalsIgnoreCase(phneSchemeName)){
                        isValid = true
                      }
                      else {
                        isValid = false
                      }
                      isValid
                    }

                    isValidMessageReference = {
                      var isValid: Boolean = false
                      if (strMessageReference.length > 0 && strMessageReference.length <= 35){
                        val isNumeric: Boolean = strMessageReference.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myMessageReference = BigDecimal(strMessageReference)
                          if (myMessageReference > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidTransactionReference = {
                      var isValid: Boolean = false
                      if (strTransactionReference.length > 0 && strTransactionReference.length <= 35){
                        val isNumeric: Boolean = strTransactionReference.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myTransactionReference = BigDecimal(strTransactionReference)
                          if (myTransactionReference > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isMatchingReference = {
                      var isValid: Boolean = false
                      if (isValidMessageReference && isValidTransactionReference) {
                        if (strMessageReference.equalsIgnoreCase(strTransactionReference)){
                          isValid = true  
                        }  
                      }
                      isValid
                    }

                    isValidAccountNumber = {
                      var isValid: Boolean = false
                      if (strAccountNumber.length > 0 && strAccountNumber.length <= 35){
                        isValid = true
                      }
                      isValid
                    }

                    isValidBankCode = {
                      var isValid: Boolean = false
                      if (strBankCode.length > 0 && strBankCode.length <= 35){
                        val isNumeric: Boolean = strBankCode.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          //val myBankCode = strBankCode.toInt
                          val myBankCode = BigDecimal(strBankCode)
                          if (myBankCode > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    /* Lets set var isValidInputData to true if valid data is received from e-Channels/ESB-CBS System */
                    if (isValidMessageReference && isValidTransactionReference && isValidAccountNumber && isValidSchemeName && isValidBankCode && !isMatchingReference){
                      /*
                      isValidInputData = true
                      myHttpStatusCode = HttpStatusCode.Accepted //TESTS ONLY
                      responseCode = 0
                      responseMessage = "Message accepted for processing."
                      */
                      isValidInputData = true
                    }
                    /*
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
                    */
                    try{
                      if (isValidInputData){
                        val myBatchSize: Integer = 1
                        val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                        val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                        val myAccountVerificationTableDetails = AccountVerificationTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                        
                        val myAccountVerificationTableResponseDetails = addOutgoingAccountVerificationDetails(myAccountVerificationTableDetails, strChannelType, strChannelCallBackUrl)
                        myID = myAccountVerificationTableResponseDetails.id
                        responseCode = myAccountVerificationTableResponseDetails.responsecode
                        responseMessage = myAccountVerificationTableResponseDetails.responsemessage
                        //println("myID - " + myID)
                        //println("responseCode - " + responseCode)
                        //println("responseMessage - " + responseMessage)
                        if (responseCode == 0){
                          myHttpStatusCode = HttpStatusCode.Accepted
                          responseMessage = "Message accepted for processing."
                        }
                      }
                      else{
                        responseMessage = "Invalid Input Data length"
                        if (!isValidMessageReference){
                          responseMessage = "Invalid Input Data. messagereference"
                        }
                        else if (!isValidTransactionReference){
                          responseMessage = "Invalid Input Data. transactionreference"
                        }
                        else if (!isValidAccountNumber){
                          responseMessage = "Invalid Input Data. accountnumber"
                        }
                        else if (!isValidSchemeName){
                          responseMessage = "Invalid Input Data. schemename"
                        }
                        else if (!isValidBankCode){
                          responseMessage = "Invalid Input Data. bankcode"
                        }
                        else if (isMatchingReference){
                          responseMessage = "Invalid Input Data. messagereference and transactionreference should have different values"
                        }
                        /*
                        val myBatchSize: Integer = 1
                        val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                        val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                        val myAccountVerificationTableDetails = AccountVerificationTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                        
                        addOutgoingAccountVerificationDetailsArchive(responseCode, responseMessage, myAccountVerificationTableDetails)
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
          insertApiValidationRequests(strChannelType, strUserName, strPassword, strClientIP, strApifunction, responseCode, responseMessage)
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
      
    implicit val  AccountVerificationDetailsResponse_Writes = Json.writes[AccountVerificationDetailsResponse]

    val myAccountVerificationResponse =  AccountVerificationDetailsResponse(responseCode, responseMessage)
    val jsonResponse = Json.toJson(myAccountVerificationResponse)

    try{
      var isAccSchemeName: Boolean = false
      /*
      val t1: String =  new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
      val t2: String =  new SimpleDateFormat("HH:mm:ss").format(new java.util.Date)
      val creationDateTime: String = t1 + "T" + t2
      */
      val t1: String =  new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
      val t2: String =  new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date)
      val creationDateTime: String = t1 + "T" + t2+ "Z"
      val schemeName: String = {
        var scheme: String = ""
          if (strSchemeName.equalsIgnoreCase(accSchemeName)){
            scheme = accSchemeName
            isAccSchemeName = true
          }
          else if (strSchemeName.equalsIgnoreCase(phneSchemeName)){
            scheme = phneSchemeName
          }
          scheme
      }
      
      val isSendRequest = {
        myHttpStatusCode match {
          case HttpStatusCode.Accepted =>
            true
          case _ =>
            false
        }
      }

      if (isSendRequest){
        val accountVerificationDetails = AccountVerificationDetails(strMessageReference, creationDateTime, firstAgentIdentification, assignerAgentIdentification, assigneeAgentIdentification: String, strTransactionReference, strAccountNumber, schemeName, strBankCode)
        //println("schemeName - " + schemeName.toString)
        //println("accountVerificationDetails - " + accountVerificationDetails.toString)
        val f = Future {
          val myRespData: String = getAccountVerificationDetails(accountVerificationDetails, isAccSchemeName)
          sendAccountVerificationRequestsIpsl(myID, myRespData, strOutgoingAccountVerificationUrlIpsl, strChannelType, strChannelCallBackUrl)
        }

        val dateToCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myCode: Int = {
          myHttpStatusCode match {
          case HttpStatusCode.Accepted =>
            202
          case HttpStatusCode.BadRequest =>
            400
          case HttpStatusCode.Unauthorized =>
            401
          case _ =>
            400
          }
        }
        val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [HttpStatusCode_CbsApi_In] = " + myCode + ", [ResponseMessage_CbsApi_In] = '" + jsonResponse.toString() + "', [Date_to_CbsApi_In] = '" + dateToCbsApi + "' where [ID] = " + myID + ";"
        //println("strSQL - " + strSQL)
        insertUpdateRecord(strSQL)
      }
      else{
        try{
          val myBatchSize: Integer = 1
          val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
          val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
          var strRequestData: String = ""

          if (strRequest != null && strRequest != None){
            strRequest = strRequest.trim
            if (strRequest.length > 0){
              strRequestData = strRequest.replace("'","")//Remove apostrophe
              strRequestData = strRequestData.trim
            }
          }

          val myAccountVerificationTableDetails = AccountVerificationTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
          
          addOutgoingAccountVerificationDetailsArchive(responseCode, responseMessage, jsonResponse.toString(), myAccountVerificationTableDetails, strChannelType, strChannelCallBackUrl)
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
    /*
    try{
      val dateToCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      val myCode: Int = {
        myHttpStatusCode match {
        case HttpStatusCode.Accepted =>
          202
        case HttpStatusCode.BadRequest =>
          400
        case HttpStatusCode.Unauthorized =>
          401
        case _ =>
          400
        }
      }
      val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [HttpStatusCode_CbsApi_In] = " + myCode + ", [ResponseMessage_CbsApi_In] = '" + jsonResponse.toString() + "', [Date_to_CbsApi_In] = '" + dateToCbsApi + "' where [ID] = " + myID + ";"
      //println("strSQL - " + strSQL)
      insertUpdateRecord(strSQL)
    }
    catch{
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage())
      case io: IOException =>
        log_errors(strApifunction + " : " + io.getMessage())
      case tr: Throwable =>
        log_errors(strApifunction + " : " + tr.getMessage())
    }
    */
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
  def addPaymentCancellationDetails = Action.async { request =>
    Future {
      val dateFromCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      val startDate: String =  new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new java.util.Date)
      var entryID: Int = 0
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      //var myS2B_PaymentDetailsResponse_BatchData: Seq[S2B_PaymentDetailsResponse_Batch] = Seq.empty[S2B_PaymentDetailsResponse_Batch]
      val strApifunction: String = "addPaymentCancellationDetails"
      var myHttpStatusCode = HttpStatusCode.BadRequest
      var isValidMessageReference: Boolean = false
      var isValidTransactionReference: Boolean = false
      var isMatchingReference: Boolean = false
      var isValidSchemeName: Boolean = false
      var isValidAmount: Boolean = false
      var isValidDebitAccount: Boolean = false
      var isValidDebitAccountName: Boolean = false
      var isValidDebtorName: Boolean = false
      var isValidDebitPhoneNumber: Boolean = false
      var isValidCreditAccount: Boolean = false
      var isValidCreditAccountName: Boolean = false
      var isValidCreditBankCode: Boolean = false
      //var isValidCreditPhoneNumber: Boolean = false
      var isValidRemittanceinfoUnstructured: Boolean = false
      var isValidPurposeCode: Boolean = false
      var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
      var strClientIP: String = ""
      var strChannelType: String = ""
      var strChannelCallBackUrl: String = ""
      var strRequest: String = ""

      var myAmount: BigDecimal = 0
      var strAmount: String = ""

      var messageidentification: String = ""

      var paymentendtoendidentification: String = ""
      var interbanksettlementamount: BigDecimal = 0
      var totalinterbanksettlementamount: BigDecimal = 0
      var mandateidentification: String = ""
      //debtor
      var debtorinformationdebtorname: String = ""
      //val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
      var debtorinformationdebtorcontactphonenumber: String = ""
      var debtoraccountinformationdebtoraccountidentification: String = ""
      //val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
      var debtoraccountinformationdebtoraccountname: String = ""
      //val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
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

      //
      var originalmessageidentification: String = ""
      var originalmessagenameidentification: String = ""  
      var originalcreationdatetime: String = ""
      var originalendtoendidentification: String = ""

      try
      {
        //var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound: Boolean = false
        var isAuthTokenFound: Boolean = false
        var isCredentialsFound: Boolean = false
        //var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        //var strClientIP : String = ""

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

          if (request.headers.get("ChannelCallBackUrl") != None){
            val myheaderChannelType = request.headers.get("ChannelCallBackUrl")
            if (myheaderChannelType.get != None){
              strChannelCallBackUrl = myheaderChannelType.get.toString
              if (strChannelCallBackUrl != null){
                strChannelCallBackUrl = strChannelCallBackUrl.trim
              }
              else{
                strChannelCallBackUrl = ""
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
            if (!isCredentialsFound){strPassword = ""}

            val myOutput = validateClientApi(strChannelType, strUserName, strPassword, strClientIP, strApifunction)
            if (myOutput.responsecode != null){
              responseCode = myOutput.responsecode
            }

            if (myOutput.responsemessage != null){
              responseMessage = myOutput.responsemessage
            }
            else{
              responseMessage = "Error occured during processing, please try again."
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
              (JsPath \ "fullnames").readNullable[JsValue] and
                (JsPath \ "phonenumber").readNullable[JsValue] and
                (JsPath \ "emailaddress").readNullable[JsValue]
              )(ContactInformation.apply _)

            implicit val DebitAccountInformation_Reads: Reads[DebitAccountInformation] = (
              (JsPath \ "debitaccountnumber").readNullable[JsValue] and
                (JsPath \ "debitaccountname").readNullable[JsValue] and
                (JsPath \ "debitcontactinformation").readNullable[ContactInformation]
              )(DebitAccountInformation.apply _)

            implicit val CreditAccountInformation_Reads: Reads[CreditAccountInformation] = (
              (JsPath \ "creditaccountnumber").readNullable[JsValue] and
                (JsPath \ "creditaccountname").readNullable[JsValue] and
                (JsPath \ "schemename").readNullable[JsValue] and
                (JsPath \ "bankcode").readNullable[JsValue] and
                (JsPath \ "creditcontactinformation").readNullable[ContactInformation]
              )(CreditAccountInformation.apply _)

            implicit val TransferPurposeInformation_Reads: Reads[TransferPurposeInformation] = (
              (JsPath \ "purposecode").readNullable[JsValue] and
                (JsPath \ "purposedescription").readNullable[JsValue]
              )(TransferPurposeInformation.apply _)

            implicit val OriginalGroupInformation_Reads: Reads[OriginalGroupInformation] = (
              (JsPath \ "originalmessageidentification").readNullable[JsValue] and
              (JsPath \ "originalmessagenameidentification").readNullable[JsValue] and
              (JsPath \ "originalcreationdatetime").readNullable[JsValue] and
                (JsPath \ "originalendtoendidentification").readNullable[JsValue]
              )(OriginalGroupInformation.apply _)  

            implicit val TransferRemittanceInformation_Reads: Reads[TransferRemittanceInformation] = (
              (JsPath \ "unstructured").readNullable[JsValue] and
                (JsPath \ "taxremittancereferencenumber").readNullable[JsValue]
              )(TransferRemittanceInformation.apply _)

            implicit val TransferMandateInformation_Reads: Reads[TransferMandateInformation] = (
              (JsPath \ "mandateidentification").readNullable[JsValue] and
                (JsPath \ "mandatedescription").readNullable[JsValue]
              )(TransferMandateInformation.apply _)

            implicit val PaymentCancellationInformation_Reads: Reads[PaymentCancellationInformation] = (
              (JsPath \ "transactionreference").readNullable[JsValue] and
                (JsPath \ "amount").readNullable[JsValue] and
                (JsPath \ "debitaccountinformation").readNullable[DebitAccountInformation] and
                (JsPath \ "creditaccountinformation").readNullable[CreditAccountInformation] and
                (JsPath \ "mandateinformation").readNullable[TransferMandateInformation] and
                (JsPath \ "remittanceinformation").readNullable[TransferRemittanceInformation] and
                (JsPath \ "purposeinformation").readNullable[TransferPurposeInformation] and
                (JsPath \ "originalgroupinformation").readNullable[OriginalGroupInformation]
              )(PaymentCancellationInformation.apply _)

            implicit val PaymentCancellationDetails_Request_Reads: Reads[PaymentCancellationDetails_Request] = (
              (JsPath \ "messagereference").readNullable[JsValue] and
                (JsPath \ "paymentdata").readNullable[PaymentCancellationInformation]
              )(PaymentCancellationDetails_Request.apply _)

            myjson.validate[PaymentCancellationDetails_Request] match {
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
                  //val myBatchReference : java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)

                  try{
                    /*
                    var myAmount: BigDecimal = 0
                    var strAmount: String = ""

                    var messageidentification: String = ""

                    var paymentendtoendidentification: String = ""
                    var interbanksettlementamount: BigDecimal = 0
                    var totalinterbanksettlementamount: BigDecimal = 0
                    var mandateidentification: String = ""
                    //debtor
                    var debtorinformationdebtorname: String = ""
                    //val debtorinformationdebtororganisationidentification: String = firstAgentIdentification
                    var debtorinformationdebtorcontactphonenumber: String = ""
                    var debtoraccountinformationdebtoraccountidentification: String = ""
                    //val debtoraccountinformationdebtoraccountschemename: String = SchemeName.ACC.toString.toUpperCase
                    var debtoraccountinformationdebtoraccountname: String = ""
                    //val debtoragentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
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
                    */
                    //default values
                    //val instructingagentinformationfinancialInstitutionIdentification: String = firstAgentIdentification
                    //val instructedagentinformationfinancialInstitutionIdentification: String = assigneeAgentIdentification //i.e IPSL
                    //val initiatingpartyinformationorganisationidentification: String = firstAgentIdentification
                    //val chargebearer: String = chargeBearer
                    //val settlementmethod: String = settlementMethod
                    //val clearingsystem: String = clearingSystem
                    //val servicelevel: String = serviceLevel
                    //val localinstrumentcode: String = localInstrumentCode
                    //val categorypurpose: String = categoryPurpose

                    //val creationDateTime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    val t1: String =  new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)
                    val t2: String =  new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date)
                    val creationDateTime: String = t1 + "T" + t2+ "Z"
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
                    //val acceptancedatetime: String = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date)

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
                    //
                    originalmessageidentification = ""
                    originalmessagenameidentification = ""  
                    originalcreationdatetime = ""
                    originalendtoendidentification = ""

                    isValidMessageReference = false
                    isValidTransactionReference = false
                    isMatchingReference = false
                    isValidSchemeName = false
                    isValidAmount = false
                    isValidDebitAccount = false
                    isValidDebitAccountName = false
                    isValidDebtorName = false
                    isValidDebitPhoneNumber = false
                    isValidCreditAccount = false
                    isValidCreditAccountName = false
                    isValidCreditBankCode = false
                    //isValidCreditPhoneNumber = false
                    isValidRemittanceinfoUnstructured = false
                    isValidPurposeCode = false

                    try{
                      var isValidPaymentdata = false

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

                      isValidPaymentdata = {
                        var isValid: Boolean = false
                        if (myPaymentDetails.paymentdata != None){
                          if (myPaymentDetails.paymentdata.get != None){
                            isValid = true
                          }
                        }  
                        isValid
                      }

                      //paymentendtoendidentification
                      /*
                      if (myPaymentDetails.paymentdata != None) {
                        if (myPaymentDetails.paymentdata.get != None) {
                          val myData = myPaymentDetails.paymentdata.get
                          paymentendtoendidentification = myData.transactionreference.getOrElse("").toString()
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
                      */
                      if (isValidPaymentdata){
                        val myData = myPaymentDetails.paymentdata.get
                        paymentendtoendidentification = myData.transactionreference.getOrElse("").toString()
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
                      
                      //strAmount
                      if (isValidPaymentdata){
                        val myData = myPaymentDetails.paymentdata.get
                          strAmount = myData.amount.getOrElse("").toString()
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
                      
                      //mandateidentification
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.mandateinformation != None){
                            if (myData1.mandateinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.mandateinformation.get

                          mandateidentification = myData2.mandateidentification.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        var isValid: Boolean = false
                        val isValid1 = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}  
                            else {false} 
                          }  
                          else {false}
                        }
                        if (isValid1){
                          val myVar1 = myData1.debitaccountinformation.get
                          if (myVar1.debitcontactinformation != None){
                            if (myVar1.debitcontactinformation.get != None){isValid = true}
                          }  
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          val myData3 = myData2.debitcontactinformation.get
                          debtorinformationdebtorname = myData3.fullnames.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        var isValid: Boolean = false
                        val isValid1 = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}  
                            else {false} 
                          }  
                          else {false}
                        }
                        if (isValid1){
                          val myVar1 = myData1.debitaccountinformation.get
                          if (myVar1.debitcontactinformation != None){
                            if (myVar1.debitcontactinformation.get != None){isValid = true}
                          }  
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          val myData3 = myData2.debitcontactinformation.get
                          debtorinformationdebtorcontactphonenumber = myData3.phonenumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          debtoraccountinformationdebtoraccountidentification = myData2.debitaccountnumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.debitaccountinformation != None){
                            if (myData1.debitaccountinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.debitaccountinformation.get
                          debtoraccountinformationdebtoraccountname = myData2.debitaccountname.getOrElse("").toString()
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
                      /*
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditorinformationcreditorname = myData2.creditaccountname.getOrElse("").toString()
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
                      */
                      
                      //creditorinformationcreditorcontactphonenumber
                      /*
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
                      */
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        var isValid: Boolean = false
                        val isValid1 = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true}  
                            else {false} 
                          }  
                          else {false}
                        }
                        if (isValid1){
                          val myVar1 = myData1.creditaccountinformation.get
                          if (myVar1.creditcontactinformation != None){
                            if (myVar1.creditcontactinformation.get != None){isValid = true}
                          }  
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          val myData3 = myData2.creditcontactinformation.get
                          //creditorinformationcreditorname
                          creditorinformationcreditorname = myData3.fullnames.getOrElse("").toString()
                          if (creditorinformationcreditorname != null && creditorinformationcreditorname != None){
                            creditorinformationcreditorname = creditorinformationcreditorname.trim
                            if (creditorinformationcreditorname.length > 0){
                              creditorinformationcreditorname = creditorinformationcreditorname.replace("'","")//Remove apostrophe
                              creditorinformationcreditorname = creditorinformationcreditorname.replace("  "," ")//Remove double spaces
                              creditorinformationcreditorname = creditorinformationcreditorname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              creditorinformationcreditorname = creditorinformationcreditorname.trim
                            }
                          }

                          //creditorinformationcreditorcontactphonenumber
                          creditorinformationcreditorcontactphonenumber = myData3.phonenumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditoraccountinformationcreditoraccountidentification = myData2.creditaccountnumber.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true}   
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          //println("myData1: " + myData1)
                          //println("myData2: " + myData2)
                          creditoraccountinformationcreditoraccountschemename = myData2.schemename.getOrElse("").toString()
                          //println("creditoraccountinformationcreditoraccountschemename: " + creditoraccountinformationcreditoraccountschemename)
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true}
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditoraccountinformationcreditoraccountname = myData2.creditaccountname.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.creditaccountinformation != None){
                            if (myData1.creditaccountinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.creditaccountinformation.get
                          creditoragentinformationfinancialInstitutionIdentification = myData2.bankcode.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.purposeinformation != None){
                            if (myData1.purposeinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.purposeinformation.get
                          purposeinformationpurposecode = myData2.purposecode.getOrElse("").toString()
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
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.remittanceinformation != None){
                            if (myData1.remittanceinformation.get != None){true}   
                            else {false}
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.remittanceinformation.get
                          remittanceinformationunstructured = myData2.unstructured.getOrElse("").toString()
                          if (remittanceinformationunstructured != null && remittanceinformationunstructured != None){
                            remittanceinformationunstructured = remittanceinformationunstructured.trim
                            if (remittanceinformationunstructured.length > 0){
                              remittanceinformationunstructured = remittanceinformationunstructured.replace("'","")//Remove apostrophe
                              remittanceinformationunstructured = remittanceinformationunstructured.replace("  "," ")//Remove double spaces
                              remittanceinformationunstructured = remittanceinformationunstructured.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              remittanceinformationunstructured = remittanceinformationunstructured.trim
                            }
                          }
                        }
                      }

                      //remittanceinformationtaxremittancereferencenumber
                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.remittanceinformation != None){
                            if (myData1.remittanceinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.remittanceinformation.get
                          remittanceinformationtaxremittancereferencenumber = myData2.taxremittancereferencenumber.getOrElse("").toString()
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

                      if (isValidPaymentdata){
                        val myData1 = myPaymentDetails.paymentdata.get
                        val isValid = {
                          if (myData1.originalgroupinformation != None){
                            if (myData1.originalgroupinformation.get != None){true} 
                            else {false}  
                          }  
                          else {false}
                        }
                        if (isValid){
                          val myData2 = myData1.originalgroupinformation.get
                          originalmessageidentification = myData2.originalmessageidentification.getOrElse("").toString()
                          if (originalmessageidentification != null && originalmessageidentification != None){
                            originalmessageidentification = originalmessageidentification.trim
                            if (originalmessageidentification.length > 0){
                              originalmessageidentification = originalmessageidentification.replace("'","")//Remove apostrophe
                              originalmessageidentification = originalmessageidentification.replace(" ","")//Remove spaces
                              originalmessageidentification = originalmessageidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              originalmessageidentification = originalmessageidentification.trim
                            }
                          }

                          originalmessagenameidentification = myData2.originalmessagenameidentification.getOrElse("").toString()
                          if (originalmessagenameidentification != null && originalmessagenameidentification != None){
                            originalmessagenameidentification = originalmessagenameidentification.trim
                            if (originalmessagenameidentification.length > 0){
                              originalmessagenameidentification = originalmessagenameidentification.replace("'","")//Remove apostrophe
                              originalmessagenameidentification = originalmessagenameidentification.replace(" ","")//Remove spaces
                              originalmessagenameidentification = originalmessagenameidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              originalmessagenameidentification = originalmessagenameidentification.trim
                            }
                          }

                          originalcreationdatetime = myData2.originalcreationdatetime.getOrElse("").toString()
                          if (originalcreationdatetime != null && originalcreationdatetime != None){
                            originalcreationdatetime = originalcreationdatetime.trim
                            if (originalcreationdatetime.length > 0){
                              originalcreationdatetime = originalcreationdatetime.replace("'","")//Remove apostrophe
                              originalcreationdatetime = originalcreationdatetime.replace(" ","")//Remove spaces
                              originalcreationdatetime = originalcreationdatetime.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              originalcreationdatetime = originalcreationdatetime.trim
                            }
                          }

                          originalendtoendidentification = myData2.originalendtoendidentification.getOrElse("").toString()
                          if (originalendtoendidentification != null && originalendtoendidentification != None){
                            originalendtoendidentification = originalendtoendidentification.trim
                            if (originalendtoendidentification.length > 0){
                              originalendtoendidentification = originalendtoendidentification.replace("'","")//Remove apostrophe
                              originalendtoendidentification = originalendtoendidentification.replace(" ","")//Remove spaces
                              originalendtoendidentification = originalendtoendidentification.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                              originalendtoendidentification = originalendtoendidentification.trim
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
                    isValidSchemeName = {
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

                    isValidMessageReference = {
                      var isValid: Boolean = false
                      if (messageidentification.length > 0 && messageidentification.length <= 35){
                        val isNumeric: Boolean = messageidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myMessageidentification = BigDecimal(messageidentification)
                          if (myMessageidentification > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidTransactionReference = {
                      var isValid: Boolean = false
                      if (paymentendtoendidentification.length > 0 && paymentendtoendidentification.length <= 35){
                        val isNumeric: Boolean = paymentendtoendidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myPaymentendtoendidentification = BigDecimal(paymentendtoendidentification)
                          if (myPaymentendtoendidentification > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isMatchingReference = {
                      var isValid: Boolean = false
                      if (isValidMessageReference && isValidTransactionReference) {
                        if (messageidentification.equalsIgnoreCase(paymentendtoendidentification)){
                          isValid = true  
                        }  
                      }
                      isValid
                    }

                    isValidAmount = {
                      if (myAmount > 0){true}
                      else {false}
                    }

                    isValidDebitAccount = {
                      var isValid: Boolean = false
                      if (debtoraccountinformationdebtoraccountidentification.length > 0 && debtoraccountinformationdebtoraccountidentification.length <= 35){
                        val isNumeric: Boolean = debtoraccountinformationdebtoraccountidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myDebtoraccount = BigDecimal(debtoraccountinformationdebtoraccountidentification)
                          if (myDebtoraccount > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidDebitAccountName = {
                      var isValid: Boolean = false
                      if (debtoraccountinformationdebtoraccountname.replace("  ","").length > 0 && debtoraccountinformationdebtoraccountname.replace("  ","").length <= 70){
                        isValid = true
                      }
                      isValid
                    }

                    isValidDebtorName  = {
                      var isValid: Boolean = false
                      if (debtorinformationdebtorname.replace("  ","").length > 0 && debtorinformationdebtorname.replace("  ","").length <= 140){
                        isValid = true
                      }
                      isValid
                    }

                    isValidDebitPhoneNumber = {
                      var isValid: Boolean = false
                      //if (debtorinformationdebtorcontactphonenumber.length == 10 || debtorinformationdebtorcontactphonenumber.length == 12){
                      if (debtorinformationdebtorcontactphonenumber.length == 14){
                        /*
                        val isNumeric: Boolean = debtorinformationdebtorcontactphonenumber.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myPhonenumber = BigDecimal(debtorinformationdebtorcontactphonenumber)
                          if (myPhonenumber > 0){isValid = true}
                        }
                        */
                        isValid = true
                      }
                      isValid
                    }

                    isValidCreditAccount = {
                      var isValid: Boolean = false
                      if (creditoraccountinformationcreditoraccountidentification.length > 0 && creditoraccountinformationcreditoraccountidentification.length <= 35){
                        val isNumeric: Boolean = creditoraccountinformationcreditoraccountidentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myCreditoraccount = BigDecimal(creditoraccountinformationcreditoraccountidentification)
                          if (myCreditoraccount > 0){isValid = true}
                        }
                        else{
                          isValid = true
                        }
                      }
                      isValid
                    }

                    isValidCreditAccountName = {
                      var isValid: Boolean = false
                      if (creditoraccountinformationcreditoraccountname.replace("  ","").length > 0 && creditoraccountinformationcreditoraccountname.replace("  ","").length <= 140){
                        isValid = true
                      }
                      isValid
                    }

                    isValidCreditBankCode = {
                      var isValid: Boolean = false
                      if (creditoragentinformationfinancialInstitutionIdentification.length > 0 && creditoragentinformationfinancialInstitutionIdentification.length <= 35){
                        val isNumeric: Boolean = creditoragentinformationfinancialInstitutionIdentification.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myCreditoragent = BigDecimal(creditoragentinformationfinancialInstitutionIdentification)
                          if (myCreditoragent > 0){isValid = true}
                        }
                      }
                      isValid
                    }
                    /*
                    isValidCreditPhoneNumber = {
                      var isValid: Boolean = false
                      if (creditorinformationcreditorcontactphonenumber.length == 10 || creditorinformationcreditorcontactphonenumber.length == 12){
                        val isNumeric: Boolean = creditorinformationcreditorcontactphonenumber.matches(strNumbersOnlyRegex) //validate numbers only i.e "[0-9]+"
                        if (isNumeric){
                          val myPhonenumber = creditorinformationcreditorcontactphonenumber.toInt
                          if (myPhonenumber > 0){isValid = true}
                        }
                      }
                      isValid
                    }
                    */
                    isValidRemittanceinfoUnstructured = {
                      var isValid: Boolean = false
                      if (remittanceinformationunstructured.replace(" ","").length > 0 && remittanceinformationunstructured.replace(" ","").length <= 140){
                        isValid = true
                      }
                      isValid
                    }

                    isValidPurposeCode = {
                      var isValid: Boolean = false
                      if (purposeinformationpurposecode.length > 0 && purposeinformationpurposecode.length <= 35){
                        isValid = true
                      }
                      isValid
                    }

                    if (isValidMessageReference && isValidTransactionReference && !isMatchingReference && isValidSchemeName && isValidAmount && isValidDebitAccount && isValidDebitAccountName && isValidDebitPhoneNumber && isValidCreditAccount && isValidCreditAccountName && isValidCreditBankCode && isValidDebtorName && isValidRemittanceinfoUnstructured && isValidPurposeCode){
                      isValidInputData = true
                      /*
                      myHttpStatusCode = HttpStatusCode.Accepted //TESTS ONLY
                      responseCode = 0
                      responseMessage = "Message accepted for processing."
                      println("messageidentification - " + messageidentification + ", paymentendtoendidentification - " + paymentendtoendidentification + ", totalinterbanksettlementamount - " + totalinterbanksettlementamount +
                        ", debtoraccountinformationdebtoraccountidentification - " + debtoraccountinformationdebtoraccountidentification + ", creditoraccountinformationcreditoraccountidentification - " + creditoraccountinformationcreditoraccountidentification)
                      */  
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
                      if (isValidInputData){
                        var isAccSchemeName: Boolean = false
                        val schemeName: String = {
                          var scheme: String = ""
                          if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(accSchemeName)){
                            scheme = accSchemeName
                            isAccSchemeName = true
                          }
                          else if (creditoraccountinformationcreditoraccountschemename.equalsIgnoreCase(phneSchemeName)){
                            scheme = phneSchemeName
                          }

                          scheme

                        }

                        creditoraccountinformationcreditoraccountschemename = schemeName

                        //TESTS ONLY
                        val originatorname: String = ""
                        val reasoncode: String = "FRAD"
                        val additionalinformation: String = "Fraudulent entry"

                        val transferDefaultInfo = TransferDefaultInfo(firstAgentIdentification, assigneeAgentIdentification, chargeBearer, settlementMethod, clearingSystem, serviceLevel, localInstrumentCode, categoryPurpose)
                        val debitcontactinformation = ContactInfo(debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber)
                        val debitAccountInfo = DebitAccountInfo(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, debitcontactinformation, SchemeName.ACC.toString.toUpperCase)
                        val creditcontactinformation = ContactInfo(creditorinformationcreditorname, creditorinformationcreditorcontactphonenumber)
                        val creditAccountInfo = CreditAccountInfo(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoraccountinformationcreditoraccountschemename, creditoragentinformationfinancialInstitutionIdentification, creditcontactinformation)
                        val purposeInfo = TransferPurposeInfo(purposeinformationpurposecode)
                        val remittanceInfo = TransferRemittanceInfo(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
                        val mandateInfo = TransferMandateInfo(mandateidentification)
                        val originalGroupInfo = OriginalGroupInfo(originalmessageidentification, originalmessagenameidentification,  originalcreationdatetime, originalendtoendidentification)
                        val cancellationStatusReasonInfo = CancellationStatusReasonInfo(originatorname, reasoncode, additionalinformation)
                        val paymentdata = PaymentCancellationInfo(paymentendtoendidentification, interbanksettlementamount, debitAccountInfo, creditAccountInfo, mandateInfo, remittanceInfo, purposeInfo, transferDefaultInfo, originalGroupInfo, cancellationStatusReasonInfo)
                        val singlePaymentCancellationInfo = SinglePaymentCancellationInfo(messageidentification, creationDateTime, numberoftransactions, totalinterbanksettlementamount, paymentdata)
                        /*  
                        val f = Future {
                          //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                          val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo, isAccSchemeName)
                          sendSingleCreditTransferRequestsIpsl(myID, myRespData)
                        }  
                        */
                        val myBatchSize: Integer = 1
                        val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                        val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                        val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())
                        val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                          SingleCreditTransferPaymentTableDetails(myBatchReference, 
                          debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
                          messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
                          amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
                          creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
                          remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
                          chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
                          myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                        /*
                        val myTableResponseDetails = addOutgoingSingleCreditTransferPaymentDetails(mySingleCreditTransferPaymentTableDetails, strChannelType, strChannelCallBackUrl)
                        myID = myTableResponseDetails.id
                        responseCode = myTableResponseDetails.responsecode
                        responseMessage = myTableResponseDetails.responsemessage
                        */
                        //TESTS ONLY
                        responseCode = 0
                        //println("myID - " + myID)
                        //println("responseCode - " + responseCode)
                        //println("responseMessage - " + responseMessage)
                        if (responseCode == 0){
                          myHttpStatusCode = HttpStatusCode.Accepted
                          responseMessage = "Message accepted for processing."

                          val f = Future {
                            //println("singlePaymentCancellationInfo - " + singlePaymentCancellationInfo)
                            val myRespData: String = getPaymentCancellationDetails(singlePaymentCancellationInfo)
                            sendPaymentCancellationRequestsIpsl(myID, myRespData, strOutgoingPaymentCancellationUrlIpsl)
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
                    //})

                    try{
                      if (isValidInputData){
                        /*
                        val myBatchSize: Integer = 1
                        val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
                        val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
                        val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())
                        val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
                          SingleCreditTransferPaymentTableDetails(myBatchReference, 
                          debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
                          messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
                          amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
                          creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
                          remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
                          chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
                          myBatchSize, strRequestData, dateFromCbsApi, strClientIP)

                        val myTableResponseDetails = addOutgoingSingleCreditTransferPaymentDetails(mySingleCreditTransferPaymentTableDetails)
                        myID = myTableResponseDetails.id
                        responseCode = myTableResponseDetails.responsecode
                        responseMessage = myTableResponseDetails.responsemessage
                        //println("myID - " + myID)
                        //println("responseCode - " + responseCode)
                        //println("responseMessage - " + responseMessage)
                        if (responseCode == 0){
                          myHttpStatusCode = HttpStatusCode.Accepted
                          responseMessage = "Message accepted for processing."

                          val f = Future {
                            //println("singleCreditTransferPaymentInfo - " + singleCreditTransferPaymentInfo)
                            val myRespData: String = getSingleCreditTransferDetails(singleCreditTransferPaymentInfo, isAccSchemeName)
                            sendSingleCreditTransferRequestsIpsl(myID, myRespData)
                          }

                        }
                        */
                      }
                      else{
                        responseMessage = "Invalid Input Data length"
                        if (!isValidMessageReference){
                          responseMessage = "Invalid Input Data. messagereference"
                        }
                        else if (!isValidTransactionReference){
                          responseMessage = "Invalid Input Data. transactionreference"
                        }
                        else if (isMatchingReference){
                          responseMessage = "Invalid Input Data. messagereference and transactionreference should have different values"
                        }
                        else if (!isValidSchemeName){
                          responseMessage = "Invalid Input Data. schemename"
                        }
                        else if (!isValidAmount){
                          responseMessage = "Invalid Input Data. amount"
                        }
                        else if (!isValidDebitAccount){
                          responseMessage = "Invalid Input Data. debitaccountnumber"
                        }
                        else if (!isValidDebitAccountName){
                          responseMessage = "Invalid Input Data. debitaccountname"
                        }
                        else if (!isValidDebitPhoneNumber){
                          responseMessage = "Invalid Input Data. debit phonenumber"
                        }
                        else if (!isValidCreditAccount){
                          responseMessage = "Invalid Input Data. creditaccountnumber"
                        }
                        else if (!isValidCreditAccountName){
                          responseMessage = "Invalid Input Data. creditaccountname"
                        }
                        else if (!isValidCreditBankCode){
                          responseMessage = "Invalid Input Data. credit bankcode"
                        }
                        else if (!isValidDebtorName){
                          responseMessage = "Invalid Input Data. debit fullnames"
                        }
                        else if (!isValidRemittanceinfoUnstructured){
                          responseMessage = "Invalid Input Data. remittanceinformation - unstructured"
                        }
                        else if (!isValidPurposeCode){
                          responseMessage = "Invalid Input Data. purposeinformation - purposecode"
                        }
                        /*
                        else if (!isValidCreditPhoneNumber){
                          responseMessage = "Invalid Input Data. credit phonenumber"
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
          insertApiValidationRequests(strChannelType, strUserName, strPassword, strClientIP, strApifunction, responseCode, responseMessage)
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
      
      implicit val  SingleCreditTransferPaymentDetailsResponse_Writes = Json.writes[SingleCreditTransferPaymentDetailsResponse]

      val mySingleCreditTransferPaymentResponse =  SingleCreditTransferPaymentDetailsResponse(responseCode, responseMessage)
      val jsonResponse = Json.toJson(mySingleCreditTransferPaymentResponse)

      try{
        val dateToCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        var isSuccessful: Boolean = false
        val myCode: Int = {
          myHttpStatusCode match {
            case HttpStatusCode.Accepted =>
              isSuccessful = true
              202
            case HttpStatusCode.BadRequest =>
              400
            case HttpStatusCode.Unauthorized =>
              401
            case _ =>
              400
          }
        }
        if (isSuccessful){
          val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [HttpStatusCode_CbsApi_In] = " + myCode + ", [ResponseMessage_CbsApi_In] = '" + jsonResponse.toString() + "', [Date_to_CbsApi_In] = '" + dateToCbsApi + "' where [ID] = " + myID + ";"
          //println("strSQL - " + strSQL)
          insertUpdateRecord(strSQL)
        }
        else{
          val myBatchSize: Integer = 1
          val strBatchReference  = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date)
          val myBatchReference: java.math.BigDecimal =  new java.math.BigDecimal(strBatchReference)
          val amount: java.math.BigDecimal =  new java.math.BigDecimal(myAmount.toString())

          var strRequestData: String = ""

          if (strRequest != null && strRequest != None){
            strRequest = strRequest.trim
            if (strRequest.length > 0){
              strRequestData = strRequest.replace("'","")//Remove apostrophe
              strRequestData = strRequestData.trim
            }
          }
          
          val mySingleCreditTransferPaymentTableDetails = //SingleCreditTransferPaymentTableDetails(myBatchReference, strAccountNumber, strBankCode, strMessageReference, strTransactionReference, strSchemeName, myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
            SingleCreditTransferPaymentTableDetails(myBatchReference, 
            debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountname, firstAgentIdentification, 
            messageidentification, paymentendtoendidentification, SchemeName.ACC.toString.toUpperCase, 
            amount, debtorinformationdebtorname, debtorinformationdebtorcontactphonenumber, 
            creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountname, creditoragentinformationfinancialInstitutionIdentification, creditoraccountinformationcreditoraccountschemename, 
            remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber, purposeinformationpurposecode, 
            chargeBearer, mandateidentification, assignerAgentIdentification, assigneeAgentIdentification, 
            myBatchSize, strRequestData, dateFromCbsApi, strClientIP)
          //println("jsonResponse + " + jsonResponse.toString())
          addOutgoingSingleCreditTransferPaymentDetailsArchive(responseCode, responseMessage, jsonResponse.toString(), mySingleCreditTransferPaymentTableDetails, strChannelType, strChannelCallBackUrl)
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
        var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        var strClientIP: String = ""

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
      var responseCode: Int = 1
      var responseMessage: String = "Error occured during processing, please try again."
      var myCoop_AcctoPesalink_PaymentDetailsResponse_BatchData : Seq[Coop_AcctoPesalink_PaymentDetailsResponse_Batch] = Seq.empty[Coop_AcctoPesalink_PaymentDetailsResponse_Batch]
      var myBatchNo: BigDecimal = 0
      val strApifunction: String = "addAccountToPesalinkTransferPaymentDetailsCoopBank"

      try
      {
        var strRequest: String = ""
        var strRequestHeader: String = ""
        var strAuthToken: String = ""
        var isDataFound: Boolean = false
        var isAuthTokenFound: Boolean = false
        var isCredentialsFound: Boolean = false
        var strChannelType: String = ""
        var strUserName: String = ""
        var strPassword: String = ""
        var strClientIP: String = ""

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
  def sendAccountVerificationRequestsIpsl(myID: java.math.BigDecimal, myRequestData: String, strApiURL: String, strChannelType: String, strCallBackApiURL: String): Unit = {
    val strApifunction: String = "sendAccountVerificationRequestsIpsl"
    //var strApiURL: String = "http://localhost:9001/iso20022/v1/verification-request"

    val myuri: Uri = strApiURL

    var isValidData: Boolean = false
    var isSuccessful: Boolean = false
    var myXmlData: String = ""
  
    try {
      isValidData = true //TESTS ONLY
      if (isValidData) {
        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        //var strUserName: String = "testUid"
        //var strPassWord: String = "testPwd"
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
        /*
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
        */
        try{
          val dateToIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
          var strRequestData: String = ""
          /*
          var strRequestData: String = myRequestData
          strRequestData = strRequestData.replace("'","")//Remove apostrophe
          strRequestData = strRequestData.replace(" ","")//Remove spaces
          strRequestData = strRequestData.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
          strRequestData = strRequestData.trim
          */
          //val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Posted_to_IpslApi] = 1, [Post_picked_IpslApi] = 1, [RequestMessage_IpslApi] = '" + strRequestData + "', [Date_to_IpslApi] = '" + dateToIpslApi + "' where ID = " + myID + ";"
          val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Posted_to_IpslApi] = 1, [Post_picked_IpslApi] = 1, [RequestMessage_IpslApi] = '" + strRequestData + "', [Date_to_IpslApi] = '" + dateToIpslApi + "' where [ID] = " + myID + ";"
          insertUpdateRecord(strSQL)

          log_data(strApifunction + " : " + " channeltype - IPSL"  + " , >> outgoing request >> - " + myRequestData + " , ID - " + myID)
        }
        catch{
          case ex: Exception =>
            log_errors(strApifunction + " : " + ex.getMessage())
          case io: IOException =>
            log_errors(strApifunction + " : " + io.getMessage())
          case tr: Throwable =>
            log_errors(strApifunction + " : " + tr.getMessage())
        }
        /*
        val strCertPath: String = "certsconf/ipslservice.crt"
        val clientContext = {
          val certStore = KeyStore.getInstance(KeyStore.getDefaultType)
          certStore.load(null, null)
          // only do this if you want to accept a custom root CA. Understand what you are doing!
          certStore.setCertificateEntry("ca", loadX509Certificate(strCertPath))

          val certManagerFactory = TrustManagerFactory.getInstance("SunX509")
          certManagerFactory.init(certStore)

          val context = SSLContext.getInstance("TLS")//TLSv1.2, TLSv1.3
          context.init(null, certManagerFactory.getTrustManagers, new SecureRandom)
          ConnectionContext.httpsClient(context)
        }
        */
        val strCertPath: String = "certsconf/bank0074_transport.p12"
        val strCaChainCertPath: String = "certsconf/ca_chain.crt.pem"
        val clientContext = {
          val certStore = KeyStore.getInstance("PKCS12")
          val myKeyStore: InputStream = getResourceStream(strCertPath)
          val password: String = "K+S2>v/dmUE%XBc+9^"
          val myPassword = password.toCharArray()
          certStore.load(myKeyStore, myPassword)
          // only do this if you want to accept a custom root CA. Understand what you are doing!
          certStore.setCertificateEntry("ca", loadX509Certificate(strCaChainCertPath))

          val certManagerFactory = TrustManagerFactory.getInstance("SunX509")
          certManagerFactory.init(certStore)

          val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
          keyManagerFactory.init(certStore, myPassword)

          val context = SSLContext.getInstance("TLS")//TLSv1.2, TLSv1.3
          context.init(keyManagerFactory.getKeyManagers, certManagerFactory.getTrustManagers, new SecureRandom)
          ConnectionContext.httpsClient(context)
        }
        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //***working*** val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        /* TESTS ONLY */
        //val accessToken: String = "sassasasss"
        myXmlData = myRequestData
        val data = HttpEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), myXmlData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data))
        //val conctx = Http().setDefaultClientHttpsContext(Http().createClientHttpsContext(Http().sslConfig))
        //val conctx = Http().createClientHttpsContext(Http().sslConfig)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data), connectionContext = clientContext)
        //val myEntryID: Future[java.math.BigDecimal] = Future(entryID)
        var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        val myStart_time: Future[String] = Future(start_time_DB)
        val myEntryID: Future[java.math.BigDecimal] = Future(myID)
        val myChannelType: Future[String] = Future(strChannelType)
        val myCallBackApiURL: Future[String] = Future(strCallBackApiURL)
        //TESTS ONLY 
        //println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              val dateFromIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              //println("start 2: " + res.status.intValue())
              if (res.status != None) {
                if (res.status.intValue() == 200) {
                  var isDataExists: Boolean = false
                  var myCount: Int = 0
                  var strid: String = ""
                  var strResponseData: String = ""
                  val strIntRegex: String = "[0-9]+" //Integers only
                  val strDecimalRegex: String = "^[0-9]*\\.?[0-9]+$" //Decimals
                  var strChannelType: String = ""
                  var strCallBackApiURL: String = ""
                  //val resByteStr: String = res.entity.toString
                  //val resByteStr: akka.util.ByteString = res.entity
                  //println("res.entity - " + res.entity.toString())

                  val myData = res.entity
                  if (myData != null){
                    val x = myData.asInstanceOf[HttpEntity.Strict].getData().decodeString(StandardCharsets.UTF_8)
                    strResponseData = x.toString
                    //println("res.entity x - " + x.toString)
                    if (myEntryID.value.isEmpty != true) {
                      if (myEntryID.value.get != None) {
                        val myVal = myEntryID.value.get
                        if (myVal.get != None) {
                          myID = myVal.get
                        }
                      }
                    }

                    if (myChannelType.value.isEmpty != true) {
                      if (myChannelType.value.get != None) {
                        val myVal = myChannelType.value.get
                        if (myVal.get != None) {
                          strChannelType = myVal.get
                        }
                      }
                    }

                    if (myCallBackApiURL.value.isEmpty != true) {
                      if (myCallBackApiURL.value.get != None) {
                        val myVal = myCallBackApiURL.value.get
                        if (myVal.get != None) {
                          strCallBackApiURL = myVal.get
                        }
                      }
                    }

                    log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + res.status.intValue())

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
                        if (verificationStatus.length > 0){
                          strAccountname = strAccountname.replace("'","")//Remove apostrophe
                          strAccountname = strAccountname.replace("  "," ")//Remove double spaces
                          strAccountname = strAccountname.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
                          strAccountname = strAccountname.trim
                        }
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
                      if (myEntryID.value.isEmpty != true) {
                        if (myEntryID.value.get != None) {
                          val myVal = myEntryID.value.get
                          if (myVal.get != None) {
                            myID = myVal.get
                          }
                        }
                      }
                      */
                      val f = Future {sendAccountVerificationResponseEchannel(myID, myAccountVerificationResponse, strChannelType, strCallBackApiURL)}
                      
                      val strStatusMessage: String = "Successful"
                      strResponseData = ""//TESTS ONLY
                      val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + res.status.intValue() + 
                      ", [StatusCode_IpslApi] = 0, [StatusMessage_IpslApi] = '" + strStatusMessage +
                      "', [isVerified] = '" + isVerified + "', [VerificationStatus] = '" + verificationStatus + 
                      "', [VerificationReasonCode] = '" + verificationReasonCode + "', [AccountName] = '" + strAccountname + 
                      "', [ResponseMessage_IpslApi] = '" + strResponseData + 
                      "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
                      insertUpdateRecord(strSQL)

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
                }
                else {

                  //Lets log the status code returned by CBS webservice
                  val strResponseData: String = ""
                  val strStatusMessage: String = "Failed"

                  if (myEntryID.value.isEmpty != true) {
                    if (myEntryID.value.get != None) {
                      val myVal = myEntryID.value.get
                      if (myVal.get != None) {
                        myID = myVal.get
                      }
                    }
                  }

                  val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + res.status.intValue() + 
                  ", [StatusCode_IpslApi] = 1, [StatusMessage_IpslApi] = '" + strStatusMessage +
                  "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
                  insertUpdateRecord(strSQL)

                  log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + res.status.intValue())
                  
                }
              }
            //println(res)
            //case Failure(_)   => sys.error("something wrong")
            case Failure(f) =>
              //println("start 3: " + f.getMessage)
              //myDataManagement.Log_errors("sendRegistrationRequests - main : " + f.getMessage + "exception error occured. Failure.")
              val strResponseData: String = ""
              val dateFromIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              val myHttpStatusCode: Int = 500
              val strHttpErrorMessage: String = f.getMessage
              val strStatusMessage: String = "Failure occured when sending the request to API. " + strHttpErrorMessage

              if (myEntryID.value.isEmpty != true) {
                if (myEntryID.value.get != None) {
                  val myVal = myEntryID.value.get
                  if (myVal.get != None) {
                    myID = myVal.get
                  }
                }
              }

              val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + myHttpStatusCode + 
              ", [StatusCode_IpslApi] = 1, [StatusMessage_IpslApi] = '" + strStatusMessage +
              "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
              insertUpdateRecord(strSQL)

              log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode + " , httperrormessage - " + strHttpErrorMessage)
              
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
  def sendSingleCreditTransferRequestsIpsl(myID: java.math.BigDecimal, myRequestData: String, strApiURL: String): Unit = {
    val strApifunction: String = "sendSingleCreditTransferRequestsIpsl"
    //var strApiURL: String = "http://localhost:9001/iso20022/v1/credit-transfer"
    
    val myuri : Uri = strApiURL

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myXmlData : String = ""
    //var strDeveloperId: String = ""//strDeveloperId_Verification

    try {
      isValidData = true//TESTS ONLY
      if (isValidData) {

        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        //var strUserName: String = "testUid"
        //var strPassWord: String = "testPwd"
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
        /*
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
        */
        try{
          val dateToIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
          var strRequestData: String = ""
          /*
          var strRequestData: String = myRequestData
          strRequestData = strRequestData.replace("'","")//Remove apostrophe
          strRequestData = strRequestData.replace(" ","")//Remove spaces
          strRequestData = strRequestData.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
          strRequestData = strRequestData.trim
          */
          val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [Posted_to_IpslApi] = 1, [Post_picked_IpslApi] = 1, [RequestMessage_IpslApi] = '" + strRequestData + "', [Date_to_IpslApi] = '" + dateToIpslApi + "' where [ID] = " + myID + ";"
          insertUpdateRecord(strSQL)

          log_data(strApifunction + " : " + " channeltype - IPSL"  + " , >> outgoing request >> - " + myRequestData + " , ID - " + myID)
        }
        catch{
          case ex: Exception =>
            log_errors(strApifunction + " : " + ex.getMessage())
          case io: IOException =>
            log_errors(strApifunction + " : " + io.getMessage())
          case tr: Throwable =>
            log_errors(strApifunction + " : " + tr.getMessage())
        }

        val strCertPath: String = "certsconf/bank0074_transport.p12"
        val strCaChainCertPath: String = "certsconf/ca_chain.crt.pem"
        val clientContext = {
          val certStore = KeyStore.getInstance("PKCS12")
          val myKeyStore: InputStream = getResourceStream(strCertPath)
          val password: String = "K+S2>v/dmUE%XBc+9^"
          val myPassword = password.toCharArray()
          certStore.load(myKeyStore, myPassword)
          // only do this if you want to accept a custom root CA. Understand what you are doing!
          certStore.setCertificateEntry("ca", loadX509Certificate(strCaChainCertPath))

          val certManagerFactory = TrustManagerFactory.getInstance("SunX509")
          certManagerFactory.init(certStore)

          val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
          keyManagerFactory.init(certStore, myPassword)

          val context = SSLContext.getInstance("TLS")//TLSv1.2, TLSv1.3
          context.init(keyManagerFactory.getKeyManagers, certManagerFactory.getTrustManagers, new SecureRandom)
          ConnectionContext.httpsClient(context)
        }
        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //***working*** val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        /* TESTS ONLY */
        //val accessToken: String = "sassasasss"
        myXmlData = myRequestData
        val data = HttpEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), myXmlData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data))
        //val conctx = Http().createClientHttpsContext(Http().sslConfig)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data), connectionContext = conctx)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data), connectionContext = clientContext)
        val myEntryID: Future[java.math.BigDecimal] = Future(myID)
        //var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        //val myStart_time: Future[String] = Future(start_time_DB)
        //TESTS ONLY
        //println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + res.status.intValue())
              val dateFromIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              var myHttpStatusCode: Int = 0
              var mystatuscode: Int = 1
              var strStatusMessage: String = "Failed processing"
              var strResponseData: String = ""
              if (res.status != None) {
                if (res.status.intValue() == 202) {
                  val myData = res.entity
                  if (myData != null){
                    val x = myData.asInstanceOf[HttpEntity.Strict].getData().decodeString(StandardCharsets.UTF_8)
                    strResponseData = x.toString
                    //println("res.entity x - " + x.toString)
                    //println("mySingleCreditTransfer - " + x.toString)
                  }
                  mystatuscode = 0
                  strStatusMessage = "successful"
                }
                else {
                  //Lets log the status code returned by CBS webservice
                  strStatusMessage = "Failed"
                }

                myHttpStatusCode = res.status.intValue()

                if (myEntryID.value.isEmpty != true) {
                  if (myEntryID.value.get != None) {
                    val myVal = myEntryID.value.get
                    if (myVal.get != None) {
                      myID = myVal.get
                    }
                  }
                }

                val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + myHttpStatusCode + 
                ", [StatusCode_IpslApi] = " + mystatuscode + ", [StatusMessage_IpslApi] = '" + strStatusMessage +
                "', [ResponseMessage_IpslApi] = '" + strResponseData + 
                "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
                insertUpdateRecord(strSQL)

                log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode)
              }
            case Failure(f) =>
              val dateFromIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              val myHttpStatusCode: Int = 500
              val strHttpErrorMessage: String = f.getMessage
              val strStatusMessage: String = "Failure occured when sending the request to API. " + strHttpErrorMessage
              val strResponseData: String = ""

              if (myEntryID.value.isEmpty != true) {
                if (myEntryID.value.get != None) {
                  val myVal = myEntryID.value.get
                  if (myVal.get != None) {
                    myID = myVal.get
                  }
                }
              }

              val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + myHttpStatusCode + 
              ", [StatusCode_IpslApi] = 1, [StatusMessage_IpslApi] = '" + strStatusMessage +
              "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
              insertUpdateRecord(strSQL)

              log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode + " , httperrormessage - " + strHttpErrorMessage)
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
  }
  def sendPaymentCancellationRequestsIpsl(myID: java.math.BigDecimal, myRequestData: String, strApiURL: String): Unit = {
    val strApifunction: String = "sendPaymentCancellationRequestsIpsl"
    //var strApiURL: String = "http://localhost:9001/iso20022/v1/payment-cancellation-request"
    
    val myuri : Uri = strApiURL

    var isValidData : Boolean = false
    var isSuccessful : Boolean = false
    var myXmlData : String = ""
    //var strDeveloperId: String = ""//strDeveloperId_Verification

    try {
      isValidData = true//TESTS ONLY
      if (isValidData) {

        //val myDataManagement = new DataManagement
        //val accessToken: String = GetCbsApiAuthorizationHeader(strDeveloperId)

        //var strUserName: String = "testUid"
        //var strPassWord: String = "testPwd"
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
        /*
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
        */
        try{
          val dateToIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
          var strRequestData: String = ""
          /*
          var strRequestData: String = myRequestData
          strRequestData = strRequestData.replace("'","")//Remove apostrophe
          strRequestData = strRequestData.replace(" ","")//Remove spaces
          strRequestData = strRequestData.replaceAll("^\"|\"$", "") //Remove beginning and ending double quote (") from a string.
          strRequestData = strRequestData.trim
          */
          val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [Posted_to_IpslApi] = 1, [Post_picked_IpslApi] = 1, [RequestMessage_IpslApi] = '" + strRequestData + "', [Date_to_IpslApi] = '" + dateToIpslApi + "' where [ID] = " + myID + ";"
          //insertUpdateRecord(strSQL)

          log_data(strApifunction + " : " + " channeltype - IPSL"  + " , >> outgoing request >> - " + myRequestData + " , ID - " + myID)
        }
        catch{
          case ex: Exception =>
            log_errors(strApifunction + " : " + ex.getMessage())
          case io: IOException =>
            log_errors(strApifunction + " : " + io.getMessage())
          case tr: Throwable =>
            log_errors(strApifunction + " : " + tr.getMessage())
        }

        val strCertPath: String = "certsconf/bank0074_transport.p12"
        val strCaChainCertPath: String = "certsconf/ca_chain.crt.pem"
        val clientContext = {
          val certStore = KeyStore.getInstance("PKCS12")
          val myKeyStore: InputStream = getResourceStream(strCertPath)
          val password: String = "K+S2>v/dmUE%XBc+9^"
          val myPassword = password.toCharArray()
          certStore.load(myKeyStore, myPassword)
          // only do this if you want to accept a custom root CA. Understand what you are doing!
          certStore.setCertificateEntry("ca", loadX509Certificate(strCaChainCertPath))

          val certManagerFactory = TrustManagerFactory.getInstance("SunX509")
          certManagerFactory.init(certStore)

          val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
          keyManagerFactory.init(certStore, myPassword)

          val context = SSLContext.getInstance("TLS")
          context.init(keyManagerFactory.getKeyManagers, certManagerFactory.getTrustManagers, new SecureRandom)
          ConnectionContext.httpsClient(context)
        }
        //val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //***working*** val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", strUserName),RawHeader("password", strPassWord)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(GET, uri = myuri).withHeaders(RawHeader("username", "FundMasterApi"),RawHeader("password", "n6,e$=p8QK\\+c^h~")))
        /* TESTS ONLY */
        //val accessToken: String = "sassasasss"
        myXmlData = myRequestData
        val data = HttpEntity(ContentType.WithCharset(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), myXmlData)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization", "bearer " + accessToken)))
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data))
        //val conctx = Http().createClientHttpsContext(Http().sslConfig)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data), connectionContext = conctx)
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data), connectionContext = clientContext)
        val myEntryID: Future[java.math.BigDecimal] = Future(myID)
        //var start_time_DB: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        //val myStart_time: Future[String] = Future(start_time_DB)
        //TESTS ONLY
        //println("start 1: " + start_time_DB)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + res.status.intValue())
              val dateFromIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              var myHttpStatusCode: Int = 0
              var mystatuscode: Int = 1
              var strStatusMessage: String = "Failed processing"
              var strResponseData: String = ""
              if (res.status != None) {
                if (res.status.intValue() == 202) {
                  val myData = res.entity
                  if (myData != null){
                    val x = myData.asInstanceOf[HttpEntity.Strict].getData().decodeString(StandardCharsets.UTF_8)
                    strResponseData = x.toString
                    //println("res.entity x - " + x.toString)
                    //println("mySingleCreditTransfer - " + x.toString)
                  }
                  mystatuscode = 0
                  strStatusMessage = "successful"
                }
                else {
                  //Lets log the status code returned by CBS webservice
                  strStatusMessage = "Failed"
                }

                myHttpStatusCode = res.status.intValue()

                if (myEntryID.value.isEmpty != true) {
                  if (myEntryID.value.get != None) {
                    val myVal = myEntryID.value.get
                    if (myVal.get != None) {
                      myID = myVal.get
                    }
                  }
                }

                val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + myHttpStatusCode + 
                ", [StatusCode_IpslApi] = " + mystatuscode + ", [StatusMessage_IpslApi] = '" + strStatusMessage +
                "', [ResponseMessage_IpslApi] = '" + strResponseData + 
                "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
                //insertUpdateRecord(strSQL)

                log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode)
              }
            case Failure(f) =>
              val dateFromIpslApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              val myHttpStatusCode: Int = 500
              val strHttpErrorMessage: String = f.getMessage
              val strStatusMessage: String = "Failure occured when sending the request to API. " + strHttpErrorMessage
              val strResponseData: String = ""

              if (myEntryID.value.isEmpty != true) {
                if (myEntryID.value.get != None) {
                  val myVal = myEntryID.value.get
                  if (myVal.get != None) {
                    myID = myVal.get
                  }
                }
              }

              val strSQL: String = "update [dbo].[OutgoingSingleCreditTransferPaymentDetails] set [Response_Received_IpslApi] = 1, [HttpStatusCode_IpslApi] = " + myHttpStatusCode + 
              ", [StatusCode_IpslApi] = 1, [StatusMessage_IpslApi] = '" + strStatusMessage +
              "', [Date_from_IpslApi] = '" + dateFromIpslApi + "' where [ID] = " + myID + ";"
              //insertUpdateRecord(strSQL)

              log_data(strApifunction + " : " + " channeltype - IPSL"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode + " , httperrormessage - " + strHttpErrorMessage)
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
  }
  def sendAccountVerificationResponseEchannel(myID: java.math.BigDecimal, myAccountVerificationData: AccountVerificationDetailsResponse_BatchData, strChannelType: String, strApiURL: String): Unit = {

    //var strApiURL: String = "http://localhost:9001/addaccountverificationreesponsebcbs"
    var isSuccessful: Boolean = false
    var isValidData: Boolean = false
    var myjsonData: String = ""
    val strApifunction: String = "sendAccountVerificationResponseEchannel"

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

    try{
      val dateToCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
      //var strRequestData: String = ""
      val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Posted_to_CbsApi_Out] = 1, [Post_picked_CbsApi_Out] = 1, [RequestMessage_CbsApi_Out] = '" + myjsonData + "', [Date_to_CbsApi_Out] = '" + dateToCbsApi + "' where [ID] = " + myID + ";"
      insertUpdateRecord(strSQL)

      log_data(strApifunction + " : " + " channeltype - "  + strChannelType + " , >> outgoing request >> - " + myjsonData + " , ID - " + myID)
    }
    catch{
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage())
      case io: IOException =>
        log_errors(strApifunction + " : " + io.getMessage())
      case tr: Throwable =>
        log_errors(strApifunction + " : " + tr.getMessage())
    }

    try
    {
      if (isValidData){
        val data = HttpEntity(ContentType(MediaTypes.`application/json`), myjsonData)
        val myEntryID: Future[java.math.BigDecimal] = Future(myID)
        //val requestData : Future[String] = Future(myjsonData)
        //var start_time_DB : String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
        //val myStart_time : Future[String] = Future(start_time_DB)
        //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data))
        val accessToken: String = "hsbjbahvs7ahvshvshv"
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = myuri, entity = data).withHeaders(RawHeader("Authorization","Bearer " + accessToken)))
        val myChannelType: Future[String] = Future(strChannelType)

        //TESTS ONLY
        //println("start 1: " + strApifunction + " " + start_time_DB+ " " + myjsonData)

        responseFuture
          .onComplete {
            case Success(res) =>
              //println("start 2: " + strApifunction + " " + res.status.intValue())
              val dateFromCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
              var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
              var myHttpStatusCode: Int = 0
              var mystatuscode: Int = 1
              var strStatusMessage: String = "Failed processing"
              var strResponseData: String = ""
              var strChannelType: String = ""

              if (res.status != None){
                if (res.status.intValue() == 200){
                  var strstatuscode: String = ""
                  var strstatusdescription: String = ""
                  var strRequestData: String = ""
                  val myData = Unmarshal(res.entity).to[TransactionResponse]

                  if (myData != None){
                    if (myData.value.getOrElse(None) != None){
                      val myTransactionResponse =  myData.value.get
                      if (myTransactionResponse.get != None){
                        strResponseData = myTransactionResponse.toString()
                        
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

                      myHttpStatusCode = res.status.intValue()
                      strStatusMessage = strstatusdescription
                    }
                    else {
                      //Lets log the status code returned by CBS webservice
                      myHttpStatusCode = res.status.intValue()
                      strStatusMessage = "Failure occured. No request was received the from API"
                      
                      var txnID: java.math.BigDecimal = new java.math.BigDecimal(0)

                      if (myEntryID.value.isEmpty != true){
                        if (myEntryID.value.get != None){
                          val myVal = myEntryID.value.get
                          if (myVal.get != None){
                            txnID = myVal.get
                          }
                        }
                      }
                      
                      val strMessage: String = "txnid - " + txnID + ", status - " + myHttpStatusCode + ", status message - " + strStatusMessage
                      log_errors(strApifunction + " : " + strMessage + " - myData.value.getOrElse(None) != None error occured.")

                    }
                  }

                  myHttpStatusCode = res.status.intValue()
                  strStatusMessage = "Successful"

                }
                else{
                  myHttpStatusCode = res.status.intValue()
                  strStatusMessage = "Failed processing"
                }
              }

              if (myEntryID.value.isEmpty != true){
                if (myEntryID.value.get != None){
                  val myVal = myEntryID.value.get
                  if (myVal.get != None){
                    myID = myVal.get
                  }
                }
              }

              if (myChannelType.value.isEmpty != true){
                if (myChannelType.value.get != None){
                  val myVal = myChannelType.value.get
                  if (myVal.get != None){
                    strChannelType = myVal.get
                  }
                }
              }

              val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Response_Received_CbsApi_Out] = 1, [HttpStatusCode_CbsApi_Out] = " + myHttpStatusCode + 
              ", [StatusCode_CbsApi_Out] = " + mystatuscode + ", [StatusMessage_CbsApi_Out] = '" + strStatusMessage +
              "', [ResponseMessage_CbsApi_Out] = '" + strResponseData + 
              "', [Date_from_CbsApi_Out] = '" + dateFromCbsApi + "' where [ID] = " + myID + ";"
              insertUpdateRecord(strSQL)
              //println("myTxnID - " + myTxnID)
              //println("strSQL - " + strSQL)
              log_data(strApifunction + " : " + " channeltype - "    + strChannelType + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode)

            case Failure(f)   =>
              try {
                val dateFromCbsApi: String  =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date)
                var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
                val strHttpErrorMessage: String = f.getMessage
                val strResponseData: String = ""
                //log_errors(strApifunction + " : Failure - " + f.getMessage + " - ex exception error occured.")

                if (myEntryID.value.isEmpty != true){
                  if (myEntryID.value.get != None){
                    val myVal = myEntryID.value.get
                    if (myVal.get != None){
                      myID = myVal.get
                    }
                  }
                }

                val myHttpStatusCode: Int = 500
                val strStatusMessage: String = "Failure occured when sending the request to API. " + strHttpErrorMessage

                val strSQL: String = "update [dbo].[OutgoingAccountVerificationDetails] set [Response_Received_CbsApi_Out] = 1, [HttpStatusCode_CbsApi_Out] = " + myHttpStatusCode + 
                ", [StatusCode_CbsApi_Out] = 1, [StatusMessage_CbsApi_Out] = '" + strStatusMessage +
                "', [Date_from_CbsApi_Out] = '" + dateFromCbsApi + "' where [ID] = " + myID + ";"
                insertUpdateRecord(strSQL)

                log_data(strApifunction + " : " + " channeltype - echannel"  + " , << incoming response << - " + strResponseData + " , ID - " + myID + " , httpstatuscode - " + myHttpStatusCode + " , httperrormessage - " + strHttpErrorMessage)
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
  def getAccountVerificationDetails(accountVerificationDetails: AccountVerificationDetails, isAccSchemeName: Boolean) : String = {

    var strOutput: String = ""
    try {
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
      val accountVerification = new AccountVerification(assignmentInformation, verificationInformation, isAccSchemeName)
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
  def getSingleCreditTransferDetails(creditTransferPaymentInfo: SingleCreditTransferPaymentInfo, isAccSchemeName: Boolean) : String = {

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
      val ultimatedebtorinformationdebtorname: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitcontactinformation.fullnames//"paul wakimani"
      val ultimatedebtorinformationdebtororganisationidentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val ultimatedebtorinformationdebtorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
      val initiatingpartyinformationorganisationidentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val debtorinformationdebtorname: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitcontactinformation.fullnames//"paul wakimani"
      val debtorinformationdebtororganisationidentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val debtorinformationdebtorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
      val debtoraccountinformationdebtoraccountidentification: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitaccountnumber//"0711000000"
      val debtoraccountinformationdebtoraccountschemename: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.schemename//"PHNE"
      val debtoraccountinformationdebtoraccountname: String = creditTransferPaymentInfo.paymentdata.debitaccountinformation.debitaccountname//"paul wakimani"
      val debtoragentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val creditoragentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      //val creditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val creditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditcontactinformation.fullnames//"Nancy Mbera"
      val creditorinformationcreditororganisationidentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      val creditorinformationcreditorcontactphonenumber: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
      val creditoraccountinformationcreditoraccountidentification: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountnumber//"0756000000"
      val creditoraccountinformationcreditoraccountschemename: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.schemename//"PHNE"
      val creditoraccountinformationcreditoraccountname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      //val ultimatecreditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val ultimatecreditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditcontactinformation.fullnames//"Nancy Mbera"
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

      val singleCreditTransfer = new SingleCreditTransfer(groupHeaderInformation, creditTransferTransactionInformation, isAccSchemeName)

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
  def getBulkCreditTransferDetails(creditTransferPaymentInfo: BulkCreditTransferPaymentInfo, isAccSchemeName: Boolean) : String = {

    var strOutput: String = ""
    try {
      val transferdefaultinformation: TransferDefaultInfo = creditTransferPaymentInfo.transferdefaultinformation
      val messageidentification: String = creditTransferPaymentInfo.messagereference//"001"
      val creationdatetime: String = creditTransferPaymentInfo.creationdatetime//"2021-03-27"
      val numberoftransactions: Int = creditTransferPaymentInfo.numberoftransactions
      val totalinterbanksettlementamount: BigDecimal = creditTransferPaymentInfo.totalinterbanksettlementamount
      val settlementmethod: String = transferdefaultinformation.settlementmethod//"CLRG"
      val clearingSystem: String = transferdefaultinformation.clearingsystem//"IPS"
      val servicelevel: String = transferdefaultinformation.servicelevel//"P2PT"
      val localinstrumentcode: String = transferdefaultinformation.localinstrumentcode//"INST"
      val categorypurpose: String = transferdefaultinformation.categorypurpose//"IBNK"
      val instructingagentinformationfinancialInstitutionIdentification: String = transferdefaultinformation.firstagentidentification//"2000"
      val instructedagentinformationfinancialInstitutionIdentification: String = transferdefaultinformation.assigneeagentidentification//"1990"
      
      val settlementinformation: SettlementInformation = SettlementInformation(settlementmethod, clearingSystem)
      val paymenttypeinformation: PaymentTypeInformation = PaymentTypeInformation(servicelevel, localinstrumentcode, categorypurpose)
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation(instructingagentinformationfinancialInstitutionIdentification)
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation(instructedagentinformationfinancialInstitutionIdentification)
      val groupHeaderInformation = GroupHeaderInformation(
        messageidentification, creationdatetime, numberoftransactions, totalinterbanksettlementamount,
        settlementinformation, paymenttypeinformation,
        instructingagentinformation, instructedagentinformation
      )
      
      val creditTransfertransactioninformationbatch = getBulkPaymentInfo(creditTransferPaymentInfo.paymentdata, transferdefaultinformation, creationdatetime)
      val bulkCreditTransfer = new BulkCreditTransfer(groupHeaderInformation, creditTransfertransactioninformationbatch, isAccSchemeName)

      val myData = bulkCreditTransfer.toXml
      strOutput = myData.toString()
    }catch {
      case ex: Exception =>
        log_errors("getBulkCreditTransferDetails : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getBulkCreditTransferDetails : " + t.getMessage + " exception error occured.")
    }

    strOutput
  }
  def getBulkPaymentInfo(myBulkPaymentInfo: Seq[BulkPaymentInfo], transferdefaultinformation: TransferDefaultInfo, creationdatetime: String) : Seq[CreditTransferTransactionInformation] = {
    var creditTransfertransactioninformationbatch = Seq[CreditTransferTransactionInformation]()
    try {
      if (!myBulkPaymentInfo.isEmpty){
        if (myBulkPaymentInfo.length > 0){
          myBulkPaymentInfo.foreach(creditTransferPaymentInfo => {

            val paymentendtoendidentification: String = creditTransferPaymentInfo.transactionreference//"2031203220210120095543e10b05af"
            val interbanksettlementamount: BigDecimal = creditTransferPaymentInfo.amount//6545.56
            val acceptancedatetime: String = creationdatetime//"2021-03-27"
            val chargebearer: String = transferdefaultinformation.chargebearer//"SLEV"
            val mandateidentification: String = creditTransferPaymentInfo.mandateinformation.mandateidentification//""
            val ultimatedebtorinformationdebtorname: String = creditTransferPaymentInfo.debitaccountinformation.debitcontactinformation.fullnames//"paul wakimani"
            val ultimatedebtorinformationdebtororganisationidentification: String = transferdefaultinformation.firstagentidentification//"2000"
            val ultimatedebtorinformationdebtorcontactphonenumber: String = creditTransferPaymentInfo.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
            val initiatingpartyinformationorganisationidentification: String = transferdefaultinformation.firstagentidentification//"2000"
            val debtorinformationdebtorname: String = creditTransferPaymentInfo.debitaccountinformation.debitcontactinformation.fullnames//"paul wakimani"
            val debtorinformationdebtororganisationidentification: String = transferdefaultinformation.firstagentidentification//"2000"
            val debtorinformationdebtorcontactphonenumber: String = creditTransferPaymentInfo.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
            val debtoraccountinformationdebtoraccountidentification: String = creditTransferPaymentInfo.debitaccountinformation.debitaccountnumber//"0711000000"
            val debtoraccountinformationdebtoraccountschemename: String = creditTransferPaymentInfo.debitaccountinformation.schemename//"PHNE"
            val debtoraccountinformationdebtoraccountname: String = creditTransferPaymentInfo.debitaccountinformation.debitaccountname//"paul wakimani"
            val debtoragentinformationfinancialInstitutionIdentification: String = transferdefaultinformation.firstagentidentification//"2000"
            val creditoragentinformationfinancialInstitutionIdentification: String = creditTransferPaymentInfo.creditaccountinformation.bankcode//"1990"
            //val creditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
            val creditorinformationcreditorname: String = creditTransferPaymentInfo.creditaccountinformation.creditcontactinformation.fullnames//"Nancy Mbera"
            val creditorinformationcreditororganisationidentification: String = creditTransferPaymentInfo.creditaccountinformation.bankcode//"1990"
            val creditorinformationcreditorcontactphonenumber: String = creditTransferPaymentInfo.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
            val creditoraccountinformationcreditoraccountidentification: String = creditTransferPaymentInfo.creditaccountinformation.creditaccountnumber//"0756000000"
            val creditoraccountinformationcreditoraccountschemename: String = creditTransferPaymentInfo.creditaccountinformation.schemename//"PHNE"
            val creditoraccountinformationcreditoraccountname: String = creditTransferPaymentInfo.creditaccountinformation.creditaccountname//"Nancy Mbera"
            //val ultimatecreditorinformationcreditorname: String = creditTransferPaymentInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
            val ultimatecreditorinformationcreditorname: String = creditTransferPaymentInfo.creditaccountinformation.creditcontactinformation.fullnames//"Nancy Mbera"
            val ultimatecreditorinformationcreditororganisationidentification: String = creditTransferPaymentInfo.creditaccountinformation.bankcode//"1990"
            val ultimatecreditorinformationcreditorcontactphonenumber: String = creditTransferPaymentInfo.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
            val purposeinformationpurposecode: String = creditTransferPaymentInfo.purposeinformation.purposecode//""
            val remittanceinformationunstructured: String = creditTransferPaymentInfo.remittanceinformation.unstructured//""
            val remittanceinformationtaxremittancereferencenumber: String = creditTransferPaymentInfo.remittanceinformation.taxremittancereferencenumber//""

            //Credit Transfer Transaction Information
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
            creditTransfertransactioninformationbatch = creditTransfertransactioninformationbatch :+ creditTransferTransactionInformation
          })
        }
      }  
    }catch {
      case ex: Exception =>
        log_errors("getBulkPaymentInfo : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getBulkPaymentInfo : " + t.getMessage + " exception error occured.")
    }
    creditTransfertransactioninformationbatch
  }
  def getBulkCreditTransferDetails_old() : String = {

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

      val bulkCreditTransfer = new BulkCreditTransfer(groupHeaderInformation, creditTransfertransactioninformationbatch, false)

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
  def getPaymentCancellationDetails(paymentCancellationInfo: SinglePaymentCancellationInfo) : String = {

    var strOutput: String = ""
    try {
      val messageidentification: String = paymentCancellationInfo.messagereference
      val creationdatetime: String = paymentCancellationInfo.creationdatetime
      val numberoftransactions: Int = paymentCancellationInfo.numberoftransactions
      val requestdatetime: String = creationdatetime
      //Details of the original transactions being cancelled
      val cancellationidentification: String = paymentCancellationInfo.paymentdata.transactionreference
      //Original Group Information And Status
      val originalmessageidentification: String = paymentCancellationInfo.paymentdata.originalgroupinformation.originalmessageidentification
      val originalmessagenameidentification: String = paymentCancellationInfo.paymentdata.originalgroupinformation.originalmessagenameidentification
      val originalcreationdatetime: String = paymentCancellationInfo.paymentdata.originalgroupinformation.originalcreationdatetime
      val originalendtoendidentification: String = paymentCancellationInfo.paymentdata.originalgroupinformation.originalendtoendidentification
      val transactioncancellationstatus: String = ""
      //Cancellation status reason Information
      val originatorname: String = paymentCancellationInfo.paymentdata.cancellationstatusreasoninformation.originatorname
      val reasoncode: String = paymentCancellationInfo.paymentdata.cancellationstatusreasoninformation.reasoncode
      val additionalinformation: String = paymentCancellationInfo.paymentdata.cancellationstatusreasoninformation.additionalinformation
      //val totalinterbanksettlementamount: BigDecimal = paymentCancellationInfo.totalinterbanksettlementamount
      val settlementmethod: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.settlementmethod//"CLRG"
      val clearingSystem: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.clearingsystem//"IPS"
      val servicelevel: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.servicelevel//"P2PT"
      val localinstrumentcode: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.localinstrumentcode//"INST"
      val categorypurpose: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.categorypurpose//"IBNK"
      val instructingagentinformationfinancialInstitutionIdentification: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val instructedagentinformationfinancialInstitutionIdentification: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.assigneeagentidentification//"1990"
      //val paymentendtoendidentification: String = paymentCancellationInfo.paymentdata.transactionreference//"2031203220210120095543e10b05af"
      //val interbanksettlementamount: BigDecimal = paymentCancellationInfo.paymentdata.amount//6545.56
      val interbanksettlementamount: String = paymentCancellationInfo.paymentdata.amount.toString()
      val acceptancedatetime: String = paymentCancellationInfo.creationdatetime//"2021-03-27"
      val chargebearer: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.chargebearer//"SLEV"
      val mandateidentification: String = paymentCancellationInfo.paymentdata.mandateinformation.mandateidentification//""
      val ultimatedebtorinformationdebtorname: String = paymentCancellationInfo.paymentdata.debitaccountinformation.debitcontactinformation.fullnames//"paul wakimani"
      val ultimatedebtorinformationdebtororganisationidentification: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val ultimatedebtorinformationdebtorcontactphonenumber: String = paymentCancellationInfo.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
      val initiatingpartyinformationorganisationidentification: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val debtorinformationdebtorname: String = paymentCancellationInfo.paymentdata.debitaccountinformation.debitcontactinformation.fullnames//"paul wakimani"
      val debtorinformationdebtororganisationidentification: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val debtorinformationdebtorcontactphonenumber: String = paymentCancellationInfo.paymentdata.debitaccountinformation.debitcontactinformation.phonenumber//"0711000000"
      val debtoraccountinformationdebtoraccountidentification: String = paymentCancellationInfo.paymentdata.debitaccountinformation.debitaccountnumber//"0711000000"
      val debtoraccountinformationdebtoraccountschemename: String = paymentCancellationInfo.paymentdata.debitaccountinformation.schemename//"PHNE"
      val debtoraccountinformationdebtoraccountname: String = paymentCancellationInfo.paymentdata.debitaccountinformation.debitaccountname//"paul wakimani"
      val debtoragentinformationfinancialInstitutionIdentification: String = paymentCancellationInfo.paymentdata.transferdefaultinformation.firstagentidentification//"2000"
      val creditoragentinformationfinancialInstitutionIdentification: String = paymentCancellationInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      //val creditorinformationcreditorname: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val creditorinformationcreditorname: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditcontactinformation.fullnames//"Nancy Mbera"
      val creditorinformationcreditororganisationidentification: String = paymentCancellationInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      val creditorinformationcreditorcontactphonenumber: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
      val creditoraccountinformationcreditoraccountidentification: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditaccountnumber//"0756000000"
      val creditoraccountinformationcreditoraccountschemename: String = paymentCancellationInfo.paymentdata.creditaccountinformation.schemename//"PHNE"
      val creditoraccountinformationcreditoraccountname: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      //val ultimatecreditorinformationcreditorname: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditaccountname//"Nancy Mbera"
      val ultimatecreditorinformationcreditorname: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditcontactinformation.fullnames//"Nancy Mbera"
      val ultimatecreditorinformationcreditororganisationidentification: String = paymentCancellationInfo.paymentdata.creditaccountinformation.bankcode//"1990"
      val ultimatecreditorinformationcreditorcontactphonenumber: String = paymentCancellationInfo.paymentdata.creditaccountinformation.creditcontactinformation.phonenumber//"0756000000"
      val purposeinformationpurposecode: String = paymentCancellationInfo.paymentdata.purposeinformation.purposecode//""
      val remittanceinformationunstructured: String = paymentCancellationInfo.paymentdata.remittanceinformation.unstructured//""
      val remittanceinformationtaxremittancereferencenumber: String = paymentCancellationInfo.paymentdata.remittanceinformation.taxremittancereferencenumber//""
      val requestExecutionDateTime: RequestExecutionDateTime = RequestExecutionDateTime(requestdatetime)
      val settlementInformation: SettlementInformation = SettlementInformation(settlementmethod, clearingSystem)
      val paymentTypeInformation: PaymentTypeInformation = PaymentTypeInformation(servicelevel, localinstrumentcode, categorypurpose)
      val instructingagentinformation: InstructingAgentInformation = InstructingAgentInformation(instructingagentinformationfinancialInstitutionIdentification)
      val instructedagentinformation: InstructedAgentInformation = InstructedAgentInformation(instructedagentinformationfinancialInstitutionIdentification)
      val cancellationAssignmentInformation = CancellationAssignmentInformation(messageidentification, creationdatetime, instructingagentinformation, instructedagentinformation)
      val originalGroupInformationAndStatus = OriginalGroupInformationAndStatus(originalmessageidentification, originalmessagenameidentification,  originalcreationdatetime)
      
      val mandateRelatedInformation: MandateRelatedInformation = MandateRelatedInformation(mandateidentification)
      val ultimateDebtorInformation: UltimateDebtorInformation = UltimateDebtorInformation(ultimatedebtorinformationdebtorname, ultimatedebtorinformationdebtororganisationidentification, ultimatedebtorinformationdebtorcontactphonenumber)
      val initiatingpartyinformation: InitiatingPartyInformation = InitiatingPartyInformation(initiatingpartyinformationorganisationidentification)
      val debtorInformation: DebtorInformation = DebtorInformation(debtorinformationdebtorname, debtorinformationdebtororganisationidentification, debtorinformationdebtorcontactphonenumber)
      val debtorAccountInformation: DebtorAccountInformation = DebtorAccountInformation(debtoraccountinformationdebtoraccountidentification, debtoraccountinformationdebtoraccountschemename, debtoraccountinformationdebtoraccountname)
      val debtorAgentInformation: DebtorAgentInformation = DebtorAgentInformation(debtoragentinformationfinancialInstitutionIdentification)
      val creditorAgentInformation: CreditorAgentInformation = CreditorAgentInformation(creditoragentinformationfinancialInstitutionIdentification)
      val creditorInformation: CreditorInformation = CreditorInformation(creditorinformationcreditorname, creditorinformationcreditororganisationidentification, creditorinformationcreditorcontactphonenumber)
      val creditorAccountInformation: CreditorAccountInformation = CreditorAccountInformation(creditoraccountinformationcreditoraccountidentification, creditoraccountinformationcreditoraccountschemename, creditoraccountinformationcreditoraccountname)
      val ultimateCreditorInformation: UltimateCreditorInformation = UltimateCreditorInformation(ultimatecreditorinformationcreditorname, ultimatecreditorinformationcreditororganisationidentification, ultimatecreditorinformationcreditorcontactphonenumber)
      val purposeInformation: PurposeInformation = PurposeInformation(purposeinformationpurposecode)
      val remittanceInformation: RemittanceInformation = RemittanceInformation(remittanceinformationunstructured, remittanceinformationtaxremittancereferencenumber)
      val cancellationStatusReasonInformation: CancellationStatusReasonInformation = CancellationStatusReasonInformation(originatorname, reasoncode, additionalinformation)
      val originalTransactionReference: OriginalTransactionReference = OriginalTransactionReference(interbanksettlementamount, requestExecutionDateTime,
        settlementInformation, paymentTypeInformation,
        mandateRelatedInformation, remittanceInformation,
        ultimateDebtorInformation, debtorInformation,
        debtorAccountInformation, debtorAgentInformation,
        creditorAgentInformation, creditorInformation,
        creditorAccountInformation, ultimateCreditorInformation,
        purposeInformation)
      val cancellationTransactionInformationAndStatus: CancellationTransactionInformationAndStatus = CancellationTransactionInformationAndStatus(cancellationidentification, originalGroupInformationAndStatus, originalendtoendidentification, transactioncancellationstatus,
        cancellationStatusReasonInformation, originalTransactionReference)
      val cancellationDetails = CancellationDetails(cancellationTransactionInformationAndStatus)

      val paymentCancellation = new PaymentCancellation(cancellationAssignmentInformation, cancellationDetails)
      val myData = paymentCancellation.toXml
      strOutput = myData.toString()
    }catch {
      case ex: Exception =>
        log_errors("getPaymentCancellationDetails : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getPaymentCancellationDetails : " + t.getMessage + " exception error occured.")
    }

    strOutput
  }
  def getSignatureId(requestType: String) : String = {
    var signatureId: String = ""
    val strApifunction: String = "getSignatureId"

    //val strSQL : String = "{ call dbo.GetReturnDate_BVS(?) }"
    try {
      if (requestType == null) return signatureId
      if (requestType.replace(" ","").trim.length == 0) return signatureId
      /*
      if (requestType.equalsIgnoreCase("accountverification")){
        signatureId = "_4614c57e-40ae-4cc2-aeb5-6e93ba1be1eb"
      }

      if (requestType.equalsIgnoreCase("singlecredittransfer")){
        signatureId = ""
      }

      if (requestType.equalsIgnoreCase("bulkcredittransfer")){
        signatureId = ""
      }
      */
      signatureId = {
        requestType.replace(" ","").trim.toLowerCase match {
          case "accountverification" => "_4614c57e-40ae-4cc2-aeb5-6e93ba1be1eb"
          case "singlecredittransfer" => "_4614c57e-40ae-4cc2-aeb5-6e93ba1be1eb"
          case "bulkcredittransfer" => ""
          case _ => ""
        }
      }
      /*
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
      */
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " requestType - " + requestType)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " requestType - " + requestType)
    }

    signatureId
  }
  def getDigestValue(requestData: String) : String = {
    val strApifunction: String = "getDigestValue"

    try {
      if (requestData == null) return ""
      if (requestData.replace(" ","").trim.length == 0) return ""
      /*
      val byteArray = requestData.getBytes
      val messageHash = messageDigest.digest(byteArray)
      digestValue = new String(messageHash)
      */
      //println("digestValue - " + digestValue)
      //import java.nio.charset.StandardCharsets
      //val str = new String(messageHash, StandardCharsets.UTF_8)
      //println("digestValue 2 - " + str)
      val messageHash = getMessageHash(requestData)
      val digestValue: String = new String(messageHash)
	  println("digestValue - " + digestValue)
      return digestValue
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " requestData - " + requestData)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " requestData - " + requestData)
    }

    return ""
  }
  def getReferenceURI(keyInfoId: String) : String = {
    var referenceURI: String = ""
    val strApifunction: String = "getReferenceURI"

    //val strSQL : String = "{ call dbo.GetReturnDate_BVS(?) }"
    try {
      //if (requestType == null) return referenceURI
      //if (requestType.replace(" ","").trim.length == 0) return referenceURI
      /*
      if (requestType.equalsIgnoreCase("accountverification")){
        signatureId = "_4614c57e-40ae-4cc2-aeb5-6e93ba1be1eb"
      }

      if (requestType.equalsIgnoreCase("singlecredittransfer")){
        signatureId = ""
      }

      if (requestType.equalsIgnoreCase("bulkcredittransfer")){
        signatureId = ""
      }
      */
      /*
      referenceURI = {
        requestType.replace(" ","").trim.toLowerCase match {
          case "accountverification" => "#_8401036a-cd29-4f5b-a48a-9ecf4d515d98"
          case "singlecredittransfer" => "#_8401036a-cd29-4f5b-a48a-9ecf4d515d98"
          case "bulkcredittransfer" => ""
          case _ => ""
        }
      }
      */
      referenceURI = "#" + keyInfoId
      /*
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
      */
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " keyInfoId - " + keyInfoId)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " keyInfoId - " + keyInfoId)
    }

    referenceURI
  }
  def getSignatureValue(requestData: String) : Array[Byte] = {
    val strApifunction: String = "getSignatureValue"

    try {
      if (requestData == null) return null
      if (requestData.replace(" ","").trim.length == 0) return null

      val messageHash = getMessageHash(requestData)
      val cipher = Cipher.getInstance(encryptionAlgorithm)

      cipher.init(Cipher.ENCRYPT_MODE, privateKey)
      val signatureValue = cipher.doFinal(messageHash)
      return signatureValue
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " requestData - " + requestData)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " requestData - " + requestData)
    }

    return null
  }
  def getKeyInfoId() : String = {
    var keyInfoId: String = ""
    val strApifunction: String = "getKeyInfoId"
    //requestType: String
    //val strSQL : String = "{ call dbo.GetReturnDate_BVS(?) }"
    try {
      //if (requestType == null) return keyInfoId
      //if (requestType.replace(" ","").trim.length == 0) return keyInfoId
      /*
      if (requestType.equalsIgnoreCase("accountverification")){
        signatureId = "_4614c57e-40ae-4cc2-aeb5-6e93ba1be1eb"
      }

      if (requestType.equalsIgnoreCase("singlecredittransfer")){
        signatureId = ""
      }

      if (requestType.equalsIgnoreCase("bulkcredittransfer")){
        signatureId = ""
      }
      */
      /*
      keyInfoId = {
        requestType.replace(" ","").trim.toLowerCase match {
          case "accountverification" => "_8401036a-cd29-4f5b-a48a-9ecf4d515d98"
          case "singlecredittransfer" => "_8401036a-cd29-4f5b-a48a-9ecf4d515d98"
          case "bulkcredittransfer" => ""
          case _ => ""
        }
      }
      */
      keyInfoId = UUID.randomUUID.toString
      /*
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
      */
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " keyInfoId - " + keyInfoId)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " keyInfoId - " + keyInfoId)
    }

    keyInfoId
  }
  def getX509Certificate() : String = {
    var strX509Certificate: String = ""
    val strApifunction: String = "getX509Certificate"

    //val strSQL : String = "{ call dbo.GetReturnDate_BVS(?) }"
    try {
      //if (requestType == null) return strX509Certificate
      //if (requestType.replace(" ","").trim.length == 0) return strX509Certificate
      /*
      if (requestType.equalsIgnoreCase("accountverification")){
        signatureId = "_4614c57e-40ae-4cc2-aeb5-6e93ba1be1eb"
      }

      if (requestType.equalsIgnoreCase("singlecredittransfer")){
        signatureId = ""
      }

      if (requestType.equalsIgnoreCase("bulkcredittransfer")){
        signatureId = ""
      }
      */
      /* contents of public certificate are assigned to var certificateData */
	    val messageDigest = MessageDigest.getInstance(messageHashAlgorithm)
      //val certificateData: String = "MIICuzCCAaOgAwIBAgIEDHHXijANBgkqhkiG9w0BAQsFADAOMQwwCgYDVQQDEwNE\nTVQwHhcNMjEwNDIxMDgxMzAyWhcNMjIwNDIxMDgxMzAyWjAOMQwwCgYDVQQDEwNE\nTVQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCyH2Pko8qpq+UKuJiU\n4kGz9tl8+6vaQtrHy+Pt6QqNi1y3L7qxCSkdRicQFJYRxLBwlz7Ri5C0D7GUCQ7o\n3/iKTQZX/yz/VhS7lVNi0K6zAvckBhgTJc0xJGgjSQgHuWKY7IYqiCIZyOvjPAb3\nFw2UWaeCEiLojG1z4Q4kNrGm8smmL4E51Y55N6AmJ/KOh/o9QrTa37dwiaKmdXbw\n2ZjrIHe3c7rQl4oSaeTq4AolbM9GUGywreZTHROb8IT0/pjymrdlmx1WA25acrAx\nj8tT6edDoNSqDvv9y6Tvxh1CxJiwZZCOlb8PVCFrhqSgUspx8fFQQ1ZFHpzr7K8l\nE3xRAgMBAAGjITAfMB0GA1UdDgQWBBTTbHcCrp0I71O7mV6qfwB0hchXSjANBgkq\nhkiG9w0BAQsFAAOCAQEAbswVclSDdPW0UHMAHjhZc1kjUH1YYgq7fl8FU9ovk0d4\ncyJ5kW62OS+1V+JE+4TNNcQKFFdQzW+/hev42aXIWzjSosWDQ727mHt3oQgfohMf\nRTCq04LxLVVeEaFMnbpP4eWhCBfV7pJlo/DeILsa2UuonyIaDV4hxBTzRu3d6r1l\nVk/KM7nGUEWTRaU/jq1M6W5SY+hgyNHsD/vz8lIItSprkcL2lFFmweR5RVBWeXzD\nZyWvELHOH9CRZi0x6jNIIM14J9CIIW6Zubd+bpwEcPDVJfVX0FxT7XnPK1fGU1Q/\nZQnP838LmG3FOz5Q0VvWTommrktY45QfX0uSvGR52w=="
      //seal certificate that was signed by ipsl i.e bank0074_seal.cert.pem
      val strCertPath: String = "certsconf/bank0074_seal.cert.pem"
      val myInputStream: InputStream = getResourceStream(strCertPath)
      val certificateData: String = scala.io.Source.fromInputStream(myInputStream).mkString
      //TESTS ONLY
      //val byteArray = certificateData.getBytes
      //val messageHash = messageDigest.digest(byteArray)
      //strX509Certificate = new String(messageHash)
      //TESTS ONLY
      strX509Certificate = certificateData
      //println("certificateData - " + certificateData)
      //println("X509Certificate - " + strX509Certificate)
      /*
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
      */
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured.")
    }

    strX509Certificate
  }
  def getSenderKeyStore() : KeyStore = {
    val strApifunction: String = "getSenderKeyStore"

    try {
      val keyStore: KeyStore = KeyStore.getInstance(keystore_type)
      keyStore.load(new FileInputStream(sender_keystore_path), senderKeyStorePwdCharArray)
      return keyStore
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured.")
    }

    return null
  }
  def getPrivateKey() : PrivateKey = {
    val strApifunction: String = "getPrivateKey"

    try {
      val senderKeyStore: KeyStore = getSenderKeyStore()
      val privateKey: PrivateKey = senderKeyStore.getKey(senderKeyPairName, senderKeyStorePwdCharArray).asInstanceOf[PrivateKey]
      //TEST ONLY
      //val privateKey: PrivateKey = senderKeyStore.getKey("1", senderKeyStorePwdCharArray).asInstanceOf[PrivateKey]
      //val strCaChainCertPath: String = "certsconf/ca_chain.crt.pem"
      //senderKeyStore.setCertificateEntry("ca", loadX509Certificate(strCaChainCertPath))
      //val privateKey: PrivateKey = senderKeyStore.getKey("1", senderKeyStorePwdCharArray).asInstanceOf[PrivateKey]
      return privateKey
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured.")
    }

    return null
  }
  def getReceiverKeyStore() : KeyStore = {
    val strApifunction: String = "getReceiverKeyStore"

    try {
      val keyStore: KeyStore = KeyStore.getInstance(keystore_type)
      keyStore.load(new FileInputStream(receiver_keystore_path), receiverKeyStorePwdCharArray)
      return keyStore
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured.")
    }

    return null
  }
  def getPublicKey() : PublicKey = {
    val strApifunction: String = "getPublicKey"

    try {
      val receiverKeyStore: KeyStore = getReceiverKeyStore()
      val certificate = receiverKeyStore.getCertificate(receiverKeyPairName)
      val publicKey = certificate.getPublicKey
      return publicKey
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured.")
    }

    return null
  }
  def getMessageHash(messageData: String) : Array[Byte] = {
    val strApifunction: String = "getMessageHash"

    try {
      if (messageData == null) return null
      if (messageData.replace(" ","").trim.length == 0) return null

	    val messageDigest = MessageDigest.getInstance(messageHashAlgorithm)
      val byteArray = messageData.getBytes
      val messageHash = messageDigest.digest(byteArray)
      return messageHash
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " messageData - " + messageData)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " messageData - " + messageData)
    }

    return null
  }
  def decryptedSignatureValue(messageData: Array[Byte]) : Array[Byte] = {
    val strApifunction: String = "decryptedSignatureValue"

    try {
      if (messageData == null) return null

      val cipher = Cipher.getInstance(encryptionAlgorithm)

      cipher.init(Cipher.DECRYPT_MODE, publicKey)
      val decryptedMessageHash = cipher.doFinal(messageData)
      return decryptedMessageHash
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " requestData - " + messageData)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " requestData - " + messageData)
    }

    return null
  }
  def verifyMessageHash(originalMessageHash: Array[Byte], decryptedMessageHash: Array[Byte]) : Boolean = {
    val strApifunction: String = "verifyMessageHash"
    var isVerified: Boolean = false

    try {
      if (originalMessageHash == null) return isVerified
      if (decryptedMessageHash == null) return isVerified

      isVerified = java.util.Arrays.equals(decryptedMessageHash, originalMessageHash)
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " isVerified - " + isVerified)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " isVerified - " + isVerified)
    }

    return isVerified
  }
  private def toXmlSignatureInformation(SignatureId: String, myDigestValue: String, myReferenceURI: String, mySignatureValue: String, myKeyInfoId: String, myX509Certificate: String) = {
        <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id={SignatureId}>
          <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
            <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
            <ds:Reference URI="">
              <ds:Transforms>
                <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                <ds:Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
              </ds:Transforms>
              <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
              <ds:DigestValue>{myDigestValue}</ds:DigestValue>
            </ds:Reference>
            <ds:Reference URI={myReferenceURI}>
              <ds:Transforms>
                <ds:Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
              </ds:Transforms>
              <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
              <ds:DigestValue>{myDigestValue}</ds:DigestValue>
            </ds:Reference>
          </ds:SignedInfo>
          <ds:SignatureValue>{mySignatureValue}</ds:SignatureValue>
          <ds:KeyInfo Id={myKeyInfoId}>
            <ds:X509Data>
              <ds:X509Certificate>{myX509Certificate}</ds:X509Certificate>
            </ds:X509Data>
          </ds:KeyInfo>
        </ds:Signature>
    }
  def addOutgoingAccountVerificationDetails(myAccountVerificationTableDetails: AccountVerificationTableDetails, strChannelType: String, strChannelCallBackUrl: String): AccountVerificationTableResponseDetails = {
    val strApifunction: String = "addOutgoingAccountVerificationDetails"
    var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
    var responseCode: Int = 1
    var responseMessage: String = ""

    val strSQL : String = "{ call dbo.Add_OutgoingAccountVerificationDetails(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) }"
    try {
      myDB.withConnection { implicit myconn =>
        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("myID", java.sql.Types.BIGINT)
          mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
          mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
          mystmt.setBigDecimal(1, myAccountVerificationTableDetails.batchreference)
          mystmt.setString(2, myAccountVerificationTableDetails.accountnumber)
          mystmt.setString(3,myAccountVerificationTableDetails.bankcode)
          mystmt.setString(4,myAccountVerificationTableDetails.messagereference)
          mystmt.setString(5,myAccountVerificationTableDetails.transactionreference)
          mystmt.setString(6,myAccountVerificationTableDetails.schemename)
          mystmt.setInt(7,myAccountVerificationTableDetails.batchsize)
          mystmt.setString(8,myAccountVerificationTableDetails.requestmessagecbsapi)
          mystmt.setString(9,myAccountVerificationTableDetails.datefromcbsapi)
          mystmt.setString(10,myAccountVerificationTableDetails.remoteaddresscbsapi)
          mystmt.setString(11,strChannelType)
          mystmt.setString(12,strChannelCallBackUrl)
          mystmt.execute()
          myID = mystmt.getBigDecimal("myID")
          responseCode = mystmt.getInt("responseCode")
          responseMessage = mystmt.getString("responseMessage")
        }
        catch{
          case ex : Exception =>
            log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
          case t: Throwable =>
            log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
        }

      }
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
    }

    val myAccountVerificationTableResponseDetails = AccountVerificationTableResponseDetails(myID, responseCode, responseMessage)
    myAccountVerificationTableResponseDetails
  }
  def addOutgoingSingleCreditTransferPaymentDetails(mySingleCreditTransferPaymentTableDetails: SingleCreditTransferPaymentTableDetails, strChannelType: String, strChannelCallBackUrl: String): SingleCreditTransferPaymentTableResponseDetails = {
    val strApifunction: String = "addOutgoingSingleCreditTransferPaymentDetails"
    var myID: java.math.BigDecimal = new java.math.BigDecimal(0)
    var responseCode: Int = 1
    var responseMessage: String = ""

    val strSQL : String = "{ call dbo.Add_OutgoingSingleCreditTransferPaymentDetails(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) }"
    try {
      myDB.withConnection { implicit myconn =>
        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("myID", java.sql.Types.BIGINT)
          mystmt.registerOutParameter("responseCode", java.sql.Types.INTEGER)
          mystmt.registerOutParameter("responseMessage", java.sql.Types.VARCHAR)
          mystmt.setBigDecimal(1, mySingleCreditTransferPaymentTableDetails.batchreference)
          mystmt.setString(2, mySingleCreditTransferPaymentTableDetails.debtoraccountnumber)
          mystmt.setString(3,mySingleCreditTransferPaymentTableDetails.debtoraccountname)
          mystmt.setString(4,mySingleCreditTransferPaymentTableDetails.debtorbankcode)
          mystmt.setString(5,mySingleCreditTransferPaymentTableDetails.messagereference)
          mystmt.setString(6,mySingleCreditTransferPaymentTableDetails.transactionreference)
          mystmt.setString(7,mySingleCreditTransferPaymentTableDetails.debtorschemename)
          mystmt.setBigDecimal(8,mySingleCreditTransferPaymentTableDetails.amount)
          mystmt.setString(9,mySingleCreditTransferPaymentTableDetails.debtorfullnames)
          mystmt.setString(10,mySingleCreditTransferPaymentTableDetails.debtorphonenumber)
          mystmt.setString(11,mySingleCreditTransferPaymentTableDetails.creditoraccountnumber)
          mystmt.setString(12,mySingleCreditTransferPaymentTableDetails.creditoraccountname)
          mystmt.setString(13,mySingleCreditTransferPaymentTableDetails.creditorbankcode)
          mystmt.setString(14,mySingleCreditTransferPaymentTableDetails.creditorschemename)
          mystmt.setString(15,mySingleCreditTransferPaymentTableDetails.remittanceinfounstructured)
          mystmt.setString(16,mySingleCreditTransferPaymentTableDetails.taxremittancereferenceno)
          mystmt.setString(17,mySingleCreditTransferPaymentTableDetails.purposecode)
          mystmt.setString(18,mySingleCreditTransferPaymentTableDetails.chargebearer)
          mystmt.setString(19,mySingleCreditTransferPaymentTableDetails.mandateidentification)
          mystmt.setString(20,mySingleCreditTransferPaymentTableDetails.instructingagentbankcode)
          mystmt.setString(21,mySingleCreditTransferPaymentTableDetails.instructedagentbankcode)
          mystmt.setInt(22,mySingleCreditTransferPaymentTableDetails.batchsize)
          mystmt.setString(23,mySingleCreditTransferPaymentTableDetails.requestmessagecbsapi)
          mystmt.setString(24,mySingleCreditTransferPaymentTableDetails.datefromcbsapi)
          mystmt.setString(25,mySingleCreditTransferPaymentTableDetails.remoteaddresscbsapi)
          mystmt.setString(26,strChannelType)
          mystmt.setString(27,strChannelCallBackUrl)
          mystmt.execute()
          myID = mystmt.getBigDecimal("myID")
          responseCode = mystmt.getInt("responseCode")
          responseMessage = mystmt.getString("responseMessage")
        }
        catch{
          case ex : Exception =>
            log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
          case t: Throwable =>
            log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
        }
      }
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
    }

    val mySingleCreditTransferPaymentTableResponseDetails = SingleCreditTransferPaymentTableResponseDetails(myID, responseCode, responseMessage)
    mySingleCreditTransferPaymentTableResponseDetails
  } 
  def addOutgoingAccountVerificationDetailsArchive(responseCode: Int, responseMessage: String, responsemessagecbsapi: String, myAccountVerificationTableDetails: AccountVerificationTableDetails, strChannelType: String, strChannelCallBackUrl: String): Unit = {
    Future {
      val strApifunction: String = "addOutgoingAccountVerificationDetailsArchive"
      val strSQL : String = "{ call dbo.Add_OutgoingAccountVerificationDetails_Archive(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) }"
      try {
        myDB.withConnection { implicit myconn =>
          try{
            val mystmt: CallableStatement = myconn.prepareCall(strSQL)
            mystmt.setBigDecimal(1, myAccountVerificationTableDetails.batchreference)
            mystmt.setString(2, myAccountVerificationTableDetails.accountnumber)
            mystmt.setString(3,myAccountVerificationTableDetails.bankcode)
            mystmt.setString(4,myAccountVerificationTableDetails.messagereference)
            mystmt.setString(5,myAccountVerificationTableDetails.transactionreference)
            mystmt.setString(6,myAccountVerificationTableDetails.schemename)
            mystmt.setInt(7,myAccountVerificationTableDetails.batchsize)
            mystmt.setString(8,myAccountVerificationTableDetails.requestmessagecbsapi)
            mystmt.setString(9,myAccountVerificationTableDetails.datefromcbsapi)
            mystmt.setString(10,myAccountVerificationTableDetails.remoteaddresscbsapi)
            mystmt.setInt(11,responseCode)
            mystmt.setString(12,responseMessage)
            mystmt.setString(13,responsemessagecbsapi)
            mystmt.setString(14,strChannelType)
            mystmt.setString(15,strChannelCallBackUrl)
            mystmt.execute()
          }
          catch{
            case ex : Exception =>
              log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
            case t: Throwable =>
              log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
          }
        }
      }catch {
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
        case t: Throwable =>
          log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " accountnumber - " + myAccountVerificationTableDetails.accountnumber)
      }
    }
  }
  def addOutgoingSingleCreditTransferPaymentDetailsArchive(responseCode: Int, responseMessage: String, responsemessagecbsapi: String, mySingleCreditTransferPaymentTableDetails: SingleCreditTransferPaymentTableDetails, strChannelType: String, strChannelCallBackUrl: String): Unit = {
    Future {
      val strApifunction: String = "addOutgoingSingleCreditTransferPaymentDetailsArchive"
      val strSQL : String = "{ call dbo.Add_OutgoingSingleCreditTransferPaymentDetails_Archive(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) }"
      try {
        myDB.withConnection { implicit myconn =>
          try{
            val mystmt: CallableStatement = myconn.prepareCall(strSQL)
            mystmt.setBigDecimal(1, mySingleCreditTransferPaymentTableDetails.batchreference)
            mystmt.setString(2, mySingleCreditTransferPaymentTableDetails.debtoraccountnumber)
            mystmt.setString(3,mySingleCreditTransferPaymentTableDetails.debtoraccountname)
            mystmt.setString(4,mySingleCreditTransferPaymentTableDetails.debtorbankcode)
            mystmt.setString(5,mySingleCreditTransferPaymentTableDetails.messagereference)
            mystmt.setString(6,mySingleCreditTransferPaymentTableDetails.transactionreference)
            mystmt.setString(7,mySingleCreditTransferPaymentTableDetails.debtorschemename)
            mystmt.setBigDecimal(8,mySingleCreditTransferPaymentTableDetails.amount)
            mystmt.setString(9,mySingleCreditTransferPaymentTableDetails.debtorfullnames)
            mystmt.setString(10,mySingleCreditTransferPaymentTableDetails.debtorphonenumber)
            mystmt.setString(11,mySingleCreditTransferPaymentTableDetails.creditoraccountnumber)
            mystmt.setString(12,mySingleCreditTransferPaymentTableDetails.creditoraccountname)
            mystmt.setString(13,mySingleCreditTransferPaymentTableDetails.creditorbankcode)
            mystmt.setString(14,mySingleCreditTransferPaymentTableDetails.creditorschemename)
            mystmt.setString(15,mySingleCreditTransferPaymentTableDetails.remittanceinfounstructured)
            mystmt.setString(16,mySingleCreditTransferPaymentTableDetails.taxremittancereferenceno)
            mystmt.setString(17,mySingleCreditTransferPaymentTableDetails.purposecode)
            mystmt.setString(18,mySingleCreditTransferPaymentTableDetails.chargebearer)
            mystmt.setString(19,mySingleCreditTransferPaymentTableDetails.mandateidentification)
            mystmt.setString(20,mySingleCreditTransferPaymentTableDetails.instructingagentbankcode)
            mystmt.setString(21,mySingleCreditTransferPaymentTableDetails.instructedagentbankcode)
            mystmt.setInt(22,mySingleCreditTransferPaymentTableDetails.batchsize)
            mystmt.setString(23,mySingleCreditTransferPaymentTableDetails.requestmessagecbsapi)
            mystmt.setString(24,mySingleCreditTransferPaymentTableDetails.datefromcbsapi)
            mystmt.setString(25,mySingleCreditTransferPaymentTableDetails.remoteaddresscbsapi)
            mystmt.setInt(26,responseCode)
            mystmt.setString(27,responseMessage)
            mystmt.setString(28,responsemessagecbsapi)
            mystmt.setString(29,strChannelType)
            mystmt.setString(30,strChannelCallBackUrl)
            mystmt.execute()
          }
          catch{
            case ex : Exception =>
              log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
            case t: Throwable =>
              log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
          }
        }
      }catch {
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
        case t: Throwable =>
          log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " transactionreference - " + mySingleCreditTransferPaymentTableDetails.transactionreference)
      }
    }
  }
  def insertApiValidationRequests(strChannelType: String, strUserName: String, strPassword: String, strClientIP: String, myApifunction: String, responseCode: Int, responseMessage: String): Unit = {
    Future {
      val strApifunction: String = "insertApiValidationRequests"
      val strSQL: String = "{ call dbo.InsertApiValidationRequests(?,?,?,?,?,?,?) }"
      try {
        myDB.withConnection { implicit myconn =>
          try{
            val mystmt: CallableStatement = myconn.prepareCall(strSQL)
            mystmt.setString(1,strChannelType)
            mystmt.setString(2,strUserName)
            mystmt.setString(3,strPassword)
            mystmt.setString(4,strClientIP)
            mystmt.setString(5,myApifunction)
            mystmt.setInt(6,responseCode)
            mystmt.setString(7,responseMessage)

            mystmt.execute()
            
          }
          catch{
            case ex : Exception =>
              log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " clientip - " + strClientIP)
            case t: Throwable =>
              log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " clientip - " + strClientIP)
          }
        }
      }catch {
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " clientip - " + strClientIP)
        case t: Throwable =>
          log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " clientip - " + strClientIP)
      }
    }
  }
  def insertUpdateRecord(strSQL: String): Unit = {
    Future {
      val isValid: Boolean = {
      if (strSQL.trim.length > 0){true}
      else{false}
      }

      if (!isValid) return

      val strApifunction: String = "insertUpdateRecord"
      try {
        myDB.withConnection { implicit myconn =>

          try{
            val mystmt = myconn.createStatement()
            mystmt.executeUpdate(strSQL)
          }
          catch{
            case ex : Exception =>
              log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " strSQL - " + strSQL)
            case t: Throwable =>
              log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " strSQL - " + strSQL)
          }

        }
      }catch {
        case ex: Exception =>
          log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " strSQL - " + strSQL)
        case t: Throwable =>
          log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " strSQL - " + strSQL)
      }  
    }
  }
  def validateClientApi(strChannelType: String, strUserName: String, strPassword: String, strClientIP: String, myApifunction: String): ClientApiResponseDetails = {
    val strApifunction: String = "validateClientApi"
    var responseCode: Int = 1
    var responseMessage: String = "Error occured during processing, please try again."

    val strSQL: String = "{ call dbo.ValidateClientAPI(?,?,?,?,?,?,?) }"
    try {
      myDB.withConnection { implicit myconn =>
        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.setString(1,strChannelType)
          mystmt.setString(2,strUserName)
          mystmt.setString(3,strPassword)
          mystmt.setString(4,strClientIP)
          mystmt.setString(5,myApifunction)

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
        catch{
          case ex : Exception =>
            log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " clientip - " + strClientIP)
          case t: Throwable =>
            log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " clientip - " + strClientIP)
        }
      }
    }catch {
      case ex: Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " exception error occured." + " clientip - " + strClientIP)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " exception error occured." + " clientip - " + strClientIP)
    }

    val myClientApiResponseDetails = ClientApiResponseDetails(responseCode, responseMessage)
    myClientApiResponseDetails
  }  
  def getOutgoingAccountVerificationUrlIpsl(): String = {

    var strURL: String = ""
    val strSQL: String = "{ call dbo.GetOutgoingAccountVerificationUrlIpsl(?) }"
    val strApifunction: String = "getOutgoingAccountVerificationUrlIpsl"

    try {
      myDB.withConnection { implicit myconn =>
        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("OutgoingAccountVerificationUrlIpsl", java.sql.Types.VARCHAR)
          mystmt.execute()
          strURL = mystmt.getString("OutgoingAccountVerificationUrlIpsl")
        }
        catch{
          case io: IOException =>
            log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
          case ex : Exception =>
            log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
          case t: Throwable =>
            log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
        }
      }
    }catch {
      case io: IOException =>
        log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }

    return  strURL
  }
  def getOutgoingSingleCreditTransferUrlIpsl(): String = {

    var strURL: String = ""
    val strSQL: String = "{ call dbo.GetOutgoingSingleCreditTransferUrlIpsl(?) }"
    val strApifunction: String = "getOutgoingSingleCreditTransferUrlIpsl"

    try {
      myDB.withConnection { implicit myconn =>
        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("OutgoingSingleCreditTransferUrlIpsl", java.sql.Types.VARCHAR)
          mystmt.execute()
          strURL = mystmt.getString("OutgoingSingleCreditTransferUrlIpsl")
        }
        catch{
          case io: IOException =>
            log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
          case ex : Exception =>
            log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
          case t: Throwable =>
            log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
        }
      }
    }catch {
      case io: IOException =>
        log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured.")
      case ex : Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured.")
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured.")
    }

    return  strURL
  }
  def getSettings(strParamKey: String): String = {

    var strParamValue: String = ""
    val strSQL: String = "{ call dbo.GetSettings(?,?) }"
    val strApifunction: String = "getSettings"

    if (strParamKey == null) return strParamValue
    if (strParamKey.trim.length == 0) return strParamValue

    try {
      myDB.withConnection { implicit myconn =>
        try{
          val mystmt: CallableStatement = myconn.prepareCall(strSQL)
          mystmt.registerOutParameter("ParamValue", java.sql.Types.VARCHAR)
          mystmt.setString(1,strParamKey)
          mystmt.execute()
          strParamValue = mystmt.getString("ParamValue")
        }
        catch{
          case io: IOException =>
            log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured." + " ParamKey - " + strParamKey)
          case ex : Exception =>
            log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " ParamKey - " + strParamKey)
          case t: Throwable =>
            log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured." + " ParamKey - " + strParamKey)
        }
      }
    }catch {
      case io: IOException =>
        log_errors(strApifunction + " : " + io.getMessage + " - io exception error occured." + " ParamKey - " + strParamKey)
      case ex : Exception =>
        log_errors(strApifunction + " : " + ex.getMessage + " - ex exception error occured." + " ParamKey - " + strParamKey)
      case t: Throwable =>
        log_errors(strApifunction + " : " + t.getMessage + " - t exception error occured." + " ParamKey - " + strParamKey)
    }

    return  strParamValue
  }
  def resourceStream_old(resourceName: String): InputStream = {
    val is = getClass.getClassLoader.getResourceAsStream(resourceName)
    require(is ne null, s"Resource $resourceName not found")
    is
  }
  def getResourceStream(resourceName: String): InputStream = {
    try {
      val myInputStream: InputStream = new FileInputStream(resourceName)
      return  myInputStream
    }catch {
      case ex: Exception =>
        log_errors("getResourceStream : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("getResourceStream : " + t.getMessage + " exception error occured.")
    }
    return null
  }
  def getSignedXml(sourceXml: String): String = {
    var rawSignedXml: String = ""
    try {
      val keyStore: KeyStore = getSenderKeyStore()
      val alias: String = senderKeyPairName
      val password = senderKeyStorePwdCharArray
      val myTransformParameterSpec: TransformParameterSpec = null
      val envelopedTransform: Transform  = fac.newTransform(Transform.ENVELOPED, myTransformParameterSpec)
      val c14NEXCTransform: Transform  = fac.newTransform(C14N, myTransformParameterSpec)
      val transforms = Arrays.asList(envelopedTransform, c14NEXCTransform)
      val digestMethod: DigestMethod = fac.newDigestMethod(DigestMethod.SHA256, null)
      val ref: Reference = fac.newReference("", digestMethod, transforms, null, null)

      // Create the SignedInfo.
      val myC14NMethodParameterSpec: C14NMethodParameterSpec = null
      val canonicalizationMethod: CanonicalizationMethod = fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, myC14NMethodParameterSpec)
      // SignatureMethod signatureMethod                 = fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null);
      val signatureMethod: SignatureMethod = fac.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null)
      val si: SignedInfo = fac.newSignedInfo(canonicalizationMethod, signatureMethod, Collections.singletonList(ref))

      // Create the KeyInfo containing the X509Data.
      val keyInfoFactory: KeyInfoFactory = fac.getKeyInfoFactory()
      val certificate: X509Certificate = keyStore.getCertificate(alias).asInstanceOf[X509Certificate]

      val newX509Data: X509Data = keyInfoFactory.newX509Data(Collections.singletonList(certificate))
      //Commented out on 10-08-2021: Emmanuel
      //We do not need to send serial name and serial no to IPS
      //val issuer: X509IssuerSerial = keyInfoFactory.newX509IssuerSerial(certificate.getIssuerX500Principal().getName(), certificate.getSerialNumber())

      //Commented out on 10-08-2021: Emmanuel
      //val data = Arrays.asList(newX509Data, issuer)
      val data = Arrays.asList(newX509Data)
      val keyInfo: KeyInfo = keyInfoFactory.newKeyInfo(data)

      // Converts XML to Document
      // System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
      val dbf: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
      // DocumentBuilderFactory dbf          = DocumentBuilderFactory.newInstance("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", this.getClass().getClassLoader());

      dbf.setNamespaceAware(true)
      dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
      dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
      val builder: DocumentBuilder = dbf.newDocumentBuilder()
      //val doc: Document = builder.parse(sourceXml) this requires xml file path
      val myInputSource = new InputSource(new StringReader(sourceXml))
      val doc: Document = builder.parse(myInputSource)

      // Create a DOMSignContext and specify the RSA PrivateKey and
      // location of the resulting XMLSignature's parent element.
      val key: Key = keyStore.getKey(alias, password)
      if (key == null) {
          throw new Exception(String.format("Private Key not found for alias '%s' in KS '%s'", alias, keyStore))
      }

      val dsc: DOMSignContext = new DOMSignContext(key, doc.getDocumentElement())
      // ds:SignatureValue
      dsc.setDefaultNamespacePrefix("ds")

      // Adds <Signature> tag before a specific tag inside XML - with or without namespace

      // Create the XMLSignature, but don't sign it yet.
      val signature: XMLSignature = fac.newXMLSignature(si, keyInfo)
      signature.sign(dsc); // Marshal, generate, and sign the enveloped signature.

      this.removeWhitespaceFromSignature(doc)
      val output: ByteArrayOutputStream = new ByteArrayOutputStream()
      val transformerFactory: TransformerFactory = TransformerFactory.newInstance()
      transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
      transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
      transformerFactory.newTransformer().transform(new DOMSource(doc), new StreamResult(output))

      rawSignedXml = output.toString()
    }catch {
      case ex: Exception =>//e.printStackTrace()
        log_errors("getSignedXml : " + ex.getMessage + " exception error occured.")//ex.printStackTrace
      case t: Throwable =>
        log_errors("getSignedXml : " + t.getMessage + " throwable error occured.")
    }
    return rawSignedXml
  }
  def removeWhitespaceFromSignature(document: Document) : Unit =  {
    try{
      val sig: Element  = document.getElementsByTagName("ds:SignatureValue").item(0).asInstanceOf[Element]
      val sigValue: String  = sig.getTextContent().replace("\r\n", "")
      sig.setTextContent(sigValue)

      val cert: Element  = document.getElementsByTagName("ds:X509Certificate").item(0).asInstanceOf[Element]
      val certValue: String  = cert.getTextContent().replace("\r\n", "")
      cert.setTextContent(certValue)
    }
    catch {
      case ex: Exception =>
        log_errors("removeWhitespaceFromSignature : " + ex.getMessage + " exception error occured.")
      case t: Throwable =>
        log_errors("removeWhitespaceFromSignature : " + t.getMessage + " exception error occured.")
    }
  }
  def loadX509Certificate(resourceName: String): Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(getResourceStream(resourceName))
  def log_data(mydetail : String) : Unit = {
    Future {
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
    }
  }
  def log_errors(mydetail : String) : Unit = {
    Future {
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
    
    return  is_Successful
  }
}