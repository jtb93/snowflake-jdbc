/*
 * Copyright (c) 2012-2020 Snowflake Computing Inc. All rights reserved.
 */
package net.snowflake.client.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.snowflake.client.category.TestCategoryArrow;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Completely compare json and arrow resultSet behaviors */
@RunWith(Parameterized.class)
@Category(TestCategoryArrow.class)
public class ResultSetJsonVsArrowMultiTZIT extends BaseJDBCTest {
  @Parameterized.Parameters(name = "format={0}, tz={1}")
  public static Collection<Object[]> data() {
    // all tests in this class need to run for both query result formats json and arrow
    String[] timeZones = new String[] {"UTC", "America/New_York", "Asia/Singapore"};
    String[] queryFormats = new String[] {"json", "arrow"};
    List<Object[]> ret = new ArrayList<>();
    for (String queryFormat : queryFormats) {
      for (String timeZone : timeZones) {
        ret.add(new Object[] {queryFormat, timeZone});
      }
    }
    return ret;
  }

  private final String queryResultFormat;
  private final String tz;

  public static Connection getConnection(int injectSocketTimeout) throws SQLException {
    Connection connection = BaseJDBCTest.getConnection(injectSocketTimeout);

    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "alter session set "
              + "TIMEZONE='America/Los_Angeles',"
              + "TIMESTAMP_TYPE_MAPPING='TIMESTAMP_LTZ',"
              + "TIMESTAMP_OUTPUT_FORMAT='DY, DD MON YYYY HH24:MI:SS TZHTZM',"
              + "TIMESTAMP_TZ_OUTPUT_FORMAT='DY, DD MON YYYY HH24:MI:SS TZHTZM',"
              + "TIMESTAMP_LTZ_OUTPUT_FORMAT='DY, DD MON YYYY HH24:MI:SS TZHTZM',"
              + "TIMESTAMP_NTZ_OUTPUT_FORMAT='DY, DD MON YYYY HH24:MI:SS TZHTZM'");
    }
    return connection;
  }

  public ResultSetJsonVsArrowMultiTZIT(String queryResultFormat, String timeZone) {
    this.queryResultFormat = queryResultFormat;
    System.setProperty("user.timezone", timeZone);
    tz = timeZone;
  }

  private Connection init(String table, String column, String values) throws SQLException {
    Connection con = getConnection(BaseJDBCTest.DONT_INJECT_SOCKET_TIMEOUT);
    try (Statement statement = con.createStatement()) {
      statement.execute("alter session set jdbc_query_result_format = '" + queryResultFormat + "'");
      statement.execute("create or replace table " + table + " " + column);
      statement.execute("insert into " + table + " values " + values);
    }
    return con;
  }

  @Test
  public void testTime() throws SQLException {
    String[] times = {
      "00:01:23",
      "00:01:23.1",
      "00:01:23.12",
      "00:01:23.123",
      "00:01:23.1234",
      "00:01:23.12345",
      "00:01:23.123456",
      "00:01:23.1234567",
      "00:01:23.12345678",
      "00:01:23.123456789"
    };
    for (int scale = 0; scale <= 9; scale++) {
      testTimeWithScale(times, scale);
    }
  }

  @Test
  public void testDate() throws Exception {
    String[] cases = {
      "2017-01-01",
      "2014-01-02",
      "2014-01-02",
      "1970-01-01",
      "1970-01-01",
      "1969-12-31",
      "0200-02-27",
      "0200-02-28",
      "0000-01-01",
      "0001-12-31"
    };
    String table = "test_arrow_date";

    String column = "(a date)";

    String values = "('" + StringUtils.join(cases, "'),('") + "'), (null)";
    try (Connection con = init(table, column, values);
        Statement statement = con.createStatement()) {
      try (ResultSet rs = statement.executeQuery("select * from " + table)) {
        int i = 0;
        while (i < cases.length) {
          assertTrue(rs.next());
          if (i == cases.length - 2) {
            assertEquals("0001-01-01", rs.getDate(1).toString());
          } else {
            assertEquals(cases[i], rs.getDate(1).toString());
          }
          i++;
        }
        assertTrue(rs.next());
        assertNull(rs.getString(1));
      }
      statement.execute("drop table " + table);
      System.clearProperty("user.timezone");
    }
  }

  public void testTimeWithScale(String[] times, int scale) throws SQLException {
    String table = "test_arrow_time";
    String column = "(a time(" + scale + "))";
    String values = "('" + StringUtils.join(times, "'),('") + "'), (null)";

    try (Connection con = init(table, column, values);
        Statement statement = con.createStatement();
        ResultSet rs = statement.executeQuery("select * from " + table)) {
      for (int i = 0; i < times.length; i++) {
        assertTrue(rs.next());
        // Java Time class does not have nanoseconds
        assertEquals("00:01:23", rs.getString(1));
      }
      assertTrue(rs.next());
      assertNull(rs.getTime(1));
    }
  }

  @Test
  public void testTimestampNTZ() throws SQLException {
    for (int scale = 0; scale <= 9; scale++) {
      testTimestampNTZWithScale(scale);
    }
  }

  public void testTimestampNTZWithScale(int scale) throws SQLException {
    String[] cases = {
      "2017-01-01 12:00:00",
      "2014-01-02 16:00:00",
      "2014-01-02 12:34:56",
      "0001-01-02 16:00:00",
      "1969-12-31 23:59:59",
      "1970-01-01 00:00:00",
      "1970-01-01 00:00:01",
      "9999-01-01 00:00:00"
    };

    String[] results = {
      "Sun, 01 Jan 2017 12:00:00 Z",
      "Thu, 02 Jan 2014 16:00:00 Z",
      "Thu, 02 Jan 2014 12:34:56 Z",
      "Sun, 02 Jan 0001 16:00:00 Z",
      "Wed, 31 Dec 1969 23:59:59 Z",
      "Thu, 01 Jan 1970 00:00:00 Z",
      "Thu, 01 Jan 1970 00:00:01 Z",
      "Fri, 01 Jan 9999 00:00:00 Z"
    };

    String table = "test_arrow_ts_ntz";

    String column = "(a timestamp_ntz(" + scale + "))";

    String values = "('" + StringUtils.join(cases, "'),('") + "'), (null)";
    try (Connection con = init(table, column, values);
        Statement statement = con.createStatement()) {
      try (ResultSet rs = statement.executeQuery("select * from " + table)) {
        int i = 0;
        while (i < cases.length) {
          assertTrue(rs.next());
          assertEquals(results[i++], rs.getString(1));
        }
        assertTrue(rs.next());
        assertNull(rs.getString(1));
      }
      statement.execute("drop table " + table);
      System.clearProperty("user.timezone");
    }
  }

  @Test
  public void testTimestampNTZWithNanos() throws SQLException {
    String[] cases = {
      "2017-01-01 12:00:00.123456789",
      "2014-01-02 16:00:00.0123",
      "2014-01-02 12:34:56.234241234",
      "0001-01-02 16:00:00.999999999",
      "1969-12-31 23:59:59.000000001",
      "1970-01-01 00:00:00.111111111",
      "1970-01-01 00:00:01.00000001",
      "9999-01-01 00:00:00.1"
    };

    String table = "test_arrow_ts_ntz";

    String column = "(a timestamp_ntz)";

    String values = "('" + StringUtils.join(cases, "'),('") + "'), (null)";
    try (Connection con = init(table, column, values);
        Statement statement = con.createStatement()) {
      try (ResultSet rs = statement.executeQuery("select * from " + table)) {
        int i = 0;
        while (i < cases.length) {
          assertTrue(rs.next());
          assertEquals(cases[i++], rs.getTimestamp(1).toString());
        }
        assertTrue(rs.next());
        assertNull(rs.getString(1));
      } finally {
        statement.execute("drop table " + table);
        System.clearProperty("user.timezone");
      }
    }
  }
}
