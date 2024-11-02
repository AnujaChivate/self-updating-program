package com.selfupdate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Timer;
import java.util.TimerTask;
import com.google.gson.Gson;

public class Update {
    // current application version
    private static final String VERSION = "1.1.0"; 
    // JSON file
    private static final String UPDATE_INFO_URL = "update-info.json";

    public static void main(String[] args) {
        System.out.println("Starting update check...");

        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            public void run() {
                try {
                    checkForUpdates();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 3600 * 1000); // Check every hour

        // Keep the main thread alive
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void checkForUpdates() throws Exception {
        // Fetch update information
        UpdateInfo updateInfo = fetchUpdateInfo(); 
        if (updateInfo == null || updateInfo.version.equals(VERSION)) {
            System.out.println("No new update available.");
            return;
        }

        System.out.println("New version available: " + updateInfo.version);
        File downloadedFile = downloadFile(updateInfo.url);
        if (downloadedFile == null) {
            System.err.println("Failed to download the update.");
            return;
        }

        if (!verifyChecksum(downloadedFile, updateInfo.checksum)) {
            System.err.println("Checksum verification failed.");
            return;
        }

        applyUpdate(downloadedFile);
        System.out.println("Update applied. Restarting...");

        restartApplication();
    }

    private static UpdateInfo fetchUpdateInfo() throws IOException {
        // Reading the update info
        try (Reader reader = new FileReader(UPDATE_INFO_URL)) {
            return new Gson().fromJson(reader, UpdateInfo.class);
        }
    }

    private static File downloadFile(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        File tempFile = File.createTempFile("update-", ".jar");
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }

    private static boolean verifyChecksum(File file, String expectedChecksum) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int numRead;
            while ((numRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, numRead);
            }
        }
        String fileChecksum = bytesToHex(digest.digest());
        return fileChecksum.equalsIgnoreCase(expectedChecksum);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void applyUpdate(File newFile) throws IOException {
        Path currentPath = Paths.get(Update.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        Files.copy(newFile.toPath(), currentPath, StandardCopyOption.REPLACE_EXISTING);
        newFile.deleteOnExit(); 
    }

    private static void restartApplication() throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File currentJar = new File(Update.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", currentJar.getPath());
        builder.start();
        System.exit(0);
    }
}

class UpdateInfo {
    String version;
    String url;
    String checksum;
}