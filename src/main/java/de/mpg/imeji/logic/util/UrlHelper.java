/**
 * License: src/main/resources/license/escidoc.license
 */
package de.mpg.imeji.logic.util;

import de.mpg.imeji.presentation.util.ProxyHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;

import javax.faces.context.FacesContext;
import java.io.UnsupportedEncodingException;
import java.net.*;

/**
 * Some Method to read URLs
 * 
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
public class UrlHelper {

    private static Logger LOGGER = Logger.getLogger(UrlHelper.class);

	/**
	 * Return the value of a parameter in an url
	 * 
	 * @param parameterName
	 * @return
	 */
	public static String getParameterValue(String parameterName) {
		return FacesContext.getCurrentInstance().getExternalContext()
				.getRequestParameterMap().get(parameterName);
	}

	/**
	 * Return a value as boolean of a parameter in a url: true if value is1,
	 * false if value is -1
	 * 
	 * @param parameterName
	 * @return
	 */
	public static boolean getParameterBoolean(String parameterName) {
		String str = FacesContext.getCurrentInstance().getExternalContext()
				.getRequestParameterMap().get(parameterName);
		if ("1".equals(str)) {
			return true;
		}
		return false;
	}

	/**
	 * Check if the uri is valid
	 * 
	 * @param uri
	 * @return
	 */
	public static boolean isValidURI(URI uri) {
		try {
			HttpClient client = new HttpClient();
			GetMethod method = new GetMethod(uri.toString());
			// client.executeMethod(method);
			ProxyHelper.executeMethod(client, method);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Add to the url the parameter
	 * 
	 * @param url
	 * @param param
	 * @param value
	 * @return
	 */
	public static String addParameter(String url, String param, String value) {
		String[] params = url.split("\\?", 2);
		if (params.length > 1 && !"".equals(params[1]))
			return url + "&" + param + "=" + value;
		return url + "?" + param + "=" + value;
	}

	/**
	 * Decode a value from with UTF-8
	 * 
	 * @param s
	 * @return
	 */
	public static String decode(String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			new RuntimeException(e);
		}
		return null;
	}

	/**
	 * Encode a value with UTF-8
	 * 
	 * @param s
	 * @return
	 */
	public static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			new RuntimeException(e);
		}
		return null;
	}

    /**
     * Encode url with query. Escape blanks with %20 etc.
     *
     * @param urlStr
     * @return
     */
    public static String encodeQuery(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(),
                    url.getQuery(),
                    url.getRef()).toURL().toString();
        } catch (MalformedURLException | URISyntaxException e) {
            LOGGER.info("Cannot parse url: " + urlStr + "\n" + e.getLocalizedMessage());
            return  urlStr;
        }
    }
}