import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class timeserver {
    private static final int PORT = 7000;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Time Server đang chạy tại cổng " + PORT);
            System.out.println("Đang chờ client kết nối...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Không thể khởi động server: " + e.getMessage());
        }
    }

}

class ClientHandler extends Thread {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final Socket socket;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile boolean running = true;
    private Thread senderThread;

    ClientHandler(Socket socket) {
        super("client-" + socket.getRemoteSocketAddress());
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientName = socket.getRemoteSocketAddress().toString();
        System.out.println("Client đã kết nối: " + clientName);

        try (DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            senderThread = new Thread(() -> sendTimeEverySecond(output),
                    "time-sender-" + clientName);
            senderThread.start();

            while (running) {
                String command = input.readUTF().trim().toUpperCase(Locale.ROOT);
                switch (command) {
                    case "PAUSE":
                        paused.set(true);
                        System.out.println(clientName + " -> PAUSE");
                        break;
                    case "RESUME":
                        paused.set(false);
                        System.out.println(clientName + " -> RESUME");
                        break;
                    default:
                        System.out.println(clientName + " -> lệnh không hợp lệ: " + command);
                }
            }
        } catch (EOFException e) {
            // Client đóng kết nối bình thường.
        } catch (IOException e) {
            if (running) {
                System.out.println("Lỗi kết nối với " + clientName + ": " + e.getMessage());
            }
        } finally {
            stopHandler();
            System.out.println("Client đã ngắt kết nối: " + clientName);
        }
    }

    private void sendTimeEverySecond(DataOutputStream output) {
        try {
            while (running) {
                if (!paused.get()) {
                    output.writeUTF(LocalDateTime.now().format(TIME_FORMAT));
                    output.flush();
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Đóng socket để đánh thức luồng đang chờ đọc lệnh từ client.
            running = false;
            closeSocket();
        }
    }

    private void stopHandler() {
        running = false;
        if (senderThread != null) {
            senderThread.interrupt();
        }
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
