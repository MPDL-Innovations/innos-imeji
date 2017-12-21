package de.mpg.imeji.logic.notification.email;

import java.util.Date;
import java.util.Locale;

import org.apache.log4j.Logger;

import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.model.CollectionImeji;
import de.mpg.imeji.logic.model.Item;
import de.mpg.imeji.logic.model.User;
import de.mpg.imeji.logic.security.authorization.util.SecurityUtil;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.util.UrlHelper;

/**
 * List of text (messages) sent from imeji to users via email
 *
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
public class EmailMessages {

  public static String getSuccessCollectionDeleteMessage(String collectionName, Locale locale) {
    return getBundle("success_collection_delete", locale).replace("XXX_collectionName_XXX",
        collectionName);
  }

  /**
   * Email content when a new password is sent
   *
   * @param password
   * @param email
   * @param username
   * @return
   */
  public static String getNewPasswordMessage(String password, String email, String username,
      Locale locale) {
    final String msg = "";
    try {
      final String name = Imeji.CONFIG.getInstanceName();
      return getEmailOnAccountAction_Body(password, email, username, "email_new_password", locale)
          .replace("XXX_INSTANCE_NAME_XXX", name);
    } catch (final Exception e) {
      Logger.getLogger(EmailMessages.class).info("Will return empty message, due to some error", e);
      return msg;
    }
  }

  /**
   * Email content when a collection has been shared with the addressee by the sender
   *
   * @param sender
   * @param dest
   * @param collectionName
   * @param collectionLink
   * @return
   */
  public static String getSharedCollectionMessage(String sender, String dest, String collectionName,
      String collectionLink, Locale locale) {
    String message = getBundle("email_shared_collection", locale);
    message = message.replace("XXX_USER_NAME_XXX,", dest).replace("XXX_NAME_XXX", collectionName)
        .replace("XXX_LINK_XXX", collectionLink).replace("XXX_SENDER_NAME_XXX", sender);
    return message;
  }

  /**
   * Read the message bundle (for example: messages_en.properties)
   *
   * @param messageBundle
   * @return
   */
  private static String getBundle(String messageBundle, Locale locale) {
    return Imeji.RESOURCE_BUNDLE.getMessage(messageBundle, locale);
  }

  /**
   * Create the content of an email according to the parameters
   *
   * @param password
   * @param email
   * @param username
   * @param message_bundle
   * @return
   */
  private static String getEmailOnAccountAction_Body(String password, String email, String username,
      String message_bundle, Locale locale) {
    final String userPage = Imeji.PROPERTIES.getApplicationURL() + "user?email=" + email;
    String emailMessage = getBundle(message_bundle, locale);
    if ("email_new_user".equals(message_bundle)) {
      emailMessage =
          emailMessage.replace("XXX_LINK_TO_APPLICATION_XXX", Imeji.PROPERTIES.getApplicationURL());
    }
    emailMessage =
        emailMessage.replace("XXX_USER_NAME_XXX", username).replace("XXX_LOGIN_XXX", email)
            .replace("XXX_PASSWORD_XXX", password).replace("XXX_LINK_TO_USER_PAGE_XXX", userPage)
            .replaceAll("XXX_INSTANCE_NAME_XXX", Imeji.CONFIG.getInstanceName());
    return emailMessage;
  }

  /**
   * Create the subject of the email being send, for either new account or new password
   *
   * @param newAccount
   * @return
   */
  public static String getEmailOnAccountAction_Subject(boolean newAccount, Locale locale) {
    String emailsubject = "";
    if (newAccount) {
      emailsubject = getBundle("email_new_user_subject", locale);
    } else {
      emailsubject = getBundle("email_new_password_subject", locale);
    }
    return emailsubject.replaceAll("XXX_INSTANCE_NAME_XXX", Imeji.CONFIG.getInstanceName());
  }

  /**
   * Create the subject of the registration request email
   *
   * @return
   */
  public static String getEmailOnRegistrationRequest_Subject(Locale locale) {
    return getBundle("email_registration_request_subject", locale)
        .replaceAll("XXX_INSTANCE_NAME_XXX", Imeji.CONFIG.getInstanceName());
  }

  /**
   * 
   * @param locale
   * @return
   */
  public static String getEmailToCreatedUser_Subject(Locale locale) {
    return getBundle("email_new_user_subject", locale).replaceAll("XXX_INSTANCE_NAME_XXX",
        Imeji.CONFIG.getInstanceName());
  }

  /**
   * 
   * @param locale
   * @return
   */
  public static String getEmailToCreatedUser_Body(Locale locale, String userName,
      String linkToSetPassword) {
    return getBundle("email_new_user", locale)
        .replaceAll("XXX_INSTANCE_NAME_XXX", Imeji.CONFIG.getInstanceName())
        .replaceAll("XXX_USER_NAME_XXX,", userName)
        .replaceAll("XXX_SET_PASSWORD_LINK_XXX", linkToSetPassword);
  }


  /**
   * Create the body of the registration request email
   *
   * @param to
   * @param password
   * @param contactEmail
   * @param session @return
   * @param navigationUrl
   */
  public static String getEmailOnRegistrationRequest_Body(User to, String url, String contactEmail,
      String expirationDate, Locale locale, String navigationUrl) {
    return getBundle("email_registration_request_body", locale)
        .replace("XXX_USER_NAME_XXX", to.getPerson().getFirstnameLastname())
        .replaceAll("XXX_INSTANCE_NAME_XXX", Imeji.CONFIG.getInstanceName())
        .replaceAll("XXX_CONTACT_EMAIL_XXX", contactEmail).replace("XXX_ACTIVATION_LINK_XXX", url)
        .replaceAll("XXX_EXPIRATION_DATE_XXX", expirationDate);
  }

  /**
   * Create the subject of an account activation email
   *
   * @param session
   * @return
   */
  public static String getEmailOnAccountActivation_Subject(User u, Locale locale) {
    return getBundle("email_account_activation_subject", locale).replace("XXX_USER_NAME_XXX",
        u.getPerson().getFirstnameLastname());
  }

  public static String getEmailOnAccountActivation_Body(User u, Locale locale) {
    return getBundle("email_account_activation_body", locale)
        .replaceAll("XXX_INSTANCE_NAME_XXX", Imeji.CONFIG.getInstanceName())
        .replace("XXX_USER_NAME_XXX", u.getPerson().getFirstnameLastname())
        .replace("XXX_USER_EMAIL_XXX", u.getEmail())
        .replace("XXX_ORGANIZATION_XXX", u.getPerson().getOrganizationString())
        .replace("XXX_TIME_XXX", new Date().toString()).replace("XXX_CREATE_COLLECTIONS_XXX",
            Boolean.toString(SecurityUtil.authorization().hasCreateCollectionGrant(u)));
  }


  /**
   * Generate email body for "Send notification email by item download" feature
   *
   * @param to
   * @param actor
   * @param item
   * @param c
   * @param session
   * @return
   */
  public static String getEmailOnItemDownload_Body(User to, User actor, Item item,
      CollectionImeji c, Locale locale) {
    return getBundle("email_item_downloaded_body", locale)
        .replace("XXX_USER_NAME_XXX", to.getPerson().getFirstnameLastname())
        .replace("XXX_ITEM_ID_XXX", ObjectHelper.getId(item.getId()))
        .replace("XXX_ITEM_LINK_XXX",
            Imeji.PROPERTIES.getApplicationURL() + "item/" + ObjectHelper.getId(item.getId()))
        .replace("XXX_COLLECTION_NAME_XXX", c.getTitle())
        .replace("XXX_COLLECTION_LINK_XXX",
            Imeji.PROPERTIES.getApplicationURL() + "collection/" + ObjectHelper.getId(c.getId()))
        .replace("XXX_ACTOR_NAME_XXX",
            (actor != null ? actor.getPerson().getCompleteName() : "non_logged_in_user"))
        .replace("XXX_ACTOR_EMAIL_XXX", (actor != null ? actor.getEmail() : ""))
        .replace("XXX_TIME_XXX", new Date().toString());
  }

  /**
   * Generate email subject for "Send notification email by item download" feature
   *
   * @param item
   * @param session
   * @return
   */
  public static String getEmailOnItemDownload_Subject(Item item, Locale locale) {
    return getBundle("email_item_downloaded_subject", locale).replace("XXX_ITEM_ID_XXX",
        item.getIdString());
  }

  /**
   * Generate email body for "Send notification email by item download" feature
   *
   * @param to
   * @param actor
   * @param itemsDownloaded
   * @param url
   * @param session
   * @return
   */
  public static String getEmailOnZipDownload_Body(User to, User actor, String itemsDownloaded,
      String url, Locale locale) {
    return getBundle("email_zip_images_downloaded_body", locale)
        .replace("XXX_USER_NAME_XXX", to.getPerson().getFirstnameLastname())
        .replace("XXX_ACTOR_NAME_XXX",
            (actor != null ? actor.getPerson().getCompleteName() : "non_logged_in_user"))
        .replace("XXX_ACTOR_EMAIL_XXX", (actor != null ? actor.getEmail() : ""))
        .replace("XXX_TIME_XXX", new Date().toString())
        .replace("XXX_ITEMS_DOWNLOADED_XXX", itemsDownloaded)
        .replaceAll("XXX_COLLECTION_XXX", getBundle("collection", locale))
        .replaceAll("XXX_FILTERED_XXX", getBundle("filtered", locale))
        .replaceAll("XXX_ITEMS_COUNT_XXX", getBundle("items_count", locale))
        .replace("XXX_QUERY_URL_XXX", UrlHelper.encodeQuery(url));
  }

  /**
   * Generate email subject for "Send notification email by item download" feature
   *
   * @param session
   * @return
   */
  public static String getEmailOnZipDownload_Subject(Locale locale) {
    return getBundle("email_zip_images_downloaded_subject", locale);
  }

  /**
   * Email content when a collection has been shared with the addressee by the sender
   *
   * @param sender
   * @param dest
   * @param collectionName
   * @param collectionLink
   * @return
   */
  public static String getUnshareMessage(String sender, String dest, String title,
      String collectionLink, Locale locale) {
    String message = getBundle("email_unshared_object", locale);
    message = message.replace("XXX_USER_NAME_XXX,", dest).replace("XXX_NAME_XXX", title)
        .replace("XXX_LINK_XXX", collectionLink).replace("XXX_SENDER_NAME_XXX", sender);
    return message;
  }

}
