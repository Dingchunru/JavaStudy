package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseUtil {
    
    private static Connection connection = null;
    private static Properties props = new Properties();
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        try (InputStream input = DatabaseUtil.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            
            if (input == null) {
                System.out.println("找不到配置文件 db.properties");
                System.exit(1);
            }
            
            props.load(input);
            Class.forName(props.getProperty("db.driver"));
            System.out.println("✅ MySQL驱动加载成功");
            
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    // 获取单例连接
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = props.getProperty("db.url");
            String username = props.getProperty("db.username");
            String password = props.getProperty("db.password");
            
            System.out.println("🔄 创建数据库连接...");
            connection = DriverManager.getConnection(url, username, password);
        }
        return connection;
    }
    
    // 关闭连接
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("✅ 数据库连接已关闭");
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                connection = null;
            }
        }
    }
    
    // 测试连接
    public static void testConnection() {
        try (Connection conn = getConnection()) {
            System.out.println("✅ 数据库连接成功！");
            
            var metaData = conn.getMetaData();
            System.out.println("📊 数据库产品: " + metaData.getDatabaseProductName());
            System.out.println("📊 数据库版本: " + metaData.getDatabaseProductVersion());
            System.out.println("📊 驱动版本: " + metaData.getDriverVersion());
            
        } catch (SQLException e) {
            System.out.println("❌ 数据库连接失败！");
            e.printStackTrace();
        }
    }
}