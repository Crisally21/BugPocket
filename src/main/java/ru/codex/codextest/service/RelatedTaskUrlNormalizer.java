package ru.codex.codextest.service;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import ru.codex.codextest.exception.InvalidRelatedTaskUrlException;

public final class RelatedTaskUrlNormalizer {
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*", Pattern.DOTALL);

    private RelatedTaskUrlNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        } else if (!SCHEME.matcher(normalized).matches()) {
            normalized = "https://" + normalized;
        }
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawAuthority() == null) {
                throw new InvalidRelatedTaskUrlException();
            }
            // URI parsing is purely syntactic: no HTTP requests or DNS lookups.
            // Validate an ASCII form of the host, but preserve the user's Unicode URL.
            String authority = uri.getRawAuthority();
            int userInfoEnd = authority.lastIndexOf('@') + 1;
            String hostAndPort = authority.substring(userInfoEnd);
            if (!hostAndPort.startsWith("[")) {
                int portStart = hostAndPort.lastIndexOf(':');
                String host = portStart < 0 ? hostAndPort : hostAndPort.substring(0, portStart);
                String port = portStart < 0 ? "" : hostAndPort.substring(portStart);
                authority = authority.substring(0, userInfoEnd) + IDN.toASCII(host) + port;
            }
            URI server = new URI(uri.getScheme() + "://" + authority).parseServerAuthority();
            if (server.getHost() == null || server.getPort() > 65535) {
                throw new InvalidRelatedTaskUrlException();
            }
            return normalized;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new InvalidRelatedTaskUrlException();
        }
    }
}
