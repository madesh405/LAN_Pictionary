import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());
    private static List<String> drawHistory = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {
        System.out.println("Pictionary Server started on port 1234...");
        ServerSocket serverSocket = new ServerSocket(1234);

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("New client connected: " + socket.getInetAddress());
            new ClientHandler(socket).start();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Send existing canvas history to the new client
                synchronized (drawHistory) {
                    for (String line : drawHistory) {
                        out.println(line);
                    }
                }

                synchronized (clients) {
                    clients.add(this);
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.equals("PING")) {
                        out.println("PONG");
                        continue;
                    }

                    if (msg.startsWith("DRAW ") || msg.equals("CLEAR")) {
                        synchronized (drawHistory) {
                            if (msg.equals("CLEAR")) {
                                drawHistory.clear();
                            } else {
                                drawHistory.add(msg);
                            }
                        }
                        broadcast(msg, this);
                    } else if (msg.startsWith("CHAT ")) {
                        // Chat message - broadcast to everyone including sender
                        broadcast(msg, null);
                        System.out.println("Chat: " + msg);
                    } else if (msg.startsWith("NAME ")) {
                        clientName = msg.substring(5);
                        System.out.println("Client named: " + clientName);
                        // Send initial user list to this client
                        sendUserList(this);
                        // Broadcast updated user list to all clients
                        broadcastUserList();
                    } else if (msg.equals("GET_USERS")) {
                        sendUserList(this);
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + clientName);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {}
                synchronized (clients) {
                    clients.remove(this);
                }
                // Broadcast updated user list after removal
                broadcastUserList();
            }
        }

        private void broadcast(String message, ClientHandler exclude) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (exclude == null || client != exclude) {
                        client.out.println(message);
                    }
                }
            }
        }

        private void sendUserList(ClientHandler client) {
            StringBuilder userList = new StringBuilder("USERS ");
            synchronized (clients) {
                boolean first = true;
                for (ClientHandler c : clients) {
                    if (c.clientName != null) {
                        if (!first) userList.append(",");
                        userList.append(c.clientName);
                        first = false;
                    }
                }
            }
            if (client.out != null) {
                client.out.println(userList.toString());
            }
        }

        private void broadcastUserList() {
            StringBuilder userList = new StringBuilder("USERS ");
            synchronized (clients) {
                boolean first = true;
                for (ClientHandler c : clients) {
                    if (c.clientName != null) {
                        if (!first) userList.append(",");
                        userList.append(c.clientName);
                        first = false;
                    }
                }
            }
            String message = userList.toString();
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.out != null) {
                        client.out.println(message);
                    }
                }
            }
        }
    }
}