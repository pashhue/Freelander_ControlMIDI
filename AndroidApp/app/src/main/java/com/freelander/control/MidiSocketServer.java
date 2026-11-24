package com.freelander.control;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;

/** Minimal loopback TCP server. The PC reaches it only through `adb forward`. */
public class MidiSocketServer {
    public static final int PORT = 8765;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile BufferedWriter writer;
    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;
    private Thread serverThread;
    private final Object writeLock = new Object();

    public void start() {
        if (running) return;
        running = true;
        serverThread = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    try {
                        serverSocket = new ServerSocket();
                        serverSocket.setReuseAddress(true);
                        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 1);
                        while (running) {
                            Socket socket = serverSocket.accept();
                            clientSocket = socket;
                            writer = new BufferedWriter(new OutputStreamWriter(
                                    socket.getOutputStream(), Charset.forName("US-ASCII")));
                            connected = true;
                            // Keep this handler alive until the host bridge closes the socket.
                            BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    socket.getInputStream(), Charset.forName("US-ASCII")));
                            while (running && reader.readLine() != null) {
                                // Reserved for future ping/config messages.
                            }
                            connected = false;
                            synchronized (writeLock) { writer = null; }
                            try { socket.close(); } catch (IOException ignored) {}
                            clientSocket = null;
                        }
                    } catch (IOException ex) {
                        connected = false;
                        synchronized (writeLock) { writer = null; }
                        closeSockets();
                        if (running) {
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                        }
                    }
                }
            }
        }, "Freelander-MIDI-TCP");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public boolean isConnected() { return connected; }

    public void send(int status, int data1, int data2) {
        synchronized (writeLock) {
            if (!connected || writer == null) return;
            try {
                writer.write(Integer.toString(status));
                writer.write(',');
                writer.write(Integer.toString(data1));
                writer.write(',');
                writer.write(Integer.toString(data2));
                writer.newLine();
                writer.flush();
            } catch (IOException ex) {
                connected = false;
                writer = null;
            }
        }
    }

    public void stop() {
        running = false;
        connected = false;
        synchronized (writeLock) { writer = null; }
        closeSockets();
        if (serverThread != null) serverThread.interrupt();
    }

    private void closeSockets() {
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }
}
