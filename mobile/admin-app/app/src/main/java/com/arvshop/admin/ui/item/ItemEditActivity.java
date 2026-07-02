package com.arvshop.admin.ui.item;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.arvshop.admin.R;
import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.data.model.Item;
import com.arvshop.admin.util.ImageEncoder;
import com.arvshop.admin.util.ItemValidator;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Add or edit an item, including camera/gallery photo capture and the customer-facing
 * merchandising controls (visible/featured/badge). Photo is downscaled and encoded to
 * base64 off the main thread before upload.
 */
public class ItemEditActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "item_id";

    public static Intent editIntent(Context ctx, long id) {
        return new Intent(ctx, ItemEditActivity.class).putExtra(EXTRA_ID, id);
    }

    private ItemEditViewModel viewModel;
    private EditText nameInput, typeInput, priceInput, qtyInput, descInput, mrpInput,
            costInput, locationInput, badgeInput;
    private CheckBox visibleCheck, featuredCheck;
    private ImageView imagePreview;
    private Button saveButton;
    private ProgressBar progress;

    private long itemId = -1;
    private String pendingImageBase64; // set when a new photo is chosen
    private Uri cameraOutputUri;

    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_edit);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();
        registerImagePickers();

        viewModel = new ViewModelProvider(this).get(ItemEditViewModel.class);
        itemId = getIntent().getLongExtra(EXTRA_ID, -1);
        setTitle(itemId > 0 ? R.string.title_edit_item : R.string.title_add_item);

        saveButton.setOnClickListener(v -> save());
        findViewById(R.id.btn_camera).setOnClickListener(v -> launchCamera());
        findViewById(R.id.btn_gallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));

        observe();

        if (itemId > 0) {
            viewModel.loadItem(itemId);
        }
    }

    private void bindViews() {
        nameInput = findViewById(R.id.input_name);
        typeInput = findViewById(R.id.input_type);
        priceInput = findViewById(R.id.input_price);
        qtyInput = findViewById(R.id.input_quantity);
        descInput = findViewById(R.id.input_description);
        mrpInput = findViewById(R.id.input_mrp);
        costInput = findViewById(R.id.input_cost);
        locationInput = findViewById(R.id.input_location);
        badgeInput = findViewById(R.id.input_badge);
        visibleCheck = findViewById(R.id.check_visible);
        featuredCheck = findViewById(R.id.check_featured);
        imagePreview = findViewById(R.id.image_preview);
        saveButton = findViewById(R.id.btn_save);
        progress = findViewById(R.id.save_progress);
        visibleCheck.setChecked(true);
    }

    private void registerImagePickers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), taken -> {
                    if (taken && cameraOutputUri != null) encodeFrom(cameraOutputUri);
                });
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) encodeFrom(uri);
                });
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "captures");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            cameraOutputUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(cameraOutputUri);
        } catch (Exception e) {
            Snackbar.make(saveButton, R.string.camera_unavailable, Snackbar.LENGTH_LONG).show();
        }
    }

    /** Decode + downscale + base64 off the main thread, then preview. */
    private void encodeFrom(Uri uri) {
        progress.setVisibility(View.VISIBLE);
        AppExecutors.get().disk().execute(() -> {
            String dataUrl = null;
            try (InputStream exifStream = getContentResolver().openInputStream(uri)) {
                int orientation = exifStream != null
                        ? ImageEncoder.readExifOrientation(exifStream)
                        : android.media.ExifInterface.ORIENTATION_NORMAL;
                try (InputStream pixelStream = getContentResolver().openInputStream(uri)) {
                    dataUrl = ImageEncoder.encode(pixelStream, orientation);
                }
            } catch (IOException ignored) {
                // dataUrl stays null → surfaced below
            }
            final String result = dataUrl;
            AppExecutors.get().mainThread(() -> {
                progress.setVisibility(View.GONE);
                if (result == null) {
                    Snackbar.make(saveButton, R.string.image_failed, Snackbar.LENGTH_LONG).show();
                    return;
                }
                pendingImageBase64 = result;
                imagePreview.setImageURI(uri);
                imagePreview.setVisibility(View.VISIBLE);
            });
        });
    }

    private void observe() {
        viewModel.loadState().observe(this, result -> {
            if (result == null || result.status != com.arvshop.admin.core.Result.Status.SUCCESS) return;
            fillForm(result.data);
        });
        viewModel.saveState().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    setSaving(true);
                    break;
                case SUCCESS:
                    setSaving(false);
                    // data == null means the create was queued offline.
                    Snackbar.make(saveButton,
                            result.data == null ? R.string.saved_offline : R.string.saved,
                            Snackbar.LENGTH_SHORT).show();
                    saveButton.postDelayed(this::finish, 700);
                    break;
                case ERROR:
                    setSaving(false);
                    Snackbar.make(saveButton, result.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void fillForm(Item item) {
        nameInput.setText(item.name);
        typeInput.setText(item.type);
        priceInput.setText(trimNumber(item.price));
        qtyInput.setText(String.valueOf(item.quantity));
        descInput.setText(item.description);
        mrpInput.setText(item.mrp > 0 ? trimNumber(item.mrp) : "");
        costInput.setText(item.purchaseCost > 0 ? trimNumber(item.purchaseCost) : "");
        locationInput.setText(item.location);
        badgeInput.setText(item.badge);
        visibleCheck.setChecked(item.visible);
        featuredCheck.setChecked(item.featured);
    }

    private void save() {
        ItemValidator.Errors errors = ItemValidator.validate(
                nameInput.getText().toString(), typeInput.getText().toString(),
                priceInput.getText().toString(), qtyInput.getText().toString());
        if (errors.hasErrors()) {
            if (errors.name != null) nameInput.setError(errors.name);
            if (errors.type != null) typeInput.setError(errors.type);
            if (errors.price != null) priceInput.setError(errors.price);
            if (errors.quantity != null) qtyInput.setError(errors.quantity);
            return;
        }
        try {
            if (itemId > 0) {
                // Edit: PUT accepts core + merchandising fields in one body.
                JSONObject payload = buildCorePayload();
                addMerchandising(payload);
                viewModel.update(itemId, payload);
            } else {
                // Create: ItemCreate ignores merchandising, so pass it separately —
                // the repository applies it via a follow-up PUT when it deviates.
                JSONObject payload = buildCorePayload();
                viewModel.create(payload, deviatingMerchandising());
            }
        } catch (JSONException e) {
            Snackbar.make(saveButton, R.string.save_failed, Snackbar.LENGTH_LONG).show();
        }
    }

    /** Fields accepted by both ItemCreate and ItemUpdate. */
    private JSONObject buildCorePayload() throws JSONException {
        JSONObject p = new JSONObject();
        p.put("name", nameInput.getText().toString().trim());
        p.put("type", typeInput.getText().toString().trim());
        p.put("price", ItemValidator.parsePositiveDouble(priceInput.getText().toString()));
        p.put("quantity", ItemValidator.parseNonNegativeInt(qtyInput.getText().toString()));
        p.put("description", descInput.getText().toString().trim());
        Double mrp = ItemValidator.parsePositiveDouble(mrpInput.getText().toString());
        if (mrp != null) p.put("mrp", mrp);
        Double cost = ItemValidator.parsePositiveDouble(costInput.getText().toString());
        if (cost != null) p.put("purchase_cost", cost);
        p.put("location", locationInput.getText().toString().trim());
        if (pendingImageBase64 != null) {
            p.put("image_base64", pendingImageBase64);
        }
        return p;
    }

    private void addMerchandising(JSONObject p) throws JSONException {
        p.put("visible", visibleCheck.isChecked());
        p.put("featured", featuredCheck.isChecked());
        p.put("badge", badgeInput.getText().toString().trim());
    }

    /**
     * @return a merchandising payload only when it deviates from server create-defaults
     *         (visible=true, featured=false, empty badge); otherwise null (no extra PUT).
     */
    private JSONObject deviatingMerchandising() throws JSONException {
        boolean hidden = !visibleCheck.isChecked();
        boolean featured = featuredCheck.isChecked();
        String badge = badgeInput.getText().toString().trim();
        if (!hidden && !featured && badge.isEmpty()) {
            return null;
        }
        JSONObject m = new JSONObject();
        addMerchandising(m);
        return m;
    }

    private void setSaving(boolean saving) {
        progress.setVisibility(saving ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!saving);
    }

    private static String trimNumber(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
