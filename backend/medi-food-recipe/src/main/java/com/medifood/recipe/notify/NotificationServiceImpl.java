package com.medifood.recipe.notify;

import com.medifood.common.notification.NotificationEvent;
import com.medifood.common.notification.NotificationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("recipeNotificationService")
public class NotificationServiceImpl {

    @Autowired
    private NotificationEventPublisher notificationEventPublisher;

    public void send(Notification n) {
        notificationEventPublisher.publish(new NotificationEvent(
                n.getRecipientId(),
                n.getActorId(),
                n.getType(),
                n.getEntityType(),
                n.getEntityId(),
                n.getExtraId() != null ? n.getExtraId() : 0L
        ));
    }
}
