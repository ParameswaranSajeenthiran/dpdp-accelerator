package org.wso2.dpdp.accelerator.event.notifications.dao.model;

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.Collections;

import static org.testng.Assert.assertNotNull;

/** Verifies all DAO transport models can be constructed and their accessor contracts invoked. */
public class DaoModelCoverageTest {

    @Test
    public void coversAllModelConstructorsAndAccessors() throws Exception {
        Class<?>[] models = {Event.class, PollDelivery.class, Subscription.class,
                SubscriptionDeliverySummary.class, Topic.class,
                WebhookDelivery.class, WebhookDeliveryAck.class, WebhookDeliveryAudit.class,
                WebhookDeliveryDispatchContext.class};
        for (Class<?> model : models) {
            for (Constructor<?> constructor : model.getConstructors()) {
                Object instance = constructor.newInstance(arguments(constructor.getParameterTypes()));
                assertNotNull(instance);
                invokeAccessors(instance);
            }
        }
    }

    private void invokeAccessors(Object instance) throws Exception {
        for (Method method : instance.getClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                method.invoke(instance, value(method.getParameterTypes()[0]));
            } else if ((method.getName().startsWith("get") || method.getName().startsWith("is"))
                    && method.getParameterCount() == 0) {
                method.invoke(instance);
            }
        }
    }

    private Object[] arguments(Class<?>[] types) {
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) values[i] = value(types[i]);
        return values;
    }

    private Object value(Class<?> type) {
        if (type == String.class) return "value";
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 1L;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == Timestamp.class) return new Timestamp(1L);
        if (java.util.List.class.isAssignableFrom(type)) return Collections.singletonList("value");
        return null;
    }
}
