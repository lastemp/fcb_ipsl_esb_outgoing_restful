package controllers

import java.nio.charset.StandardCharsets
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.KeyGenerator
import java.security.SecureRandom

class AES {
  val encryption: String = "AES" 	
  val algorithm: String = "AES/CBC/PKCS5Padding"
  val secretKeyFactoryAlgorithm = "PBKDF2WithHmacSHA256"
  //val password: String = ");ZwTVH8`{@.?[5x,C43g65mykw++e" //live
  val password: String = "G/D%H,E7-Q[VPYYAp/MtVTg]6z%A<S" //test
  //val salt = "7a4b5366-dba8-459c-9a70-db3a420688af393a8e1-e49e-4741-8059-9ee8c6996b7" //live
  val salt = "eec681c3-c304-4b27-9c91-83a2c3dc11368a7c414-9f3d-419c-b3c7-931c98b94dc" //test
  //val key = generateKey(128)
  val key = getKeyFromPassword(password,salt)
  val iv = generateIv()
  
  def main(args : Array[String]) : Unit = {
  }
  def generateKey(n: Int) : SecretKey = {
	val keyGenerator: KeyGenerator = KeyGenerator.getInstance(encryption)
    try {
       keyGenerator.init(n)
	   val key: SecretKey = keyGenerator.generateKey()
	   return key
    }
    catch {
      case ex: Exception =>
      case t: Throwable =>
    }
	
	return null
  }
  def getKeyFromPassword(password: String, salt: String) : SecretKey = {
	val factory: SecretKeyFactory = SecretKeyFactory.getInstance(secretKeyFactoryAlgorithm)
    try {
       val spec: KeySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 256)
	   val secret: SecretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), encryption)
	   return secret
    }
    catch {
      case ex: Exception =>
      case t: Throwable =>
    }
	
	return null
  }
  def generateIv() : IvParameterSpec = {
    try {
	   val iv = new Array[Byte](16)
	   //We commented below line since we want to generate same encrypted value for any given text 
	   //new SecureRandom().nextBytes(iv)
	   return new IvParameterSpec(iv)
    }
    catch {
      case ex: Exception =>
      case t: Throwable =>
    }
	
	return null
  }
  //def encrypt(input: String, key: SecretKey, iv: IvParameterSpec) : String = {
  def encrypt(input: String) : String = {
	if (key == null) return ""
	if (iv == null) return ""
    try {
       val cipher: Cipher = Cipher.getInstance(algorithm)
	   cipher.init(Cipher.ENCRYPT_MODE, key, iv)
	   val cipherText = cipher.doFinal(input.getBytes())
	   
	   return Base64.getEncoder().encodeToString(cipherText)
    }
    catch {
      case ex: Exception =>
      case t: Throwable =>
    }
	
	return ""
  }
  //def decrypt(cipherText: String, key: SecretKey, iv: IvParameterSpec) : String = {
  def decrypt(cipherText: String) : String = {
	if (key == null) return ""
	if (iv == null) return ""  
    try {
       val cipher: Cipher = Cipher.getInstance(algorithm)
	   cipher.init(Cipher.DECRYPT_MODE, key, iv)
       val myByteArray = cipher.doFinal(Base64.getDecoder().decode(cipherText))
	   val plainText = new String(myByteArray)
	   
	   return plainText
    }
    catch {
      case ex: Exception =>
      case t: Throwable =>
    }
	
	return ""
  }
  def isMatch(inputText: String, encodedEncryptedText: String) : Boolean = {
	var isMatching: Boolean = false 
	//inputText - raw/plain text
	//encodedEncryptedText - this is encrypted text(generated using known key) that is base64 format
	
	if (inputText == null) return isMatching
	if (encodedEncryptedText == null) return isMatching
	if (inputText.length == 0) return isMatching
	if (encodedEncryptedText.length == 0) return isMatching
	
    try {
	   val encodedInputText = encrypt(inputText)
	   
	   if (encodedInputText.equals(encodedEncryptedText)){
		isMatching = true  
	   }
	   
	   return isMatching
    }
    catch {
      case ex: Exception =>
      case t: Throwable =>
    }
	
	return false
  }
}
