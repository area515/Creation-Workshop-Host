package org.area515.resinprinter.security.keystore;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.naming.InvalidNameException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.security.UserManagementException;
import org.area515.resinprinter.util.security.Friend;
import org.area515.resinprinter.util.security.PhotonicUser;
import org.eclipse.jetty.util.security.Credential;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "java.security.*", "javax.crypto.*"})
public class RendezvousExchange {
	public static final String testMessage = "This is my test message";

	public class WaitForTwoRemoteMessages implements Answer<Object> {
		private CountDownLatch latch = new CountDownLatch(2);
		
		@Override
		public Object answer(InvocationOnMock invocation) throws Throwable {
			if (invocation.getMethod().getName().equals("remoteMessageReceived")) {
				latch.countDown();
			}
			
			return null;
		}
		
		public void awaitForMessages() throws InterruptedException {
			latch.await(15, TimeUnit.SECONDS);
		}
	}
	
	@Test
	@PrepareForTest(NotificationManager.class)
	public void messageExchange() throws Exception {
		WaitForTwoRemoteMessages waiter = new WaitForTwoRemoteMessages();
		PowerMockito.mockStatic(NotificationManager.class, waiter);
		
		String password = "test";
		int httpPort1 = 1111;
		int httpPort2 = 2222;
		int wsPort = 3333;
		
		RendezvousPipe pipe = new RendezvousPipe(wsPort);
		
		File user1Keystore = File.createTempFile("user1","keystore");
		File user2Keystore = File.createTempFile("user2","keystore");
		
		user1Keystore.delete();
		user2Keystore.delete();
		
		String username1 = "User1";
		String username2 = "User2";
		
		KeystoreLoginService service1 = new KeystoreLoginService(user1Keystore, password, false);
		KeystoreLoginService service2 = new KeystoreLoginService(user2Keystore, password, false);
		
		TestServer httpServer1 = new TestServer(httpPort1, testMessage, service1);
		TestServer httpServer2 = new TestServer(httpPort2, testMessage, service2);
		
		PhotonicUser insertUser1 = new PhotonicUser(username1, password, null, username1 + "@stuff.com", new String[]{PhotonicUser.FULL_RIGHTS}, false);
		PhotonicUser insertUser2 = new PhotonicUser(username2, password, null, username2 + "@stuff.com", new String[]{PhotonicUser.FULL_RIGHTS}, false);
		
		PhotonicUser user1 = service1.update(insertUser1);
		PhotonicUser user2 = service2.update(insertUser2);
		
		Assert.assertNotNull(service1.login(username1, Credential.getCredential(password), null));
		Assert.assertNotNull(service2.login(username2, Credential.getCredential(password), null));
		
		RendezvousClient server1 = new RendezvousClient(user1Keystore, password, false, new URI("ws://127.0.0.1:" + wsPort + "/httpTunnel"), new URI("http://127.0.0.1:" + httpPort1), service1);
		RendezvousClient server2 = new RendezvousClient(user2Keystore, password, false, new URI("ws://127.0.0.1:" + wsPort + "/httpTunnel"), new URI("http://127.0.0.1:" + httpPort2), service2);
		
		X509FriendshipFeature friendship1 = new X509FriendshipFeature(server1);
		X509FriendshipFeature friendship2 = new X509FriendshipFeature(server2);
		
		server1.setX509FriendshipFeature(friendship1);
		server2.setX509FriendshipFeature(friendship2);
		
		server1.sendFriendRequest(user1.getUserId(), user2.getUserId());
		server2.sendFriendRequest(user2.getUserId(), user1.getUserId());
		
		waiter.awaitForMessages();
		
		List<Friend> friendRequests1 = friendship1.getFriendRequests();
		List<Friend> friendRequests2 = friendship2.getFriendRequests();
		
		Friend newFriendFor1 = friendRequests1.get(0);
		Friend newFriendFor2 = friendRequests2.get(0);
		
		friendship1.acceptFriendRequest(newFriendFor1);
		friendship2.acceptFriendRequest(newFriendFor2);
		
		service1.trustNewFriend(newFriendFor1);
		service2.trustNewFriend(newFriendFor2);
		
		Assert.assertTrue(friendship1.getFriendRequests().isEmpty());
		Assert.assertTrue(friendship2.getFriendRequests().isEmpty());
		
		HttpGet getRequest = new HttpGet("services");
		
		HttpResponse bufferFrom1To2 = server1.sendRequestToRemote(user1.getUserId(), user2.getUserId(), getRequest, 20, TimeUnit.SECONDS);
		HttpResponse bufferFrom2To1 = server2.sendRequestToRemote(user2.getUserId(), user1.getUserId(), getRequest, 20, TimeUnit.SECONDS);
		
		byte[] dataFrom1 = new byte[testMessage.length()];
		byte[] dataFrom2 = new byte[testMessage.length()];
		
		bufferFrom1To2.getEntity().getContent().read(dataFrom1);
		bufferFrom2To1.getEntity().getContent().read(dataFrom2);
		
		Assert.assertEquals(testMessage, new String(dataFrom1));
		Assert.assertEquals(testMessage, new String(dataFrom2));
		
		//===================
		//TODO: This local execution of the concurrency test failed on: 12/8/2016
		//The I believe this occurred because there was a timeout waiting for a response from method of: org.area515.resinprinter.security.keystore.RendezvousExchange.HttpRequestRunnable
		//Why did we timeout?
		
		runOneWayConcurrencyTest(server1, user1.getUserId(), user2.getUserId());
		//===================
		
		pipe.close();
		httpServer1.close();
		httpServer2.close();
	}
	
	
	public static class HttpRequestRunnable implements Callable<Boolean> {
		private RendezvousClient client;
		private HttpGet request;
		private UUID from;
		private UUID to;
		
		private HttpRequestRunnable(RendezvousClient client, UUID from, UUID to) {
			this.client = client;
			this.request = new HttpGet("services");
			this.from = from;
			this.to = to;
		}
	
		@Override
		public Boolean call() throws Exception {
			HttpResponse response = client.sendRequestToRemote(from, to, request, 20, TimeUnit.SECONDS);
			byte[] dataFrom1 = new byte[testMessage.length()];
			response.getEntity().getContent().read(dataFrom1);
			return testMessage.equals(new String(dataFrom1));
		}
	}
	
	public void runOneWayConcurrencyTest(RendezvousClient client, UUID from, UUID to) throws InterruptedException, ExecutionException {
		int workToPerform = 99;//TODO: we aren't ready to stress test key exchanges so we keep this under 100
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(15);
		Future<Boolean> callables[] = new Future[workToPerform];
		for (int t = 0; t < workToPerform; t++) {
			callables[t] = executor.submit(new HttpRequestRunnable(client, from, to));
		}
		for (Future<Boolean> callable : callables) {
			Assert.assertTrue(callable.get());
		}
		executor.shutdown();
		if (!executor.awaitTermination(40, TimeUnit.SECONDS)) {
			Assert.fail("Timeout occurred");
		}
	}
}