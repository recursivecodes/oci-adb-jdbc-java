# oci-adb-jdbc-java

* Create Application

```bash
fn create app oci-adb-jdbc-java-app --annotation oracle.com/oci/subnetIds='["ocid1.subnet.oc1.phx.aaaaaaaaxi5jl3qf7weahirffrn6ttv2qjnagomwjvm367fcqocfmu6de5qa"]'
```

* Create Function

```bash
fn init --runtime java oci-adb-jdbc-java
```

* Move into fn directory

```bash
cd oci-adb-jdbc-java
```

* Create Config

```bash
fn config app oci-adb-jdbc-java-app DB_PASSWORD [password]
fn config app oci-adb-jdbc-java-app DB_URL jdbc:oracle:thin:\@barnevents_low\?TNS_ADMIN=/tmp/wallet 
fn config app oci-adb-jdbc-java-app DB_USER [user]
```

* Download Wallet

```bash
oci db autonomous-data-warehouse generate-wallet --autonomous-data-warehouse-id ocid1.autonomousdatabase.oc1.phx... --password Str0ngPa$$word1 --file /projects/fn/oci-adb-jdbc-java/wallet.zip
```

* Unzip wallet, upload contents to ** private ** bucket.

* Create Dynamic Group

```
ALL{resource.type='fnfunc', resource.compartment.id='ocid1.compartment.xxxxx'}
```

* Dynamic Group Policy

```
Allow dynamic-group functions-dynamic-group to read buckets in compartment demo-compartment
```

* Add dependencies to `pom.xml`:

```xml
<dependency>
    <groupId>com.oracle.ojdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>19.3.0.0</version>
</dependency>
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-full</artifactId>
    <version>1.12.5</version>
</dependency>
<dependency>
    <groupId>javax.activation</groupId>
    <artifactId>activation</artifactId>
    <version>1.1.1</version>
</dependency>
```

* Deploy

```bash
fn deploy --app oci-adb-jdbc-java-app
```

* Invoke

```bash
fn invoke oci-adb-jdbc-java-app oci-adb-jdbc-java
```