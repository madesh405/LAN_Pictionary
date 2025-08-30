import javax.swing.*;
import java.io.*;
import java.net.*;

public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private GUI gui; // reference to GUI

    // Constructor connects to server
    public Client(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Start a thread to listen for server messages
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        if (gui != null) {
                            gui.addMessage(msg); // update chat area in GUI
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Link GUI to client
    public void setGUI(GUI gui) {
        this.gui = gui;
    }

    // Send message to server
    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    // Entry point to run client
    public static void main(String[] args) {
        // Connect to server (adjust host/port as needed)
        Client client = new Client("localhost", 1234);

        // Create GUI and pass client
        SwingUtilities.invokeLater(() -> {
            new GUI(client);
        });
    }
}
