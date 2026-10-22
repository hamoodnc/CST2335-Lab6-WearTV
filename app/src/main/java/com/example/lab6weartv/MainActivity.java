package com.example.lab6weartv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Displays an endless, cached slideshow of random CATAAS cat images. */
public class MainActivity extends Activity {
    private static final String TAG = "Lab6WearTV";
    private static final String CAT_API_URL = "https://cataas.com/cat?json=true";
    private static final int DISPLAY_SECONDS = 5;
    private static final int TIMER_STEPS = 100;

    private ImageView catImageView;
    private ProgressBar progressBar;
    private CatImages catImagesTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main);

        catImageView = findViewById(R.id.catImageView);
        progressBar = findViewById(R.id.imageProgressBar);
        progressBar.setMax(TIMER_STEPS);
        progressBar.setProgress(TIMER_STEPS);

        catImagesTask = new CatImages();
        catImagesTask.execute(CAT_API_URL);
    }

    @Override
    protected void onDestroy() {
        if (catImagesTask != null) {
            catImagesTask.cancel(true);
        }
        super.onDestroy();
    }

    /**
     * Downloads images away from the UI thread and publishes all view changes back
     * to the UI thread through onProgressUpdate, as required by the lab.
     */
    @SuppressWarnings("deprecation")
    public class CatImages extends AsyncTask<String, Integer, String> {
        private static final int SHOW_NEW_IMAGE = -1;
        private Bitmap currentCatPicture;

        @Override
        protected String doInBackground(String... apiUrls) {
            String apiUrl = apiUrls.length == 0 ? CAT_API_URL : apiUrls[0];

            while (!isCancelled()) {
                try {
                    JSONObject cat = readJson(apiUrl);
                    String id = cat.getString("id");
                    String imageUrl = cat.getString("url");
                    File cachedImage = new File(getFilesDir(), safeFileName(id) + ".cat");

                    if (cachedImage.isFile() && cachedImage.length() > 0) {
                        currentCatPicture = decodeFile(cachedImage);
                        Log.i(TAG, "Loaded cached cat image: " + cachedImage.getName());
                    } else {
                        currentCatPicture = downloadAndCache(imageUrl, cachedImage);
                        Log.i(TAG, "Downloaded and cached cat image: " + cachedImage.getName());
                    }

                    if (currentCatPicture == null) {
                        throw new IllegalStateException("The cat image could not be decoded");
                    }

                    publishProgress(SHOW_NEW_IMAGE);
                    for (int remaining = TIMER_STEPS; remaining >= 0 && !isCancelled(); remaining--) {
                        publishProgress(remaining);
                        Thread.sleep((DISPLAY_SECONDS * 1000L) / TIMER_STEPS);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception exception) {
                    Log.e(TAG, "Unable to load the next cat image", exception);
                    sleepBeforeRetry();
                }
            }
            return "Slideshow stopped";
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values.length == 0 || isFinishing() || isDestroyed()) {
                return;
            }
            if (values[0] == SHOW_NEW_IMAGE) {
                catImageView.setImageBitmap(currentCatPicture);
            } else {
                progressBar.setProgress(values[0]);
            }
        }

        private JSONObject readJson(String urlString) throws Exception {
            HttpURLConnection connection = openConnection(urlString, "application/json");
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copy(input, output);
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            } finally {
                connection.disconnect();
            }
        }

        private Bitmap downloadAndCache(String urlString, File destination) throws Exception {
            HttpURLConnection connection = openConnection(urlString, "image/*");
            File temporary = new File(destination.getAbsolutePath() + ".download");
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                copy(input, output);
            } finally {
                connection.disconnect();
            }

            Bitmap downloaded = decodeFile(temporary);
            if (downloaded == null) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
                throw new IllegalStateException("Downloaded response was not a valid image");
            }

            if (!temporary.renameTo(destination)) {
                copyFile(temporary, destination);
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
            return downloaded;
        }

        private Bitmap decodeFile(File file) throws Exception {
            try (FileInputStream input = new FileInputStream(file)) {
                return BitmapFactory.decodeStream(input);
            }
        }

        private HttpURLConnection openConnection(String urlString, String accept) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent", "CST2335-Lab6-WearTV/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IllegalStateException("HTTP request failed with status " + status);
            }
            return connection;
        }

        private void copy(InputStream input, java.io.OutputStream output) throws Exception {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }

        private void copyFile(File source, File destination) throws Exception {
            try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                copy(input, output);
            }
        }

        private String safeFileName(String id) {
            return id.replaceAll("[^A-Za-z0-9_-]", "_");
        }

        private void sleepBeforeRetry() {
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
