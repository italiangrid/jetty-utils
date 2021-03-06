/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare, 2012-2015.
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
package org.italiangrid.utils.jetty;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import eu.emi.security.authn.x509.X509CertChainValidatorExt;
import eu.emi.security.authn.x509.helpers.ssl.SSLTrustManager;
import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.impl.SocketFactoryCreator;

/**
 * A builder that configures a Jetty server TLS connector integrated with CANL
 * {@link X509CertChainValidatorExt} certificate validation services.
 * 
 */
public class TLSServerConnectorBuilder {

  /**
   * Default service certificate file.
   */
  public static final String DEFAULT_CERTIFICATE_FILE = "/etc/grid-security/hostcert.pem";

  /**
   * Default service certificate private key file.
   */
  public static final String DEFAULT_CERTIFICATE_KEY_FILE = "/etc/grid-security/hostcert.pem";

  /**
   * The port for this connector.
   */
  private int port;

  /**
   * The certificate file location.
   */
  private String certificateFile = DEFAULT_CERTIFICATE_FILE;

  /**
   * The certificate private key file location.
   */
  private String certificateKeyFile = DEFAULT_CERTIFICATE_KEY_FILE;

  /**
   * The password to decrypt the certificate private key file.
   */
  private char[] certicateKeyPassword = null;

  /**
   * The certificate validator used by this connector builder.
   */
  private final X509CertChainValidatorExt certificateValidator;

  /**
   * Whether client auth will be required for this connector.
   */
  private boolean tlsNeedClientAuth = false;

  /**
   * Whether cluent auth is supported for this connector.
   */
  private boolean tlsWantClientAuth = true;

  /**
   * Supported SSL protocols.
   */
  private String[] includeProtocols;

  /**
   * Disabled SSL protocols.
   */
  private String[] excludeProtocols;

  /**
   * Supported cipher suites.
   */
  private String[] includeCipherSuites;

  /**
   * Disabled cipher suites.
   */
  private String[] excludeCipherSuites;

  /**
   * The HTTP configuration for the connector being created.
   */
  private HttpConfiguration httpConfiguration;

  /**
   * The key manager to use for the connector being created.
   */
  private KeyManager keyManager;

  /**
   * The server for which the connector is being created.
   */
  private final Server server;

  /**
   * Returns an instance of the {@link TLSServerConnectorBuilder}.
   * 
   * @param s
   *          the {@link Server} for which the connector is being created
   * @param certificateValidator
   *          a {@link X509CertChainValidatorExt} used to validate certificates
   * @return an instance of the {@link TLSServerConnectorBuilder}
   */
  public static TLSServerConnectorBuilder instance(Server s,
    X509CertChainValidatorExt certificateValidator) {

    return new TLSServerConnectorBuilder(s, certificateValidator);
  }

  /**
   * Private ctor.
   * 
   * @param s
   *          the {@link Server} for which the connector is being created
   * @param certificateValidator
   *          a {@link X509CertChainValidatorExt} used to validate certificates
   */
  private TLSServerConnectorBuilder(Server s,
    X509CertChainValidatorExt certificateValidator) {

    if (s == null) {
      throw new IllegalArgumentException("Server cannot be null");
    }

    if (certificateValidator == null) {
      throw new IllegalArgumentException("certificateValidator cannot be null");
    }

    this.server = s;
    this.certificateValidator = certificateValidator;
  }

  private void credentialsSanityChecks() {

    checkFileExistsAndIsReadable(new File(certificateFile),
      "Error accessing certificate file");

    checkFileExistsAndIsReadable(new File(certificateKeyFile),
      "Error accessing certificate key file");

  }

  private void loadCredentials() {

    credentialsSanityChecks();

    PEMCredential serviceCredentials = null;

    try {

      serviceCredentials = new PEMCredential(certificateKeyFile,
        certificateFile, certicateKeyPassword);

    } catch (KeyStoreException | CertificateException | IOException e) {

      throw new RuntimeException("Error setting up service credentials", e);
    }

    keyManager = serviceCredentials.getKeyManager();
  }

