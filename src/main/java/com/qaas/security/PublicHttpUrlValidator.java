package com.qaas.security;

import com.qaas.exception.BadRequestException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

public final class PublicHttpUrlValidator {

    private PublicHttpUrlValidator() {
    }

    public static void validate(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException e) {
            throw invalidTarget();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || host == null || host.isBlank() || uri.getRawUserInfo() != null) {
            throw invalidTarget();
        }

        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")) {
            throw invalidTarget();
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) {
                    throw invalidTarget();
                }
            }
        } catch (UnknownHostException e) {
            throw invalidTarget();
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(bytes);
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);

        return first != 0
                && first != 10
                && !(first == 100 && second >= 64 && second <= 127)
                && first != 127
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && (second == 0 || second == 168))
                && !(first == 192 && second == 88 && third == 99)
                && !(first == 192 && second == 0 && third == 2)
                && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                && !(first == 203 && second == 0 && third == 113)
                && first < 224;
    }

    private static boolean isPublicIpv6(byte[] bytes) {
        if (isIpv4Mapped(bytes)) {
            byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
            return isPublicIpv4(ipv4);
        }

        boolean firstTenZero = true;
        for (int i = 0; i < 10; i++) {
            firstTenZero &= bytes[i] == 0;
        }
        if (firstTenZero && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
            byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
            return isPublicIpv4(ipv4);
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean uniqueLocal = (first & 0xfe) == 0xfc;
        boolean documentation = first == 0x20 && second == 0x01
                && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8;
        boolean transitionOrSpecial = first == 0x20 && second == 0x01
                && (Byte.toUnsignedInt(bytes[2]) & 0xf0) == 0;
        boolean nat64 = first == 0x00 && Byte.toUnsignedInt(bytes[1]) == 0x64
                && Byte.toUnsignedInt(bytes[2]) == 0xff && Byte.toUnsignedInt(bytes[3]) == 0x9b;

        return !uniqueLocal && !documentation && !transitionOrSpecial && !nat64;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private static BadRequestException invalidTarget() {
        return new BadRequestException(
                "Target URL must use HTTP(S) and resolve only to public IP addresses");
    }
}
