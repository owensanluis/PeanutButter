# SECURITY Svcs 

## Run on Windows

1. Extract the entire submission ZIP to a writable folder.
2. Install a Java runtime (JDK 17 or 21 is suitable; JDK is also needed for building).
3. Open `dist` and run `run.bat`, or open a terminal there and run `java -jar CSSECDV.jar`.
4. Keep `CSSECDV.jar`, `database.db`, and `lib/sqlite-jdbc-3.23.1.jar` together in `dist`. The JAR resolves its database beside itself even when launched from another working folder.

On Linux, use `sh dist/run.sh`. The runtime must support the bundled SQLite JDBC native library. Linux x86-64 / OpenJDK 21 was tested for service and Swing-panel integration; Windows desktop and NetBeans GUI execution must still be checked on your machine.

## Demo accounts — already present

| Username | Password | Role |
|---|---|---|
| admin | DemoPass!2026 | 5 — Administrator |
| manager | DemoPass!2026 | 4 — Manager |
| staff | DemoPass!2026 | 3 — Staff |
| client1 | DemoPass!2026 | 2 — Client |
| client2 | DemoPass!2026 | 2 — Client |

These are public, disposable classroom credentials. Every account has a different salted password hash. The database contains Antivirus, Firewall, and Scanner products, and starts with empty purchase history and logs. The original insecure demo database was replaced with this fresh fixture; no real customer records are included. Normal startup does not recreate accounts or reset passwords.

## Where each role works

- Client: purchase products; read/search only that account's purchase history.
- Staff: add, edit and delete products.
- Manager: add, edit and delete products; read/search everyone's purchase history.
- Administrator: view users, change their roles, disable/re-enable them, reset passwords after entering the administrator's current password, delete users, and view/clear logs. Administrator is not a business superuser.
- All active users: change their own password using the top menu; current password is mandatory. Self password change signs out all sessions for the account.
- A disabled account cannot sign in, including with the correct password. An administrator must verify the person offline and re-enable the account through Users. Automatic lockout does not expire.

## Build and test

Open the folder containing `build.xml` in NetBeans as an existing Java project. Select an installed JDK and confirm the SQLite JAR under Project Properties > Libraries. Clean and Build, then Run. NetBeans runs against the project-root `database.db`; the executable JAR uses `dist/database.db`. They are independent copies.

Alternatively, with JDK tools on PATH:

```bat
build.bat test
```

```sh
sh build.sh test
```

Builds preserve an existing `dist/database.db`. A NetBeans Clean removes `dist`; back up any demo activity you want to keep before Clean and Build. The test suite uses temporary databases, never the supplied demo database. See `docs/test-results.txt` for the recorded 140-check passing run. The source targets Java 8 bytecode and was compiled with OpenJDK 21; Java 8 itself was not tested.

To reset only a disposable demo, close the application and replace `dist/database.db` with an untouched copy from the submission. Do not run tests against a real database. `tools/DemoSetup.java` is an offline fixture generator that refuses existing paths and is excluded from the application JAR.
