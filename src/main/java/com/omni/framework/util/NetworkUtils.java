package com.omni.framework.util;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.tomcat.servlet.HttpServletRequest;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络工具类。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>探测本机真实 IPv4（跳过回环 / 链路本地 / 虚拟网卡，按内网优先级排序）</li>
 *   <li>列举网卡候选列表，供 UI 选择</li>
 *   <li>从 HttpServletRequest 提取客户端真实 IP（兼容反向代理）</li>
 * </ul>
 *
 * <p>说明：此类不依赖 Spring，可被 Worker 端等无容器场景复用。</p>
 */
public final class NetworkUtils {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtils.class);

    /** 视为"虚拟"的网卡名前缀/包含片段（小写） */
    private static final String[] VIRTUAL_HINTS = {
            "docker", "br-", "veth", "virbr", "vmnet", "vboxnet",
            "tun", "tap", "utun", "zt", "tailscale", "cni", "flannel", "cali"
    };

    /** 常见回环/链路本地前缀 */
    private static final String[] LOOPBACK_PREFIXES = {"127.", "169.254."};

    private NetworkUtils() {}

    // ==================== 本机 IP ====================

    /**
     * 获取本机首选 IPv4（非回环、非链路本地、非虚拟网卡）。
     * 内网优先级：192.168.* &gt; 10.* &gt; 172.16-31.* &gt; 其他
     *
     * @return 首选 IPv4 字符串；全部失败时返回 "127.0.0.1"
     */
    public static String getLocalIpv4() {
        try {
            List<NetworkInterface> candidates = listPhysicalInterfaces();
            for (NetworkInterface ni : candidates) {
                String ip = firstIpv4(ni);
                if (ip != null) return ip;
            }
            // 兜底：JVM 默认
            InetAddress local = InetAddress.getLocalHost();
            if (local instanceof Inet4Address) {
                return local.getHostAddress();
            }
        } catch (Throwable t) {
            log.warn("获取本机 IPv4 失败: {}", t.getMessage());
        }
        return "127.0.0.1";
    }

    /**
     * 列举所有网卡（含回环），供 UI 选择候选 IP。
     * 每个元素：{ name, displayName, ip, loopback, virtual, up }
     */
    public static List<Map<String, Object>> listNetworkInterfaces() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return result;
            while (all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                String ipv4 = firstIpv4IncludingLoopback(ni);
                if (ipv4 == null) continue;

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", ni.getName());
                m.put("displayName", safeDisplayName(ni));
                m.put("ip", ipv4);
                m.put("loopback", safeIsLoopback(ni));
                m.put("virtual", safeIsVirtual(ni) || isVirtualName(ni.getName()));
                m.put("up", safeIsUp(ni));
                result.add(m);
            }
        } catch (Throwable t) {
            log.warn("列举网卡失败: {}", t.getMessage());
        }
        return result;
    }

    /**
     * 获取客户端真实 IP。依次尝试：
     * X-Forwarded-For（取第一个） → X-Real-IP → remoteAddr
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";

        String xff = safeHeader(request, "X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = safeHeader(request, "X-Real-IP");
        if (real != null && !real.isEmpty() && !"unknown".equalsIgnoreCase(real)) {
            return real.trim();
        }
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isEmpty()) return "unknown";
        // 兼容 IPv6 映射的 IPv4
        if (remote.startsWith("::ffff:")) return remote.substring(7);
        return remote;
    }

    // ==================== 内部实现 ====================

    private static String safeHeader(HttpServletRequest request, String name) {
        try { return request.getHeader(name); }
        catch (Throwable t) { return null; }
    }

    private static String safeDisplayName(NetworkInterface ni) {
        try {
            String n = ni.getDisplayName();
            return n != null ? n : ni.getName();
        } catch (Throwable t) {
            return ni.getName();
        }
    }

    private static boolean safeIsLoopback(NetworkInterface ni) {
        try { return ni.isLoopback(); } catch (Throwable t) { return false; }
    }

    private static boolean safeIsVirtual(NetworkInterface ni) {
        try { return ni.isVirtual(); } catch (Throwable t) { return false; }
    }

    private static boolean safeIsUp(NetworkInterface ni) {
        try { return ni.isUp(); } catch (Throwable t) { return false; }
    }

    private static boolean isVirtualName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String hint : VIRTUAL_HINTS) {
            if (lower.startsWith(hint) || lower.contains(hint)) return true;
        }
        return false;
    }

    private static List<NetworkInterface> listPhysicalInterfaces() throws Exception {
        List<NetworkInterface> list = new ArrayList<>();
        Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
        if (all == null) return list;

        while (all.hasMoreElements()) {
            NetworkInterface ni = all.nextElement();
            if (!safeIsUp(ni)) continue;
            if (safeIsLoopback(ni)) continue;
            if (safeIsVirtual(ni)) continue;
            if (isVirtualName(ni.getName())) continue;
            if (firstIpv4(ni) == null) continue;
            list.add(ni);
        }
        Collections.sort(list, (a, b) -> {
            String ipA = firstIpv4(a);
            String ipB = firstIpv4(b);
            return Integer.compare(ipPriority(ipB), ipPriority(ipA));
        });
        return list;
    }

    private static int ipPriority(String ip) {
        if (ip == null) return 0;
        if (ip.startsWith("192.168.")) return 4;
        if (ip.startsWith("10.")) return 3;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return 2;
            } catch (Exception ignored) {}
        }
        return 1;
    }

    private static String firstIpv4(NetworkInterface ni) {
        try {
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs != null && addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!(addr instanceof Inet4Address)) continue;
                if (addr.isLoopbackAddress()) continue;
                if (addr.isLinkLocalAddress()) continue;
                String ip = addr.getHostAddress();
                if (isLoopbackPrefix(ip)) continue;
                return ip;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String firstIpv4IncludingLoopback(NetworkInterface ni) {
        try {
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs != null && addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isLoopbackPrefix(String ip) {
        for (String p : LOOPBACK_PREFIXES) {
            if (ip.startsWith(p)) return true;
        }
        return false;
    }
}