package org.codirex;

import android.database.MatrixCursor;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.codirex.axiom.android.content.Intents;
import org.codirex.axiom.android.database.CursorMapper;
import org.codirex.axiom.android.display.Dp;
import org.codirex.axiom.android.display.WindowInsetsHelper;
import org.codirex.axiom.android.lifecycle.LifecycleDisposable;
import org.codirex.axiom.android.os.MainThread;
import org.codirex.axiom.concurrent.atomic.AtomicInt;
import org.codirex.axiom.collection.map.Multimap;
import org.codirex.axiom.collection.list.SmallList;
import org.codirex.axiom.concurrent.Once;
import org.codirex.axiom.lifecycle.Disposable;
import org.codirex.axiom.lifecycle.Scope;
import org.codirex.axiom.option.Option;
import org.codirex.axiom.result.Result;
import org.codirex.databinding.ActivityMainBinding;

/**
 * A small, self-contained demo exercising axiom-base, axiom-collections, axiom-concurrent,
 * and axiom-android together. Not a real app feature — just proof that the pieces fit.
 *
 * <p><strong>Note:</strong> this file was written without being able to compile it against
 * the real Android Gradle Plugin / AndroidX artifacts (no network access to Google's Maven
 * repository in the environment this was produced in) — unlike axiom-base/-concurrent/
 * -collections/-android, which were verified with javac against hand-written SDK stubs. The
 * three library modules' APIs used below were verified directly; the framework/AndroidX
 * calls (view binding, {@code AppCompatActivity}, {@code MatrixCursor}) are written from
 * their real, stable signatures but haven't been build-verified here. Do a Gradle sync to
 * confirm before relying on it.
 */
public final class MainActivity extends AppCompatActivity {

    // Demonstrates HandlerExecutor/MainThread's usual companion: a background executor whose
    // lifetime is tied to this activity via LifecycleDisposable, so it's shut down on destroy
    // instead of leaking a thread past the activity.
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final AtomicInt runCount = new AtomicInt(0);
    private final Once oneTimeSetup = Once.create();

    private ActivityMainBinding binding;
    private String lastResult = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowInsetsHelper.applySystemBarsPadding(binding.getRoot());

        Scope scope = LifecycleDisposable.bindTo(this);
        scope.bind(Disposable.of(background::shutdown));

        oneTimeSetup.run(() -> android.util.Log.d("AxiomDemo", "one-time setup ran"));

        binding.runDemoButton.setOnClickListener(v -> runDemo());
        binding.shareButton.setOnClickListener(v -> startActivity(Intents.share(lastResult, "Share via")));
    }

    private void runDemo() {
        binding.runDemoButton.setEnabled(false);
        background.execute(() -> {
            String summary = buildDemoSummary(runCount.incrementAndGet());
            // We're on the background executor here; hop back to the main thread to touch views.
            MainThread.run(() -> {
                lastResult = summary;
                binding.resultText.setText(summary);
                binding.runDemoButton.setEnabled(true);
                binding.shareButton.setEnabled(true);
            });
        });
    }

    /** Runs the actual cross-module demo logic; safe to call off the main thread. */
    private static String buildDemoSummary(int runNumber) {
        StringBuilder out = new StringBuilder();
        out.append("Run #").append(runNumber).append("\n\n");

        // axiom-base: Result / Option
        Result<Integer, String> division = safeDivide(84, 2);
        String resultLine = division.fold(v -> "84 / 2 = " + v, err -> "error: " + err);
        out.append("Result: ").append(resultLine).append('\n');

        Option<String> missing = Option.none();
        out.append("Option (absent): ").append(missing.orElse("(fallback used)")).append('\n');

        // axiom-android: Dp/Px conversion (density read from the demo's fixed value here,
        // since this static method has no Context; MainActivity itself would normally pass
        // getResources().getDisplayMetrics() when converting for real UI work)
        Dp dp = Dp.of(16f);
        out.append("16dp as a value object: ").append(dp).append('\n');

        // axiom-collections: SmallList + Multimap
        SmallList<String> steps = SmallList.of("parsed input", "queried rows", "grouped results");
        Multimap<String, Integer> lengths = Multimap.create();
        for (String step : steps) {
            lengths.put(String.valueOf(step.length() / 5), step.length());
        }
        out.append("Steps: ").append(steps).append('\n');
        out.append("Grouped by length-bucket: ").append(lengths).append('\n');

        // axiom-android: CursorReader / CursorMapper over an in-memory cursor (no ContentResolver needed)
        MatrixCursor cursor = new MatrixCursor(new String[]{"id", "label"});
        cursor.addRow(new Object[]{1, "alpha"});
        cursor.addRow(new Object[]{2, "beta"});
        List<String> labels = CursorMapper.mapAll(cursor, reader -> reader.intValue("id") + ":" + reader.string("label"));
        out.append("Cursor rows: ").append(labels);

        return out.toString();
    }

    private static Result<Integer, String> safeDivide(int a, int b) {
        if (b == 0) {
            return Result.failure("division by zero");
        }
        return Result.success(a / b);
    }
}