  /**
   * Configures SSL session parameters for the jetty {@link SslContextFactory}.
   * 
   * @param contextFactory
   *          the {@link SslContextFactory} being configured
   */
  private void configureContextFactory(SslContextFactory contextFactory) {

    if (excludeProtocols != null) {
      contextFactory.setExcludeProtocols(excludeProtocols);
    }

    if (includeProtocols != null) {
      contextFactory.setIncludeProtocols(includeProtocols);
    }

    if (excludeCipherSuites != null) {
      contextFactory.setExcludeCipherSuites(excludeCipherSuites);
    }

    if (includeCipherSuites != null) {
      contextFactory.setIncludeCipherSuites(includeCipherSuites);
    }

    contextFactory.setWantClientAuth(tlsWantClientAuth);
    contextFactory.setNeedClientAuth(tlsNeedClientAuth);

  }

  /**
   * Builds a default {@link HttpConfiguration} for the TLS-enabled connector
   * being created
   * 
   * @return the default {@link HttpConfiguration}
   */
  private HttpConfiguration defaultHttpConfiguration() {

    HttpConfiguration httpsConfig = new HttpConfiguration();

    httpsConfig.setSecureScheme("https");

    httpsConfig.setSecurePort(port);

    httpsConfig.setOutputBufferSize(32768);
    httpsConfig.setRequestHeaderSize(8192);
    httpsConfig.setResponseHeaderSize(8192);

    httpsConfig.setSendServerVersion(true);
    httpsConfig.setSendDateHeader(false);

    httpsConfig.addCustomizer(new SecureRequestCustomizer());

    return httpsConfig;

  }

  /**
   * Gives access to the {@link HttpConfiguration} used for the TLS-enabled
   * connector being created. If the configuration is not set, it creates it
   * using {@link #defaultHttpConfiguration()}.
   * 
   * @return the {@link HttpConfiguration} being used for the TLS-enabled
   *         connector.
   */
  public HttpConfiguration httpConfiguration() {

    if (httpConfiguration == null) {
      httpConfiguration = defaultHttpConfiguration();
    }

    return httpConfiguration;

  }

  /**
   * Sets the port for the connector being created.
   * 
   * @param port
   *          the port for the connector
   * @return this builder
   */
  public TLSServerConnectorBuilder withPort(int port) {

    this.port = port;
    return this;
  }

  /**
   * Sets the certificate file for the connector being created.
   * 
   * @param certificateFile
   *          the certificate file
   * @return this builder
   */
  public TLSServerConnectorBuilder withCertificateFile(String certificateFile) {

    this.certificateFile = certificateFile;
    return this;
  }

  /**
   * Sets the certificate key file for the connector being created.
   * 
   * @param certificateKeyFile
   *          the certificate key file
   * @return this builder
   */
  public TLSServerConnectorBuilder withCertificateKeyFile(
    String certificateKeyFile) {

    this.certificateKeyFile = certificateKeyFile;
    return this;
  }

  /**
   * The the certificate key password for the connector being built
   * 
   * @param certificateKeyPassword
   *          the certificate key password
   * @return this builder
   */
  public TLSServerConnectorBuilder withCertificateKeyPassword(
    char[] certificateKeyPassword) {

    this.certicateKeyPassword = certificateKeyPassword;
    return this;
  }

  /**
   * Sets the {@link SslContextFactory#setNeedClientAuth(boolean)} parameter for
   * the connector being created.
   * 
   * @param needClientAuth
   *          true if client authentication is required
   * @return this builder
   */
  public TLSServerConnectorBuilder withNeedClientAuth(boolean needClientAuth) {

    this.tlsNeedClientAuth = needClientAuth;
    return this;
  }

  /**
   * Sets the {@link SslContextFactory#setWantClientAuth(boolean)} parameter for
   * the connector being created.
   * 
   * @param wantClientAuth
   *          true if client authentication is wanted
   * @return this builder
   */
  public TLSServerConnectorBuilder withWantClientAuth(boolean wantClientAuth) {

    this.tlsWantClientAuth = wantClientAuth;
    return this;
  }

