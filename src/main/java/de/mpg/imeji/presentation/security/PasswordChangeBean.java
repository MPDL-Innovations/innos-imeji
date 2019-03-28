package de.mpg.imeji.presentation.security;

import java.io.IOException;
import java.util.Calendar;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;

import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.model.User;
import de.mpg.imeji.logic.notification.email.EmailMessages;
import de.mpg.imeji.logic.notification.email.EmailService;
import de.mpg.imeji.logic.security.user.UserService;
import de.mpg.imeji.logic.security.user.pwdreset.PasswordResetController;
import de.mpg.imeji.logic.util.StringHelper;
import de.mpg.imeji.logic.util.UrlHelper;
import de.mpg.imeji.presentation.beans.SuperBean;
import de.mpg.imeji.presentation.session.BeanHelper;
import de.mpg.imeji.presentation.session.SessionBean;
import de.mpg.imeji.util.DateHelper;

@ManagedBean(name = "PasswordChangeBean")
@ViewScoped
public class PasswordChangeBean extends SuperBean {
  private static final long serialVersionUID = 2461713268070189067L;
  private String newPassword;
  private String repeatedPassword;
  private String resetEmail;
  private String token;
  private boolean resetFinished;
  private final PasswordResetController passwordresetService = new PasswordResetController();
  private String from;
  private User user;
  @ManagedProperty(value = "#{SessionBean}")
  private SessionBean sessionBean;

  @PostConstruct
  public void init() {
    newPassword = null;
    repeatedPassword = null;
    resetFinished = false;
    from = UrlHelper.getParameterValue("from");
    user = retrieveUser();
  }

  /**
   * Send Email to reset password
   * 
   * @throws ImejiException
   * @throws IOException
   */
  public void sendResetEmail() throws ImejiException, IOException {

    if (!EmailService.isValidEmail(resetEmail)) {
      BeanHelper.error(resetEmail + " " + Imeji.RESOURCE_BUNDLE.getLabel("reset_invalid_email", getLocale()));
      redirect(getNavigation().getLoginUrl());
    } else {
      User user;
      try {
        user = new UserService().retrieve(resetEmail, Imeji.adminUser);
      } catch (ImejiException e) {
        BeanHelper.error(resetEmail + ": " + Imeji.RESOURCE_BUNDLE.getMessage("error_user_not_found", getLocale()));
        redirect(getNavigation().getLoginUrl());
        return;
      }
      String url = getNavigation().getApplicationUrl() + "pwdreset?token=" + passwordresetService.generateResetToken(user);
      Calendar expirationDate = DateHelper.getCurrentDate();
      expirationDate.add(Calendar.DAY_OF_MONTH, Integer.valueOf(Imeji.CONFIG.getRegistrationTokenExpiry()));

      new EmailService().sendMail(resetEmail, null, EmailMessages.getResetRequestEmailSubject(getLocale()),
          EmailMessages.getResetRequestEmailBody(url, user, DateHelper.printDate(expirationDate), getLocale()));

      BeanHelper.info(Imeji.RESOURCE_BUNDLE.getMessage("email_password_reset_sent", getLocale()));
      redirect(getNavigation().getHomeUrl());
    }
  }

  /**
   * Reset the password
   * 
   * @throws IOException
   * @throws ImejiException
   */
  public void resetPassword() throws IOException, ImejiException {
    if (!isValidPassword()) {
      reload();
      return;
    }
    if (user != null) {
      user = passwordresetService.resetPassword(user, newPassword);
    } else {
      if (!passwordresetService.isValidToken(token)) {
        BeanHelper.error(Imeji.RESOURCE_BUNDLE.getMessage("error_password_link_invalid", getLocale()));
        redirect(getNavigation().getLoginUrl());
        return;
      }
      user = passwordresetService.resetPassword(token, newPassword);
      sessionBean.setUser(user);
      new EmailService().sendMail(user.getEmail(), null, EmailMessages.getResetConfirmEmailSubject(getLocale()),
          EmailMessages.getResetConfirmEmailBody(user, getLocale()));
    }
    BeanHelper.info(Imeji.RESOURCE_BUNDLE.getMessage("success_change_user_password", getLocale()));
    resetFinished = true;
    redirect(StringHelper.isNullOrEmptyTrim(getBackUrl()) ? getNavigation().getHomeUrl() : getBackUrl());
  }

  /**
   * Retrieve the user for which the user will be changed
   * 
   * @return
   * @throws ImejiException
   */
  private User retrieveUser() {
    if (!StringHelper.isNullOrEmptyTrim(UrlHelper.getParameterValue("email"))) {
      try {
        return new UserService().retrieve(UrlHelper.getParameterValue("email"), getSessionUser());
      } catch (ImejiException e) {
        return null;
      }
    } else {
      return getSessionUser();
    }
  }

  /**
   * Stop the password reset if the password is not valid
   * 
   * @throws IOException
   */
  private boolean isValidPassword() throws IOException {
    if (newPassword == null || newPassword.equals("")) {
      BeanHelper.error(Imeji.RESOURCE_BUNDLE.getMessage("error_empty_password", getLocale()));
      return false;
    }
    if (!newPassword.equals(repeatedPassword)) {
      BeanHelper.error(Imeji.RESOURCE_BUNDLE.getMessage("error_user_repeat_password", getLocale()));
      return false;
    }
    return true;
  }

  public String getNewPassword() {
    return newPassword;
  }

  public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
  }

  public String getRepeatedPassword() {
    return repeatedPassword;
  }

  public void setRepeatedPassword(String repeatedPassword) {
    this.repeatedPassword = repeatedPassword;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getResetEmail() {
    return resetEmail;
  }

  public void setResetEmail(String resetEmail) {
    this.resetEmail = resetEmail;
  }

  public boolean isResetFinished() {
    return resetFinished;
  }

  public void setResetFinished(boolean resetFinished) {
    this.resetFinished = resetFinished;
  }

  public SessionBean getSessionBean() {
    return sessionBean;
  }

  public void setSessionBean(SessionBean sessionBean) {
    this.sessionBean = sessionBean;
  }

  public String getFrom() {
    return from;
  }

  public User getUser() {
    return user;
  }
}
