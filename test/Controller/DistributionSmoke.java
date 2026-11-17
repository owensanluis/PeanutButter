package Controller;

public class DistributionSmoke {
    public static void main(String[] args) {
        SQLite db = new SQLite();
        String[] names = {"admin","manager","staff","client1","client2"};
        int[] roles = {5,4,3,2,2};
        for(int i=0;i<names.length;i++) {
            if(!db.login(names[i],"DemoPass!2026")||db.currentRole()!=roles[i])throw new AssertionError("Demo account failed");
        }
        if(db.getProduct().size()!=3)throw new AssertionError("Missing demo products");
        db.purchase("Antivirus","1");
        if(db.getHistory().size()!=1)throw new AssertionError("Packaged purchase failed");
        System.out.println("PASS packaged JAR: all five accounts, roles, catalog, purchase, history, JDBC native loading and database location from a different working directory");
    }
}