  /**
   * Sets SSL included protocols. See
   * {@link SslContextFactory#setIncludeProtocols(String...)}.
   * 
   * @param includeProtocols
   *          the array of included protocol names
   * @return this builder
   */
  public TLSServerConnectorBuilder withIncludeProtocols(
    String... includeProtocols) {

    this.includeProtocols = includeProtocols;
    return this;
  }

  /**
   * Sets SSL excluded protocols. See
   * {@link SslContextFactory#setExcludeProtocols(String...)}.
   * 
   * @param excludeProtocols
   *          the array of excluded protocol names
   * @return this builder
   */
  public TLSServerConnectorBuilder withExcludeProtocols(
    String... excludeProtocols) {

    this.excludeProtocols = excludeProtocols;
    return this;
  }

  /**
   * Sets the SSL included cipher suites.
   * 
   * @param includeCipherSuites
   *          the array of included cipher suites.
   * @return this builder
   */
  public TLSServerConnectorBuilder withIncludeCipherSuites(
    String... includeCipherSuites) {

    this.includeCipherSuites = includeCipherSuites;
    return this;
  }

  /**
   * Sets the SSL ecluded cipher suites.
   * 
   * @param excludeCipherSuites
   *          the array of excluded cipher suites.
   * @return this builder
   */
  public TLSServerConnectorBuilder withExcludeCipherSuites(
    String... excludeCipherSuites) {

    this.excludeCipherSuites = excludeCipherSuites;
    return this;
  }

  /**
   * Sets the {@link HttpConfiguration} for the connector being built.
   * 
   * @param conf
   *          the {@link HttpConfiguration} to use
   * @return this builder
   */
  public TLSServerConnectorBuilder withHttpConfiguration(HttpConfiguration conf) {

    this.httpConfiguration = conf;
    return this;
  }

  /**
   * Sets the {@link KeyManager} for the connector being built.
   * 
   * @param km
   *          the {@link KeyManager} to use
   * @return this builder
   */
  public TLSServerConnectorBuilder withKeyManager(KeyManager km) {

    this.keyManager = km;
    return this;
  }

  private SSLContext buildSSLContext() {

    SSLContext sslCtx;

    try {

      KeyManager[] kms = new KeyManager[] { keyManager };
      SSLTrustManager tm = new SSLTrustManager(certificateValidator);

      sslCtx = SSLContext.getInstance("TLS");
      sslCtx.init(kms, new TrustManager[] { tm }, null);

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("TLS protocol not supported.", e);
    } catch (KeyManagementException e) {
      throw new RuntimeException(e);
    }

    return sslCtx;
  }

  /**
   * Builds a {@link ServerConnector} based on the
   * {@link TLSServerConnectorBuilder} parameters
   * 
   * @return a {@link ServerConnector}
   */
  public ServerConnector build() {

    if (keyManager == null) {
      loadCredentials();
    }

    SSLContext sslContext = buildSSLContext();
    SslContextFactory cf = new SslContextFactory();
    cf.setSslContext(sslContext);

    configureContextFactory(cf);

    if (httpConfiguration == null) {
      httpConfiguration = defaultHttpConfiguration();
    }

    ServerConnector connector = new ServerConnector(server,
      new SslConnectionFactory(cf, HttpVersion.HTTP_1_1.asString()),
      new HttpConnectionFactory(httpConfiguration));

    connector.setPort(port);
    return connector;
  }

  /**
   * Checks that file exists and is readable.
   * 
   * @param f
   *          the {@link File} to be checked
   * @param prefix
   *          A prefix string for the error message, in case the file does not
   *          exist and is not readable
   * @throws RuntimeException
   *           if the file does not exist or is not readable
   */
  private void checkFileExistsAndIsReadable(File f, String prefix) {

    String errorMessage = null;

    if (!f.exists()) {
      errorMessage = "File does not exists";
    } else if (!f.canRead()) {
      errorMessage = "File is not readable";
    } else if (f.isDirectory()) {
      errorMessage = "File is a directory";
    }

    if (errorMessage != null) {
      String msg = String.format("%s: %s [%s]", prefix, errorMessage,
        f.getAbsolutePath());
      throw new RuntimeException(msg);
    }

  }
}
