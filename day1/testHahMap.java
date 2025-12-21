import java.util.*;

public class testHahMap {
    public static void main(String[] args) {
        HashMap<String, Integer> scoreMap = new HashMap<>();

        scoreMap.put("2025001", 90); // 学号2025001，成绩90
        scoreMap.put("2025002", 85);
        scoreMap.put("2025002", 88); // 重复键，覆盖原有值（85→88）
        scoreMap.put("2025003", 95);
        System.out.println("初始映射：" + scoreMap);

        String targetId = "2025001";
        Integer score = scoreMap.get(targetId);
        if (score != null) {
            System.out.println("\n学号" + targetId + "的成绩：" + score);
        } else {
            System.out.println("\n学号" + targetId + "不存在");
        }

        scoreMap.put("2025003", 98);
        System.out.println("\n修改后映射：" + scoreMap);

        System.out.println("\n遍历元素");
        Set<String> keySet = scoreMap.keySet(); // 获取所有键的集合
        for (String id : keySet) {
            System.out.println("学号：" + id + "，成绩：" + scoreMap.get(id));
        }

        scoreMap.remove("2025002");
        System.out.println("\n删除学号2025002后：" + scoreMap);

        System.out.println("\n是否包含学号2025001：" + scoreMap.containsKey("2025001"));
        System.out.println("\n映射大小：" + scoreMap.size());
        scoreMap.clear();
        System.out.println("清空后映射：" + scoreMap);
    }
}
