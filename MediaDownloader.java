import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class MediaDownloader {
    private static final String[] FORMAT_VIDEO = {"MP4", "MKV", "WEBM"};
    private static final String[] FORMAT_AUDIO = {"MP3", "M4A", "WAV"};

    private static final String[] KUALITAS_VIDEO = {
        "Terbaik", "2160p", "1440p", "1080p", "720p", "480p", "360p"
    };

    private static final String[] KUALITAS_AUDIO = {
        "128 kbps", "192 kbps", "256 kbps", "320 kbps"
    };

    private final JFrame jendela = new JFrame("Pengunduh Media");
    private final JTextField tautan = new JTextField();
    private final JComboBox<String> mode =
        new JComboBox<>(new String[]{"Video + audio", "Audio saja"});
    private final JComboBox<String> format = new JComboBox<>();
    private final JComboBox<String> kualitas = new JComboBox<>();
    private final JTextField folder = new JTextField(
        Path.of(System.getProperty("user.home"), "Downloads").toString()
    );
    private final JTextArea log = new JTextArea(12, 55);
    private final JButton tombolUnduh = new JButton("Unduh");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MediaDownloader().tampilkan());
    }

    private void tampilkan() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tautan media"));
        form.add(tautan);
        form.add(new JLabel("Jenis unduhan"));
        form.add(mode);
        form.add(new JLabel("Format file"));
        form.add(format);
        form.add(new JLabel("Kualitas"));
        form.add(kualitas);
        form.add(new JLabel("Folder tujuan"));
        form.add(folder);

        mode.addActionListener(event -> perbaruiFormat());
        format.addActionListener(event -> perbaruiKualitas());
        tombolUnduh.addActionListener(event -> mulaiUnduh());
        perbaruiFormat();

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        JPanel isi = new JPanel(new BorderLayout(0, 12));
        isi.setBorder(new EmptyBorder(16, 16, 16, 16));
        isi.add(form, BorderLayout.NORTH);
        isi.add(new JScrollPane(log), BorderLayout.CENTER);
        isi.add(tombolUnduh, BorderLayout.SOUTH);

        jendela.setContentPane(isi);
        jendela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jendela.pack();
        jendela.setLocationRelativeTo(null);
        jendela.setVisible(true);
    }

    private void perbaruiFormat() {
        boolean audioSaja = mode.getSelectedIndex() == 1;
        format.setModel(new DefaultComboBoxModel<>(
            audioSaja ? FORMAT_AUDIO : FORMAT_VIDEO
        ));
        perbaruiKualitas();
    }

    private void perbaruiKualitas() {
        boolean audioSaja = mode.getSelectedIndex() == 1;
        boolean wav = "WAV".equals(format.getSelectedItem());

        String[] pilihanKualitas = audioSaja ? KUALITAS_AUDIO : KUALITAS_VIDEO;
        if (wav) {
            pilihanKualitas = new String[]{"PCM"};
        }

        kualitas.setModel(new DefaultComboBoxModel<>(pilihanKualitas));
        kualitas.setEnabled(!wav);
    }

    private void mulaiUnduh() {
        URI url;
        Path tujuan;

        try {
            url = URI.create(tautan.getText().strip());
            boolean tautanWeb = "https".equalsIgnoreCase(url.getScheme())
                || "http".equalsIgnoreCase(url.getScheme());

            if (!tautanWeb || url.getHost() == null || url.getUserInfo() != null
                || folder.getText().isBlank()) {
                throw new IllegalArgumentException();
            }

            tujuan = Path.of(folder.getText().strip()).toAbsolutePath();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(jendela,
                "Periksa tautan HTTP/HTTPS dan folder tujuan.");
            return;
        }

        boolean audioSaja = mode.getSelectedIndex() == 1;
        String pilihan = (String) kualitas.getSelectedItem();
        String formatFile = ((String) format.getSelectedItem())
            .toLowerCase(Locale.ROOT);

        List<String> perintah = buatPerintah(
            url, tujuan, audioSaja, pilihan, formatFile
        );

        log.setText("Memulai unduhan...\n");
        tombolUnduh.setEnabled(false);

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Files.createDirectories(tujuan);

                ProcessBuilder builder = new ProcessBuilder(perintah)
                    .redirectErrorStream(true);
                builder.environment().put("PYTHONIOENCODING", "utf-8");

                Process proses = builder.start();
                try (BufferedReader keluaran =
                         proses.inputReader(StandardCharsets.UTF_8)) {
                    String baris;
                    while ((baris = keluaran.readLine()) != null) {
                        publish(baris);
                    }
                }

                return proses.waitFor();
            }

            @Override
            protected void process(List<String> baris) {
                log.append(String.join("\n", baris) + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    log.append(get() == 0
                        ? "\nUnduhan selesai.\n"
                        : "\nUnduhan gagal. Periksa pesan di atas.\n");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.append("\nProses terhenti.\n");
                } catch (ExecutionException e) {
                    log.append("\nGagal: " + e.getCause().getMessage() + "\n");
                } finally {
                    tombolUnduh.setEnabled(true);
                }
            }
        }.execute();
    }

    private List<String> buatPerintah(
        URI url, Path tujuan, boolean audioSaja, String pilihan, String formatFile
    ) {
        Path tools = Path.of("tools").toAbsolutePath();
        List<String> perintah = new ArrayList<>(List.of(
            tools.resolve("yt-dlp.exe").toString(),
            "--ignore-config", "--no-playlist", "--playlist-items", "1",
            "--newline", "--no-overwrites", "--windows-filenames",
            "--ffmpeg-location", tools.toString(),
            "--paths", "home:" + tujuan,
            "--output", "%(title).150B [%(id)s].%(ext)s",
            "--print", "after_move:Disimpan: %(filepath)s",
            "--progress"
        ));

        Path deno = tools.resolve("deno.exe");
        if (Files.isRegularFile(deno)) {
            perintah.addAll(List.of("--js-runtimes", "deno:" + deno));
        }

        if (audioSaja) {
            perintah.addAll(List.of(
                "-f", "bestaudio/best",
                "--extract-audio", "--audio-format", formatFile
            ));

            if (!formatFile.equals("wav")) {
                String bitrate = pilihan.replace(" kbps", "K");
                perintah.addAll(List.of("--audio-quality", bitrate));
            }
        } else {
            perintah.addAll(List.of(
                "-f", "bv+ba/b",
                "--recode-video", formatFile
            ));

            if (!pilihan.equals("Terbaik")) {
                String resolusi = pilihan.replace("p", "");
                perintah.addAll(List.of("-S", "res:" + resolusi));
            }
        }

        perintah.addAll(List.of("--", url.toASCIIString()));
        return perintah;
    }
}