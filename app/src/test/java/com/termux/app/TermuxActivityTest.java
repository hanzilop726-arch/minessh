package com.termux.app;

import com.termux.shared.termux.data.TermuxUrlUtils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;

public class TermuxActivityTest {

    private void assertUrlsAre(String text, String... urls) {
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        Collections.addAll(expected, urls);
        Assert.assertEquals(expected, TermuxUrlUtils.extractUrls(text));
    }

    @Test
    public void testExtractUrls() {
        assertUrlsAre("hello http://example.com world", "http://example.com");

        assertUrlsAre("http://example.com\nhttp://another.com", "http://example.com", "http://another.com");

        assertUrlsAre("hello http://example.com world and http://more.example.com with secure https://more.example.com",
            "http://example.com", "http://more.example.com", "https://more.example.com");

        assertUrlsAre("hello https://example.com/#bar https://example.com/foo#bar",
            "https://example.com/#bar", "https://example.com/foo#bar");
    }

    @Test
    public void testSshCommandUsesHostDetailsAndQuotesKeyPath() {
        String command = SshCommandUtils.buildCommand("admin", "192.0.2.10", 2222, "/home/user/ssh keys/id_ed25519");

        Assert.assertTrue(command.contains("exec ssh -p 2222 -i '/home/user/ssh keys/id_ed25519' 'admin@192.0.2.10'"));
    }

    @Test
    public void testSshCommandEscapesShellQuotesInKeyPath() {
        String command = SshCommandUtils.buildCommand("admin", "example.com", 22, "/tmp/key'file");

        Assert.assertTrue(command.contains("-i '/tmp/key'\\''file' 'admin@example.com'"));
    }

}
