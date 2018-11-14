/*
 *
 * Copyright (c) 2013 - 2018 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.ca.mgmt.db.port;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.bind.JAXBException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xipki.ca.mgmt.db.DbSchemaInfo;
import org.xipki.ca.mgmt.db.DbToolBase;
import org.xipki.ca.mgmt.db.jaxb.ca.FileOrBinaryType;
import org.xipki.ca.mgmt.db.jaxb.ca.FileOrValueType;
import org.xipki.datasource.DataAccessException;
import org.xipki.datasource.DataSourceWrapper;
import org.xipki.util.Base64;
import org.xipki.util.IoUtil;
import org.xipki.util.Args;
import org.xml.sax.SAXException;

/**
 * TODO.
 * @author Lijun Liao
 * @since 2.0.0
 */

public class DbPorter extends DbToolBase {

  public enum OcspDbEntryType {
    CERT("certs", "CERT", 1);

    private final String dirName;

    private final String tableName;

    private final float sqlBatchFactor;

    private OcspDbEntryType(String dirName, String tableName, float sqlBatchFactor) {
      this.dirName = dirName;
      this.tableName = tableName;
      this.sqlBatchFactor = sqlBatchFactor;
    }

    public String getDirName() {
      return dirName;
    }

    public String getTableName() {
      return tableName;
    }

    public float getSqlBatchFactor() {
      return sqlBatchFactor;
    }

  }

  public enum CaDbEntryType {
    CERT("certs", "CERT", 1),
    CRL("crls", "CRL", 0.1f),
    REQUEST("requests", "REQUEST", 0.1f),
    REQCERT("reqcerts", "REQCERT", 50);

    private final String dirName;

    private final String tableName;

    private final float sqlBatchFactor;

    private CaDbEntryType(String dirName, String tableName, float sqlBatchFactor) {
      this.dirName = dirName;
      this.tableName = tableName;
      this.sqlBatchFactor = sqlBatchFactor;
    }

    public String getDirName() {
      return dirName;
    }

    public String getTableName() {
      return tableName;
    }

    public float getSqlBatchFactor() {
      return sqlBatchFactor;
    }

  }

  public static final String FILENAME_CA_CONFIGURATION = "ca-configuration.xml";

  public static final String FILENAME_CA_CERTSTORE = "ca-certstore.xml";

  public static final String FILENAME_OCSP_CERTSTORE = "ocsp-certstore.xml";

  public static final String DIRNAME_CRL = "crl";

  public static final String DIRNAME_CERT = "cert";

  public static final String PREFIX_FILENAME_CERTS = "certs-";

  public static final String EXPORT_PROCESS_LOG_FILENAME = "export.process";

  public static final String IMPORT_PROCESS_LOG_FILENAME = "import.process";

  public static final String IMPORT_TO_OCSP_PROCESS_LOG_FILENAME = "import-to-ocsp.process";

  public static final int VERSION = 1;

  protected final int dbSchemaVersion;

  protected final int maxX500nameLen;

  protected final DbSchemaInfo dbSchemaInfo;

  public DbPorter(DataSourceWrapper datasource, String baseDir, AtomicBoolean stopMe)
      throws DataAccessException {
    super(datasource, baseDir, stopMe);

    this.dbSchemaInfo = new DbSchemaInfo(datasource);
    this.dbSchemaVersion = Integer.parseInt(dbSchemaInfo.getVariableValue("VERSION"));
    this.maxX500nameLen = Integer.parseInt(dbSchemaInfo.getVariableValue("X500NAME_MAXLEN"));
  }

  protected FileOrValueType buildFileOrValue(String content, String fileName) throws IOException {
    if (content == null) {
      return null;
    }

    Args.notNull(fileName, "fileName");

    FileOrValueType ret = new FileOrValueType();
    if (content.length() < 256) {
      ret.setValue(content);
      return ret;
    }

    File file = new File(baseDir, fileName);
    IoUtil.mkdirsParent(file.toPath());
    IoUtil.save(file, content.getBytes("UTF-8"));

    ret.setFile(fileName);
    return ret;
  }

  protected String value(FileOrValueType fileOrValue) throws IOException {
    if (fileOrValue == null) {
      return null;
    }

    if (fileOrValue.getValue() != null) {
      return fileOrValue.getValue();
    }

    File file = new File(baseDir, fileOrValue.getFile());
    return new String(IoUtil.read(file), "UTF-8");
  }

  protected FileOrBinaryType buildFileOrBase64Binary(String base64Content, String fileName)
      throws IOException {
    if (base64Content == null) {
      return null;
    }
    return buildFileOrBinary(Base64.decode(base64Content), fileName);
  }

  protected FileOrBinaryType buildFileOrBinary(byte[] content, String fileName) throws IOException {
    if (content == null) {
      return null;
    }

    Args.notNull(fileName, "fileName");

    FileOrBinaryType ret = new FileOrBinaryType();
    if (content.length < 256) {
      ret.setBinary(content);
      return ret;
    }

    File file = new File(baseDir, fileName);
    IoUtil.mkdirsParent(file.toPath());
    IoUtil.save(file, content);

    ret.setFile(fileName);
    return ret;
  }

  protected byte[] binary(FileOrBinaryType fileOrValue) throws IOException {
    if (fileOrValue == null) {
      return null;
    }

    if (fileOrValue.getBinary() != null) {
      return fileOrValue.getBinary();
    }

    File file = new File(baseDir, fileOrValue.getFile());
    return IoUtil.read(file);
  }

  public static final Schema retrieveSchema(String schemaPath) throws JAXBException {
    Args.notNull(schemaPath, "schemaPath");

    URL schemaUrl = DbPorter.class.getResource(schemaPath);
    final SchemaFactory schemaFact = SchemaFactory.newInstance(
        javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try {
      return schemaFact.newSchema(schemaUrl);
    } catch (SAXException ex) {
      throw new JAXBException("could not load schemas for the specified classes\nDetails:\n"
          + ex.getMessage());
    }
  }

  public static void echoToFile(String content, File file) throws IOException {
    Args.notNull(content, "content");
    Args.notNull(file, "file");

    OutputStream out = null;
    try {
      out = Files.newOutputStream(file.toPath());
      out.write(content.getBytes());
    } finally {
      if (out != null) {
        out.flush();
        out.close();
      }
    }
  }

}