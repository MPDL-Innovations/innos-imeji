package de.mpg.imeji.logic.events.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.reflections.Reflections;

import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.events.messages.Message;
import de.mpg.imeji.logic.events.messages.Message.MessageType;

/**
 * Service managing message subscription
 * 
 * @author saquet
 *
 */
public class ListenerService {
  private static Map<MessageType, List<Listener>> subscriptions = new HashMap<>();
  private static Logger LOGGER = Logger.getLogger(ListenerService.class);

  /**
   * Initialize the {@link ListenerService} by registering all existing {@link Listener}
   */
  public void init() {
    LOGGER.info("Initializing message subscribers...");
    for (Listener s : findAllListeners()) {
      register(s);
      LOGGER.info("Registered: " + s.getClass().getName());
    }
    LOGGER.info("Initializing message subscribers done!");
  }

  /**
   * Subscribe to a particular topic
   * 
   * @param listener
   */
  public void register(Listener listener) {
    List<Listener> l = subscriptions.getOrDefault(listener.getMessageType(), new ArrayList<>());
    l.add(listener);
    for (MessageType m : listener.getMessageType()) {
      subscriptions.put(m, l);
    }
  }

  /**
   * Call all subscriber to this message
   * 
   * @param message
   */
  public void notifySubscribers(Message message) {
    subscriptions.getOrDefault(message.getType(), new ArrayList<>()).stream()
        .peek(s -> s.send(message)).forEach(s -> Imeji.getEXECUTOR().submit(s));
  }

  /**
   * Find all Classes extending {@link Listener} and return an instance of it
   * 
   * @return
   */
  private List<Listener> findAllListeners() {
    Reflections reflections = new Reflections("de.mpg.imeji");
    Set<Class<? extends Listener>> subscriberClasses = reflections.getSubTypesOf(Listener.class);
    List<Listener> l = new ArrayList<>();
    for (Class<? extends Listener> c : subscriberClasses) {
      try {
        l.add(c.newInstance());
      } catch (InstantiationException | IllegalAccessException e) {
        LOGGER.error("Error instancing " + c, e);
      }
    }
    return l;
  }
}