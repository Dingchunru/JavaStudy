package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class MainApp {
    
    public static void main(String[] args) {
        System.out.println("===== MySQL JDBC 示例程序 =====");
        
        // 1. 测试连接
        DatabaseUtil.testConnection();
        System.out.println();
        
        // 2. 查询数据
        queryUsers();
        System.out.println();
        
        // 3. 插入数据
        insertUser("赵六", "zhaoliu@example.com");
        System.out.println();
        
        // 4. 再次查询
        queryUsers();
        System.out.println();
        
        // 5. 使用预编译语句查询
        queryUserByName("张三");
    }
    
    // 查询所有用户
    private static void queryUsers() {
        System.out.println("📋 用户列表：");
        
        String sql = "SELECT id, name, email, created_at FROM users ORDER BY id";
        
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            System.out.println("ID\t姓名\t邮箱\t\t\t创建时间");
            System.out.println("--------------------------------------------------");
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String email = rs.getString("email");
                String createdAt = rs.getTimestamp("created_at").toString();
                
                System.out.printf("%d\t%s\t%s\t%s%n", 
                    id, name, email, createdAt.substring(0, 16));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 插入用户
    private static void insertUser(String name, String email) {
        System.out.println("➕ 插入新用户: " + name);
        
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            
            int rows = pstmt.executeUpdate();
            System.out.println("✅ 成功插入 " + rows + " 行数据");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 使用预编译语句查询
    private static void queryUserByName(String userName) {
        System.out.println("🔍 查询用户: " + userName);
        
        String sql = "SELECT * FROM users WHERE name = ?";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("找到用户:");
                    System.out.println("ID: " + rs.getInt("id"));
                    System.out.println("姓名: " + rs.getString("name"));
                    System.out.println("邮箱: " + rs.getString("email"));
                } else {
                    System.out.println("未找到用户: " + userName);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}