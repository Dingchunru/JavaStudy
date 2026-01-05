import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Date;

public class User {
    private ObjectId id;
    private String username;
    private String email;
    private int age;
    
    @BsonProperty("created_at")
    private Date createdAt;
    
    private List<String> hobbies;
    private Address address;
    
    // 构造方法
    public User() {}
    
    public User(String username, String email, int age) {
        this.username = username;
        this.email = email;
        this.age = age;
        this.createdAt = new Date();
    }
    
    // getters 和 setters
    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    public List<String> getHobbies() { return hobbies; }
    public void setHobbies(List<String> hobbies) { this.hobbies = hobbies; }
    
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    
    @Override
    public String toString() {
        return String.format("User[id=%s, username=%s, email=%s, age=%d]", 
                id, username, email, age);
    }
}

// 嵌套地址类
class Address {
    private String city;
    private String street;
    private String zipCode;
    
    public Address() {}
    
    public Address(String city, String street, String zipCode) {
        this.city = city;
        this.street = street;
        this.zipCode = zipCode;
    }
    
    // getters 和 setters
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    
    @Override
    public String toString() {
        return String.format("Address[city=%s, street=%s, zip=%s]", 
                city, street, zipCode);
    }
}