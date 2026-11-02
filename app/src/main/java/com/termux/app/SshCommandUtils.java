package com.termux.app;

public final class SshCommandUtils {

    private SshCommandUtils() { }

    public static String buildCommand(String username, String host, int port, String keyPath) {
        StringBuilder command = new StringBuilder(
            "if ! command -v ssh >/dev/null 2>&1; then "
                + "echo 'OpenSSH is missing. Installing it now...'; "
                + "if ! pkg install -y openssh; then "
                + "echo 'OpenSSH install failed. Check your network and package repositories.'; "
                + "exec \"$PREFIX/bin/sh\"; "
                + "fi; "
                + "fi; exec ssh -p ").append(port);

        if (keyPath != null && !keyPath.trim().isEmpty()) {
            command.append(" -i ").append(shellQuote(keyPath.trim()));
        }
        command.append(' ').append(shellQuote(username + "@" + host));
        return command.toString();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}