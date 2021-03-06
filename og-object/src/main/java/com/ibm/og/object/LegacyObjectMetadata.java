/* Copyright (c) IBM Corporation 2016. All Rights Reserved.
 * Project name: Object Generator
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */

package com.ibm.og.object;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.common.io.BaseEncoding;

/**
 * A defacto {@code ObjectMetadata} implementation.
 * 
 * @since 1.0
 */
public class LegacyObjectMetadata implements ObjectMetadata {
  public static final int OBJECT_NAME_SIZE = 18;
  public static final int OBJECT_SIZE_SIZE = 8;
  public static final int OBJECT_SUFFIX_SIZE = 4;
  public static final int OBJECT_SIZE = OBJECT_NAME_SIZE + OBJECT_SIZE_SIZE + OBJECT_SUFFIX_SIZE;
  private static final BaseEncoding ENCODING = BaseEncoding.base16().lowerCase();
  protected final ByteBuffer objectBuffer;

  protected LegacyObjectMetadata(final ByteBuffer objectBuffer) {
    this.objectBuffer = objectBuffer;
  }

  /**
   * Configures an instance using the provided metadata packed as bytes
   * 
   * @param objectBytes the object metadata as bytes
   * @return a {@code LegacyObjectMetadata} instance
   * @throws IllegalArgumentException if the length of objectBytes is invalid
   */
  public static LegacyObjectMetadata fromBytes(final byte[] objectBytes) {
    checkNotNull(objectBytes);
    checkArgument(objectBytes.length == OBJECT_SIZE,
        String.format("objectName length must be == %s", OBJECT_SIZE) + " [%s]",
        objectBytes.length);

    return new LegacyObjectMetadata(ByteBuffer.allocate(OBJECT_SIZE).put(objectBytes));
  }

  /**
   * Configures an instance using the provided metadata,
   * 
   * @param objectName the object name; must be base16 encoded / uuid friendly
   * @param objectSize the size of the object
   * @param containerSuffix object's container suffix
   * @return a {@code LegacyObjectMetadata} instance
   * @throws IllegalArgumentException if objectSize is negative
   */
  public static LegacyObjectMetadata fromMetadata(final String objectName, final long objectSize,
      final int containerSuffix) {
    checkNotNull(objectName);
    // HACK; assume 1 char == 2 bytes for object name string length checking
    final int stringLength = 2 * OBJECT_NAME_SIZE;
    checkArgument(objectName.length() == stringLength,
        String.format("objectName length must be == %s", stringLength) + " [%s]",
        objectName.length());
    checkArgument(objectSize >= 0, "objectSize must be >= 0 [%s]", objectSize);
    checkArgument(containerSuffix >= -1, "containerSuffix must be >= -1 [%s]", containerSuffix);

    final ByteBuffer objectBuffer = ByteBuffer.allocate(OBJECT_SIZE);
    objectBuffer.put(ENCODING.decode(objectName), 0, OBJECT_NAME_SIZE);
    objectBuffer.putLong(objectSize);
    objectBuffer.putInt(containerSuffix);
    return new LegacyObjectMetadata(objectBuffer);
  }

  @Override
  public String getName() {
    return ENCODING.encode(this.objectBuffer.array(), 0, OBJECT_NAME_SIZE);
  }

  @Override
  public long getSize() {
    return this.objectBuffer.getLong(OBJECT_NAME_SIZE);
  }

  @Override
  public int getContainerSuffix() {
    return this.objectBuffer.getInt(OBJECT_NAME_SIZE + OBJECT_SIZE_SIZE);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == null) {
      return false;
    }

    if (!(obj instanceof ObjectMetadata)) {
      return false;
    }

    final ObjectMetadata other = (ObjectMetadata) obj;
    return Arrays.equals(toBytes(), other.toBytes());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(toBytes());
  }

  @Override
  public int compareTo(final ObjectMetadata o) {
    // TODO this compareTo implementation is heavily borrowed from String.toString. It is
    // ignorant of character encoding issues, but in our case we are storing hex digits so it
    // should be sufficient
    final byte[] b1 = toBytes();
    final byte[] b2 = o.toBytes();
    final int len1 = b1.length;
    final int len2 = b2.length;
    final int lim = Math.min(len1, len2);

    int k = 0;
    while (k < lim) {
      final byte c1 = b1[k];
      final byte c2 = b2[k];
      if (c1 != c2) {
        return c1 - c2;
      }
      k++;
    }
    return len1 - len2;
  }

  @Override
  public byte[] toBytes() {
    return this.objectBuffer.array();
  }

  @Override
  public String toString() {
    return String.format("LegacyObjectMetadata [name=%s, size=%s]", getName(), getSize());
  }
}
