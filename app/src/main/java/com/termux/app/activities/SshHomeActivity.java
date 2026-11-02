package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.termux.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SshHomeActivity extends AppCompatActivity {

    public static final String EXTRA_SSH_HOST = "com.termux.ssh.HOST";
    public static final String EXTRA_SSH_USER = "com.termux.ssh.USER";
    public static final String EXTRA_SSH_PORT = "com.termux.ssh.PORT";
    public static final String EXTRA_SSH_KEY = "com.termux.ssh.KEY";

    private static final String PREFERENCES = "minesssh_hosts";
    private static final String HOSTS_KEY = "hosts_json";
    private static final int REQUEST_IMPORT_PRIVATE_KEY = 41;
    private final List<Host> hosts = new ArrayList<>();
    private LinearLayout hostsContainer;
    private TextView hostCount;
    private TextView emptyHosts;
    private EditText searchField;
    private EditText pendingKeyPathField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh_home);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(20, 39, 34));
        window.setNavigationBarColor(Color.rgb(243, 244, 239));
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        hostsContainer = findViewById(R.id.hosts_container);
        hostCount = findViewById(R.id.host_count);
        emptyHosts = findViewById(R.id.empty_hosts);
        searchField = findViewById(R.id.host_search);
        findViewById(R.id.add_host_button).setOnClickListener(view -> showHostEditor(null));
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderHosts();
            }

            @Override
            public void afterTextChanged(android.text.Editable text) { }
        });
        loadHosts();
        renderHosts();
    }

    private void loadHosts() {
        hosts.clear();
        String encodedHosts = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(HOSTS_KEY, "[]");
        try {
            JSONArray entries = new JSONArray(encodedHosts);
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.optJSONObject(index);
                if (entry == null) continue;
                Host host = new Host();
                host.name = entry.optString("name");
                host.address = entry.optString("address");
                host.username = entry.optString("username");
                host.port = entry.optInt("port", 22);
                host.group = entry.optString("group");
                host.keyPath = entry.optString("keyPath");
                host.favorite = entry.optBoolean("favorite");
                if (!TextUtils.isEmpty(host.address) && !TextUtils.isEmpty(host.username)) hosts.add(host);
            }
        } catch (JSONException ignored) {
            Toast.makeText(this, "Saved hosts could not be read", Toast.LENGTH_LONG).show();
        }
    }

    private void saveHosts() {
        JSONArray entries = new JSONArray();
        for (Host host : hosts) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("name", host.name);
                entry.put("address", host.address);
                entry.put("username", host.username);
                entry.put("port", host.port);
                entry.put("group", host.group);
                entry.put("keyPath", host.keyPath);
                entry.put("favorite", host.favorite);
                entries.put(entry);
            } catch (JSONException ignored) {
            }
        }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(HOSTS_KEY, entries.toString()).apply();
    }

    private void renderHosts() {
        hostsContainer.removeAllViews();
        hostCount.setText(getResources().getQuantityString(R.plurals.ssh_host_count, hosts.size(), hosts.size()));
        String query = searchField.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Host> visibleHosts = new ArrayList<>();
        for (Host host : hosts) {
            String haystack = (host.name + " " + host.address + " " + host.username + " " + host.group).toLowerCase(Locale.ROOT);
            if (haystack.contains(query)) visibleHosts.add(host);
        }
        Collections.sort(visibleHosts, Comparator.comparing((Host host) -> !host.favorite)
            .thenComparing(host -> host.group.toLowerCase(Locale.ROOT))
            .thenComparing(host -> host.name.toLowerCase(Locale.ROOT)));
        emptyHosts.setVisibility(visibleHosts.isEmpty() ? View.VISIBLE : View.GONE);

        String lastGroup = null;
        for (Host host : visibleHosts) {
            String group = TextUtils.isEmpty(host.group) ? "UNGROUPED" : host.group.toUpperCase(Locale.ROOT);
            if (!group.equals(lastGroup)) {
                TextView groupTitle = new TextView(this);
                groupTitle.setText(group);
                groupTitle.setTextColor(Color.rgb(102, 113, 107));
                groupTitle.setTextSize(11);
                groupTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                groupTitle.setLetterSpacing(0.06f);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                titleParams.topMargin = dp(15);
                titleParams.bottomMargin = dp(8);
                hostsContainer.addView(groupTitle, titleParams);
                lastGroup = group;
            }
            hostsContainer.addView(createHostCard(host));
        }
    }

    private View createHostCard(Host host) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(Color.rgb(224, 228, 221));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(9);
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(14), dp(10), dp(14));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        TextView marker = new TextView(this);
        marker.setText(host.name.isEmpty() ? host.address.substring(0, 1).toUpperCase(Locale.ROOT) : host.name.substring(0, 1).toUpperCase(Locale.ROOT));
        marker.setTextColor(Color.rgb(20, 39, 34));
        marker.setTextSize(16);
        marker.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marker.setGravity(Gravity.CENTER);
        marker.setBackgroundResource(R.drawable.ssh_avatar_background);
        marker.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ssh_accent)));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        row.addView(marker, markerParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, dp(4), 0);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(details, detailsParams);

        TextView title = new TextView(this);
        title.setText(TextUtils.isEmpty(host.name) ? host.address : host.name);
        title.setTextColor(Color.rgb(23, 35, 31));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        details.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(host.username + "@" + host.address + "  ·  " + host.port);
        subtitle.setTextColor(Color.rgb(102, 113, 107));
        subtitle.setTextSize(12);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        details.addView(subtitle, subtitleParams);

        TextView favorite = new TextView(this);
        favorite.setText(host.favorite ? "★" : "☆");
        favorite.setTextColor(host.favorite ? Color.rgb(174, 125, 29) : Color.rgb(135, 145, 137));
        favorite.setTextSize(23);
        favorite.setGravity(Gravity.CENTER);
        favorite.setContentDescription(host.favorite ? "Remove favorite" : "Add favorite");
        row.addView(favorite, new LinearLayout.LayoutParams(dp(40), dp(48)));
        favorite.setOnClickListener(view -> {
            host.favorite = !host.favorite;
            saveHosts();
            renderHosts();
        });

        card.setOnClickListener(view -> connect(host));
        card.setOnLongClickListener(view -> {
            showHostActions(host);
            return true;
        });
        return card;
    }

    private void showHostEditor(@Nullable Host existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), 0);

        EditText name = addField(form, "Host name", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT);
        EditText address = addField(form, "Hostname or IP address", existing == null ? "" : existing.address, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText username = addField(form, "Username", existing == null ? "" : existing.username, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText port = addField(form, "Port", existing == null ? "22" : String.valueOf(existing.port), InputType.TYPE_CLASS_NUMBER);
        EditText group = addField(form, "Group (optional)", existing == null ? "" : existing.group, InputType.TYPE_CLASS_TEXT);
        EditText keyPath = addField(form, "Private key path (optional)", existing == null ? "" : existing.keyPath, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        pendingKeyPathField = keyPath;
        com.google.android.material.button.MaterialButton importKey = new com.google.android.material.button.MaterialButton(this);
        importKey.setText("Import .pem private key");
        importKey.setTextAllCaps(false);
        importKey.setOnClickListener(view -> importPrivateKey());
        form.addView(importKey, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        CheckBox favorite = new CheckBox(this);
        favorite.setText("Add to favorites");
        favorite.setChecked(existing != null && existing.favorite);
        form.addView(favorite);

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Add SSH host" : "Edit SSH host")
            .setView(formScroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(existing == null ? "Save host" : "Save changes", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String hostAddress = address.getText().toString().trim();
            String hostUser = username.getText().toString().trim();
            int hostPort;
            try {
                hostPort = Integer.parseInt(port.getText().toString().trim());
            } catch (NumberFormatException exception) {
                port.setError("Enter a port from 1 to 65535");
                return;
            }
            if (!hostAddress.matches("[A-Za-z0-9._:-]{1,253}") || hostAddress.startsWith("-")) {
                address.setError("Enter a valid hostname or IP address");
                return;
            }
            if (!hostUser.matches("[A-Za-z0-9._-]{1,64}")) {
                username.setError("Enter a valid username");
                return;
            }
            if (hostPort < 1 || hostPort > 65535) {
                port.setError("Enter a port from 1 to 65535");
                return;
            }

            Host saved = existing == null ? new Host() : existing;
            saved.name = name.getText().toString().trim();
            saved.address = hostAddress;
            saved.username = hostUser;
            saved.port = hostPort;
            saved.group = group.getText().toString().trim();
            saved.keyPath = keyPath.getText().toString().trim();
            saved.favorite = favorite.isChecked();
            if (existing == null) hosts.add(saved);
            saveHosts();
            renderHosts();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void importPrivateKey() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "application/x-pem-file", "application/octet-stream", "text/plain"
        });
        startActivityForResult(intent, REQUEST_IMPORT_PRIVATE_KEY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_PRIVATE_KEY || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        File importedKey = null;
        try {
            importedKey = copyPrivateKeyToAppStorage(data.getData());
            if (pendingKeyPathField != null) pendingKeyPathField.setText(importedKey.getAbsolutePath());
            Toast.makeText(this, "Private key imported securely", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            if (importedKey != null) importedKey.delete();
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File copyPrivateKeyToAppStorage(Uri keyUri) throws IOException {
        File keyDirectory = new File(getFilesDir(), "ssh-keys");
        if (!keyDirectory.exists() && !keyDirectory.mkdirs()) throw new IOException("Could not create private key storage");
        File keyFile = new File(keyDirectory, "key-" + UUID.randomUUID() + ".pem");
        try {
            try (InputStream input = getContentResolver().openInputStream(keyUri);
                 FileOutputStream output = new FileOutputStream(keyFile)) {
                if (input == null) throw new IOException("Could not read the selected key file");
                byte[] buffer = new byte[4096];
                int totalBytes = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > 1024 * 1024) throw new IOException("Private key file is larger than 1 MB");
                    output.write(buffer, 0, read);
                }
            }
            byte[] header = new byte[256];
            int headerLength;
            try (FileInputStream input = new FileInputStream(keyFile)) {
                headerLength = input.read(header);
            }
            String headerText = new String(header, 0, Math.max(headerLength, 0), StandardCharsets.US_ASCII);
            if (!headerText.contains("PRIVATE KEY-----"))
                throw new IOException("Selected file is not a supported PEM/OpenSSH private key");

            keyFile.setReadable(false, false);
            keyFile.setWritable(false, false);
            if (!keyFile.setReadable(true, true) || !keyFile.setWritable(true, true))
                throw new IOException("Could not protect the imported private key");
            return keyFile;
        } catch (IOException exception) {
            keyFile.delete();
            throw exception;
        }
    }

    private EditText addField(LinearLayout parent, String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(inputType);
        field.setTextSize(15);
        parent.addView(field, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        return field;
    }

    private void showHostActions(Host host) {
        String[] actions = {"Connect", "Edit", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(host.name.isEmpty() ? host.address : host.name)
            .setItems(actions, (dialog, selected) -> {
                if (selected == 0) connect(host);
                if (selected == 1) showHostEditor(host);
                if (selected == 2) confirmDelete(host);
            })
            .show();
    }

    private void confirmDelete(Host host) {
        new AlertDialog.Builder(this)
            .setTitle("Delete host?")
            .setMessage("Remove " + (host.name.isEmpty() ? host.address : host.name) + " from saved hosts?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (dialog, selected) -> {
                hosts.remove(host);
                saveHosts();
                renderHosts();
            })
            .show();
    }

    private void connect(Host host) {
        Intent intent = new Intent(this, SshTerminalActivity.class);
        intent.putExtra(SshTerminalActivity.EXTRA_HOST_NAME, TextUtils.isEmpty(host.name) ? host.address : host.name);
        intent.putExtra(EXTRA_SSH_HOST, host.address);
        intent.putExtra(EXTRA_SSH_USER, host.username);
        intent.putExtra(EXTRA_SSH_PORT, host.port);
        intent.putExtra(EXTRA_SSH_KEY, host.keyPath);
        startActivity(intent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Host {
        String name = "";
        String address = "";
        String username = "";
        int port = 22;
        String group = "";
        String keyPath = "";
        boolean favorite;
    }
}