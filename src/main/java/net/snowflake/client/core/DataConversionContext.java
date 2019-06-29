/*
 * Copyright (c) 2012-2019 Snowflake Computing Inc. All rights reserved.
 */
package net.snowflake.client.core;

import net.snowflake.common.core.SFBinaryFormat;
import net.snowflake.common.core.SnowflakeDateTimeFormat;

import java.util.TimeZone;

/**
 * This class contains formatter info about each data type and related flags
 * etc. And it is scoped to a single result set. a.k.a each result set object
 * should have its own formatter info
 */
public interface DataConversionContext
{
  /**
   * timestamp_ltz formatter
   */
  SnowflakeDateTimeFormat getTimestampLTZFormatter();

  /**
   * timestamp_ntz formatter
   */
  SnowflakeDateTimeFormat getTimestampNTZFormatter();

  /**
   * timestamp_tz formatter
   */
  SnowflakeDateTimeFormat getTimestampTZFormatter();

  /**
   * date formatter
   */
  SnowflakeDateTimeFormat getDateFormatter();

  /**
   * time formatter
   */
  SnowflakeDateTimeFormat getTimeFormatter();

  /**
   * binary formatter
   */
  SFBinaryFormat getBinaryFormatter();

  /**
   * get scale from Snowflake metadata
   */
  int getScale(int columnIndex);

  /**
   * @return current session
   */
  SFSession getSession();

  /**
   * @return session time zone
   */
  TimeZone getTimeZone();

  /**
   * whether to honor client time zone for timestamp_ntz
   *
   * @return
   */
  boolean getHonorClientTZForTimestampNTZ();

  /**
   * result version
   *
   * @return
   */
  long getResultVersion();
}
