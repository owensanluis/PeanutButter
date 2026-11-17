SECURITY Svcs standalone distribution

Keep CSSECDV.jar, database.db and lib/sqlite-jdbc-3.23.1.jar together.
Use a writable folder. Launch run.bat (Windows), sh run.sh (Linux), or:
    java -jar CSSECDV.jar
A Java runtime is required; NetBeans and a separate SQLite installation are not.

Demo users: admin / manager / staff / client1 / client2
Demo password for all five: DemoPass!2026
Each account uses a distinct salted hash. Classroom fixtures only.

Database changes persist beside the JAR. Close the app before restoring a
fresh demo database. Role 1 accounts require Administrator re-enablement.
See ../README.md and ../docs/Security_Documentation.pdf for details.
