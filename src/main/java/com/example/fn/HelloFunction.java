package com.example.fn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import oracle.jdbc.OracleDriver;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HelloFunction {

    File walletDir = new File("/tmp", "wallet");

    public String handleRequest(String input) {
        System.setProperty("oracle.jdbc.fanEnabled", "false");
        String name = (input == null || input.isEmpty()) ? "world"  : input;

        String dbUser = System.getenv().get("DB_USER");
        String dbPassword = System.getenv().get("DB_PASSWORD");
        String dbUrl = System.getenv().get("DB_URL");

        if( needWalletDownload() ) {
            System.out.println("start wallet download...");
            downloadWallet();
            System.out.println("end wallet download...");
        }
        ResultSet resultSet = null;

        try {
            DriverManager.registerDriver(new OracleDriver());
            Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPassword);
            Statement st = con.createStatement();
            resultSet = st.executeQuery("select * from employees");
            List<HashMap<String, Object>> recordList = convertResultSetToList(resultSet);
            System.out.println( new ObjectMapper().writeValueAsString(recordList) );
            System.out.println("***");
            con.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        return "Hello, " + name + "!";
    }

    private List<HashMap<String,Object>> convertResultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>();

        while (rs.next()) {
            HashMap<String,Object> row = new HashMap<String, Object>(columns);
            for(int i=1; i<=columns; ++i) {
                row.put(md.getColumnName(i),rs.getObject(i));
            }
            list.add(row);
        }

        return list;
    }

    private Boolean needWalletDownload() {
        if( walletDir.exists() ) {
            System.out.println("Wallet exists, don't download it again...");
            return false;
        }
        else {
            System.out.println("Didn't find a wallet, let's download one...");
            boolean dirCreated = walletDir.mkdirs();
            System.out.println(dirCreated);
            return true;
        }
    }

    private void downloadWallet() {
        // create directory

        // Use Resource Principal
        final ResourcePrincipalAuthenticationDetailsProvider provider =
                ResourcePrincipalAuthenticationDetailsProvider.builder().build();

        ObjectStorage client = new ObjectStorageClient(provider);
        client.setRegion(Region.US_PHOENIX_1);
        System.out.println("Listing all objects...");
        // List all objects in wallet bucket
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .namespaceName("toddrsharp")
                .bucketName("wallet")
                .build();
        ListObjectsResponse listObjectsResponse = client.listObjects(listObjectsRequest);
        System.out.println("Listed all objects...");

        // Iterate over each wallet file, downloading it to the Function's Docker container
        listObjectsResponse.getListObjects().getObjects().stream().forEach(objectSummary -> {
            System.out.println("Looping... Current object... " + objectSummary.getName());

            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .namespaceName("toddrsharp")
                    .bucketName("wallet")
                    .objectName(objectSummary.getName())
                    .build();
            GetObjectResponse objectResponse = client.getObject(objectRequest);

            try {
                File f = new File(walletDir + "/" + objectSummary.getName());
                FileUtils.copyToFile( objectResponse.getInputStream(), f );
                System.out.println(f.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}