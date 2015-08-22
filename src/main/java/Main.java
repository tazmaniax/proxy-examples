import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.GeocodingApi.ComponentFilter;
import com.google.maps.model.GeocodingResult;

import sun.misc.BASE64Encoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;

public class Main extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    if (req.getRequestURI().endsWith("/proximo")) {
      showProximo(req,resp);
    } else if (req.getRequestURI().endsWith("/fixie")) {
      showFixie(req,resp);
    } else if (req.getRequestURI().endsWith("/quotaguard")) {
      showQuotaGuard(req,resp);
    } else {
      resp.getWriter().print("<p><a href='/proximo'>Proximo</a></p>");
      resp.getWriter().print("<p><a href='/fixie'>Fixie</a></p>");
      resp.getWriter().print("<p><a href='/quotaguard'>QuotaGuard</a></p>");
    }
  }

  private void showProximo(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // This could also go in app init
    URL proximo = new URL(System.getenv("PROXIMO_URL"));
    String userInfo = proximo.getUserInfo();
    String user = userInfo.substring(0, userInfo.indexOf(':'));
    String password = userInfo.substring(userInfo.indexOf(':') + 1);

    System.setProperty("socksProxyHost", proximo.getHost());
    Authenticator.setDefault(new ProxyAuthenticator(user, password));

    String urlStr = "http://httpbin.org/ip";

    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet request = new HttpGet(urlStr);

    CloseableHttpResponse response = httpClient.execute(request);
    try {
      GeocodingResult geocodeResult = geocode("WC1B 3DG", "GB");
      resp.getWriter().print("Your IP is: " + handleResponse(response) + ", GeocodeResult: " + (geocodeResult != null ? geocodeResult.formattedAddress : "Not found"));
    } catch (Exception e) {
      resp.getWriter().print(e.getMessage());
    }
  }

  private void showQuotaGuard(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // This could also go in app init
    URL proxyUrl = new URL(System.getenv("QUOTAGUARDSTATIC_URL"));
    String userInfo = proxyUrl.getUserInfo();
    String user = userInfo.substring(0, userInfo.indexOf(':'));
    String password = userInfo.substring(userInfo.indexOf(':') + 1);

    System.setProperty("socksProxyHost", proxyUrl.getHost());
    Authenticator.setDefault(new ProxyAuthenticator(user, password));

    String urlStr = "http://httpbin.org/ip";

    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet request = new HttpGet(urlStr);

    CloseableHttpResponse response = httpClient.execute(request);
    try {
      GeocodingResult geocodeResult = geocode("WC1B 3DG", "GB");
      resp.getWriter().print("Your IP is: " + handleResponse(response) + ", GeocodeResult: " + (geocodeResult != null ? geocodeResult.formattedAddress : "Not found"));
    } catch (Exception e) {
      resp.getWriter().print(e.getMessage());
    }
  }

  private void showFixie(HttpServletRequest request, HttpServletResponse resp)
      throws ServletException, IOException {
    URL proxyUrl = new URL(System.getenv("FIXIE_URL"));
    String userInfo = proxyUrl.getUserInfo();
    String user = userInfo.substring(0, userInfo.indexOf(':'));
    String password = userInfo.substring(userInfo.indexOf(':') + 1);

    DefaultHttpClient httpclient = new DefaultHttpClient();
    try {
      httpclient.getCredentialsProvider().setCredentials(
            new AuthScope(proxyUrl.getHost(), proxyUrl.getPort()),
            new UsernamePasswordCredentials(user, password));
      HttpHost proxy = new HttpHost(proxyUrl.getHost(), proxyUrl.getPort());
      httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
      String encodedAuth = new BASE64Encoder().encode(userInfo.getBytes());

      HttpHost target = new HttpHost("httpbin.org", 80, "http");
      HttpGet req = new HttpGet("/ip");
      req.setHeader("Host", "httpbin.org");
      req.setHeader("Proxy-Authorization", "Basic " + encodedAuth);

      System.out.println("executing request to " + target + " via "
              + proxy);
      HttpResponse rsp = httpclient.execute(target, req);
      HttpEntity entity = rsp.getEntity();

      System.out.println("----------------------------------------");
      System.out.println(rsp.getStatusLine());
      Header[] headers = rsp.getAllHeaders();
      for (int i = 0; i < headers.length; i++) {
          System.out.println(headers[i]);
      }
      System.out.println("----------------------------------------");

      resp.getWriter().print("Your IP is: ");
      resp.getWriter().print(EntityUtils.toString(entity));
      
      GeocodingResult geocodeResult = geocode("WC1B 3DG", "GB");
      resp.getWriter().print(", GeocodeResult: " + (geocodeResult != null ? geocodeResult.formattedAddress : "Not found"));
    } finally {
        // When HttpClient instance is no longer needed,
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpclient.getConnectionManager().shutdown();
    }
  }

  private static String handleResponse(CloseableHttpResponse response) throws IOException {
    StatusLine statusLine = response.getStatusLine();
    HttpEntity entity = response.getEntity();
    if (statusLine.getStatusCode() >= 300) {
      throw new HttpResponseException(
          statusLine.getStatusCode(),
          statusLine.getReasonPhrase());
    }
    if (entity == null) {
      throw new ClientProtocolException("Response contains no content");
    }
    return readStream(entity.getContent());
  }

  private static String readStream(InputStream is) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    String output = "";
    String tmp = reader.readLine();
    while (tmp != null) {
      output += tmp;
      tmp = reader.readLine();
    }
    return output;
  }

  public static void main(String[] args) throws Exception{
    Server server = new Server(Integer.valueOf(System.getenv("PORT")));
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);
    context.addServlet(new ServletHolder(new Main()),"/*");
    server.start();
    server.join();
  }

  private static class ProxyAuthenticator extends Authenticator {
    private final PasswordAuthentication passwordAuthentication;

    private ProxyAuthenticator(String user, String password) {
      passwordAuthentication = new PasswordAuthentication(user, password.toCharArray());
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      return passwordAuthentication;
    }
  }
  
	
	private static GeocodingResult geocode(String searchString, String countryCode) {
		GeoApiContext context = new GeoApiContext().setApiKey(System.getenv("GOOGLE_SERVER_API_KEY"));
		
		// Set proxy;
//		context.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port)));

		String ccTLDCountryCode = countryCode.equalsIgnoreCase("GB") ? "uk" : countryCode.toLowerCase();
		
		try {
			// e.g. for US "1600 Amphitheatre Parkway Mountain View, CA 94043"
			// https://maps.googleapis.com/maps/api/geocode/json?key=AIzaSyCuYNqhbdxzM8Ffhr6FtD3lqcWhoCSHdJo&components=country%3AGB&address=NW6+6RG&region=uk
			GeocodingResult[] results = GeocodingApi
					.geocode(context, searchString.toString())
					.region(ccTLDCountryCode)
					.components(ComponentFilter.country(countryCode))
					.await();
			
			if (results.length > 0) {
				return results[0];
			}
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		
		return null;
	}
}
