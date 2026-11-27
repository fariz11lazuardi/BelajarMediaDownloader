import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Cursor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.imageio.ImageIO;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
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
    private final JButton tombolUnduh = new TombolModern("Unduh");
    private final JButton tombolBatal = new TombolModern("Batal");
    private final JButton tombolBukaFolder = new TombolModern("Buka folder hasil");
    private final java.awt.CardLayout pilihanAksiUnduhan = new java.awt.CardLayout();
    private final JPanel aksiUnduhan = new JPanel(pilihanAksiUnduhan);
    private Path folderHasil;
    private final JProgressBar progres = new JProgressBar(0, 100);
    private KendaliUnduh unduhanAktif;

    private final JButton tombolCek = new TombolModern("Cek tautan");
    private final JLabel gambarMedia = new JLabel(" ", SwingConstants.CENTER);
    private final JTextArea infoMedia = new JTextArea(3, 28);

    private record Pratinjau(String judul, ImageIcon thumbnail) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MediaDownloader().tampilkan());
    }

    private static final Color KANVAS = new Color(0xF1F5FB);
    private static final Color PUTIH = Color.WHITE;
    private static final Color TINTA = new Color(0x142440);
    private static final Color REDUP = new Color(0x475A75);
    private static final Color GARIS = new Color(0xD6E1EF);
    private static final Color AKSEN = new Color(0x2459D3);
    private static final Color BIRU_MUDA = new Color(0xE8EFFD);
    private static final String FONT = "Segoe UI";

    private void tampilkan() {
        jendela.setTitle("MediaDownloader");
        JPanel utama = new JPanel(new BorderLayout());
        utama.setBackground(KANVAS);

        JPanel header = new JPanel(new BorderLayout(13, 0));
        header.setBackground(TINTA);
        header.setBorder(new EmptyBorder(18, 24, 18, 24));
        JLabel ikon = new JLabel("\u2193", SwingConstants.CENTER);
        ikon.setFont(new Font(FONT, Font.BOLD, 27));
        ikon.setForeground(PUTIH);
        ikon.setOpaque(true);
        ikon.setBackground(AKSEN);
        ikon.setPreferredSize(new Dimension(42, 42));
        header.add(ikon, BorderLayout.WEST);
        JLabel nama = new JLabel("MediaDownloader");
        nama.setForeground(PUTIH);
        nama.setFont(new Font(FONT, Font.BOLD, 24));
        header.add(nama, BorderLayout.CENTER);
        utama.add(header, BorderLayout.NORTH);

        aturKolom(tautan);
        aturKolom(folder);
        aturPilihan(mode);
        aturPilihan(format);
        aturPilihan(kualitas);
        aturTombol(tombolCek, false);
        aturTombol(tombolUnduh, true);

        JPanel barisTautan = tanpaLatar(new BorderLayout(9, 0));
        barisTautan.add(tautan, BorderLayout.CENTER);
        barisTautan.add(tombolCek, BorderLayout.EAST);
        tombolCek.setEnabled(false);
        tombolCek.addActionListener(event -> cekTautan());
        tautan.addActionListener(event -> tombolCek.doClick());
        tautan.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { tautanBerubah(); }
            @Override public void removeUpdate(DocumentEvent event) { tautanBerubah(); }
            @Override public void changedUpdate(DocumentEvent event) { tautanBerubah(); }
        });

        JPanel pilihan = tanpaLatar(new GridLayout(1, 3, 10, 0));
        pilihan.add(kolom("Jenis unduhan", mode, mode));
        pilihan.add(kolom("Format file", format, format));
        pilihan.add(kolom("Kualitas", kualitas, kualitas));
        JPanel barisFolder = tanpaLatar(new BorderLayout(9, 0));
        JButton pilihFolder = new TombolModern("Pilih folder...");
        aturTombol(pilihFolder, false);
        pilihFolder.addActionListener(event -> pilihFolderTujuan(pilihFolder));
        barisFolder.add(folder, BorderLayout.CENTER);
        barisFolder.add(pilihFolder, BorderLayout.EAST);

        JPanel formulir = kartu();
        formulir.add(kolom("Tautan media", barisTautan, tautan));
        formulir.add(Box.createVerticalStrut(12));
        formulir.add(pilihan);
        formulir.add(Box.createVerticalStrut(12));
        formulir.add(kolom("Folder tujuan", barisFolder, folder));

        mode.addActionListener(event -> perbaruiFormat());
        format.addActionListener(event -> perbaruiKualitas());
        tombolUnduh.addActionListener(event -> mulaiUnduh());
        perbaruiFormat();

        gambarMedia.setPreferredSize(new Dimension(160, 90));
        gambarMedia.setOpaque(true);
        gambarMedia.setBackground(BIRU_MUDA);
        gambarMedia.setForeground(REDUP);
        gambarMedia.setFont(new Font(FONT, Font.PLAIN, 11));
        aturTeks(infoMedia, new Font(FONT, Font.PLAIN, 15));
        infoMedia.setText("Masukkan tautan, lalu tekan Enter atau klik Cek tautan.");
        JPanel pratinjau = kartu();
        pratinjau.add(label("Pratinjau media", 15));
        pratinjau.add(Box.createVerticalStrut(9));
        JPanel detail = tanpaLatar(new BorderLayout(14, 0));
        detail.add(gambarMedia, BorderLayout.WEST);
        detail.add(gulir(infoMedia), BorderLayout.CENTER);
        pratinjau.add(detail);

        aturTeks(log, new Font("Consolas", Font.PLAIN, 12));
        log.setRows(5);
        JPanel aktivitas = kartu();
        aktivitas.add(label("Aktivitas unduhan", 15));
        aktivitas.add(Box.createVerticalStrut(7));
        aktivitas.add(gulir(log));

        JPanel atas = tanpaLatar(null);
        atas.setLayout(new BoxLayout(atas, BoxLayout.Y_AXIS));
        atas.add(formulir);
        atas.add(Box.createVerticalStrut(12));
        atas.add(pratinjau);
        JPanel isi = tanpaLatar(new BorderLayout(0, 12));
        isi.setBorder(new EmptyBorder(16, 22, 18, 22));
        isi.add(atas, BorderLayout.NORTH);
        isi.add(aktivitas, BorderLayout.CENTER);
        JPanel aksi = tanpaLatar(new BorderLayout());
        JPanel tombolAksi = tanpaLatar(new GridLayout(1, 2, 8, 0));
        aturTombol(tombolBatal, false);
        tombolBatal.setEnabled(false);
        tombolBatal.addActionListener(event -> batalkanUnduhan());
        aturTombol(tombolBukaFolder, false);
        tombolBukaFolder.setPreferredSize(new Dimension(152, 37));
        tombolBukaFolder.addActionListener(event -> bukaFolderHasil());
        aksiUnduhan.setOpaque(false);
        aksiUnduhan.add(tombolBatal, "batal");
        aksiUnduhan.add(tombolBukaFolder, "folder");
        tombolAksi.add(aksiUnduhan);
        tombolAksi.add(tombolUnduh);

        progres.setFont(new Font(FONT, Font.BOLD, 12));
        progres.setForeground(AKSEN);
        progres.setBackground(PUTIH);
        progres.setBorder(BorderFactory.createLineBorder(GARIS));
        progres.setPreferredSize(new Dimension(180, 24));
        progres.setStringPainted(true);
        progres.setString("Siap mengunduh");
        JPanel indikator = tanpaLatar(new java.awt.GridBagLayout());
        var lebarProgres = new java.awt.GridBagConstraints();
        lebarProgres.weightx = 1;
        lebarProgres.fill = java.awt.GridBagConstraints.HORIZONTAL;
        lebarProgres.insets = new java.awt.Insets(0, 0, 12, 12);
        indikator.add(progres, lebarProgres);
        aksi.add(indikator, BorderLayout.CENTER);
        aksi.add(tombolAksi, BorderLayout.EAST);
        isi.add(aksi, BorderLayout.SOUTH);
        utama.add(isi, BorderLayout.CENTER);

        utama.setPreferredSize(new Dimension(780, 690));
        jendela.setContentPane(utama);
        jendela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jendela.pack();
        jendela.setMinimumSize(new Dimension(650, 650));
        jendela.setLocationRelativeTo(null);
        jendela.setVisible(true);
    }

    private static JPanel tanpaLatar(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel kartu() {
        JPanel panel = new JPanel();
        panel.setBackground(PUTIH);
        panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GARIS, 1, true), new EmptyBorder(12, 15, 12, 15)
        ));
        return panel;
    }

    private static JLabel label(String teks, int ukuran) {
        JLabel label = new JLabel(teks);
        label.setFont(new Font(FONT, Font.BOLD, ukuran));
        label.setForeground(TINTA);
        label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel kolom(String nama, JComponent isi, JComponent input) {
        JPanel panel = tanpaLatar(new BorderLayout(0, 4));
        JLabel petunjuk = label(nama, 13);
        petunjuk.setLabelFor(input);
        panel.add(petunjuk, BorderLayout.NORTH);
        panel.add(isi, BorderLayout.CENTER);
        return panel;
    }

    private static void aturKolom(JTextField kolom) {
        kolom.setFont(new Font(FONT, Font.PLAIN, 14));
        kolom.setForeground(TINTA);
        kolom.setBackground(PUTIH);
        kolom.setCaretColor(TINTA);
        kolom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GARIS, 1, true), new EmptyBorder(6, 10, 6, 10)
        ));
        kolom.setPreferredSize(new Dimension(130, 37));
    }

    private static void aturPilihan(JComboBox<String> combo) {
        combo.setFont(new Font(FONT, Font.PLAIN, 14));
        combo.setBackground(PUTIH);
        combo.setForeground(TINTA);
        combo.setBorder(BorderFactory.createLineBorder(GARIS, 1, true));
        combo.setPreferredSize(new Dimension(125, 37));
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton panah = new javax.swing.plaf.basic.BasicArrowButton(
                    SwingConstants.SOUTH, PUTIH, PUTIH, AKSEN, PUTIH
                );
                panah.setBorder(BorderFactory.createEmptyBorder());
                return panah;
            }

            @Override
            public void paintCurrentValueBackground(Graphics grafik, java.awt.Rectangle area, boolean fokus) {
                grafik.setColor(fokus ? BIRU_MUDA : PUTIH);
                grafik.fillRect(area.x, area.y, area.width, area.height);
            }
        });
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                JList<?> daftar, Object nilai, int indeks, boolean dipilih, boolean fokus
            ) {
                super.getListCellRendererComponent(daftar, nilai, indeks, dipilih, fokus);
                setBorder(new EmptyBorder(5, 9, 5, 9));
                setBackground(dipilih ? BIRU_MUDA : PUTIH);
                setForeground(combo.isEnabled() ? TINTA : REDUP);
                return this;
            }
        });
    }

    private static void aturTeks(JTextArea area, Font font) {
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(font);
        area.setForeground(TINTA);
        area.setBackground(PUTIH);
    }

    private static JScrollPane gulir(JTextArea area) {
        JScrollPane pane = new JScrollPane(area);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return pane;
    }

    private static void aturTombol(JButton tombol, boolean utama) {
        tombol.setFont(new Font(FONT, Font.BOLD, 13));
        tombol.setForeground(utama ? PUTIH : AKSEN);
        tombol.setBackground(utama ? AKSEN : BIRU_MUDA);
        tombol.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tombol.setPreferredSize(new Dimension(utama ? 142 : 115, utama ? 41 : 37));
    }

    private static final class TombolModern extends JButton {
        TombolModern(String teks) {
            super(teks);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        @Override protected void paintComponent(Graphics grafik) {
            Graphics2D bentuk = (Graphics2D) grafik.create();
            try {
                bentuk.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color warna = isEnabled() ? getBackground() : KANVAS;
                if (isEnabled() && getModel().isRollover()) warna = warna.darker();
                bentuk.setColor(warna);
                bentuk.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                if (isFocusOwner()) {
                    bentuk.setColor(AKSEN.equals(getBackground()) ? PUTIH : AKSEN);
                    bentuk.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 7, 7);
                }
            } finally {
                bentuk.dispose();
            }
            super.paintComponent(grafik);
        }
    }

    private boolean sedangMengecek;

    private void tautanBerubah() {
        gambarMedia.setIcon(null);
        gambarMedia.setText(" ");
        infoMedia.setText("Tekan Enter atau klik Cek tautan untuk melihat pratinjau.");
        tombolCek.setEnabled(!sedangMengecek && !tautan.getText().isBlank());
    }

    private void cekTautan() {
        String masukan = tautan.getText().strip();
        URI url;
        try {
            url = URI.create(masukan);
            if (url.getHost() == null || url.getUserInfo() != null
                || !("http".equalsIgnoreCase(url.getScheme())
                    || "https".equalsIgnoreCase(url.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            infoMedia.setText("Masukkan tautan HTTP atau HTTPS yang valid.");
            return;
        }

        sedangMengecek = true;
        tombolCek.setEnabled(false);
        gambarMedia.setIcon(null);
        gambarMedia.setText(" ");
        infoMedia.setText("Mengecek tautan...");

        new SwingWorker<Pratinjau, Void>() {
            @Override
            protected Pratinjau doInBackground() throws IOException, InterruptedException {
                return ambilPratinjau(url);
            }

            @Override
            protected void done() {
                sedangMengecek = false;
                tombolCek.setEnabled(!tautan.getText().isBlank());
                if (!masukan.equals(tautan.getText().strip())) return;
                try {
                    Pratinjau hasil = get();
                    infoMedia.setText(hasil.judul());
                    infoMedia.setCaretPosition(0);
                    gambarMedia.setIcon(hasil.thumbnail());
                    gambarMedia.setText(hasil.thumbnail() == null ? "Thumbnail tidak tersedia" : "");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    infoMedia.setText("Pengecekan terhenti.");
                } catch (ExecutionException error) {
                    infoMedia.setText("Gagal mengecek tautan. " + error.getCause().getMessage());
                    infoMedia.setCaretPosition(0);
                }
            }
        }.execute();
    }

    private static Pratinjau ambilPratinjau(URI url) throws IOException, InterruptedException {
        Path sementara = Files.createTempDirectory("media-preview-");
        List<String> keluaran = new ArrayList<>();
        try {
            Path tools = direktoriTools();
            List<String> perintah = new ArrayList<>(List.of(
                tools.resolve("yt-dlp.exe").toString(),
                "--ignore-config", "--no-playlist", "--playlist-items", "1",
                "--skip-download", "--no-simulate", "--write-thumbnail",
                "--convert-thumbnails", "png", "--socket-timeout", "12",
                "--retries", "1", "--extractor-retries", "1",
                "--ffmpeg-location", tools.toString(),
                "--paths", "home:" + sementara, "--output", "thumbnail.%(ext)s",
                "--print", "Judul: %(title)s"
            ));
            tambahkanOpsiPlatform(perintah, url);
            perintah.addAll(List.of("--", url.toASCIIString()));
            int status = jalankan(perintah, keluaran::add);
            String judul = keluaran.stream().filter(baris -> baris.startsWith("Judul: "))
                .map(baris -> baris.substring(7)).findFirst().orElse("");
            if (judul.isBlank()) {
                String pesan = keluaran.stream().filter(baris -> baris.startsWith("ERROR:"))
                    .findFirst().orElse("Media tidak ditemukan.");
                throw new IOException(pesan);
            }

            ImageIcon ikon = null;
            Path fileGambar = sementara.resolve("thumbnail.png");
            if (status == 0 && Files.isRegularFile(fileGambar)) {
                var gambar = ImageIO.read(fileGambar.toFile());
                if (gambar != null) {
                    double skala = Math.min(160.0 / gambar.getWidth(), 90.0 / gambar.getHeight());
                    int lebar = Math.max(1, (int) (gambar.getWidth() * skala));
                    int tinggi = Math.max(1, (int) (gambar.getHeight() * skala));
                    ikon = new ImageIcon(gambar.getScaledInstance(lebar, tinggi, Image.SCALE_SMOOTH));
                }
            }
            return new Pratinjau(judul, ikon);
        } finally {
            try (var berkas = Files.list(sementara)) {
                for (Path file : berkas.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(sementara);
        }
    }

    private void pilihFolderTujuan(JButton tombol) {
        Path pembuka = direktoriTools().resolve("FolderPicker.exe");
        if (!Files.isRegularFile(pembuka)) {
            pilihFolderSwing();
            return;
        }

        String folderAwal = folder.getText().strip();
        tombol.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws IOException, InterruptedException {
                Path hasil = Files.createTempFile("media-folder-", ".txt");
                try {
                    ProcessBuilder builder = new ProcessBuilder(
                        pembuka.toString(), hasil.toString(),
                        Long.toString(ProcessHandle.current().pid()), folderAwal
                    );
                    int status = builder.start().waitFor();
                    if (status == 1) return null;
                    if (status != 0) throw new IOException("Dialog Windows gagal dibuka.");
                    return Files.readString(hasil, StandardCharsets.UTF_8);
                } finally {
                    Files.deleteIfExists(hasil);
                }
            }

            @Override
            protected void done() {
                tombol.setEnabled(true);
                try {
                    String pilihan = get();
                    if (pilihan != null && !pilihan.isBlank()) folder.setText(pilihan);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException error) {
                    pilihFolderSwing();
                }
            }
        }.execute();
    }

    private void pilihFolderSwing() {
        JFileChooser pemilih = new JFileChooser();
        pemilih.setDialogTitle("Pilih folder tujuan unduhan");
        pemilih.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        pemilih.setAcceptAllFileFilterUsed(false);

        String teksFolder = folder.getText().strip();
        if (!teksFolder.isEmpty()) {
            try {
                Path tujuan = Path.of(teksFolder).toAbsolutePath();
                while (tujuan != null && !Files.isDirectory(tujuan)) {
                    tujuan = tujuan.getParent();
                }
                if (tujuan != null) {
                    pemilih.setCurrentDirectory(tujuan.toFile());
                }
            } catch (java.nio.file.InvalidPathException ignored) {
                // Gunakan folder awal bawaan jika teks tidak valid.
            }
        }

        if (pemilih.showOpenDialog(jendela) == JFileChooser.APPROVE_OPTION) {
            folder.setText(pemilih.getSelectedFile().toPath().toAbsolutePath().toString());
        }
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

        boolean mp4 = !audioSaja && "mp4".equals(formatFile);

        KendaliUnduh kendali = new KendaliUnduh();
        unduhanAktif = kendali;
        folderHasil = null;
        pilihanAksiUnduhan.show(aksiUnduhan, "batal");
        log.setText("Memulai unduhan...\n");
        progres.setValue(0);
        progres.setString("Menyiapkan unduhan...");
        progres.setIndeterminate(true);
        tombolUnduh.setEnabled(false);
        tombolBatal.setEnabled(true);

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Files.createDirectories(tujuan);

                if (mp4) {
                    return unduhMp4Kompatibel(url, tujuan, pilihan, this::publish, kendali);
                }
                return jalankan(
                    buatPerintah(url, tujuan, audioSaja, pilihan, formatFile),
                    this::publish, kendali
                );
            }

            @Override
            protected void process(List<String> baris) {
                if (kendali.dibatalkan()) return;
                for (String pesan : baris) tampilkanProgres(pesan);
                log.setCaretPosition(log.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    if (kendali.dibatalkan()) {
                        progres.setString("Unduhan dibatalkan");
                        log.append("\nUnduhan dibatalkan.\n");
                    } else if (get() == 0) {
                        progres.setValue(100);
                        progres.setString("100% - Selesai");
                        folderHasil = tujuan;
                        pilihanAksiUnduhan.show(aksiUnduhan, "folder");
                        log.append("\nUnduhan selesai.\n");
                    } else {
                        progres.setString("Unduhan gagal");
                        log.append("\nUnduhan gagal. Periksa pesan di atas.\n");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    progres.setString("Proses terhenti");
                    log.append("\nProses terhenti.\n");
                } catch (ExecutionException e) {
                    progres.setString("Unduhan gagal");
                    log.append("\nGagal: " + e.getCause().getMessage() + "\n");
                } finally {
                    progres.setIndeterminate(false);
                    tombolBatal.setEnabled(false);
                    tombolUnduh.setEnabled(true);
                    unduhanAktif = null;
                }
            }
        }.execute();
    }

    private void bukaFolderHasil() {
        if (folderHasil == null) return;
        try {
            if (!Files.isDirectory(folderHasil)) {
                throw new IOException("Folder tujuan sudah tidak tersedia.");
            }
            java.awt.Desktop.getDesktop().open(folderHasil.toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException | SecurityException error) {
            JOptionPane.showMessageDialog(jendela,
                "Folder tidak dapat dibuka:\n" + folderHasil,
                "Buka folder hasil", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void batalkanUnduhan() {
        if (unduhanAktif == null) return;
        tombolBatal.setEnabled(false);
        progres.setIndeterminate(true);
        progres.setString("Membatalkan...");
        unduhanAktif.batalkan();
    }

    private void tampilkanProgres(String pesan) {
        String awalan = "MEDIA_PROGRESS:";
        if (pesan.startsWith(awalan)) {
            String persentase = pesan.substring(awalan.length()).strip();
            if (persentase.matches("[0-9]+(?:\\.[0-9]+)?%")) {
                double angka = Double.parseDouble(persentase.substring(0, persentase.length() - 1));
                progres.setIndeterminate(false);
                progres.setValue((int) Math.max(0, Math.min(100, angka)));
                progres.setString("Transfer berkas: " + persentase);
            } else {
                progres.setIndeterminate(true);
                progres.setString("Mengunduh...");
            }
            return;
        }

        if (pesan.equals("MEDIA_POSTPROCESS") || pesan.startsWith("[Merger]")
            || pesan.startsWith("[ExtractAudio]") || pesan.startsWith("[VideoConvertor]")
            || pesan.startsWith("[VideoRemuxer]")) {
            progres.setIndeterminate(true);
            progres.setString("Memproses hasil unduhan...");
        }

        if (!pesan.equals("MEDIA_POSTPROCESS")) log.append(pesan + "\n");
    }

    private static final class KendaliUnduh {
        private boolean batal;
        private Process proses;

        synchronized boolean dibatalkan() {
            return batal;
        }

        synchronized Process mulai(ProcessBuilder builder) throws IOException {
            if (batal) return null;
            proses = builder.start();
            return proses;
        }

        synchronized void batalkan() {
            batal = true;
            if (proses == null) return;
            List<ProcessHandle> turunan = proses.descendants().toList();
            proses.destroyForcibly();
            for (ProcessHandle anak : turunan) anak.destroyForcibly();
        }
    }

    private int unduhMp4Kompatibel(
        URI url, Path tujuan, String pilihan, Consumer<String> logBaris, KendaliUnduh kendali
    ) throws IOException, InterruptedException {
        Path direktoriKerja = Files.createTempDirectory(tujuan, ".media-");
        try {
            int hasil = jalankan(buatPerintah(url, direktoriKerja, false, pilihan, "mp4"), logBaris, kendali);
            if (hasil != 0) {
                return hasil;
            }

            Path sumber;
            try (var berkas = Files.list(direktoriKerja)) {
                sumber = berkas.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".mkv"))
                    .findFirst().orElseThrow(() -> new IOException("File video tidak ditemukan."));
            }

            String nama = sumber.getFileName().toString();
            Path hasilAkhir = tujuan.resolve(
                nama.substring(0, nama.length() - 4) + " - Kompatibel Windows.mp4"
            );

            if (Files.exists(hasilAkhir)) {
                logBaris.accept("File sudah tersedia: " + hasilAkhir);
                return 0;
            }

            boolean videoKompatibel = cekCodec(sumber, "v:0", "codec_name,pix_fmt", "h264,yuv420p", kendali);
            boolean audioKompatibel = cekCodec(sumber, "a:0", "codec_name", "aac", kendali);
            logBaris.accept("MEDIA_POSTPROCESS");
            logBaris.accept(videoKompatibel && audioKompatibel
                ? "Menyimpan video dan audio ke MP4."
                : "Menyesuaikan codec untuk pemutar Windows. Mohon tunggu.");

            Path sementara = direktoriKerja.resolve("hasil.mp4");
            List<String> konversi = new ArrayList<>(List.of(
                direktoriTools().resolve("ffmpeg.exe").toString(),
                "-hide_banner", "-loglevel", "error", "-nostdin",
                "-progress", "pipe:1", "-nostats", "-stats_period", "10",
                "-i", sumber.toString(), "-map", "0:v:0", "-map", "0:a:0"
            ));
            konversi.addAll(videoKompatibel
                ? List.of("-c:v", "copy")
                : List.of("-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2", "-c:v", "libx264",
                    "-preset", "veryfast", "-crf", "21", "-pix_fmt", "yuv420p"));
            konversi.addAll(audioKompatibel
                ? List.of("-c:a", "copy")
                : List.of("-c:a", "aac", "-b:a", "160k"));
            konversi.addAll(List.of("-movflags", "+faststart", sementara.toString()));
            hasil = jalankan(konversi, baris -> {
                if (baris.startsWith("out_time=")) {
                    logBaris.accept("Waktu video yang sudah diproses: " + baris.substring(9));
                } else if (!baris.matches("^[a-zA-Z0-9_]+=.*")) {
                    logBaris.accept(baris);
                }
            }, kendali);

            if (hasil == 0 && !kendali.dibatalkan()) {
                Files.move(sementara, hasilAkhir);
                logBaris.accept("Disimpan: " + hasilAkhir);
            }
            return hasil;
        } finally {
            try (var berkas = Files.walk(direktoriKerja)) {
                for (Path file : berkas.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                logBaris.accept("Folder sementara belum dapat dibersihkan: " + direktoriKerja);
            }
        }
    }

    private static boolean cekCodec(Path file, String stream, String properti, String target, KendaliUnduh kendali)
        throws IOException, InterruptedException {
        List<String> keluaran = new ArrayList<>();
        int hasil = jalankan(List.of(
            direktoriTools().resolve("ffprobe.exe").toString(), "-v", "error",
            "-select_streams", stream, "-show_entries", "stream=" + properti,
            "-of", "csv=p=0", file.toString()
        ), keluaran::add, kendali);
        return hasil == 0 && keluaran.contains(target);
    }

    private static int jalankan(List<String> perintah, Consumer<String> logBaris)
        throws IOException, InterruptedException {
        return jalankan(perintah, logBaris, null);
    }

    private static int jalankan(List<String> perintah, Consumer<String> logBaris, KendaliUnduh kendali)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(perintah).redirectErrorStream(true);
        builder.environment().put("PYTHONIOENCODING", "utf-8");

        Process proses = kendali == null ? builder.start() : kendali.mulai(builder);
        if (proses == null) return 1;
        try (BufferedReader keluaran = proses.inputReader(StandardCharsets.UTF_8)) {
            String baris;
            while ((baris = keluaran.readLine()) != null) {
                logBaris.accept(baris);
            }
        }
        return proses.waitFor();
    }

    private static Path direktoriTools() {
        return Path.of(System.getProperty("media.tools", "tools")).toAbsolutePath();
    }

    private static void tambahkanOpsiPlatform(List<String> perintah, URI url) {
        Path tools = direktoriTools();
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("tiktok.com") || host.endsWith(".tiktok.com")) {
            perintah.addAll(List.of("--impersonate", "Chrome:Windows"));
        }

        Path deno = tools.resolve("deno.exe");
        if (Files.isRegularFile(deno)) {
            perintah.addAll(List.of("--js-runtimes", "deno:" + deno));
        }
    }

    private List<String> buatPerintah(
        URI url, Path tujuan, boolean audioSaja, String pilihan, String formatFile
    ) {
        Path tools = direktoriTools();

        List<String> perintah = new ArrayList<>(List.of(
            tools.resolve("yt-dlp.exe").toString(),
            "--ignore-config", "--no-playlist", "--playlist-items", "1",
            "--newline", "--no-overwrites", "--windows-filenames",
            "--ffmpeg-location", tools.toString(),
            "--paths", "home:" + tujuan,
            "--output", "%(title).150B [%(id)s].%(ext)s",
            "--print", "after_move:Disimpan: %(filepath)s",
            "--progress", "--progress-delta", "0.25",
            "--progress-template", "download:MEDIA_PROGRESS:%(progress._percent_str)s"
        ));

        tambahkanOpsiPlatform(perintah, url);

        if (audioSaja) {
            perintah.addAll(List.of(
                "-f", "bestaudio/best",
                "--extract-audio", "--audio-format", formatFile
            ));

            if (!formatFile.equals("wav")) {
                String bitrate = pilihan.replace(" kbps", "K");
                perintah.addAll(List.of("--audio-quality", bitrate));
            }
        } else if (formatFile.equals("mp4")) {
            String prioritasResolusi = pilihan.equals("Terbaik")
                ? "res" : "res:" + pilihan.replace("p", "");
            perintah.addAll(List.of(
                "-f", "bv+ba/b", "--merge-output-format", "mkv", "--remux-video", "mkv",
                "-S", prioritasResolusi + ",vcodec:h264,acodec:aac"
            ));
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