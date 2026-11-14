package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a {@link NotificationRequest} through the channels it asks for (or all channels when it
 * does not say). Strategies are found by the channel they declare, so a new channel only needs a
 * new {@link NotificationStrategy} bean.
 */
@Service
public class NotificationDispatcher {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

  private final Map<NotificationChannel, NotificationStrategy> strategyByChannel =
      new EnumMap<>(NotificationChannel.class);

  /** Spring passes in every NotificationStrategy bean (email, in-app, ...). */
  public NotificationDispatcher(List<NotificationStrategy> strategies) {
    for (NotificationStrategy strategy : strategies) {
      NotificationStrategy previous = strategyByChannel.put(strategy.channel(), strategy);
      if (previous != null) {
        throw new IllegalStateException("Two strategies handle channel " + strategy.channel());
      }
    }
  }

  /**
   * Sends the request on each wanted channel. A failing channel does not stop the others; after all
   * channels were tried, the first failure is rethrown so the message is retried. Channels that
   * already delivered skip the retry through their dedup key.
   */
  public void dispatch(NotificationRequest request) {
    RuntimeException firstFailure = null;

    for (NotificationStrategy strategy : strategiesFor(request)) {
      try {
        strategy.sendNotification(request);
      } catch (RuntimeException e) {
        log.warn("Notification {} failed on channel {}", request.type(), strategy.channel(), e);
        if (firstFailure == null) {
          firstFailure = e;
        } else {
          firstFailure.addSuppressed(e);
        }
      }
    }

    if (firstFailure != null) {
      throw firstFailure;
    }
  }

  /** The strategies the request wants: all of them, or only the channels it names. */
  private List<NotificationStrategy> strategiesFor(NotificationRequest request) {
    if (request.isForAllChannels()) {
      return List.copyOf(strategyByChannel.values());
    }
    return request.channels().stream().map(this::strategyFor).toList();
  }

  private NotificationStrategy strategyFor(NotificationChannel channel) {
    NotificationStrategy strategy = strategyByChannel.get(channel);
    if (strategy == null) {
      // Fail loudly: silently skipping would make the notification disappear.
      throw new IllegalArgumentException("No strategy for channel " + channel);
    }
    return strategy;
  }
}
