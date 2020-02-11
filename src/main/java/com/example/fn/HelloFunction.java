package com.example.fn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HelloFunction {

    private PoolDataSource poolDataSource;

    private final File walletDir = new File("/tmp", "wallet");
    private final String namespace = System.getenv().get("NAMESPACE");
    private final String bucketName = System.getenv().get("BUCKET_NAME");
    private final String dbUser = System.getenv().get("DB_USER");
    private final String dbPassword = System.getenv().get("DB_PASSWORD");
    private final String dbUrl = System.getenv().get("DB_URL");

    final static String CONN_FACTORY_CLASS_NAME="oracle.jdbc.pool.OracleDataSource";

    public HelloFunction() {
        System.out.println("Setting up pool data source");
        poolDataSource = PoolDataSourceFactory.getPoolDataSource();
        try {
            poolDataSource.setConnectionFactoryClassName(CONN_FACTORY_CLASS_NAME);
            poolDataSource.setURL(dbUrl);
            poolDataSource.setUser(dbUser);
            poolDataSource.setPassword(dbPassword);
            poolDataSource.setConnectionPoolName("UCP_POOL");
            poolDataSource.setInitialPoolSize(1);
            poolDataSource.setMinPoolSize(1);
            poolDataSource.setMaxPoolSize(1);
        }
        catch (SQLException e) {
            System.out.println("Pool data source error!");
            e.printStackTrace();
        }
        System.out.println("Pool data source setup...");
    }

    public List handleRequest(String input) throws SQLException, JsonProcessingException {
        System.setProperty("oracle.jdbc.fanEnabled", "false");
        String name = (input == null || input.isEmpty()) ? "world"  : input;

        if( needWalletDownload() ) {
            System.out.println("Start wallet download...");
            downloadWallet();
            System.out.println("End wallet download!");
        }
        Connection conn = poolDataSource.getConnection();
        conn.setAutoCommit(false);

        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from employees");
        List<HashMap<String, Object>> recordList = convertResultSetToList(resultSet);
        System.out.println( new ObjectMapper().writeValueAsString(recordList) );
        System.out.println("***");

        conn.close();

        return recordList;
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
            walletDir.mkdirs();
            return true;
        }
    }

    private void downloadWallet() {
        // Use Resource Principal
        final ResourcePrincipalAuthenticationDetailsProvider provider =
                ResourcePrincipalAuthenticationDetailsProvider.builder().build();

        ObjectStorage client = new ObjectStorageClient(provider);
        client.setRegion(Region.US_PHOENIX_1);

        System.out.println("Retrieving a list of all objects in /" + namespace + "/" + bucketName + "...");
        // List all objects in wallet bucket
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .build();
        ListObjectsResponse listObjectsResponse = client.listObjects(listObjectsRequest);
        System.out.println("List retrieved. Starting download of each object...");

        // Iterate over each wallet file, downloading it to the Function's Docker container
        listObjectsResponse.getListObjects().getObjects().stream().forEach(objectSummary -> {
            System.out.println("Downloading wallet file: [" + objectSummary.getName() + "]");

            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectSummary.getName())
                    .build();
            GetObjectResponse objectResponse = client.getObject(objectRequest);

            try {
                File f = new File(walletDir + "/" + objectSummary.getName());
                FileUtils.copyToFile( objectResponse.getInputStream(), f );
                System.out.println("Stored wallet file: " + f.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}