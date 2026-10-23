package ru.mirea.dolgovis.timeservice;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.mirea.dolgovis.timeservice.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String[] HOSTS =
            {"time.nist.gov", "time-a-g.nist.gov", "time-b-g.nist.gov"};
    private static final int PORT = 13;
    private static final int TIMEOUT_MS = 7000;

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
            v.setPadding(systemBars.left + 16, systemBars.top + 16,
                    systemBars.right + 16, systemBars.bottom + 16);
            return insets;
        });

        binding.buttonGetTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestTime();
            }
        });
    }
    private void requestTime() {
        binding.buttonGetTime.setEnabled(false);
        binding.textViewStatus.setText("Подключаемся к серверу времени...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                String line = null;
                String usedHost = null;
                String error = null;

                for (String host : HOSTS) {
                    try {
                        line = fetchTime(host);
                        usedHost = host;
                        break;
                    } catch (IOException e) {
                        Log.w(TAG, "Сервер " + host + " недоступен: " + e);
                        error = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }

                final String resultLine = line;
                final String resultHost = usedHost;
                final String resultError = error;

                // Возврат в главный поток для обновления интерфейса
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        binding.buttonGetTime.setEnabled(true);
                        if (resultLine == null) {
                            binding.textViewStatus.setText(
                                    "Не удалось получить время. " + resultError);
                        } else {
                            showTime(resultLine, resultHost);
                        }
                    }
                });
            }
        });
    }

    // Подключение по сокету и чтение ответа сервера
    private String fetchTime(String host) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, PORT), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            BufferedReader reader = SocketUtils.getReader(socket);
            // Ответ состоит из двух строк: первая пустая, во второй время.
            // Пропускаем пустые строки и берём первую содержательную.
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    Log.d(TAG, line);
                    return line;
                }
            }
            throw new IOException("Пустой ответ сервера");
        }
    }

    /**
     * Формат ответа NIST Daytime:
     * JJJJJ YY-MM-DD HH:MM:SS TT L H msADV UTC(NIST) OTM
     * пример: 60585 25-10-03 12:34:56 50 0 0 123.4 UTC(NIST) *
     * JJJJJ - модифицированная юлианская дата, время приходит в UTC.
     */
    private void showTime(String line, String host) {
        binding.textViewRaw.setText("Ответ " + host + ":\n" + line);

        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            binding.textViewStatus.setText("Неожиданный формат ответа");
            return;
        }

        try {
            SimpleDateFormat input = new SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(parts[1] + " " + parts[2]);

            Locale ru = new Locale("ru");
            TimeZone moscow = TimeZone.getTimeZone("Europe/Moscow");

            SimpleDateFormat dateFormat = new SimpleDateFormat("d MMMM yyyy, EEEE", ru);
            dateFormat.setTimeZone(moscow);

            SimpleDateFormat moscowTime = new SimpleDateFormat("HH:mm:ss", ru);
            moscowTime.setTimeZone(moscow);

            SimpleDateFormat utcTime = new SimpleDateFormat("HH:mm:ss", ru);
            utcTime.setTimeZone(TimeZone.getTimeZone("UTC"));

            binding.textViewStatus.setText("Время получено с сервера " + host);
            binding.textViewDate.setText("Дата: " + dateFormat.format(date));
            binding.textViewTimeMoscow.setText(moscowTime.format(date));
            binding.textViewTimeUtc.setText("UTC: " + utcTime.format(date)
                    + "  |  Москва (UTC+3)");
        } catch (ParseException e) {
            Log.e(TAG, "Ошибка разбора времени", e);
            binding.textViewStatus.setText("Не удалось разобрать ответ: " + line);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}