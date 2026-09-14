import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class timeclient extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int PORT = 7000;

    private final JLabel timeLabel = new JLabel("Đang kết nối...", SwingConstants.CENTER);
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resumeButton = new JButton("Resume");
    private final AtomicBoolean disconnectMessageShown = new AtomicBoolean(false);

    private transient Socket socket;
    private transient DataInputStream input;
    private transient DataOutputStream output;
    private volatile boolean closing;

    @SuppressWarnings("this-escape")
    public timeclient(String serverIP) {
        createUI(serverIP);
        connect(serverIP);
    }

    private void createUI(String serverIP) {
        setTitle("TCP Time Client - " + serverIP + ":" + PORT);
        setSize(500, 220);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        timeLabel.setFont(new Font("SansSerif", Font.BOLD, 34));
        pauseButton.setEnabled(false);
        resumeButton.setEnabled(false);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(pauseButton);
        buttonPanel.add(resumeButton);

        add(timeLabel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pauseButton.addActionListener(e -> sendCommand("PAUSE"));
        resumeButton.addActionListener(e -> sendCommand("RESUME"));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closing = true;
                closeConnection();
            }
        });

        setVisible(true);
    }

    private void connect(String serverIP) {
        Thread connectionThread = new Thread(() -> {
            try {
                socket = new Socket(serverIP, PORT);
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                SwingUtilities.invokeLater(() -> {
                    pauseButton.setEnabled(true);
                    resumeButton.setEnabled(false);
                });
                receiveTime();
            } catch (IOException e) {
                if (!closing) {
                    showDisconnected("Không thể kết nối tới " + serverIP + ":" + PORT);
                }
            } finally {
                closeConnection();
            }
        }, "server-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void receiveTime() throws IOException {
        while (!socket.isClosed()) {
            String time = input.readUTF();
            SwingUtilities.invokeLater(() -> timeLabel.setText(time));
        }
    }

    private void sendCommand(String command) {
        if (output == null || socket == null || socket.isClosed()) {
            showDisconnected("Chưa kết nối được với server.");
            return;
        }

        try {
            output.writeUTF(command);
            output.flush();

            boolean isPaused = command.equals("PAUSE");
            pauseButton.setEnabled(!isPaused);
            resumeButton.setEnabled(isPaused);
        } catch (IOException e) {
            showDisconnected("Đã mất kết nối với server.");
            closeConnection();
        }
    }

    private void showDisconnected(String message) {
        if (!disconnectMessageShown.compareAndSet(false, true)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (closing || !isDisplayable()) {
                return;
            }
            timeLabel.setText("Mất kết nối");
            pauseButton.setEnabled(false);
            resumeButton.setEnabled(false);
            JOptionPane.showMessageDialog(this, message, "Lỗi kết nối",
                    JOptionPane.ERROR_MESSAGE);
        });
    }

    private void closeConnection() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String serverIP = getServerIP(args);
            if (serverIP != null) {
                new timeclient(serverIP);
            }
        });
    }

    private static String getServerIP(String[] args) {
        if (args.length > 0 && !args[0].trim().isEmpty()) {
            return args[0].trim();
        }

        while (true) {
            String serverIP = JOptionPane.showInputDialog(null,
                    "Nhập IP của Time Server (cổng 7000):", "localhost");
            if (serverIP == null) {
                return null;
            }

            serverIP = serverIP.trim();
            if (!serverIP.isEmpty()) {
                return serverIP;
            }

            JOptionPane.showMessageDialog(null, "Vui lòng nhập IP server.",
                    "Thông tin không hợp lệ", JOptionPane.WARNING_MESSAGE);
        }
    }
}
