package org.basex.http;

import static org.basex.core.users.UserText.*;
import static org.basex.util.http.Method.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest.*;
import java.nio.charset.*;
import java.util.*;

import org.basex.*;
import org.basex.core.*;
import org.basex.io.*;
import org.basex.io.in.*;
import org.basex.util.*;
import org.basex.util.http.*;
import org.basex.util.list.*;
import org.junit.jupiter.api.*;

/**
 * This class contains common methods for the HTTP services.
 *
 * @author BaseX Team 2005-22, BSD License
 * @author Christian Gruen
 */
public abstract class HTTPTest extends SandboxTest {
  /** HTTP server. */
  private static BaseXHTTP http;
  /** Root path. */
  private static String rootUrl;

  // INITIALIZATION ===============================================================================

  /**
   * Initializes the test.
   * @param url root path
   * @param local local flag
   * @throws Exception exception
   */
  protected static void init(final String url, final boolean local) throws Exception {
    init(url, local, false);
  }

  /**
   * Initializes the test.
   * @param url root path
   * @param local local flag
   * @param auth enforce authentication
   * @throws Exception exception
   */
  protected static void init(final String url, final boolean local, final boolean auth)
      throws Exception {

    final StringList sl = new StringList();
    sl.add("-p" + DB_PORT, "-h" + HTTP_PORT, "-s" + STOP_PORT, "-z", "-q");
    if(local) sl.add("-l");
    if(!auth) sl.add("-U" + ADMIN);
    http = new BaseXHTTP(sl.toArray());
    rootUrl = url;

    final StaticOptions sopts = HTTPContext.get().context().soptions;
    assertTrue(new IOFile(sopts.get(StaticOptions.WEBPATH)).md());
  }

  /**
   * Finishes the test.
   * @throws IOException I/O exception
   */
  @AfterAll public static void stop() throws IOException {
    http.stop();

    // cleanup: remove project specific system properties
    final StringList keys = new StringList();
    final Properties props = System.getProperties();
    for(final Object key : props.keySet()) {
      final String path = key.toString();
      if(path.startsWith(Prop.DBPREFIX)) keys.add(path);
    }
    for(final String key : keys) props.remove(key);
  }

  // PROTECTED METHODS ============================================================================

  /**
   * Executes the specified GET request and returns the result.
   * @param expected expected result
   * @param request request
   * @param params query parameters
   * @throws IOException I/O exception
   */
  protected static void get(final String expected, final String request, final Object... params)
      throws IOException {
    assertEquals(expected, get(200, request, params));
  }

  /**
   * Executes the specified GET request and returns the result.
   * @param status status code to check
   * @param request request
   * @param params query parameters
   * @return string result, or {@code null} for a failure
   * @throws IOException I/O exception
   */
  protected static String get(final int status, final String request, final Object... params)
      throws IOException {
    return send(status, GET.name(), null, null, request, params);
  }

  /**
   * Executes the specified DELETE request.
   * @param status status code to check
   * @param request request
   * @param params query parameters
   * @return response code
   * @throws IOException I/O exception
   */
  protected static String delete(final int status, final String request, final Object... params)
      throws IOException {
    return send(status, DELETE.name(), null, null, request, params);
  }

  /**
   * Executes the specified HEAD request and returns the result.
   * @param status status code to check
   * @param request request
   * @param params query parameters
   * @return string result, or {@code null} for a failure
   * @throws IOException I/O exception
   */
  protected static String head(final int status, final String request, final Object... params)
      throws IOException {
    return send(status, HEAD.name(), null, null, request, params);
  }

  /**
   * Executes the specified OPTIONS request and returns the result.
   * @param request request
   * @param params query parameters
   * @return string result, or {@code null} for a failure
   * @throws IOException I/O exception
   */
  protected static String options(final String request, final Object... params)
      throws IOException {
    return send(200, OPTIONS.name(), null, null, request, params);
  }

  /**
   * Executes the specified POST request.
   * @param payload payload
   * @param type media type
   * @param request path
   * @return string result
   * @throws IOException I/O exception
   */
  protected static String post(final String payload, final MediaType type, final String request)
      throws IOException {
    return post(200, payload, type, request);
  }

  /**
   * Executes the specified POST request.
   * @param status status code to check
   * @param payload payload
   * @param type media type
   * @param request path
   * @param params query parameters
   * @return string result
   * @throws IOException I/O exception
   */
  protected static String post(final int status, final String payload, final MediaType type,
      final String request, final Object... params) throws IOException {
    return send(status, Method.POST.name(), new ArrayInput(payload), type, request, params);
  }

  /**
   * Executes the specified PUT request.
   * @param is input stream (may be {@code null})
   * @param request query
   * @throws IOException I/O exception
   */
  protected static void put(final InputStream is, final String request) throws IOException {
    put(201, is, request);
  }

  /**
   * Executes the specified PUT request.
   * @param status status code to check
   * @param is input stream (may be {@code null})
   * @param request query
   * @param params query parameters
   * @throws IOException I/O exception
   */
  protected static void put(final int status, final InputStream is, final String request,
      final Object... params) throws IOException {
    send(status, Method.PUT.name(), is, null, request, params);
  }

  /**
   * Executes the specified PUT request.
   * @param status status code to check
   * @param method HTTP method
   * @param is input stream (may be {@code null})
   * @param type media type (optional, may be {@code null})
   * @param request query
   * @param params TODO
   * @return string result
   * @throws IOException I/O exception
   */
  protected static String send(final int status, final String method, final InputStream is,
      final MediaType type, final String request, final Object... params) throws IOException {

    final BodyPublisher pub = is != null ? HttpRequest.BodyPublishers.ofInputStream(() -> is) :
      HttpRequest.BodyPublishers.noBody();

    final StringBuilder sb = new StringBuilder(rootUrl + request);
    final int pl = params.length;
    for(int p = 0; p < pl; p += 2) {
      sb.append(p == 0 ? '?' : '&').append(params[p]).append('=').
        append(URLEncoder.encode(params[p + 1].toString(), Charset.forName("UTF-8")));
    }
    final URI uri = URI.create(sb.toString());
    final HttpRequest.Builder builder = HttpRequest.newBuilder(uri).method(method, pub);
    if(type != null) builder.setHeader(HTTPText.CONTENT_TYPE, type.toString());

    try {
      final HttpClient client = IOUrl.clientBuilder(true).build();
      final HttpResponse<String> response = client.send(builder.build(),
          HttpResponse.BodyHandlers.ofString());
      final String body = response.body();
      assertEquals(status, response.statusCode(), method + ' ' + request + "\nResponse: " + body);
      return body;
    } catch(final InterruptedException ex) {
      throw new IOException(ex);
    }
  }
}
