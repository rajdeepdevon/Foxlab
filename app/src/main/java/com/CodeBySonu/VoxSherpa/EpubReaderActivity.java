package com.CodeBySonu.VoxSherpa;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public final class EpubReaderActivity extends AppCompatActivity {
    private static final int REQUEST_OPEN_EPUB = 1;
    private TextView promptText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.epub_reader_activity);

        ImageView backButton = findViewById(R.id.epub_back);
        View selectBook = findViewById(R.id.epub_select_book);
        promptText = findViewById(R.id.epub_prompt);

        backButton.setOnClickListener(view -> finish());
        selectBook.setOnClickListener(view -> openEpubPicker());
    }

    private void openEpubPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/epub+zip",
                "application/pdf",
                "text/plain"
        });
        try {
            startActivityForResult(intent, REQUEST_OPEN_EPUB);
        } catch (ActivityNotFoundException error) {
            Snackbar.make(findViewById(R.id.epub_reader_root),
                    "No file picker is available on this device.", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_EPUB || resultCode != RESULT_OK || data == null) return;

        Uri epubUri = data.getData();
        if (epubUri == null) return;
        if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(
                        epubUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException error) {
                android.util.Log.w("EpubReaderActivity", "The EPUB provider did not allow a persistent read grant.", error);
            }
        }

        String displayName = getDisplayName(epubUri);
        promptText.setText(displayName.isEmpty()
                ? "EPUB selected - Reader support coming soon"
                : displayName + "\nReader support coming soon");
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) {
                    String name = cursor.getString(nameColumn);
                    return name == null ? "" : name;
                }
            }
        } catch (RuntimeException error) {
            android.util.Log.w("EpubReaderActivity", "Could not read EPUB display name.", error);
        }
        return "";
    }
}
