package Phase1.day6.src.main.java.com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class testJdbc{
    // 测试链接
    public void testConnection(){

    }
    
    //  查询所有用户
    public void queryUsers(){
        System.out.println("查询所有用户");

        Connection conn=null;
        Statement stmt =null;
        ResultSet rs=null;

        try{
            conn=DatabaseUtil.getConnection();
            stmt =conn.createStatement();

            String sql ="select id, name, email from users;";
            rs=stmt.executeQuery(sql);

            while(rs.next()){
                int id=rs.getInt("id");
                String name=rs.getString("name");
                String email=rs.getString("email");

                System.out.printf("ID: %d, 姓名: %s, 邮箱: %s%n", id, name, email);
            }
        }
        catch(SQLException e){
            e.printStackTrace();
        }
        finally{
            // 关闭资源
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    public void insertUser(String name, String email){
        System.out.println("插入用户："+name);

        Connection conn=null;
        Statement stmt=null;

        try{
            conn=DatabaseUtil.getConnection();
            stmt =conn.createStatement();

            String sql=String.format("insert into users (name, email) values ('%s', '%s')", name, email);
            
            System.out.println("执行SQL插入操作："+sql);

            int rows=stmt.executeUpdate(sql);
            System.out.println("插入成功，影响行数："+rows);
        }
        catch(SQLException e){
            // 检查是否是重复数据错误
            if (e.getMessage().contains("Duplicate entry"))
                System.out.println("插入失败：邮箱 '" + email + "' 已存在！");
            else 
                System.out.println("插入失败：" + e.getMessage());

        }
        finally{
            try{
                if(stmt!=null)  stmt.close();
                if(conn!=null)  conn.close();
            }
            catch(SQLException e){
                e.printStackTrace();
            }
        }
    }

    public void queryUserByName(String userName){
        System.out.println("查询用户："+userName);

        Connection conn = null;
        PreparedStatement pstmt=null;
        ResultSet rs=null;

        try{
            conn =DatabaseUtil.getConnection();

            // 使用PreparedStatement防止SQL注入
            String sql="select id, name, email from users where name=?";
            pstmt =conn.prepareStatement(sql);
            pstmt.setString(1, userName);
            rs=pstmt.executeQuery();

            boolean found=false;
            while(rs.next()){
                found=true;
                int id=rs.getInt("id");
                String name=rs.getString("name");
                String email=rs.getString("email");
                System.out.printf("找到用户 - ID：%d姓名：%s，邮箱：%s%n",id, name, email);
            }
            if(!found){
                System.out.printf("为找到用户：%s",userName);
            }
        }
        catch(SQLException e){
            e.printStackTrace();
        }
        finally{
            try{
                if(rs!=null)    rs.close();
                if(pstmt!=null)     pstmt.close();
                if(conn!=null)  conn.close();
            }
            catch(SQLException e){
                e.printStackTrace();
            }
        }
    } 
}

public class MainApp {
    
    public static void main(String[] args) {
        System.out.println("===== MySQL JDBC 程序 =====");
        
        testJdbc testjdbc = new testJdbc();
        // 查询数据
        testjdbc.queryUsers();
        System.out.println();
        
        
        // 插入数据
        testjdbc.insertUser("赵六", "zhaoliu@example.com");
        System.out.println();

        // 插入数据
        testjdbc.insertUser("吴七", "wuqi@example.com");
        System.out.println();
        
        // 再次查询
        testjdbc.queryUsers();
        System.out.println();
        
        // 使用预编译语句查询
        testjdbc.queryUserByName("张三");
    }
}