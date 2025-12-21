import java.util.*;

class Student{
    private String name;
    private int score;
    public Student(String name, int score){
        this.name=name;
        this.score=score;
    }
    public String getName(){
        return this.name;
    }
    public int getScore(){
        return this.score;
    }
    public void setName(String name){
        this.name=name;
    }
    public void setScore(int score){
        this.score=score;
    }
    @Override
    public String toString(){
        return "Studen{姓名=‘"+name+"’ 成绩="+score+"}";
    }
}
public class testArrayList{
    public static void main(String[] args){
        ArrayList<Student> studentList=new ArrayList<>();

        studentList.add(new Student("张三",93));
        studentList.add(new Student("李四",83));
        studentList.add(new Student("王五",45));
        studentList.add(new Student("赵六",88));
        System.out.println("初始列表:\n"+studentList);

        Student stu=studentList.get(0);
        System.out.println("随机访问：\n"+stu);

        studentList.set(1,new Student("李斯",89));
        System.out.println("修改李四："+studentList);

        System.out.println("遍历列表");
        for (int i = 0; i < studentList.size(); i++) {
            System.out.println(studentList.get(i));
        }

        System.out.println("删除低于90分的学生");
        for (int i = 0; i < studentList.size(); i++) {
            if(studentList.get(i).getScore()<90)
                studentList.remove(i--);
        }
        System.out.println("删除后的列表：" + studentList);
        System.out.println("列表长度：" + studentList.size());

    }

}