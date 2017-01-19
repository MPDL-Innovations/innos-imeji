package de.mpg.imeji.presentation.authentication;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

import de.mpg.imeji.logic.authentication.impl.HttpAuthentication;
import de.mpg.imeji.logic.user.UserService;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.presentation.session.SessionBean;

/**
 * {@link Filter} for imeji authentification
 *
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
@WebFilter(urlPatterns = "/*", dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD},
    asyncSupported = true)
public class AuthenticationFilter implements Filter {
  private FilterConfig filterConfig = null;
  private final Pattern jsfPattern = Pattern.compile(".*\\/jsf\\/.*\\.xhtml");
  private static final Logger LOGGER = Logger.getLogger(AuthenticationFilter.class);

  /*
   * (non-Javadoc)
   *
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
    setFilterConfig(null);
  }

  /*
   * (non-Javadoc)
   *
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse,
   * javax.servlet.FilterChain)
   */
  @Override
  public void doFilter(ServletRequest serv, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    try {
      final HttpServletRequest request = (HttpServletRequest) serv;
      final SessionBean session = getSession(request);
      if (session != null && session.getUser() == null) {
        final HttpAuthentication httpAuthentification = new HttpAuthentication(request);
        if (httpAuthentification.hasLoginInfos()) {
          session.setUser(httpAuthentification.doLogin());
        }
      } else if (session != null && session.getUser() != null) {
        if (isReloadUser(request, session.getUser())) {
          session.reloadUser();
        }
      }
    } catch (final Exception e) {
      LOGGER.info("We had some exception in Authentication filter", e);
    } finally {
      chain.doFilter(serv, resp);
    }
  }

  /**
   * True if it is necessary to reload the User. This method tried to reduce as much as possible
   * reload of the user, to avoid too much database queries.
   *
   * @param req
   * @return
   */
  private boolean isReloadUser(HttpServletRequest req, User user) {
    return isXHTMLRequest(req) && !isAjaxRequest(req) && isModifiedUser(user);
  }

  /**
   * True if the {@link User} has been modified in the database (for instance, a user has share
   * something with him)
   *
   * @param user
   * @return
   */
  private boolean isModifiedUser(User user) {
    try {
      return new UserService().isModified(user);
    } catch (final Exception e) {
      return true;
    }
  }

  /**
   * True if the request is done from an xhtml page
   *
   * @param req
   * @return
   */
  private boolean isXHTMLRequest(HttpServletRequest req) {
    final Matcher m = jsfPattern.matcher(req.getRequestURI());
    return m.matches();
  }

  /**
   * True of the request is an Ajax Request
   *
   * @param req
   * @return
   */
  private boolean isAjaxRequest(HttpServletRequest req) {
    return "partial/ajax".equals(req.getHeader("Faces-Request"));
  }


  /*
   * (non-Javadoc)
   *
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig arg0) throws ServletException {
    this.setFilterConfig(arg0);
  }

  public FilterConfig getFilterConfig() {
    return filterConfig;
  }

  public void setFilterConfig(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  /**
   * Return the {@link SessionBean} form the {@link HttpSession}
   *
   * @param req
   * @return
   */
  private SessionBean getSession(HttpServletRequest req) {
    return (SessionBean) req.getSession(true).getAttribute(SessionBean.class.getSimpleName());
  }


}