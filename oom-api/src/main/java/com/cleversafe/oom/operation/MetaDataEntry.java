//
// Copyright (C) 2005-2011 Cleversafe, Inc. All rights reserved.
//
// Contact Information:
// Cleversafe, Inc.
// 222 South Riverside Plaza
// Suite 1700
// Chicago, IL 60606, USA
//
// licensing@cleversafe.com
//
// END-OF-HEADER
//
// -----------------------
// @author: rveitch
//
// Date: Feb 25, 2014
// ---------------------

package com.cleversafe.oom.operation;

/**
 * A key value pair that describes a metadata entry.
 */
public interface MetaDataEntry
{
   /**
    * Gets the key of this metadata entry.
    * 
    * @return the key of this metadata entry
    */
   String getKey();

   /**
    * Gets the value of this metadata entry.
    * 
    * @return the value of this metadata entry
    */
   String getValue();
}