/* Copyright (c) IBM Corporation 2016. All Rights Reserved.
 * Project name: Object Generator
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */

package com.ibm.og.util.io;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An <code>InputStream</code> implementation that tracks time to first byte.
 * 
 * @since 1.0
 */
public class MonitoringInputStream extends FilterInputStream {
  private long firstRead;

  /**
   * Constructs a <code>MonitoringInputStream</code> instance using the provided stream
   * 
   * @param in the stream to wrap
   * @throws NullPointerException if in is null
   */
  public MonitoringInputStream(final InputStream in) {
    super(checkNotNull(in));
  }

  @Override
  public int read() throws IOException {
    final int val = super.read();
    if (this.firstRead == 0) {
      this.firstRead = System.nanoTime();
    }
    return val;
  }

  @Override
  public int read(final byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(final byte[] b, final int off, final int len) throws IOException {
    final int size = super.read(b, off, len);
    if (this.firstRead == 0) {
      this.firstRead = System.nanoTime();
    }
    return size;
  }

  /**
   * Gets the timestamp for when the first {@code read} call was issued
   * 
   * @return first read timestamp, in nanoseconds
   */
  public long getFirstRead() {
    return this.firstRead;
  }

  @Override
  public String toString() {
    return String.format("MonitoringInputStream [in=%s]", this.in);
  }
}
