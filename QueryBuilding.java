import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class QueryBuilding {
    public static void main(String[] args) {
        String url = "jdbc:mysql://60.205.13.156:3306/bi?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
        String user = "root";
        String password = "QwErTy1234%^&*()_+|";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            Statement stmt = conn.createStatement();
            
            System.out.println("--- CTE Query ---");
            String query = "WITH RECURSIVE id_tree AS ( " +
                    "SELECT t.id, t.parent_id, 1 AS level " +
                    "FROM bi_object t " +
                    "WHERE t.id = 1034167 " +
                    "AND t.del_flag = '0' " +
                    "UNION ALL " +
                    "SELECT c.id, c.parent_id, p.level + 1 " +
                    "FROM bi_object c " +
                    "JOIN id_tree p ON c.parent_id = p.id " +
                    "WHERE c.del_flag = '0' AND p.level < 4 " +
                    ") " +
                    "SELECT o.id, o.name, o.parent_id, o.order_num, tobj.weight AS standard_weight " +
                    "FROM bi_object o " +
                    "LEFT JOIN bi_template_object tobj ON o.template_object_id = tobj.id " +
                    "JOIN id_tree t ON o.id = t.id " +
                    "ORDER BY o.order_num;";
            
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                System.out.println("ID: " + rs.getString("id") + ", Parent: " + rs.getString("parent_id") + ", Name: " + rs.getString("name"));
            }
            rs.close();

            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
