package org.schabi.newpipe.extractor.utils;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

/**
 * Android-compatible replacement for NewPipe's Utils class.
 * URLDecoder/URLEncoder Charset overloads require a newer Android runtime.
 */
public final class Utils {
    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    private static final Pattern M_PATTERN = Pattern.compile("(https?)?://m\\.");
    private static final Pattern WWW_PATTERN = Pattern.compile("(https?)?://www\\.");

    private Utils() { }

    public static String encodeUrlUtf8(final String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException error) {
            throw new AssertionError(error);
        }
    }

    public static String decodeUrlUtf8(final String url) {
        try {
            return URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException error) {
            throw new AssertionError(error);
        }
    }

    public static String removeNonDigitCharacters(final String value) {
        return value.replaceAll("\\D+", "");
    }

    public static long mixedNumberWordToLong(final String numberWord) throws ParsingException {
        String multiplier = "";
        try {
            multiplier = Parser.matchGroup("[\\d]+([\\.,][\\d]+)?([KMBkmb])+", numberWord, 2);
        } catch (final ParsingException ignored) { }
        final double count = Double.parseDouble(
                Parser.matchGroup1("([\\d]+([\\.,][\\d]+)?)", numberWord).replace(",", "."));
        switch (multiplier.toUpperCase()) {
            case "K": return (long) (count * 1e3);
            case "M": return (long) (count * 1e6);
            case "B": return (long) (count * 1e9);
            default: return (long) count;
        }
    }

    public static void checkUrl(final String pattern, final String url) throws ParsingException {
        checkUrl(Pattern.compile(pattern), url);
    }

    public static void checkUrl(final Pattern pattern, final String url) throws ParsingException {
        if (isNullOrEmpty(url)) throw new IllegalArgumentException("Url can't be null or empty");
        if (!Parser.isMatch(pattern, url.toLowerCase())) {
            throw new ParsingException("Url doesn't match the pattern");
        }
    }

    public static String replaceHttpWithHttps(final String url) {
        if (url == null) return null;
        return url.startsWith(HTTP) ? HTTPS + url.substring(HTTP.length()) : url;
    }

    public static String getQueryValue(final URL url, final String parameterName) {
        final String queryString = url.getQuery();
        if (queryString == null) return null;
        for (final String parameter : queryString.split("&")) {
            final String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && decodeUrlUtf8(parts[0]).equals(parameterName)) {
                return decodeUrlUtf8(parts[1]);
            }
        }
        return null;
    }

    public static URL stringToURL(final String url) throws MalformedURLException {
        try {
            return new URL(url);
        } catch (final MalformedURLException error) {
            if (("no protocol: " + url).equals(error.getMessage())) return new URL(HTTPS + url);
            throw error;
        }
    }

    public static boolean isHTTP(final URL url) {
        if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) return false;
        return url.getPort() == -1 || url.getPort() == url.getDefaultPort();
    }

    public static String removeMAndWWWFromUrl(final String url) {
        if (M_PATTERN.matcher(url).find()) return url.replace("m.", "");
        if (WWW_PATTERN.matcher(url).find()) return url.replace("www.", "");
        return url;
    }

    public static String removeUTF8BOM(final String value) {
        String result = value;
        if (result.startsWith("\uFEFF")) result = result.substring(1);
        if (result.endsWith("\uFEFF")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static String getBaseUrl(final String url) throws ParsingException {
        try {
            final URL parsed = stringToURL(url);
            return parsed.getProtocol() + "://" + parsed.getAuthority();
        } catch (final MalformedURLException error) {
            final String message = error.getMessage();
            if (message != null && message.startsWith("unknown protocol: ")) {
                return message.substring("unknown protocol: ".length());
            }
            throw new ParsingException("Malformed url: " + url, error);
        }
    }

    public static String followGoogleRedirectIfNeeded(final String url) {
        try {
            final URL decoded = stringToURL(url);
            if (decoded.getHost().contains("google") && decoded.getPath().equals("/url")) {
                return decodeUrlUtf8(Parser.matchGroup1("&url=([^&]+)(?:&|$)", url));
            }
        } catch (final Exception ignored) { }
        return url;
    }

    public static boolean isNullOrEmpty(final String value) { return value == null || value.isEmpty(); }
    public static boolean isNullOrEmpty(final Collection<?> value) { return value == null || value.isEmpty(); }
    public static <K, V> boolean isNullOrEmpty(final Map<K, V> value) { return value == null || value.isEmpty(); }
    public static boolean isBlank(final String value) { return value == null || value.trim().isEmpty(); }

    public static String join(final String delimiter, final String mapJoin,
                              final Map<? extends CharSequence, ? extends CharSequence> elements) {
        return elements.entrySet().stream()
                .map(entry -> entry.getKey() + mapJoin + entry.getValue())
                .collect(Collectors.joining(delimiter));
    }

    public static String nonEmptyAndNullJoin(final CharSequence delimiter, final String... elements) {
        return Arrays.stream(elements)
                .filter(value -> !isNullOrEmpty(value) && !value.equals("null"))
                .collect(Collectors.joining(delimiter));
    }

    public static String getStringResultFromRegexArray(final String input, final String[] regexes)
            throws Parser.RegexException { return getStringResultFromRegexArray(input, regexes, 0); }

    public static String getStringResultFromRegexArray(final String input, final Pattern[] regexes)
            throws Parser.RegexException { return getStringResultFromRegexArray(input, regexes, 0); }

    public static String getStringResultFromRegexArray(final String input, final String[] regexes, final int group)
            throws Parser.RegexException {
        return getStringResultFromRegexArray(input, Arrays.stream(regexes)
                .filter(Objects::nonNull).map(Pattern::compile).toArray(Pattern[]::new), group);
    }

    public static String getStringResultFromRegexArray(final String input, final Pattern[] regexes, final int group)
            throws Parser.RegexException {
        for (final Pattern regex : regexes) {
            try {
                final String result = Parser.matchGroup(regex, input, group);
                if (result != null) return result;
            } catch (final Parser.RegexException ignored) { }
        }
        throw new Parser.RegexException("No regex matched the input on group " + group);
    }
}
