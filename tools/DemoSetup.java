import Controller.SQLite;
import Controller.PasswordUtils;
import java.nio.file.*;
import java.sql.*;

/** Offline fixture generator, deliberately excluded from the executable application JAR. */
public class DemoSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || Files.exists(Paths.get(args[0]))) {
            throw new IllegalArgumentException("Supply one NEW database path; existing files are never overwritten.");
        }
        Path path = Paths.get(args[0]);
        new SQLite(path);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
            c.setAutoCommit(false);
            String[] names = {"admin", "manager", "staff", "client1", "client2"};
            int[] roles = {5,4,3,2,2};
            for (int i=0;i<names.length;i++) {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO users(username,password,role,previous_role) VALUES(?,?,?,?)")) {
                    p.setString(1,names[i]); p.setString(2,PasswordUtils.hash("DemoPass!2026"));
                    p.setInt(3,roles[i]); p.setInt(4,roles[i]); p.executeUpdate();
                }
            }
            try (PreparedStatement p=c.prepareStatement("INSERT INTO product(name,stock,price) VALUES(?,?,?)")) {
                String[] names2={"Antivirus","Firewall","Scanner"}; int[] stocks={5,3,10}; double[] prices={500,1000,100};
                for(int i=0;i<3;i++){p.setString(1,names2[i]);p.setInt(2,stocks[i]);p.setDouble(3,prices[i]);p.executeUpdate();}
            }
            c.commit();
        }
        System.out.println("Fresh demo database created; credentials are in README.md.");
    }
}
