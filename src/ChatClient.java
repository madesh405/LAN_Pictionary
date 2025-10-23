import javax.swing.*;
import javax.sound.sampled.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Base64;

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
    private String currentCallUser = null;
    private boolean isInCall = false;
    private AudioCapture audioCapture;
    private AudioPlayback audioPlayback;

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

        // Panel to hold user list with call buttons
        JPanel userListPanel = new JPanel(new BorderLayout());
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setCellRenderer(new UserListCellRenderer());

        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int index = userList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    Rectangle cellBounds = userList.getCellBounds(index, index);
                    // Check if click is in the call button area (right 40 pixels)
                    if (e.getX() > cellBounds.x + cellBounds.width - 40) {
                        String selectedUser = userListModel.getElementAt(index);
                        if (!selectedUser.equals(username)) {
                            initiateCall(selectedUser);
                        } else {
                            JOptionPane.showMessageDialog(ChatClient.this, "You cannot call yourself!", "Error", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userList);
        userListPanel.add(userScroll, BorderLayout.CENTER);
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
                        } else if (msg.startsWith("CALL_REQUEST ")) {
                            String caller = msg.substring(13);
                            SwingUtilities.invokeLater(() -> handleCallRequest(caller));
                        } else if (msg.startsWith("CALL_ACCEPT ")) {
                            String callee = msg.substring(12);
                            SwingUtilities.invokeLater(() -> handleCallAccept(callee));
                        } else if (msg.startsWith("CALL_REJECT ")) {
                            String callee = msg.substring(12);
                            SwingUtilities.invokeLater(() -> handleCallReject(callee));
                        } else if (msg.startsWith("CALL_END ")) {
                            String otherUser = msg.substring(9);
                            SwingUtilities.invokeLater(() -> handleCallEnd(otherUser));
                        } else if (msg.startsWith("VOICE_DATA ")) {
                            String[] parts = msg.substring(11).split(" ", 2);
                            if (parts.length == 2) {
                                String sender = parts[0];
                                String voiceData = parts[1];
                                if (isInCall && sender.equals(currentCallUser)) {
                                    playAudio(voiceData);
                                }
                            }
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

    private void initiateCall(String targetUser) {
        if (isInCall) {
            JOptionPane.showMessageDialog(this, "You are already in a call!", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        currentCallUser = targetUser;
        out.println("CALL_REQUEST " + targetUser);
        chatArea.append("Calling " + targetUser + "...\n");
    }

    private void handleCallRequest(String caller) {
        if (isInCall) {
            out.println("CALL_REJECT " + caller);
            return;
        }

        int response = JOptionPane.showConfirmDialog(this,
                caller + " is calling you. Accept?",
                "Incoming Call",
                JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            currentCallUser = caller;
            isInCall = true;
            out.println("CALL_ACCEPT " + caller);
            chatArea.append("Voice call started with " + caller + "\n");
            startVoiceChat();
        } else {
            out.println("CALL_REJECT " + caller);
        }
    }

    private void handleCallAccept(String callee) {
        isInCall = true;
        chatArea.append("Voice call started with " + callee + "\n");
        startVoiceChat();

        // Show end call button in a non-blocking way
        SwingUtilities.invokeLater(() -> {
            JButton endCallBtn = new JButton("End Call");
            JDialog callDialog = new JDialog(this, "Voice Call", false);
            callDialog.setLayout(new BorderLayout());

            JLabel callLabel = new JLabel("Voice call active with " + callee, SwingConstants.CENTER);
            callLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
            callDialog.add(callLabel, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel();
            endCallBtn.setBackground(new Color(220, 50, 50));
            endCallBtn.setForeground(Color.WHITE);
            endCallBtn.setFocusPainted(false);
            buttonPanel.add(endCallBtn);
            callDialog.add(buttonPanel, BorderLayout.SOUTH);

            endCallBtn.addActionListener(e -> {
                endCall();
                callDialog.dispose();
            });

            callDialog.pack();
            callDialog.setLocationRelativeTo(this);
            callDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            callDialog.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    endCall();
                    callDialog.dispose();
                }
            });
            callDialog.setVisible(true);
        });
    }

    private void handleCallReject(String callee) {
        currentCallUser = null;
        chatArea.append(callee + " rejected your call.\n");
        JOptionPane.showMessageDialog(this, callee + " rejected your call.", "Call Rejected", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleCallEnd(String otherUser) {
        stopVoiceChat();
        isInCall = false;
        currentCallUser = null;
        chatArea.append("Call ended with " + otherUser + ".\n");
        JOptionPane.showMessageDialog(this, "Call ended.", "Call Ended", JOptionPane.INFORMATION_MESSAGE);
    }

    private void endCall() {
        if (isInCall && currentCallUser != null) {
            out.println("CALL_END " + currentCallUser);
            stopVoiceChat();
            isInCall = false;
            chatArea.append("Call ended with " + currentCallUser + ".\n");
            currentCallUser = null;
        }
    }

    private void startVoiceChat() {
        try {
            audioCapture = new AudioCapture();
            audioPlayback = new AudioPlayback();
            audioCapture.start();
            audioPlayback.start();
        } catch (Exception e) {
            chatArea.append("Error starting voice chat: " + e.getMessage() + "\n");
            endCall();
        }
    }

    private void stopVoiceChat() {
        if (audioCapture != null) {
            audioCapture.stopCapture();
            audioCapture = null;
        }
        if (audioPlayback != null) {
            audioPlayback.stopPlayback();
            audioPlayback = null;
        }
    }

    private void playAudio(String base64Data) {
        if (audioPlayback != null) {
            try {
                byte[] audioData = Base64.getDecoder().decode(base64Data);
                audioPlayback.play(audioData);
            } catch (Exception e) {
                // Silently ignore decode errors
            }
        }
    }

    // Audio capture class
    class AudioCapture extends Thread {
        private TargetDataLine microphone;
        private volatile boolean running = true;

        public AudioCapture() throws LineUnavailableException {
            AudioFormat format = new AudioFormat(8000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
        }

        public void run() {
            microphone.start();
            byte[] buffer = new byte[1024];

            while (running) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && isInCall && currentCallUser != null) {
                    String encoded = Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, bytesRead));
                    out.println("VOICE_DATA " + currentCallUser + " " + encoded);
                }
            }
        }

        public void stopCapture() {
            running = false;
            if (microphone != null) {
                microphone.stop();
                microphone.close();
            }
        }
    }

    // Audio playback class
    class AudioPlayback extends Thread {
        private SourceDataLine speakers;
        private volatile boolean running = true;
        private java.util.concurrent.BlockingQueue<byte[]> audioQueue;

        public AudioPlayback() throws LineUnavailableException {
            AudioFormat format = new AudioFormat(8000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            audioQueue = new java.util.concurrent.LinkedBlockingQueue<>();
        }

        public void run() {
            speakers.start();

            while (running) {
                try {
                    byte[] audioData = audioQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (audioData != null) {
                        speakers.write(audioData, 0, audioData.length);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        public void play(byte[] audioData) {
            try {
                audioQueue.offer(audioData);
            } catch (Exception e) {
                // Silently ignore
            }
        }

        public void stopPlayback() {
            running = false;
            if (speakers != null) {
                speakers.drain();
                speakers.stop();
                speakers.close();
            }
        }
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

    // Custom cell renderer for user list with call button
    class UserListCellRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel nameLabel;
        private JLabel callButton;

        public UserListCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setOpaque(true);

            nameLabel = new JLabel();
            nameLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

            callButton = new JLabel("((\uD83D\uDCDE))");
            callButton.setFont(new Font("Dialog", Font.PLAIN, 16));
            callButton.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            callButton.setHorizontalAlignment(SwingConstants.CENTER);
            callButton.setPreferredSize(new Dimension(35, 20));

            add(nameLabel, BorderLayout.CENTER);
            add(callButton, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value);

            // Hide call button for own username
            if (value.equals(username)) {
                callButton.setVisible(false);
            } else {
                callButton.setVisible(true);
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
                nameLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                nameLabel.setForeground(list.getForeground());
            }

            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient());
    }
}