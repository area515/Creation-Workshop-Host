package org.area515.resinprinter.security;

import java.io.File;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Base64;

import javax.websocket.server.ServerContainer;

import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.security.keystore.KeystoreLoginService;
import org.area515.resinprinter.util.security.Friend;
import org.area515.resinprinter.util.security.KeystoreUtilities;
import org.area515.resinprinter.util.security.Message;
import org.area515.resinprinter.util.security.PhotonicCrypto;
import org.area515.resinprinter.util.security.PhotonicUser;
import org.eclipse.jetty.server.UserIdentity;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class KeystoreSecurityTest {
	private static PhotonicCrypto crypto1;
	private static PhotonicCrypto crypto2;
	private static PhotonicCrypto uninitializedCrypto;
	private static File user1Keystore;
	private static File user2Keystore;
	private static KeystoreLoginService service1;
	private static KeystoreLoginService service2;
	private static PhotonicUser user1;
	private static PhotonicUser user2;
	
	public static Friend buildFriend(PhotonicUser user, PhotonicCrypto crypto) throws CertificateEncodingException {
		Friend user1sFriendIsUser2 = new Friend();
		user1sFriendIsUser2.setUser(user);
		user1sFriendIsUser2.setTrustData(new String[]{
				Base64.getEncoder().encodeToString(crypto.getCertificates()[0].getEncoded()),
				Base64.getEncoder().encodeToString(crypto.getCertificates()[1].getEncoded())
			});
		
		return user1sFriendIsUser2;
	}
	
	@BeforeClass
	public static void setupCryptos() throws Exception {
		NotificationManager.start(null, Mockito.mock(ServerContainer.class));
		user1Keystore = File.createTempFile("user1","keystore");
		user1Keystore.deleteOnExit();
		user2Keystore = File.createTempFile("user2","keystore");
		user2Keystore.deleteOnExit();

		user1Keystore.delete();
		user2Keystore.delete();
		
		String password = "test";
		
		String username1 = "User1";
		String username2 = "User2";
		
		service1 = new KeystoreLoginService(user1Keystore, password, false);
		service2 = new KeystoreLoginService(user2Keystore, password, false);
		
		PhotonicUser insertUser1 = new PhotonicUser(username1, password, null, username1 + "@stuff.com", new String[]{PhotonicUser.FULL_RIGHTS}, false);
		PhotonicUser insertUser2 = new PhotonicUser(username2, password, null, username2 + "@stuff.com", new String[]{PhotonicUser.FULL_RIGHTS}, false);
		
		user1 = service1.update(insertUser1);
		user2 = service2.update(insertUser2);
		
		crypto1 = KeystoreUtilities.getPhotonicCrypto(user1, user1Keystore, password, password, false);
		crypto2 = KeystoreUtilities.getPhotonicCrypto(user2, user2Keystore, password, password, false);

		Friend user1sFriendIsUser2 = buildFriend(user2, crypto2);
		Friend user2sFriendIsUser1 = buildFriend(user1, crypto1);
		
		service1.trustNewFriend(user1sFriendIsUser2);
		service2.trustNewFriend(user2sFriendIsUser1);
		
		PhotonicCrypto remoteCypto1 = KeystoreUtilities.getPhotonicCrypto(user1, user2Keystore, null, password, false);
		PhotonicCrypto remoteCrypto2 = KeystoreUtilities.getPhotonicCrypto(user2, user1Keystore, null, password, false);
		
		crypto1.setRemoteCrypto(remoteCrypto2);
		crypto2.setRemoteCrypto(remoteCypto1);
		
		uninitializedCrypto = KeystoreUtilities.getPhotonicCrypto(user1, user1Keystore, password, password, false);
	}
	
	@AfterClass
	public static void teardownCryptos() throws UserManagementException {
		service1.remove(user2);
		service2.remove(user1);
		user1Keystore.delete();
		user2Keystore.delete();
	}
	
	@Test
    public void createKeystoreAndAddUser() throws Exception {
		NotificationManager.start(null, Mockito.mock(ServerContainer.class));
		
		String testUsername = "<>!@#$%^&*()~` ,.;'[]-=?:\"{}|\\frank,cn=Wes";//Ensure they can't perform a name spoof
		
		service1.start(null, null);
		Assert.assertNull(service1.login(testUsername, testUsername, null));
		PhotonicUser insertUser = new PhotonicUser(testUsername, testUsername, null, testUsername, new String[]{PhotonicUser.FULL_RIGHTS}, false);
		PhotonicUser user = service1.update(insertUser);
		Assert.assertNotNull(user);
		Assert.assertEquals(user.getName(), testUsername);
		Assert.assertEquals(testUsername, user.getName());
		Assert.assertTrue(service1.getUsers().contains(user));
		UserIdentity identity = service1.login(testUsername, testUsername, null);
		Assert.assertTrue(service1.validate(identity));
		service1.logout(identity);
		Assert.assertFalse(service1.validate(identity));
		identity = service1.login(testUsername, testUsername, null);
		Assert.assertTrue(service1.validate(identity));
		service1.remove(user);
		Assert.assertFalse(service1.validate(identity));
		Assert.assertNull(service1.login(testUsername, testUsername, null));
		Assert.assertFalse(service1.getUsers().contains(user));
	}
    
	@Test
    public void attemptReplayAttacks() throws Exception {
		byte[] dangerousActionThatShouldOnlyHappenOnce = "Don't do this more than once!!".getBytes();
		
		//Exchange key first
		Message keyexchange = crypto1.buildKeyExchange();
		Assert.assertNull(crypto2.getData(keyexchange));
		
		//Crypto2 says hello
		Message dataMessage = crypto2.buildEncryptedMessage(ByteBuffer.wrap(dangerousActionThatShouldOnlyHappenOnce));
		Assert.assertArrayEquals(dangerousActionThatShouldOnlyHappenOnce, crypto1.getData(dataMessage));
		
		//Man in the middle replays dangerousAction
		try {
			crypto1.getData(dataMessage);
			Assert.fail("ReplayAttack is possible!");
		} catch (InvalidAlgorithmParameterException badiv) {}
		
		//Man in the middle attempts to replay attack with a good iv (still doesn't work)
		dataMessage.setIvOffset(dataMessage.getIvOffset() + 1);
		byte[] data = crypto1.getData(dataMessage);
		Assert.assertFalse(Arrays.equals(data, dangerousActionThatShouldOnlyHappenOnce));
	}
	
	private void testConversation(byte[]... conversation) throws Exception {
		for (byte[] data : conversation) {
			Message dataMessage = crypto2.buildEncryptedMessage(ByteBuffer.wrap(data));
			Assert.assertArrayEquals(data, crypto1.getData(dataMessage));
		}
	}
	
	private void testReverseConversation(byte[]... conversation) throws Exception {
		for (byte[] data : conversation) {
			Message dataMessage = crypto1.buildEncryptedMessage(ByteBuffer.wrap(data));
			Assert.assertArrayEquals(data, crypto2.getData(dataMessage));
		}
	}
	
	@Test
    public void testConversationBetweenTwoTrustedUsersWithMultipleKeyExchanges() throws Exception {
		byte[] greeting = "hello!".getBytes();
		byte[] greetingResponse = "Well hello. How are you!".getBytes();
		byte[] greetingResponseResponse = "I'm fantastic, and you?".getBytes();
		byte[] greetingResponseResponseResponse = "Not so good I have a sore back. :(".getBytes();
		Message keyExchange;
		
		//Exchange key first
		keyExchange = crypto1.buildKeyExchange();
		Assert.assertNull(crypto2.getData(keyExchange));
		testConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
		
		//Attempt another key exchange in the middle of the conversation
		keyExchange = crypto1.buildKeyExchange();
		Assert.assertNull(crypto2.getData(keyExchange));
		testConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
		
		//Attempt another key exchange, and reverse the conversation
		keyExchange = crypto1.buildKeyExchange();
		Assert.assertNull(crypto2.getData(keyExchange));
		testReverseConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
		
		//Attempt another key exchange, in the middle of a reverse conversation
		keyExchange = crypto1.buildKeyExchange();
		Assert.assertNull(crypto2.getData(keyExchange));
		testReverseConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
		
		
		//Attempt a reverse key exchange
		keyExchange = crypto2.buildKeyExchange();
		Assert.assertNull(crypto1.getData(keyExchange));
		testConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
		
		//Attempt a reverse key exchange in the middle of the conversation
		keyExchange = crypto2.buildKeyExchange();
		Assert.assertNull(crypto1.getData(keyExchange));
		testConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);

		//Attempt a reverse key exchange in the middle of a reverse conversation
		keyExchange = crypto2.buildKeyExchange();
		Assert.assertNull(crypto1.getData(keyExchange));
		testReverseConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
		
		//Attempt a reverse key exchange in the middle of a reverse conversation
		keyExchange = crypto2.buildKeyExchange();
		Assert.assertNull(crypto1.getData(keyExchange));
		testReverseConversation(greeting, greetingResponse, greetingResponseResponse, greetingResponseResponseResponse);
	}
    
	@Test
    public void attemptConversationWithoutAKeyExchange() throws Exception {
		byte[] greeting = "hello!".getBytes();
		
		try {
			uninitializedCrypto.buildEncryptedMessage(ByteBuffer.wrap(greeting));
			Assert.fail("You can't encrypt before you initialized the crypto.");
		} catch (InvalidKeyException badiv) {}
	}
	
	@Test
	public void testInsecureCommunicationsWhenSecureIsRequired() throws Exception {
		byte[] greeting = "hello!".getBytes();
		
		try {
			crypto1.buildMessage(greeting);
			Assert.fail("We allowed insecure communications on a secure crypto");
		} catch (InvalidAlgorithmParameterException e) {}
		
		try {
			crypto1.getData(new Message());
			Assert.fail("We allowed insecure communications on a secure crypto");
		} catch (InvalidAlgorithmParameterException e) {}
	}
	
	@Test
	public void keyExchangeMissingSignature() throws Exception {
		try {
			Message missingSignature = new Message();
			missingSignature.setEncryptionAlgorithm("RSA");
			
			crypto1.getData(missingSignature);
			Assert.fail("We allowed insecure communications on a secure crypto");
		} catch (InvalidAlgorithmParameterException e) {}
	}
	
	@Test
	public void uninitializedSymmetric() throws Exception {
		try {
			Message missingSignature = new Message();
			missingSignature.setEncryptionAlgorithm("AES");
			missingSignature.setIvOffset(2);
			
			uninitializedCrypto.getData(missingSignature);
			Assert.fail("You shouldn't get this far on an uninitialized crypto");
		} catch (InvalidKeyException e) {}
	}
	
	@Test
	public void ensureOutOfOrderMessagingWorksOk() throws Exception {
		byte[] greeting = "hello!".getBytes();
		byte[] greetingResponse = "Well hello. How are you!".getBytes();
		byte[] greetingResponseResponse = "I'm fantastic, and you?".getBytes();
		byte[] greetingResponseResponseResponse = "Not so good I have a sore back. :(".getBytes();
		Message keyExchange;

		keyExchange = crypto1.buildKeyExchange();
		Assert.assertNull(crypto2.getData(keyExchange));

		Message message1 = crypto2.buildEncryptedMessage(ByteBuffer.wrap(greeting));
		Message message2 = crypto2.buildEncryptedMessage(ByteBuffer.wrap(greetingResponse));
		Message message3 = crypto2.buildEncryptedMessage(ByteBuffer.wrap(greetingResponseResponse));
		Message message4 = crypto2.buildEncryptedMessage(ByteBuffer.wrap(greetingResponseResponseResponse));
		Assert.assertArrayEquals(greetingResponse, crypto1.getData(message2));
		Assert.assertArrayEquals(greetingResponseResponseResponse, crypto1.getData(message4));
		Assert.assertArrayEquals(greeting, crypto1.getData(message1));
		Assert.assertArrayEquals(greetingResponseResponse, crypto1.getData(message3));
	}

	//TODO: Test two way + out of order messaging and I think we'll have a problem.
	
	//TODO: Test oracle attacks
}
