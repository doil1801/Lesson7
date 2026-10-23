package ru.mirea.dolgovis.httpurlconnection; // ← ваш пакет!

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.mirea.dolgovis.httpurlconnection.databinding.ActivityMainBinding; // ← ваш пакет!

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String IP_URL = "https://ipinfo.io/json";
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true";

    private ActivityMainBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.buttonGetInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isOnline()) {
                    loadData();
                } else {
                    Toast.makeText(MainActivity.this, "Нет интернета",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void loadData() {
        binding.buttonGetInfo.setEnabled(false);
        binding.textViewStatus.setText("Загружаем...");
        clearFields();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                JSONObject ipJson = null;
                JSONObject weatherJson = null;

                try {
                    ipJson = new JSONObject(downloadUrl(IP_URL));
                    Log.d(TAG, "IP info: " + ipJson);

                    String[] loc = ipJson.getString("loc").split(",");
                    String weatherUrl = String.format(Locale.US, WEATHER_URL,
                            loc[0].trim(), loc[1].trim());
                    weatherJson = new JSONObject(downloadUrl(weatherUrl));
                    Log.d(TAG, "Weather: " + weatherJson);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Ошибка загрузки", e);
                    error = e.getClass().getSimpleName() + ": " + e.getMessage();
                }

                final JSONObject ip = ipJson;
                final JSONObject weather = weatherJson;
                final String err = error;

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        binding.buttonGetInfo.setEnabled(true);
                        if (err != null) {
                            binding.textViewStatus.setText("Ошибка: " + err);
                            if (ip != null) showIp(ip);
                        } else {
                            binding.textViewStatus.setText("Данные получены");
                            showIp(ip);
                            showWeather(weather);
                        }
                    }
                });
            }
        });
    }

    private String downloadUrl(String address) throws IOException {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(10000);
            connection.setConnectTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setDoInput(true);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // 200 OK
                inputStream = connection.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                }
                return bos.toString(StandardCharsets.UTF_8.name());
            } else {
                throw new IOException(connection.getResponseMessage()
                        + ". Error Code: " + responseCode);
            }
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showIp(JSONObject json) {
        binding.textViewIp.setText("IP-адрес: " + json.optString("ip", "-"));
        binding.textViewCity.setText("Город: " + json.optString("city", "-"));
        binding.textViewRegion.setText("Регион: " + json.optString("region", "-"));
        binding.textViewCountry.setText("Страна: " + json.optString("country", "-"));
        binding.textViewOrg.setText("Провайдер: " + json.optString("org", "-"));
        binding.textViewTimezone.setText("Часовой пояс: " + json.optString("timezone", "-"));
        binding.textViewLoc.setText("Координаты: " + json.optString("loc", "-"));
    }

    private void showWeather(JSONObject json) {
        try {
            JSONObject current = json.getJSONObject("current_weather");
            double temp = current.getDouble("temperature");
            double wind = current.getDouble("windspeed");
            int code = current.getInt("weathercode");
            String time = current.optString("time", "-");

            binding.textViewTemp.setText(
                    String.format(Locale.US, "Температура: %.1f °C", temp));
            binding.textViewWind.setText(
                    String.format(Locale.US, "Ветер: %.1f км/ч", wind));
            binding.textViewWeatherDesc.setText("Погода: " + describeWeather(code));
            binding.textViewWeatherTime.setText("Время замера: " + time);
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка разбора погоды", e);
            binding.textViewTemp.setText("Не удалось разобрать данные погоды");
        }
    }

    private String describeWeather(int code) {
        if (code == 0) return "ясно";
        if (code <= 3) return "малооблачно / облачно";
        if (code == 45 || code == 48) return "туман";
        if (code >= 51 && code <= 57) return "морось";
        if (code >= 61 && code <= 67) return "дождь";
        if (code >= 71 && code <= 77) return "снег";
        if (code >= 80 && code <= 82) return "ливень";
        if (code == 85 || code == 86) return "снегопад";
        if (code >= 95) return "гроза";
        return "код " + code;
    }

    private void clearFields() {
        binding.textViewIp.setText("");
        binding.textViewCity.setText("");
        binding.textViewRegion.setText("");
        binding.textViewCountry.setText("");
        binding.textViewOrg.setText("");
        binding.textViewTimezone.setText("");
        binding.textViewLoc.setText("");
        binding.textViewTemp.setText("");
        binding.textViewWind.setText("");
        binding.textViewWeatherDesc.setText("");
        binding.textViewWeatherTime.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}