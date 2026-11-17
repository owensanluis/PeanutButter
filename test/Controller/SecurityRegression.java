package Controller;

import Model.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import static Controller.AccessControl.Resource.*;

/** Standalone integration tests against disposable real SQLite databases. */
public class SecurityRegression {
    private static int passed;
    private static Path db;
    private static SQLite app;
    private static final String PASS = "DemoPass!2026";
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        passed++; System.out.println("PASS " + name);
    }
    private static void denied(Runnable action, String name) {
        try { action.run(); throw new AssertionError("Not rejected: " + name); }
        catch (AppException ex) { check(!ex.getMessage().contains("SQL") && !ex.getMessage().contains("Exception"), name); }
    }
    private static Object scalar(String sql, Object... args) throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db); PreparedStatement p=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);
            try(ResultSet r=p.executeQuery()){r.next();return r.getObject(1);}
        }
    }
    private static void execute(String sql, Object... args) throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);PreparedStatement p=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();
        }
    }
    private static void login(String name) {check(app.login(name,PASS),"login "+name);}
    private static void fixture() throws Exception {
        db=Files.createTempDirectory("security-tests-").resolve("test.db");app=new SQLite(db);
        for(String user:new String[]{"admin","manager","staff","client1","client2"})app.register(user,PASS,PASS);
        execute("UPDATE users SET role=5,previous_role=5 WHERE username='admin'");
        execute("UPDATE users SET role=4,previous_role=4 WHERE username='manager'");
        execute("UPDATE users SET role=3,previous_role=3 WHERE username='staff'");
    }
    public static void main(String[] args) throws Exception {
        fixture();
        denied(()->app.getProduct(),"anonymous catalog denied");
        denied(()->app.getUsers(),"anonymous users denied");
        denied(()->app.getLogs(),"anonymous logs denied");
        denied(()->app.getHistory(),"anonymous history denied");
        denied(()->app.authorize(null),"unknown resource denied");
        String hash=(String)scalar("SELECT password FROM users WHERE username='admin'");
        check(hash.startsWith("pbkdf2-sha256$600000$"),"versioned PBKDF2 storage");
        check(!hash.equals(scalar("SELECT password FROM users WHERE username='manager'")),"unique per-user salts");
        check(PasswordUtils.verify(PASS,hash) && !PasswordUtils.verify("Wrong!123",hash),"hash verification");
        check(!PasswordUtils.verify(PASS,"malformed"),"malformed hash fails closed");
        for(String user:new String[]{"ab","a b","abc' OR 1=1--"," leading","trailing ",String.join("",Collections.nCopies(33,"a"))})
            denied(()->app.register(user,PASS,PASS),"invalid username rejected: length="+user.length());
        for(String pass:new String[]{"Aa1!aaa","AA123456!","aa123456!","Abcdefgh!","Abcdefg12","Aa1! aaa",String.join("",Collections.nCopies(65,"a"))})
            denied(()->app.register("newuser",pass,pass),"invalid password rejected: length="+pass.length());
        denied(()->app.register("newuser",PASS,"different"),"confirmation mismatch rejected");
        app.register("minuser","Aa1!aaaa","Aa1!aaaa");
        String max="Aa1!"+String.join("",Collections.nCopies(60,"a"));app.register("maxuser",max,max);
        check(app.login("minuser","Aa1!aaaa")&&app.login("maxuser",max),"8 and 64 character password boundaries accepted");
        denied(()->app.register("client1",PASS,PASS),"duplicate registration rejected");
        check(!app.login("unknown",PASS),"unknown account login denied");
        check(!app.login("admin' OR 1=1--",PASS),"login injection denied");
        for(int i=1;i<=4;i++)check(!app.login("client2","Wrong!123"),"failed login "+i);
        check(((Number)scalar("SELECT role FROM users WHERE username='client2'")).intValue()==2,"not disabled before fifth failure");
        check(!app.login("client2","Wrong!123"),"fifth failure denied");
        check(((Number)scalar("SELECT role FROM users WHERE username='client2'")).intValue()==1,"exact fifth failure disables role");
        check(!app.login("client2",PASS),"correct password cannot bypass disabled role");
        check(!new SQLite(db).login("client2",PASS),"lockout persists across application restart");
        login("admin");app.toggleLock("client2");login("client2");
        check(((Number)scalar("SELECT failed_attempts FROM users WHERE username='client2'")).intValue()==0,"admin unlock resets failure counter");
        app.login("client2","Wrong!123");login("client2");
        check(app.getPreviousUse().contains("FAILURE"),"previous failed attempt status shown");
        login("client2");check(app.getPreviousUse().contains("SUCCESS"),"previous successful attempt status shown");
        check(((Number)scalar("SELECT failed_attempts FROM users WHERE username='client2'")).intValue()==0,"success resets consecutive failures");
        // Permission allowlist for all six states, including explicit denial to Admin business operations.
        for(int role=0;role<=5;role++)for(AccessControl.Resource resource:AccessControl.Resource.values()) {
            boolean expected=false;
            if(role>=2) switch(resource){
                case HOME_CLIENT: expected=role==2;break;case HOME_STAFF:expected=role==3;break;
                case HOME_MANAGER:expected=role==4;break;case HOME_ADMIN:expected=role==5;break;
                case PRODUCTS:expected=role<=4;break;case EDIT_PRODUCTS:expected=role==3||role==4;break;
                case PURCHASE:expected=role==2;break;case HISTORY:expected=role==2||role==4;break;
                case USERS:case LOGS:expected=role==5;break;case PASSWORD:expected=true;break;
            }
            if(AccessControl.allows(role,resource)!=expected)throw new AssertionError("ACL "+role+" "+resource);
        }
        check(true,"complete 6-state authorization matrix");
        login("staff");app.saveProduct(null,"Antivirus","5","500.00");
        denied(()->app.getHistory(),"staff cannot read history");denied(()->app.getUsers(),"staff cannot read users");
        denied(()->app.getLogs(),"staff cannot read logs");denied(()->app.purchase("Antivirus","1"),"staff cannot purchase");
        for(String quantity:new String[]{"-1","100001","1.5","999999999999"," 1","1 "})
            denied(()->app.saveProduct(null,"Invalid",quantity,"1.00"),"invalid stock rejected: "+quantity);
        for(String price:new String[]{"NaN","Infinity","0","-1","1000000.01","1.001","1e3"," 1"})
            denied(()->app.saveProduct(null,"Invalid","1",price),"invalid price rejected: "+price);
        denied(()->app.saveProduct(null,"<script>","1","1.00"),"product text injection rejected");
        denied(()->app.saveProduct(null,"Antivirus","1","1.00"),"duplicate product rejected");
        app.saveProduct(null,"Boundary","100000","1000000.00");check(app.getProduct().size()==2,"max stock and price accepted");
        login("client1");
        denied(()->app.saveProduct(null,"Exploit","1","1"),"client direct product mutation denied");
        denied(()->app.authorize(HOME_ADMIN),"client direct admin navigation denied");
        denied(()->app.setRole("client1","5"),"client privilege escalation denied");
        denied(()->app.clearLogs(),"client cannot clear logs");
        denied(()->app.purchase("Antivirus","0"),"zero purchase rejected");
        denied(()->app.purchase("Antivirus","6"),"overselling rejected");
        check(((Number)scalar("SELECT stock FROM product WHERE name='Antivirus'")).intValue()==5,"rejected purchase leaves stock unchanged");
        app.purchase("Antivirus","2");check(app.getHistory().size()==1,"purchase history created");
        check(((Number)scalar("SELECT stock FROM product WHERE name='Antivirus'")).intValue()==3,"purchase deducts exact quantity");
        login("client2");check(app.getHistory().isEmpty(),"client cannot read another customer's history");
        check(app.searchHistory("client1").isEmpty(),"search cannot bypass history ownership");
        app.purchase("Antivirus","1");
        login("manager");check(app.getHistory().size()==2,"manager reads all history");
        app.saveProduct("Antivirus","Renamed Product","2","900.00");app.removeProduct("Renamed Product");
        check(app.getHistory().get(0).getUnitPrice()==500.0,"history retains price after rename and deletion");
        denied(()->app.searchHistory("x' OR 1=1--"),"history injection rejected");
        denied(()->app.searchHistory(String.join("",Collections.nCopies(65,"a"))),"oversize search rejected");
        login("admin");
        denied(()->app.getProduct(),"admin is not business superuser");denied(()->app.getHistory(),"admin cannot read purchases");
        denied(()->app.saveProduct(null,"Exploit","1","1"),"admin cannot manage products");
        denied(()->app.setRole("staff","6"),"out of range role rejected");
        denied(()->app.setRole("staff","03"),"noncanonical role rejected");
        denied(()->app.removeUser("admin"),"self deletion denied");
        check(app.getUsers().get(0).getPassword().equals("********"),"credential material not exposed in user list");
        SQLite other = new SQLite(db);check(other.login("staff",PASS),"second session established");
        app.setRole("staff","4");denied(()->other.getProduct(),"role change revokes existing session");
        app.setRole("staff","3");
        denied(()->app.changePassword("client1","Wrong!123","NewPass!2026","NewPass!2026"),"password reset requires current admin password");
        check(!app.isSignedIn(),"failed reauthentication clears session");login("client1");
        denied(()->app.changePassword("client2",PASS,"NewPass!2026","NewPass!2026"),"client cannot reset another password");
        app.changePassword("client1",PASS,"NewPass!2026","NewPass!2026");check(!app.isSignedIn(),"self password change logs out");
        check(!app.login("client1",PASS)&&app.login("client1","NewPass!2026"),"only new password authenticates");
        login("admin");app.changePassword("client1",PASS,PASS,PASS);login("client1");
        // Prove rollback if history insertion fails AFTER stock update.
        login("staff");app.saveProduct(null,"Atomic","2","10.00");login("client1");
        execute("CREATE TRIGGER fail_history BEFORE INSERT ON history BEGIN SELECT RAISE(ABORT,'test failure'); END");
        denied(()->app.purchase("Atomic","1"),"database failure reported generically");
        check(!app.isSignedIn(),"database failure clears session");
        check(((Number)scalar("SELECT stock FROM product WHERE name='Atomic'")).intValue()==2,"transaction rolls back stock on history failure");
        execute("DROP TRIGGER fail_history");
        // Concurrent users cannot sell more stock than available.
        login("staff");app.saveProduct("Atomic","Atomic","1","10.00");
        SQLite a=new SQLite(db), b=new SQLite(db);a.login("client1",PASS);b.login("client2",PASS);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> buyA=()->{start.await();try{a.purchase("Atomic","1");return true;}catch(AppException ex){return false;}};
        Callable<Boolean> buyB=()->{start.await();try{b.purchase("Atomic","1");return true;}catch(AppException ex){return false;}};
        Future<Boolean> fa=pool.submit(buyA),fb=pool.submit(buyB);start.countDown();int successes=(fa.get()?1:0)+(fb.get()?1:0);pool.shutdown();
        check(successes==1 && ((Number)scalar("SELECT stock FROM product WHERE name='Atomic'")).intValue()==0,"concurrent purchase prevents overselling");
        login("admin");
        for(String event:new String[]{"AUTH_SUCCESS","AUTH_FAILURE","ACCESS_VIOLATION","VALIDATION_FAILURE"})
            check(app.getLogs().stream().anyMatch(l->l.getEvent().equals(event)&&l.getTimestamp()!=null),"timestamped log "+event);
        check(app.getLogs().stream().noneMatch(l->l.getDesc().contains(PASS)),"audit log excludes passwords");
        app.clearLogs();check(app.getLogs().size()==1&&app.getLogs().get(0).getEvent().equals("LOG_CLEAR"),"log clearing leaves accountability record");
        app.register("deleteuser",PASS,PASS);app.removeUser("deleteuser");check(!app.login("deleteuser",PASS),"deleted account denied");
        login("admin");app.removeUser("client2");app.register("client2",PASS,PASS);login("client2");
        check(app.getHistory().isEmpty(),"recreated username cannot inherit deleted account history");
        check(!new SQLite(db).login("client2", "Wrong!123"),"failed login with fresh service");
        login("admin");app.toggleLock("staff");
        check(!app.login("staff",PASS),"administrator can disable staff");login("admin");app.toggleLock("staff");login("staff");
        check(app.currentRole()==3,"unlock restores staff role");
        // UI panels can be constructed and populated without a display server.
        SwingUtilities.invokeAndWait(()->{
            try {
                View.Login login=new View.Login();View.Register register=new View.Register();
                for(Object[] field:new Object[][]{{login,"passwordFld"},{register,"passwordFld"},{register,"confpassFld"}}) {
                    java.lang.reflect.Field f=field[0].getClass().getDeclaredField((String)field[1]);f.setAccessible(true);
                    check(f.get(field[0]) instanceof JPasswordField,"masked UI "+field[1]);
                }
                app.login("admin",PASS);View.AdminHome home=new View.AdminHome();home.init(app);home.showPnl("mgmtUser");home.showPnl("mgmtLogs");
                denied(()->home.showPnl("mgmtProduct"),"admin panel cannot show products");home.clearData();
                app.login("client1",PASS);View.ClientHome client=new View.ClientHome();client.init(app);client.showPnl("mgmtProduct");client.showPnl("mgmtHistory");
                denied(()->client.showPnl("mgmtLogs"),"client panel cannot show logs");
                app.login("staff",PASS);View.StaffHome staff=new View.StaffHome();staff.init(app);staff.showPnl("mgmtProduct");
                denied(()->staff.showPnl("mgmtHistory"),"staff panel cannot show history");
                app.login("manager",PASS);View.ManagerHome manager=new View.ManagerHome();manager.init(app);manager.showPnl("mgmtProduct");manager.showPnl("mgmtHistory");
                check(true,"all four Swing home panels populate");
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
        // Migration handles an old database with a partially added column.
        Path legacy=db.getParent().resolve("legacy.db");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+legacy);PreparedStatement p=c.prepareStatement("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT UNIQUE,password TEXT,role INTEGER,locked INTEGER,failed_attempts INTEGER DEFAULT 0)")){p.executeUpdate();}
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+legacy);PreparedStatement p=c.prepareStatement("INSERT INTO users VALUES(1,'legacy','plaintext',5,0,0)")){p.executeUpdate();}
        SQLite migrated=new SQLite(legacy);check(!migrated.login("legacy","plaintext"),"legacy plaintext credentials invalidated");
        new SQLite(legacy);check(true,"schema migration is idempotent");
        // Runtime read failure must revoke an already authenticated session.
        login("client1");execute("ALTER TABLE users RENAME TO unavailable_users");
        denied(()->app.getProduct(),"authorization database error fails closed");check(!app.isSignedIn(),"authorization error revokes session");
        check(!app.login("client1",PASS),"database login failure returns generic authentication denial");
        System.out.println("RESULT: "+passed+" checks passed.");
    }
}
