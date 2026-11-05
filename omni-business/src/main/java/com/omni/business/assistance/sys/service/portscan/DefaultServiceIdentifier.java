package com.omni.business.assistance.sys.service.portscan;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认服务识别器：
 *   1. 优先按进程名模糊匹配（如 mysqld → MySQL）
 *   2. 其次按端口号映射（如 3306 → MySQL）
 *   3. 都无法识别返回 "未知"
 *
 * 新增端口映射 = reg(port, service, category)；
 * 新增进程名识别 = PROCESS_HINTS 追加一行。
 */
public class DefaultServiceIdentifier implements ServiceIdentifier {

    /** 进程名片段 → 服务名（顺序无关，命中即返回） */
    private static final String[][] PROCESS_HINTS = {
            {"mysqld",        "MySQL"},
            {"mariadbd",      "MariaDB"},
            {"postgres",      "PostgreSQL"},
            {"redis-server",  "Redis"},
            {"redis",         "Redis"},
            {"mongod",        "MongoDB"},
            {"elasticsearch", "Elasticsearch"},
            {"zookeeper",     "ZooKeeper"},
            {"kafka",         "Kafka"},
            {"rabbitmq",      "RabbitMQ"},
            {"nginx",         "Nginx"},
            {"httpd",         "Apache HTTPD"},
            {"apache",        "Apache"},
            {"tomcat",        "Tomcat"},
            {"docker",        "Docker"},
            {"containerd",    "containerd"},
            {"sshd",          "SSH"},
            {"ssh",           "SSH"},
            {"java",          "Java 应用"},
            {"node",          "Node.js"},
            {"python",        "Python 应用"},
            {"sqlservr",      "SQL Server"}
    };

    private static final Map<Integer, String> PORT_SERVICE = new HashMap<>();
    private static final Map<Integer, String> PORT_CATEGORY = new HashMap<>();

    static {
        // ---------- 系统服务 ----------
        reg(21, "FTP", "系统服务");
        reg(22, "SSH", "系统服务");
        reg(23, "Telnet", "系统服务");
        reg(25, "SMTP", "系统服务");
        reg(53, "DNS", "系统服务");
        reg(69, "TFTP", "系统服务");
        reg(110, "POP3", "系统服务");
        reg(135, "RPC", "系统服务");
        reg(139, "NetBIOS", "系统服务");
        reg(143, "IMAP", "系统服务");
        reg(161, "SNMP", "系统服务");
        reg(389, "LDAP", "系统服务");
        reg(445, "SMB", "系统服务");
        reg(636, "LDAPS", "系统服务");
        reg(3389, "RDP", "系统服务");
        reg(5985, "WinRM (HTTP)", "系统服务");
        reg(5986, "WinRM (HTTPS)", "系统服务");

        // ---------- Web ----------
        reg(80,   "HTTP", "Web");
        reg(443,  "HTTPS", "Web");
        reg(3000, "Node.js Dev", "Web");
        reg(5000, "Flask / .NET", "Web");
        reg(8000, "HTTP (Alt)", "Web");
        reg(8080, "HTTP (Alt)", "Web");
        reg(8443, "HTTPS (Alt)", "Web");
        reg(8888, "HTTP (Alt)", "Web");
        reg(9000, "HTTP (Alt)", "Web");

        // ---------- 数据库 / 中间件 ----------
        reg(1433,  "SQL Server", "数据库");
        reg(1521,  "Oracle", "数据库");
        reg(3306,  "MySQL / MariaDB", "数据库");
        reg(5432,  "PostgreSQL", "数据库");
        reg(6379,  "Redis", "缓存");
        reg(11211, "Memcached", "缓存");
        reg(9200,  "Elasticsearch", "搜索");
        reg(9300,  "Elasticsearch (Transport)", "搜索");
        reg(27017, "MongoDB", "数据库");
        reg(2181,  "ZooKeeper", "中间件");
        reg(9092,  "Kafka", "中间件");
        reg(5672,  "RabbitMQ", "中间件");
        reg(15672, "RabbitMQ Management", "中间件");
        reg(61616, "ActiveMQ", "中间件");
        reg(2375,  "Docker (unsecure)", "容器");
        reg(2376,  "Docker TLS", "容器");
        reg(2379,  "etcd", "中间件");
        reg(8500,  "Consul", "中间件");
    }

    private static void reg(int port, String service, String category) {
        PORT_SERVICE.put(port, service);
        PORT_CATEGORY.put(port, category);
    }

    @Override
    public String identify(int port, String protocol, String processName) {
        if (processName != null && !processName.isEmpty()) {
            String lower = processName.toLowerCase();
            for (String[] h : PROCESS_HINTS) {
                if (lower.contains(h[0])) return h[1];
            }
        }
        return PORT_SERVICE.getOrDefault(port, "未知");
    }

    @Override
    public String category(int port) {
        return PORT_CATEGORY.getOrDefault(port, "其他");
    }
}