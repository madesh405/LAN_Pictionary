# LAN Pictionary - Multiplayer Drawing & Voice Chat

A real-time multiplayer drawing application with integrated voice chat, built with Java Swing.

## Features

- Real-time collaborative drawing
- Pencil and eraser tools
- Group text chat
- One-on-one voice calls between users
- Live user list
- Canvas clear sync
- Ping monitor
- LAN network support

## Requirements

- Java JDK 11 or higher
- Microphone (for voice chat)
- All computers on the same network

## Installation

Clone or download the repository:

```bash
git clone https://github.com/madesh405/LAN_Pictionary.git
cd LAN_Pictionary
```

Compile the source files:

```bash
javac Server.java
javac ChatClient.java
```

## Running the Application

### Start the Server

On one computer, run:

```bash
java Server
```

The server will start on port 1234.

Find your server's IP address:
- Windows: `ipconfig`
- Mac/Linux: `ifconfig` or `ip addr`

Look for IPv4 address (e.g., 192.168.1.100)

### Connect Clients

On each client computer, run:

```bash
java ChatClient
```

When prompted:
1. Enter your username
2. Enter the server IP address
   - Use `localhost` if on the same machine as server
   - Use the server's IP (e.g., `192.168.1.100`) for other machines

## Usage

- **Drawing**: Click and drag on canvas
- **Tools**: Click Pencil or Eraser buttons to switch modes
- **Clear**: Click Clear Canvas to reset for everyone
- **Chat**: Type message and press Enter
- **Voice Call**: Click phone icon next to username to call
- **End Call**: Click End Call button in call dialog

