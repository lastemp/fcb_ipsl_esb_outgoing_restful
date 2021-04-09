package controllers

/***
  * Created by Emmanuel on 9/9/2017.
  */
import java.sql.DriverManager
import java.sql.Connection
import java.io.IOException
import java.sql.ResultSet
import javax.sql.rowset.CachedRowSet

import com.sun.rowset.CachedRowSetImpl
import play.api.db.Database
//
import javax.inject.Inject;
import play.api.mvc._
import play.api.db._
//import play.api.db.{Database,NamedDatabase}
//@inject(db : Database)
// REMOVED FOR TEST
//class DataManagement @Inject() (mydb : Database, val controllerComponents : ControllerComponents) extends  BaseController {
class DataManagement @Inject() (val controllerComponents : ControllerComponents) extends  BaseController {
//class DataManagement  {
  // connect to the database named "mysql" on the localhost
  //var driver = "com.mysql.jdbc.Driver"
  //val url = "jdbc:mysql://localhost/mysql"
  var mydb : Database = null //TEST ONLY
  val url = "jdbc:mysql://localhost:3306/test_db"
  val username = "kppf"
  val password = "kppf"
  var staffid : String = ""
  var surname : String = ""
  var onames : String = ""
  var strErrorMsg : String = ""
  // there's probably a better way to do this
  //var connection : Connection = null
  /***
  try {
    // make the connection
    Class.forName(driver)
    connection = DriverManager.getConnection(url, username, password)

    // create the statement, and run the select query
    val statement = connection.createStatement()
    //val resultSet = statement.executeQuery("SELECT host, user FROM user")
    val resultSet = statement.executeQuery("select staffid,surname,onames from agents where staffid = 1")
    while ( resultSet.next() ) {
      staffid = resultSet.getString("staffid")
      surname = resultSet.getString("surname")
      onames = resultSet.getString("onames")
      println("host, user, onames = " + staffid + ", " + surname + ", " + onames)
    }
  } catch {
    case e => e.printStackTrace
  }

  try {
    // make the connection
    //Class.forName(driver)
    //connection = DriverManager.getConnection(url, username, password)

    // create the statement, and run the select query
    //var strSQL : String = "insert into agents(staffid,surname,onames,status) values (3,'test','me',1)"
    //var strSQL : String = "update agents set surname = 'Owino' where staffid = 1"
    //var strSQL2 : String = "update agents set staffid = 2 where onames = 'Paul'"
    //var strSQL2 : String = "update agents set surname = 'Kariuki' where staffid = 2"
    var strSQL3 : String = "delete from agents where staffid = 3"
    val state1 = connection.createStatement()
    //val resultSet = statement.executeQuery("SELECT host, user FROM user")
    //state1.executeUpdate(strSQL)
    //state1.executeUpdate(strSQL2)
    state1.executeUpdate(strSQL3)
  } catch {
    case e => e.printStackTrace
  }

  connection.close()
    */

