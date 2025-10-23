import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClient extends JFrame {
    private Canvas canvas;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JLabel pingLabel;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private Timer pingTimer;
    private long lastPingTime;

    public ChatClient() {
        setTitle("Pictionary Client");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Canvas for drawing (center)
        canvas = new Canvas();
        add(canvas, BorderLayout.CENTER);

        // Right panel for users list
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 700));

        JLabel usersLabel = new JLabel("Connected Users", SwingConstants.CENTER);
        usersLabel.setFont(new Font("Arial", Font.BOLD, 14));
        usersLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        rightPanel.add(usersLabel, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScroll = new JScrollPane(userList);
        rightPanel.add(userScroll, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.EAST);

        // Bottom panel for chat and controls
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(1200, 200));

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setRows(6);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        bottomPanel.add(chatScroll, BorderLayout.CENTER);

        // Chat input and controls
        JPanel inputPanel = new JPanel(new BorderLayout());

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton pencilBtn = new JButton("✏️ Pencil");
        JButton eraserBtn = new JButton("🧹 Eraser");
        JButton clearBtn = new JButton("Clear Canvas");
        pingLabel = new JLabel("Ping: -- ms");
        pingLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        pencilBtn.setBackground(new Color(100, 200, 100));
        pencilBtn.setOpaque(true);

        controlsPanel.add(pencilBtn);
        controlsPanel.add(eraserBtn);
        controlsPanel.add(clearBtn);
        controlsPanel.add(pingLabel);
        inputPanel.add(controlsPanel, BorderLayout.WEST);

        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        JButton sendBtn = new JButton("Send");
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendBtn, BorderLayout.EAST);
        inputPanel.add(chatInputPanel, BorderLayout.CENTER);

        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Event listeners
        pencilBtn.addActionListener(e -> {
            canvas.setDrawMode(true);
            pencilBtn.setBackground(new Color(100, 200, 100));
            eraserBtn.setBackground(null);
        });
        eraserBtn.addActionListener(e -> {
            canvas.setDrawMode(false);
            eraserBtn.setBackground(new Color(255, 200, 200));
            pencilBtn.setBackground(null);
        });
        sendBtn.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        clearBtn.addActionListener(e -> clearCanvas());

        // Connect to server
        connectToServer();

        // Start ping monitoring
        startPingMonitor();

        setVisible(true);
    }

    private void connectToServer() {
        try {
            username = JOptionPane.showInputDialog(this, "Enter your name:", "Username", JOptionPane.QUESTION_MESSAGE);
            if (username == null || username.trim().isEmpty()) {
                username = "User" + new Random().nextInt(1000);
            }

            String serverIP = JOptionPane.showInputDialog(this, "Enter server IP:", "Connect", JOptionPane.QUESTION_MESSAGE);
            if (serverIP == null || serverIP.trim().isEmpty()) {
                serverIP = "localhost";
            }

            socket = new Socket(serverIP, 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("NAME " + username);

            // Start listening thread
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        if (msg.equals("PONG")) {
                            long ping = System.currentTimeMillis() - lastPingTime;
                            SwingUtilities.invokeLater(() -> pingLabel.setText("Ping: " + ping + " ms"));
                        } else if (msg.startsWith("DRAW ")) {
                            String[] parts = msg.substring(5).split(" ");
                            int x1 = Integer.parseInt(parts[0]);
                            int y1 = Integer.parseInt(parts[1]);
                            int x2 = Integer.parseInt(parts[2]);
                            int y2 = Integer.parseInt(parts[3]);
                            boolean isEraser = parts.length > 4 && parts[4].equals("ERASE");
                            SwingUtilities.invokeLater(() -> canvas.drawLine(x1, y1, x2, y2, isEraser));
                        } else if (msg.equals("CLEAR")) {
                            SwingUtilities.invokeLater(() -> canvas.clear());
                        } else if (msg.startsWith("CHAT ")) {
                            String chatMsg = msg.substring(5);
                            SwingUtilities.invokeLater(() -> chatArea.append(chatMsg + "\n"));
                        } else if (msg.startsWith("USERS ")) {
                            String userListStr = msg.substring(6);
                            SwingUtilities.invokeLater(() -> updateUserList(userListStr));
                        }
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ChatClient.this, "Disconnected from server", "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(0);
                    });
                }
            }).start();

            chatArea.append("Connected to server as " + username + "\n");

            // Request initial user list
            out.println("GET_USERS");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not connect to server: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void updateUserList(String userListStr) {
        userListModel.clear();
        if (!userListStr.isEmpty()) {
            String[] users = userListStr.split(",");
            for (String user : users) {
                if (!user.trim().isEmpty()) {
                    userListModel.addElement(user.trim());
                }
            }
        }
    }

    private void startPingMonitor() {
        pingTimer = new Timer(2000, e -> {
            lastPingTime = System.currentTimeMillis();
            out.println("PING");
        });
        pingTimer.start();
    }

    private void sendChat() {
        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            out.println("CHAT " + username + ": " + msg);
            chatInput.setText("");
        }
    }

    private void clearCanvas() {
        out.println("CLEAR");
        canvas.clear();
    }

    // Canvas class for drawing
    class Canvas extends JPanel {
        private Image image;
        private Graphics2D g2;
        private int lastX = -1, lastY = -1;
        private boolean drawMode = true; // true = pencil, false = eraser

        public Canvas() {
            setBackground(Color.WHITE);

            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    lastX = e.getX();
                    lastY = e.getY();
                }

                public void mouseDragged(MouseEvent e) {
                    int x = e.getX();
                    int y = e.getY();
                    if (lastX != -1 && lastY != -1) {
                        drawLine(lastX, lastY, x, y, !drawMode);
                        String mode = drawMode ? "" : " ERASE";
                        out.println("DRAW " + lastX + " " + lastY + " " + x + " " + y + mode);
                    }
                    lastX = x;
                    lastY = y;
                }

                public void mouseReleased(MouseEvent e) {
                    lastX = -1;
                    lastY = -1;
                }
            };

            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public void setDrawMode(boolean drawMode) {
            this.drawMode = drawMode;
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                image = createImage(getWidth(), getHeight());
                g2 = (Graphics2D) image.getGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                clear();
            }
            g.drawImage(image, 0, 0, null);
        }

        public void drawLine(int x1, int y1, int x2, int y2, boolean isEraser) {
            if (g2 != null) {
                g2.setStroke(new BasicStroke(isEraser ? 20 : 3));
                g2.setColor(isEraser ? Color.WHITE : Color.BLACK);
                g2.drawLine(x1, y1, x2, y2);
                repaint();
            }
        }

        public void clear() {
            if (g2 != null) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                repaint();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient());
    }
}