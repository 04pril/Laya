// SPDX-License-Identifier: GPL-3.0-only
// JNI ABI retained from the upstream Morphe WireGuard engine.
package app.morphe.extension.shared.wireguard;

/**
 * App-local userspace WireGuard backend. No VpnService or OS TUN.
 * Library is loaded only during explicit profile validation or enabled startup.
 */
public final class NativeTunnel {
    static { System.loadLibrary("morphewireguard"); }
    private NativeTunnel() {}
    public static native int start(String config, String username, String password, int port);
    public static native void stop();
    public static native void validate(String config);

    public static final class InvalidProfileException extends IllegalArgumentException {
        public InvalidProfileException(String code) { super(code); }
    }
}
