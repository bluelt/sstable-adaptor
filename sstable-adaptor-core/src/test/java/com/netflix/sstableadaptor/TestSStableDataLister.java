/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.sstableadaptor;


import com.netflix.sstableadaptor.sstable.CasspactorIterator;
import com.netflix.sstableadaptor.sstable.CasspactorSSTableReader;
import com.netflix.sstableadaptor.util.SSTableUtils;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.db.rows.Cell;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.apache.cassandra.io.sstable.ISSTableScanner;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *  Tests on reading local sstable and s3 sstable files.
 */
public class TestSStableDataLister extends TestBaseSSTableFunSuite {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestSStableDataLister.class);

    /**
     * Setting up resources prior to running any tests.
     * @throws Exception when we cannot initialize the resources
     */
    @BeforeClass
    public static void setup() throws Exception {
        LOGGER.info("Running TestSStableDataLister setup ...");
        TestBaseSSTableFunSuite.setup();
    }

    /**
     * Tear down resources after all tests.
     * @throws Exception when teardown has an issue
     */
    @AfterClass
    public static void teardown() throws Exception {
        LOGGER.info("Tearing TestSStableDataLister down ...");
        TestBaseSSTableFunSuite.teardown();
    }

    /**
     *  This test works on the sstable file.
     *      location: /src/test/resources/data/bills_compress/mc-6-big-Data.db
     *
     *   The corresponding table definition is :
     *       CREATE TABLE bills_compress (
     *          user text,
     *          balance int static,
     *          expense_id int,
     *          amount int,
     *          name text,
     *          PRIMARY KEY (user, expense_id)
     *       )
     *
     */
    @Test
    public void testOnLocalDataSimplePartitionKey() {
        final String inputSSTableFullPathFileName = DATA_DIR + "bills_compress/mc-6-big-Data.db";
        final int counter = getRowCount(inputSSTableFullPathFileName);

        LOGGER.info("\nCounter: " + counter);
        Assert.assertEquals(4, counter);
    }

    /**
     *  This test works on the sstable file.
     *      location: S3_INPUT_DIR/mc-6-big-Data.db
     *
     *   The corresponding table definition is :
     *       CREATE TABLE bills_compress (
     *          user text,
     *          balance int static,
     *          expense_id int,
     *          amount int,
     *          name text,
     *          PRIMARY KEY (user, expense_id)
     *       )
     *   Also needs to run S3 proxy to work around the credential setting
     */
    @Test
    public void testOnS3DataSimplePartitionKey() {
        if (S3_INPUT_DIR == null) {
            LOGGER.info("Skip this test as there is no setting for S3_INPUT_DIR");
            return;
        }

        final String inputSSTableFullPathFileName = S3_INPUT_DIR + "/mc-6-big-Data.db";
        LOGGER.info("Processing on file at: " + inputSSTableFullPathFileName);

        final int counter = getRowCount(inputSSTableFullPathFileName);

        LOGGER.info("\nCounter: " + counter);
        Assert.assertEquals(4, counter);
    }


    /**
     *  Test on a local data with composite partition key.
     *  CREATE TABLE compressed_bills (
     *       user text,
     *       email text,
     *       account_id text static,
     *       balance int static,
     *       expense_id int,
     *       item_id int,
     *       amount int,
     *       name text,
     *   PRIMARY KEY ((user, email), expense_id, item_id))
     */
    @Test
    public void testOnLocalDataCompositePartitionKey() {
        final String inputSSTableFullPathFileName = DATA_DIR + "compressed_bills/mc-2-big-Data.db";
        final int counter = getRowCount(inputSSTableFullPathFileName);

        LOGGER.info("\nCounter: " + counter);
        Assert.assertEquals(16, counter);
    }

    private int getRowCount(final String inputSSTableFullPathFileName) {
        LOGGER.info("Input file name: " + inputSSTableFullPathFileName);
        int counter = 0;

        try {
            final CasspactorSSTableReader casspactorSsTableReader =
                        new CasspactorSSTableReader(inputSSTableFullPathFileName);
            final ISSTableScanner currentScanner =
                        casspactorSsTableReader.getSSTableScanner(Long.MIN_VALUE, Long.MAX_VALUE);

            while (currentScanner.hasNext()) {
                LOGGER.info("===================111==================================");
                final UnfilteredRowIterator unfilteredRowIterator = currentScanner.next();
                final ByteBuffer partitionKey = unfilteredRowIterator.partitionKey().getKey();
                final CFMetaData cfMetaData = casspactorSsTableReader.getCfMetaData();
                LOGGER.info("Partition key: " + new String(unfilteredRowIterator.partitionKey().getKey().array()));

                final List<Object> list = SSTableUtils.parsePrimaryKey(cfMetaData, partitionKey);
                Assert.assertEquals(cfMetaData.partitionKeyColumns().size(), list.size());
                for (Object val : list) {
                    LOGGER.info("Partition key val ::::: " + val);
                }

                LOGGER.info("\tStatic: " + unfilteredRowIterator.staticRow());

                final Row staticRow = unfilteredRowIterator.staticRow();
                LOGGER.info("static info: " + staticRow.isStatic());

                while (unfilteredRowIterator.hasNext()) {
                    LOGGER.info("\t---------------2222---------------------------------");
                    final Row row = (Row) unfilteredRowIterator.next();

                    LOGGER.info("\tClustering: " + ByteBufferUtil.toInt(row.clustering().get(0)));

                    final Iterable<Cell> cells = row.cells();
                    final Iterator<Cell> cellsIterator = cells.iterator();
                    LOGGER.info("\tCells: ");
                    while (cellsIterator.hasNext()) {
                        final Cell cell = cellsIterator.next();
                        LOGGER.info("Type: " + cell.column().type);
                        LOGGER.info("\t\t" + cell.toString());
                    }
                    LOGGER.info("\t------------------------------------------------");
                    counter++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return -1;
        }

        return counter;
    }

    /**
     * Test on the CasspactorIterator.
     * @throws IOException
     */
    @Test
    public void testCasspactorIterator() throws IOException {
        final String inputSSTableFullPathFileName = DATA_DIR + "bills_compress/mc-6-big-Data.db";
        final CasspactorSSTableReader reader1 = new CasspactorSSTableReader(inputSSTableFullPathFileName);
        final CasspactorSSTableReader reader2 = new CasspactorSSTableReader(inputSSTableFullPathFileName);
        final CFMetaData cfMetaData = reader1.getCfMetaData();

        final List<ISSTableScanner> scanners = new ArrayList<>();
        final int nowInSecs = (int) (System.currentTimeMillis() / 1000);

        scanners.add(reader1.getSSTableScanner());
        scanners.add(reader2.getSSTableScanner());

        int counter = 0;
        try (CasspactorIterator ci = new CasspactorIterator(scanners, reader1.getCfMetaData(), nowInSecs)) {
            while (ci.hasNext()) {

                LOGGER.info("=====================================================");
                final UnfilteredRowIterator unfilteredRowIterator = ci.next();
                final ByteBuffer partitionKey = unfilteredRowIterator.partitionKey().getKey();

                LOGGER.info("Partition key: " + new String(unfilteredRowIterator.partitionKey().getKey().array()));

                final List<Object> list = SSTableUtils.parsePrimaryKey(cfMetaData, partitionKey);
                Assert.assertEquals(cfMetaData.partitionKeyColumns().size(), list.size());
                for (Object val : list) {
                    LOGGER.info("Partition key: " + val);
                }

                LOGGER.info("\tStatic: " + unfilteredRowIterator.staticRow());
                final Row staticRow = unfilteredRowIterator.staticRow();
                LOGGER.info("static info: " + staticRow.isStatic());

                while (unfilteredRowIterator.hasNext()) {
                    LOGGER.info("\t-----------------------------------------------");
                    final Row row = (Row) unfilteredRowIterator.next();
                    LOGGER.info("\tClustering: " + ByteBufferUtil.toInt(row.clustering().get(0)));

                    final Iterable<Cell> cells = row.cells();
                    final Iterator<Cell> cellsIterator = cells.iterator();
                    LOGGER.info("\tCells: ");
                    while (cellsIterator.hasNext()) {
                        final Cell cell = cellsIterator.next();
                        LOGGER.info("Type: " + cell.column().type);
                        LOGGER.info("\t\t" + cell.toString());
                    }

                    LOGGER.info("\t------------------------------------------------");
                    counter++;
                }
            }
        }

        Assert.assertEquals(4, counter);
    }

}