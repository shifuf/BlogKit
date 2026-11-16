package com.blog.system.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationPayloadCipherTest {
 @Test void queuedTokensAreEncryptedAndTamperIsRejected() throws Exception {
  var type=Class.forName("com.blog.system.service.NotificationPayloadCipher");var c=type.getConstructor(String.class).newInstance("test-payload-key-with-at-least-32-characters");
  String encrypted=(String)type.getMethod("seal",String.class).invoke(c,"https://blog.test/subscribe?confirm=secret");assertFalse(encrypted.contains("secret"));assertEquals("https://blog.test/subscribe?confirm=secret",type.getMethod("open",String.class).invoke(c,encrypted));
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getMethod("open",String.class).invoke(c,encrypted.substring(0,encrypted.length()-3)+"AAA"));
 }
}
