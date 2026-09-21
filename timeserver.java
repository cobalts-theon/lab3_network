import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class timeserver {
    private static final int PORT = 7000;

    public static void main(String[] args) {
        SystemClockMonitor.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Time Server đang chạy tại cổng " + PORT);
            System.out.println("Múi giờ hệ thống: " + SystemClockMonitor.getZoneId());
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

/**
 * Theo dõi đồng hồ và múi giờ hệ thống một lần cho toàn server. Java giữ cache
 * múi giờ mặc định, vì vậy cần xóa cache này để nhận thay đổi từ Windows
 * Settings trong lúc chương trình vẫn đang chạy.
 */
final class SystemClockMonitor {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final long CHECK_INTERVAL_MS = 500;
    private static final long CLOCK_CHANGE_THRESHOLD_MS = 2000;
    private static final AtomicReference<ClockState> STATE = new AtomicReference<>();
    private static boolean started;

    private SystemClockMonitor() {
    }

    static synchronized void start() {
        if (started) {
            return;
        }

        ZoneId initialZone = reloadSystemTimeZone();
        STATE.set(new ClockState(initialZone, 0));

        Thread monitorThread = new Thread(SystemClockMonitor::monitor,
                "system-clock-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        started = true;
    }

    static String getZoneId() {
        return STATE.get().zoneId.getId();
    }

    static ClockSnapshot snapshot() {
        ClockState state = STATE.get();
        String formattedTime = TIME_FORMAT.format(Instant.now().atZone(state.zoneId));
        return new ClockSnapshot(formattedTime, state.changeVersion);
    }

    private static void monitor() {
        long previousWallMillis = System.currentTimeMillis();
        long previousNanoTime = System.nanoTime();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            long currentWallMillis = System.currentTimeMillis();
            long currentNanoTime = System.nanoTime();
            long wallElapsed = currentWallMillis - previousWallMillis;
            long monotonicElapsed = (currentNanoTime - previousNanoTime) / 1_000_000L;
            long clockDifference = wallElapsed - monotonicElapsed;

            ClockState previousState = STATE.get();
            ZoneId currentZone = reloadSystemTimeZone();
            boolean zoneChanged = !currentZone.equals(previousState.zoneId);
            boolean clockChanged = clockDifference > CLOCK_CHANGE_THRESHOLD_MS
                    || clockDifference < -CLOCK_CHANGE_THRESHOLD_MS;

            if (zoneChanged || clockChanged) {
                ClockState newState = new ClockState(currentZone,
                        previousState.changeVersion + 1);
                STATE.set(newState);

                System.out.println("[!] Phát hiện thay đổi thời gian hệ thống:");
                if (zoneChanged) {
                    System.out.println("    Múi giờ: " + previousState.zoneId
                            + " -> " + currentZone);
                }
                if (clockChanged) {
                    System.out.println("    Đồng hồ lệch " + clockDifference
                            + "ms so với thời gian thực đã trôi qua");
                }
                System.out.println("    Thời gian mới: " + snapshot().formattedTime);
            }

            previousWallMillis = currentWallMillis;
            previousNanoTime = currentNanoTime;
        }
    }

    /**
     * TimeZone.getDefault() được JVM cache. Xóa cả thuộc tính user.timezone và
     * cache TimeZone buộc JVM hỏi lại múi giờ hiện tại của hệ điều hành.
     */
    private static synchronized ZoneId reloadSystemTimeZone() {
        System.clearProperty("user.timezone");
        TimeZone.setDefault(null);
        return TimeZone.getDefault().toZoneId();
    }

    private static final class ClockState {
        private final ZoneId zoneId;
        private final long changeVersion;

        private ClockState(ZoneId zoneId, long changeVersion) {
            this.zoneId = zoneId;
            this.changeVersion = changeVersion;
        }
    }

    static final class ClockSnapshot {
        private final String formattedTime;
        private final long changeVersion;

        private ClockSnapshot(String formattedTime, long changeVersion) {
            this.formattedTime = formattedTime;
            this.changeVersion = changeVersion;
        }

        String getFormattedTime() {
            return formattedTime;
        }

        long getChangeVersion() {
            return changeVersion;
        }
    }
}

class ClientHandler extends Thread {
    // Prefix đặc biệt gửi kèm khi phát hiện thay đổi giờ hệ thống
    static final String TIME_CHANGE_PREFIX = "TIMECHANGE:";

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
            long lastChangeVersion = SystemClockMonitor.snapshot().getChangeVersion();

            while (running) {
                Thread.sleep(1000);

                SystemClockMonitor.ClockSnapshot snapshot = SystemClockMonitor.snapshot();
                boolean timeChanged = snapshot.getChangeVersion() != lastChangeVersion;

                if (timeChanged) {
                    output.writeUTF(TIME_CHANGE_PREFIX + snapshot.getFormattedTime());
                    output.flush();
                } else if (!paused.get()) {
                    output.writeUTF(snapshot.getFormattedTime());
                    output.flush();
                }

                lastChangeVersion = snapshot.getChangeVersion();
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
