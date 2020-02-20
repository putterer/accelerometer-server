package de.putterer.accelerometer_server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Server {

	public static final int SERVER_PORT = 9380;
	public static final int MAX_MESSAGE_LENGTH = 65507;

	public static final byte TYPE_SUBSCRIBE = 10;
	public static final byte TYPE_UNSUBSCRIBE = 11;
	public static final byte TYPE_CONFIRM_SUBSCRIPTION = 12;
	public static final byte TYPE_CONFIRM_UNSUBSCRIPTION = 13;
	public static final byte TYPE_ACCELERATION_INFO = 20;

	private static DatagramSocket socket;
	private static List<Subscription> subscriptions = new ArrayList<>();

	private static volatile int nextMessageId = 0;

	public static void init() {
		try {
			socket = new DatagramSocket(SERVER_PORT);
		} catch(IOException e) {
			System.err.println("Couldn't start server");
			e.printStackTrace();
			System.exit(-1);
		}

		new Thread(Server::listener).start();
	}

	public static void onAcceleration(float[] acceleration) {
		//TODO: only do at limited interval, not full rate? (full rate for game should be 20 ms)
		ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 8 + 4 * 3);
		buffer.put(TYPE_ACCELERATION_INFO);
		buffer.putInt(nextMessageId++);
		buffer.putLong(System.currentTimeMillis());
		buffer.putFloat(acceleration[0]);
		buffer.putFloat(acceleration[1]);
		buffer.putFloat(acceleration[2]);

		byte[] data = buffer.array();

		synchronized (subscriptions) {
			subscriptions.forEach(subscription ->
					sendMessage(subscription.getAddress(), subscription.getPort(), data)
			);
		}
	}

	private static void onMessage(DatagramPacket packet) {
		if(packet.getLength() == 0) {
			return;
		}

		switch (packet.getData()[0]) {
			case TYPE_SUBSCRIBE:
				Subscription subscription = new Subscription(packet.getAddress().getHostAddress(), packet.getPort());
				synchronized (subscriptions) {
					subscriptions.removeIf(s -> s.getAddress().equals(packet.getAddress().getHostAddress()) && s.getPort() == packet.getPort());
					subscriptions.add(subscription);
				}
				sendMessage(subscription.getAddress(), subscription.getPort(), new byte[] {TYPE_CONFIRM_SUBSCRIPTION});
				System.out.println("Got subscription from " + subscription.getAddress());
				break;
			case TYPE_UNSUBSCRIBE:
				synchronized (subscriptions) {
					Optional<Subscription> unsubscription = subscriptions.stream().filter(s ->
							s.getAddress().equals(packet.getAddress().getHostAddress())
									&& s.getPort() == packet.getPort())
							.findFirst();

					if(! unsubscription.isPresent()) {
						System.err.println("Couldn't find subscription to remove for " + packet.getAddress().getHostAddress());
					} else {
						subscriptions.remove(unsubscription.get());
						sendMessage(unsubscription.get().getAddress(), unsubscription.get().getPort(), new byte[] {TYPE_CONFIRM_UNSUBSCRIPTION});
						System.out.println("Got UNsubscription from " + unsubscription.get().getAddress());
					}
				}
				break;
			default:
				System.err.println("Received packet with unknown type from " + packet.getAddress().getHostAddress());
		}
	}

	private static void sendMessage(String address, int port, byte[] data) {
		try {
			DatagramPacket packet = new DatagramPacket(data, data.length);
			packet.setAddress(InetAddress.getByName(address));
			packet.setPort(port);
			socket.send(packet);
		} catch(IOException e) {
			System.err.println("Error while sending message to " + address);
			e.printStackTrace();
		}
	}

	private static void listener() {
		while(true) {
			byte[] buffer = new byte[MAX_MESSAGE_LENGTH];
			while(true) {
				DatagramPacket packet = new DatagramPacket(buffer, MAX_MESSAGE_LENGTH);
				try {
					socket.receive(packet);
				} catch(IOException e) {
					System.err.println("Error while reading from datagram socket");
					e.printStackTrace();
				}

				Server.onMessage(packet);
			}
		}
	}

	public static class Subscription {
		private final String address;
		private final int port;

		public Subscription(String address, int port) {
			this.address = address;
			this.port = port;
		}

		public String getAddress() {
			return address;
		}

		public int getPort() {
			return port;
		}
	}
}