  def Select_one_Record(strSQL : String): Object = {
    var is_Successful : Boolean = false
    val resultSet = null
    //var driver = "com.mysql.jdbc.Driver"
    var connection : Connection = null
    var driver = "com.mysql.jdbc.Driver"

    try{
      //connection = mySQL_connection()
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed != false){
          val mystate = connection.createStatement()
          val resultSet = mystate.executeQuery("select staffid,surname,onames from agents where staffid = 1")
          while ( resultSet.next() ) {
            staffid = resultSet.getString("staffid")
            surname = resultSet.getString("surname")
            onames = resultSet.getString("onames")
            println("host, user, onames = " + staffid + ", " + surname + ", " + onames)
          }
          is_Successful = true
        }
        else
        {
          is_Successful = false
        }
      }
      else
      {
        is_Successful = false
      }
      is_Successful = true
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      connection.close()
      connection = null
      //clsConnection.clsConn.close()
    }
    return  resultSet
  }

  def Select_many_Records(strSQL : String): ResultSet = {
    var is_Successful : Boolean = false
    var resultSet : ResultSet = null
    var driver = "com.mysql.jdbc.Driver"
    var connection : Connection = null
    try{
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      val mystate = connection.createStatement()
      //val mystate = clsConnection.clsConn.createStatement()
      //val resultSet = mystate.executeQuery(strSQL)
      resultSet = mystate.executeQuery(strSQL)
      is_Successful = true
      //return resultSet
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      //connection.close()
      //connection = null
      //clsConnection.clsConn.close()
    }
    return  resultSet
    //return null
  }

  def Select_many_Records_extr(strSQL : String): CachedRowSet = {
    var is_Successful : Boolean = false
    var resultSet : ResultSet = null
    var myCachedRowSet : CachedRowSet = new CachedRowSetImpl()
    var driver = "com.mysql.jdbc.Driver"
    var connection : Connection = null
    try{

      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      //var mystate = connection.createStatement()
      //val mystate = clsConnection.clsConn.createStatement()
      //val resultSet = mystate.executeQuery(strSQL)
      //resultSet = mystate.executeQuery(strSQL)
      //myCachedRowSet.populate(resultSet)
      //s_Successful = true
      //mystate = null
      //return resultSet

      //connection = mySQL_connection()
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed == false){
          var mystate = connection.createStatement()
          resultSet = mystate.executeQuery(strSQL)
          myCachedRowSet.populate(resultSet)

          mystate = null

          is_Successful = true
        }
        else
        {
          is_Successful = false
        }
      }
      else
      {
        is_Successful = false
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      resultSet.close()
      resultSet = null
      connection.close()
      connection = null
      driver = null
      //clsConnection.clsConn.close()
    }
    return  myCachedRowSet
    //return null
  }

  def Select_many_Records_extr_dynamic(strSQL : String , connection : Connection): CachedRowSet = {
    var is_Successful : Boolean = false
    var resultSet : ResultSet = null
    var myCachedRowSet : CachedRowSet = new CachedRowSetImpl()
    //var driver = "com.mysql.jdbc.Driver"
    //var connection : Connection = null
    try{

      //connection = mySQL_connection()
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed == false){
          var mystate = connection.createStatement()
          resultSet = mystate.executeQuery(strSQL)
          myCachedRowSet.populate(resultSet)

          mystate = null

          is_Successful = true
        }
        else
        {
          is_Successful = false
        }
      }
      else
      {
        is_Successful = false
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      resultSet.close()
      resultSet = null
      //connection.close()
      //connection = null
      //driver = null
      //clsConnection.clsConn.close()
    }
    return  myCachedRowSet
    //return null
  }

  def Insert_update_Record(strSQL : String): Boolean = {

    var is_Successful : Boolean = false
    var myID : Int = 0
    //var driver = "com.mysql.jdbc.Driver"
    //var connection : Connection = null
    //var strID : String = ""
    try{
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      //connection = mySQL_connection()
      //var mydb : Database = null
      val myconn = mydb.getConnection()
      var mystate = myconn.createStatement()

      mystate.executeUpdate(strSQL)
      is_Successful = true

    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
        println(strErrorMsg)
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
        println(strErrorMsg)
    }
    finally {

    }
    return  is_Successful
  }

  def Insert_update_Record_dynamic(strSQL : String, connection : Connection): Boolean = {
    var is_Successful : Boolean = false
    var myID : Int = 0
    //var driver = "com.mysql.jdbc.Driver"
    //var connection : Connection = null
    //var strID : String = ""
    try{
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      //connection = mySQL_connection()
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed == false){
          var mystate = connection.createStatement()
          //val mystate = clsConnection.clsConn.createStatement()
          mystate.executeUpdate(strSQL)
          is_Successful = true
          mystate = null

        }
        else
        {
          is_Successful = false
        }
      }
      else
      {
        is_Successful = false
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      //connection.close()
      //clsConnection.clsConn.close()
      //connection = null
      //driver = null
    }
    return  is_Successful
  }

  def Insert_update_Record_Int(strSQL : String): Int = {
    var is_Successful : Boolean = false
    var myID : Int = 0
    var driver = "com.mysql.jdbc.Driver"
    var connection : Connection = null
    //var strID : String = ""
    try{
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      //connection = mySQL_connection()
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed == false){
          var mystate = connection.createStatement()
          //val mystate = clsConnection.clsConn.createStatement()
          mystate.executeUpdate(strSQL)
          var resultSet = mystate.executeQuery("select LAST_INSERT_ID() as myID")
          while ( resultSet.next()){
            myID = resultSet.getInt("myID")
          }

          resultSet.close()
          mystate = null
          resultSet = null

          if (myID > 0){
            is_Successful = true
          }
          else
          {
            is_Successful = false
            myID  = 0
          }
        }
        else
        {
          is_Successful = false
          myID  = 0
        }
      }
      else
      {
        is_Successful = false
        myID  = 0
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      connection.close()
      //clsConnection.clsConn.close()
      connection = null
      driver = null
    }
    return  myID
  }
  def Insert_update_Record_str(strSQL : String): String = {
    var is_Successful : Boolean = false
    var myID : Int = 0
    var driver = "com.mysql.jdbc.Driver"
    var connection : Connection = null
    //var strID : String = ""
    try{
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      //connection = mySQL_connection()
      strErrorMsg = ""
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed == false){
          var mystate = connection.createStatement()
          //val mystate = clsConnection.clsConn.createStatement()
          mystate.executeUpdate(strSQL)
          var resultSet = mystate.executeQuery("select LAST_INSERT_ID() as myID")
          while ( resultSet.next()){
            myID = resultSet.getInt("myID")
          }

          resultSet.close()
          mystate = null
          resultSet = null

          if (myID > 0){
            is_Successful = true
          }
          else
          {
            is_Successful = false
            myID  = 0
          }
        }
        else
        {
          is_Successful = false
          myID  = 0
        }
      }
      else
      {
        is_Successful = false
        myID  = 0
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      connection.close()
      //clsConnection.clsConn.close()
      connection = null
      driver = null
    }
    return  strErrorMsg
  }

  def Insert_update_Record_Int_dynamic(strSQL : String, connection : Connection): Int = {
    var is_Successful : Boolean = false
    var myID : Int = 0
    //var driver = "com.mysql.jdbc.Driver"
    //var connection : Connection = null
    //var strID : String = ""
    try{
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      //clsConnection.clsConn = DriverManager.getConnection(url, username, password)
      //connection = mySQL_connection()
      //Class.forName(driver)
      //connection = DriverManager.getConnection(url, username, password)
      if (connection != null){
        if (connection.isClosed == false){
          var mystate = connection.createStatement()
          //val mystate = clsConnection.clsConn.createStatement()
          mystate.executeUpdate(strSQL)
          var resultSet = mystate.executeQuery("select LAST_INSERT_ID() as myID")
          while ( resultSet.next()){
            myID = resultSet.getInt("myID")
          }

          resultSet.close()
          mystate = null
          resultSet = null

          if (myID > 0){
            is_Successful = true
          }
          else
          {
            is_Successful = false
            myID  = 0
          }
        }
        else
        {
          is_Successful = false
          myID  = 0
        }
      }
      else
      {
        is_Successful = false
        myID  = 0
      }
    }
    catch {
      case io: IOException =>
        io.printStackTrace()
        strErrorMsg = io.toString
      case ex : Exception =>
        ex.printStackTrace()
        strErrorMsg = ex.toString
    }
    finally {
      //connection.close()
      //clsConnection.clsConn.close()
      //connection = null
      //driver = null
    }
    return  myID
  }

}

