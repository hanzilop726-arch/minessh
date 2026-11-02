package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.SshCommandUtils;
import com.termux.app.TermuxInstaller;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;
import java.nio.charset.StandardCharsets;

public final class SshTerminalActivity extends AppCompatActivity implements ServiceConnection, TerminalViewClient {

    public static final String EXTRA_HOST_NAME = "com.termux.ssh.HOST_NAME";

    private TerminalView terminalView;
    private TextView connectionStatus;
    private View statusDot;
    private TermuxService termuxService;
    private TermuxSession termuxSession;
    private boolean isBound;
    private boolean controlArmed;
    private boolean altArmed;
    private boolean isDisconnecting;
    private int fontSize = 15;
    private final SshSessionClient sessionClient = new SshSessionClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh_terminal);
        getWindow().setStatusBarColor(Color.rgb(23, 35, 31));
        getWindow().setNavigationBarColor(Color.rgb(16, 23, 20));

        terminalView = findViewById(R.id.ssh_terminal_view);
        terminalView.setTerminalViewClient(this);
        terminalView.setTextSize(fontSize);
        terminalView.setKeepScreenOn(true);
        connectionStatus = findViewById(R.id.terminal_connection_status);
        statusDot = findViewById(R.id.terminal_status_dot);

        String hostName = getIntent().getStringExtra(EXTRA_HOST_NAME);
        String host = getIntent().getStringExtra(SshHomeActivity.EXTRA_SSH_HOST);
        String username = getIntent().getStringExtra(SshHomeActivity.EXTRA_SSH_USER);
        int port = getIntent().getIntExtra(SshHomeActivity.EXTRA_SSH_PORT, 22);
        String keyPath = getIntent().getStringExtra(SshHomeActivity.EXTRA_SSH_KEY);
        ((TextView) findViewById(R.id.terminal_host_title)).setText(hostName == null ? host : hostName);

        findViewById(R.id.terminal_back).setOnClickListener(view -> onBackPressed());
        findViewById(R.id.terminal_keyboard).setOnClickListener(view -> showKeyboard());
        findViewById(R.id.key_esc).setOnClickListener(view -> writeTerminalText("\u001b"));
        findViewById(R.id.key_tab).setOnClickListener(view -> writeTerminalText("\t"));
        findViewById(R.id.key_up).setOnClickListener(view -> writeTerminalText("\u001b[A"));
        findViewById(R.id.key_down).setOnClickListener(view -> writeTerminalText("\u001b[B"));
        findViewById(R.id.key_pipe).setOnClickListener(view -> writeTerminalText("|"));
        setModifierButton(findViewById(R.id.key_ctrl), true);
        setModifierButton(findViewById(R.id.key_alt), false);

        if (host == null || username == null || host.isEmpty() || username.isEmpty() || port < 1 || port > 65535) {
            setConnectionState("Invalid host details", false);
            return;
        }

        Intent serviceIntent = new Intent(this, TermuxService.class);
        try {
            startService(serviceIntent);
            isBound = bindService(serviceIntent, this, 0);
            if (!isBound) setConnectionState("Could not start terminal service", false);
        } catch (Exception exception) {
            setConnectionState("Could not start terminal service", false);
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        termuxService = ((TermuxService.LocalBinder) binder).service;
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> openSshSession(
            getIntent().getStringExtra(SshHomeActivity.EXTRA_SSH_USER),
            getIntent().getStringExtra(SshHomeActivity.EXTRA_SSH_HOST),
            getIntent().getIntExtra(SshHomeActivity.EXTRA_SSH_PORT, 22),
            getIntent().getStringExtra(SshHomeActivity.EXTRA_SSH_KEY)));
    }

    private void openSshSession(String username, String host, int port, String keyPath) {
        if (isFinishing() || termuxService == null) return;
        setConnectionState("Starting SSH", false);
        File shell = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "bin/sh");
        String command = SshCommandUtils.buildCommand(username, host, port, keyPath);
        termuxSession = termuxService.createTermuxSession(shell.getAbsolutePath(),
            new String[] {"-c", command}, null, TermuxConstants.TERMUX_HOME_DIR_PATH, false,
            getIntent().getStringExtra(EXTRA_HOST_NAME));
        if (termuxSession == null) {
            setConnectionState("Could not open SSH terminal", false);
            return;
        }
        TerminalSession session = termuxSession.getTerminalSession();
        session.updateTerminalSessionClient(sessionClient);
        terminalView.attachSession(session);
        terminalView.requestFocus();
        setConnectionState("Terminal ready · " + username + "@" + host, true);
    }

    private void setModifierButton(MaterialButton button, boolean control) {
        button.setOnClickListener(view -> {
            if (control) controlArmed = !controlArmed;
            else altArmed = !altArmed;
            boolean armed = control ? controlArmed : altArmed;
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(armed ? "#D8F26A" : "#263A33")));
            button.setTextColor(Color.parseColor(armed ? "#142722" : "#D8F26A"));
        });
    }

    private void setConnectionState(String message, boolean connected) {
        connectionStatus.setText(message);
        statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            Color.parseColor(connected ? "#8FCF77" : "#E5B454")));
    }

    private void writeTerminalText(String text) {
        TerminalSession session = terminalView.mTermSession;
        if (session == null) return;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        session.write(bytes, 0, bytes.length);
        terminalView.requestFocus();
    }

    private void showKeyboard() {
        terminalView.requestFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) inputMethodManager.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Disconnect from server?")
            .setMessage("The SSH session will close.")
            .setNegativeButton("Stay connected", null)
            .setPositiveButton("Disconnect", (dialog, which) -> disconnectAndFinish())
            .show();
    }

    private void disconnectAndFinish() {
        isDisconnecting = true;
        if (termuxSession != null) {
            TerminalSession session = termuxSession.getTerminalSession();
            session.finishIfRunning();
            if (termuxService != null) termuxService.removeTermuxSession(session);
            termuxSession = null;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        terminalView.setKeepScreenOn(false);
        if (isBound) {
            unbindService(this);
            isBound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        termuxService = null;
        setConnectionState("Terminal service disconnected", false);
    }

    @Override
    public float onScale(float scale) {
        if (scale > 1.05f && fontSize < 28) fontSize++;
        else if (scale < 0.95f && fontSize > 8) fontSize--;
        terminalView.setTextSize(fontSize);
        return 1f;
    }

    @Override
    public void onSingleTapUp(MotionEvent event) {
        showKeyboard();
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() { return false; }

    @Override
    public boolean shouldEnforceCharBasedInput() { return true; }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() { return false; }

    @Override
    public boolean isTerminalViewSelected() { return true; }

    @Override
    public void copyModeChanged(boolean copyMode) { }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event, TerminalSession session) { return false; }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }

    @Override
    public boolean onLongPress(MotionEvent event) { return false; }

    @Override
    public boolean readControlKey() { return controlArmed; }

    @Override
    public boolean readAltKey() { return altArmed; }

    @Override
    public boolean readShiftKey() { return false; }

    @Override
    public boolean readFnKey() { return false; }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        if (ctrlDown || controlArmed) {
            int controlCode = Character.toLowerCase(codePoint) & 0x1f;
            session.write(new byte[] {(byte) controlCode}, 0, 1);
            controlArmed = false;
            return true;
        }
        if (altArmed) {
            session.writeCodePoint(true, codePoint);
            altArmed = false;
            return true;
        }
        return false;
    }

    @Override
    public void onEmulatorSet() { }

    @Override
    public void logError(String tag, String message) { android.util.Log.e(tag, message); }

    @Override
    public void logWarn(String tag, String message) { android.util.Log.w(tag, message); }

    @Override
    public void logInfo(String tag, String message) { android.util.Log.i(tag, message); }

    @Override
    public void logDebug(String tag, String message) { android.util.Log.d(tag, message); }

    @Override
    public void logVerbose(String tag, String message) { android.util.Log.v(tag, message); }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception exception) {
        android.util.Log.e(tag, message, exception);
    }

    @Override
    public void logStackTrace(String tag, Exception exception) { android.util.Log.e(tag, exception.getMessage(), exception); }

    private final class SshSessionClient extends TermuxTerminalSessionClientBase {
        @Override
        public void onTextChanged(@NonNull TerminalSession changedSession) {
            runOnUiThread(() -> terminalView.onScreenUpdated());
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finishedSession) {
            runOnUiThread(() -> {
                setConnectionState("Disconnected", false);
                if (!isDisconnecting && termuxService != null) termuxService.removeTermuxSession(finishedSession);
            });
        }

        @Override
        public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
            super.setTerminalShellPid(session, pid);
            if (termuxService != null) {
                TermuxSession storedSession = termuxService.getTermuxSessionForTerminalSession(session);
                if (storedSession != null) storedSession.getExecutionCommand().mPid = pid;
            }
        }
    }
}