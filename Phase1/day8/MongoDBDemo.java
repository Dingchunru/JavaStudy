import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class MongoDBDemo {
    
    public static void main(String[] args) {
        // 1. 初始化连接
        MongoDBUtil mongoUtil = new MongoDBUtil("mongodb://localhost:27017", "testdb");
        
        try {
            String collectionName = "users";
            
            // 2. 创建集合（如果不存在）
            mongoUtil.createCollectionIfNotExists(collectionName);
            
            // 3. 创建索引
            mongoUtil.createIndex(collectionName, "email", true);
            mongoUtil.createIndex(collectionName, "age", false);
            
            // 4. 插入文档 - 使用 Document 对象
            Document user1 = new Document("username", "john_doe")
                    .append("email", "john@example.com")
                    .append("age", 28)
                    .append("city", "New York")
                    .append("hobbies", Arrays.asList("reading", "gaming"));
            
            String id1 = mongoUtil.insertOne(collectionName, user1);
            System.out.println("Inserted user with ID: " + id1);
            
            // 5. 插入更多文档
            Document user2 = new Document("username", "jane_smith")
                    .append("email", "jane@example.com")
                    .append("age", 32)
                    .append("city", "Los Angeles")
                    .append("hobbies", Arrays.asList("hiking", "photography"));
            
            Document user3 = new Document("username", "bob_wilson")
                    .append("email", "bob@example.com")
                    .append("age", 25)
                    .append("city", "New York")
                    .append("hobbies", Arrays.asList("cooking", "music"));
            
            Document user4 = new Document("username", "alice_brown")
                    .append("email", "alice@example.com")
                    .append("age", 40)
                    .append("city", "Chicago")
                    .append("hobbies", Arrays.asList("yoga", "painting"));
            
            List<Document> users = Arrays.asList(user2, user3, user4);
            mongoUtil.insertMany(collectionName, users);
            
            // 6. 查询所有文档
            System.out.println("\n=== 所有用户 ===");
            List<Document> allUsers = mongoUtil.findAll(collectionName);
            allUsers.forEach(doc -> System.out.println(doc.toJson()));
            
            // 7. 条件查询
            System.out.println("\n=== 年龄大于30的用户 ===");
            Bson ageFilter = gt("age", 30);
            List<Document> olderUsers = mongoUtil.find(collectionName, ageFilter);
            olderUsers.forEach(doc -> System.out.println(doc.toJson()));
            
            // 8. 复杂条件查询
            System.out.println("\n=== 来自纽约且年龄小于30的用户 ===");
            Bson complexFilter = and(
                eq("city", "New York"),
                lt("age", 30)
            );
            List<Document> nyYoungUsers = mongoUtil.find(collectionName, complexFilter);
            nyYoungUsers.forEach(doc -> System.out.println(doc.toJson()));
            
            // 9. 更新文档
            System.out.println("\n=== 更新john的年龄 ===");
            Bson updateFilter = eq("username", "john_doe");
            Bson updateOperation = set("age", 29);
            mongoUtil.updateOne(collectionName, updateFilter, updateOperation);
            
            // 10. 批量更新
            System.out.println("\n=== 为所有纽约用户添加标签 ===");
            Bson nyFilter = eq("city", "New York");
            Bson addField = set("tags", Arrays.asList("east_coast", "big_city"));
            mongoUtil.updateMany(collectionName, nyFilter, addField);
            
            // 11. 删除文档
            System.out.println("\n=== 删除邮箱为bob@example.com的用户 ===");
            Bson deleteFilter = eq("email", "bob@example.com");
            mongoUtil.deleteOne(collectionName, deleteFilter);
            
            // 12. 查询更新后的数据
            System.out.println("\n=== 更新后的所有用户 ===");
            List<Document> updatedUsers = mongoUtil.findAll(collectionName);
            updatedUsers.forEach(doc -> System.out.println(doc.toJson()));
            
            // 13. 聚合查询示例
            mongoUtil.aggregateExample(collectionName);
            
            // 14. 使用POJO进行操作
            System.out.println("\n=== 使用POJO操作 ===");
            demoWithPOJO(mongoUtil);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 15. 关闭连接
            mongoUtil.close();
        }
    }
    
    private static void demoWithPOJO(MongoDBUtil mongoUtil) {
        String collectionName = "users_pojo";
        
        // 创建POJO用户
        User user = new User("michael_scott", "michael@example.com", 45);
        user.setHobbies(Arrays.asList("management", "comedy"));
        
        Address address = new Address("Scranton", "Dunder Mifflin St", "18503");
        user.setAddress(address);
        
        // 插入POJO
        String userId = mongoUtil.insertOne(collectionName, user);
        System.out.println("Inserted POJO user with ID: " + userId);
        
        // 查询POJO用户
        System.out.println("\n=== 查询POJO用户 ===");
        Document foundUser = mongoUtil.findOne(collectionName, eq("email", "michael@example.com"));
        if (foundUser != null) {
            System.out.println("Found user: " + foundUser.toJson());
        }
    }
}