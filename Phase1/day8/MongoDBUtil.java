import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.*;
import static com.mongodb.client.model.Aggregates.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoDBUtil {
    private MongoClient mongoClient;
    private MongoDatabase database;
    
    /**
     * 构造函数
     * @param connectionString 连接字符串，如 "mongodb://localhost:27017"
     * @param databaseName 数据库名
     */
    public MongoDBUtil(String connectionString, String databaseName) {
        // 配置 POJO 编解码器，用于自动序列化/反序列化 Java 对象
        CodecRegistry pojoCodecRegistry = fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
        
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .codecRegistry(pojoCodecRegistry)
                .build();
        
        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(databaseName);
        System.out.println("Connected to MongoDB database: " + databaseName);
    }
    
    /**
     * 获取集合
     */
    public MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName);
    }
    
    /**
     * 获取支持POJO的集合
     */
    public <T> MongoCollection<T> getCollection(String collectionName, Class<T> clazz) {
        return database.getCollection(collectionName, clazz);
    }
    
    /**
     * 创建集合（如果不存在）
     */
    public void createCollectionIfNotExists(String collectionName) {
        try {
            database.createCollection(collectionName);
            System.out.println("Collection created: " + collectionName);
        } catch (Exception e) {
            // 集合已存在
            System.out.println("Collection already exists: " + collectionName);
        }
    }
    
    /**
     * 创建索引
     */
    public void createIndex(String collectionName, String fieldName, boolean ascending) {
        MongoCollection<Document> collection = getCollection(collectionName);
        IndexOptions indexOptions = new IndexOptions().unique(false);
        
        if (ascending) {
            collection.createIndex(new Document(fieldName, 1), indexOptions);
        } else {
            collection.createIndex(new Document(fieldName, -1), indexOptions);
        }
        
        System.out.println("Created index on field: " + fieldName);
    }
    
    /**
     * 插入单个文档
     */
    public String insertOne(String collectionName, Document document) {
        MongoCollection<Document> collection = getCollection(collectionName);
        InsertOneResult result = collection.insertOne(document);
        System.out.println("Inserted document with id: " + result.getInsertedId());
        return result.getInsertedId().asObjectId().getValue().toString();
    }
    
    /**
     * 插入单个 POJO 对象
     */
    public String insertOne(String collectionName, Object obj) {
        MongoCollection<Document> collection = getCollection(collectionName);
        Document document = Document.parse(obj.toString());
        InsertOneResult result = collection.insertOne(document);
        System.out.println("Inserted POJO with id: " + result.getInsertedId());
        return result.getInsertedId().asObjectId().getValue().toString();
    }
    
    /**
     * 插入多个文档
     */
    public void insertMany(String collectionName, List<Document> documents) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.insertMany(documents);
        System.out.println("Inserted " + documents.size() + " documents");
    }
    
    /**
     * 查询所有文档
     */
    public List<Document> findAll(String collectionName) {
        MongoCollection<Document> collection = getCollection(collectionName);
        List<Document> results = new ArrayList<>();
        
        try (MongoCursor<Document> cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        }
        
        return results;
    }
    
    /**
     * 带条件查询
     */
    public List<Document> find(String collectionName, Bson filter) {
        MongoCollection<Document> collection = getCollection(collectionName);
        List<Document> results = new ArrayList<>();
        
        try (MongoCursor<Document> cursor = collection.find(filter).iterator()) {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        }
        
        return results;
    }
    
    /**
     * 查询单个文档
     */
    public Document findOne(String collectionName, Bson filter) {
        MongoCollection<Document> collection = getCollection(collectionName);
        return collection.find(filter).first();
    }
    
    /**
     * 更新单个文档
     */
    public long updateOne(String collectionName, Bson filter, Bson update) {
        MongoCollection<Document> collection = getCollection(collectionName);
        UpdateResult result = collection.updateOne(filter, update);
        System.out.println("Matched: " + result.getMatchedCount() + 
                          ", Modified: " + result.getModifiedCount());
        return result.getModifiedCount();
    }
    
    /**
     * 更新多个文档
     */
    public long updateMany(String collectionName, Bson filter, Bson update) {
        MongoCollection<Document> collection = getCollection(collectionName);
        UpdateResult result = collection.updateMany(filter, update);
        System.out.println("Matched: " + result.getMatchedCount() + 
                          ", Modified: " + result.getModifiedCount());
        return result.getModifiedCount();
    }
    
    /**
     * 删除文档
     */
    public long deleteOne(String collectionName, Bson filter) {
        MongoCollection<Document> collection = getCollection(collectionName);
        DeleteResult result = collection.deleteOne(filter);
        System.out.println("Deleted: " + result.getDeletedCount() + " document(s)");
        return result.getDeletedCount();
    }
    
    /**
     * 删除多个文档
     */
    public long deleteMany(String collectionName, Bson filter) {
        MongoCollection<Document> collection = getCollection(collectionName);
        DeleteResult result = collection.deleteMany(filter);
        System.out.println("Deleted: " + result.getDeletedCount() + " document(s)");
        return result.getDeletedCount();
    }
    
    /**
     * 聚合查询示例
     */
    public void aggregateExample(String collectionName) {
        MongoCollection<Document> collection = getCollection(collectionName);
        
        List<Bson> pipeline = Arrays.asList(
            match(gte("age", 20)),  // 过滤年龄 >= 20
            group("$city", Accumulators.sum("count", 1)),  // 按城市分组统计
            sort(descending("count"))  // 按数量降序排序
        );
        
        System.out.println("\n=== 聚合查询结果 ===");
        for (Document doc : collection.aggregate(pipeline)) {
            System.out.println(doc.toJson());
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed");
        }
    }
}