/* Copyright (c) IBM Corporation 2016. All Rights Reserved.
 * Project name: Object Generator
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */

package com.ibm.og.s3;

import com.ibm.og.http.ResponseBodyConsumer;
import com.ibm.og.util.Context;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A response body consumer which processes the body of multipart responses
 * Pulls UploadId from Initiate response and errors from Complete response
 * 
 * @since 1.0
 */
public class S3MultipartWriteResponseBodyConsumer implements ResponseBodyConsumer {
  @Override
  public Map<String, String> consume(final int statusCode, final InputStream response)
      throws IOException {
    checkNotNull(response);

    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(response, Charsets.UTF_8));

    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = null;
    Document document = null;

    try {
      documentBuilder = documentBuilderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    try {
      if(response.available() > 0) {
        document = documentBuilder.parse(response);
      }
    } catch (SAXException e) {
      throw new RuntimeException(e);
    }

    if(document != null) {
      NodeList uploadIdNodeList = document.getElementsByTagName("UploadId");
      if (uploadIdNodeList != null) {
        if (uploadIdNodeList.getLength() > 0) {
          String uploadId = uploadIdNodeList.item(0).getTextContent();
          return ImmutableMap.of(Context.X_OG_MULTIPART_UPLOAD_ID, uploadId);
        }
      }
      NodeList errorBodyList = document.getElementsByTagName("Error");
      if (errorBodyList != null) {
        if(errorBodyList.getLength() > 0) {
          String errorBody = errorBodyList.item(0).getTextContent();
          throw new RuntimeException(errorBody);
        }
      }
    }

    return ImmutableMap.of();
  }

  @Override
  public String toString() {
    return "S3MultipartWriteResponseBodyConsumer []";
  }
}
