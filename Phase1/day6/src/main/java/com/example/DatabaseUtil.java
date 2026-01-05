package Phase1.day6.src.main.java.com.example;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseUtil {

    //数据库配置信息
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        try {
            Properties props=new Properties();
            InputStream input=DatabaseUtil.class.getClassLoader().getResourceAsStream("db.properties");
            props.load(input);

            // 获取配置信息
            URL=props.getProperty("db.url");
            USER=props.getProperty("db.username");
            PASSWORD=props.getProperty("db.password");

            // 加载数据库驱动
            Class.forName(props.getProperty("db.driver"));

            System.out.println("数据库驱动加载成功");
        }
        catch(Exception e){
            e.printStackTrace();
            throw new RuntimeException("加载数据库配置失败",e);
        }
    }

    // 获取数据库连接
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
